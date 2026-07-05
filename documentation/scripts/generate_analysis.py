#!/usr/bin/env python3
"""
generate_analysis.py — Convert Markdown analysis documents to HTML via Jinja2.

Usage:
    python generate_analysis.py                          # all markdown files
    python generate_analysis.py --file 06-geschaeftslogik.md
    python generate_analysis.py --input-dir ../../analyse --output-dir ../analysis
"""

import re
import argparse
import html as html_module
from datetime import datetime
from pathlib import Path

from jinja2 import Environment, FileSystemLoader

# ── Paths ──────────────────────────────────────────────────────────────────
SCRIPTS_DIR  = Path(__file__).parent
TEMPLATE_DIR = SCRIPTS_DIR / 'templates'
ANALYSE_DIR  = SCRIPTS_DIR.parent.parent / 'analyse'
OUTPUT_DIR   = SCRIPTS_DIR.parent / 'analysis'
DATA_DIR     = SCRIPTS_DIR.parent / 'data'

# ── Navigation definition ──────────────────────────────────────────────────
ANALYSIS_PAGES = [
    ('README.md',              'index.html',               'Documentation Overview'),
    ('00-uebersicht.md',       '00-overview.html',         'Application Overview'),
    ('01-programme.md',        '01-programs.html',         'Program Inventory'),
    ('02-aufrufhierarchie.md', '02-call-hierarchy.html',   'Call Hierarchy'),
    ('03-datenstrukturen.md',  '03-data-structures.html',  'Data Structures'),
    ('04-schnittstellen.md',   '04-interfaces.html',       'External Interfaces'),
    ('05-fachliche-module.md', '05-business-modules.html', 'Business Modules'),
    ('06-geschaeftslogik.md',  '06-business-logic.html',   'Detailed Business Logic'),
    ('07-java-mapping.md',     '07-java-mapping.html',     'Java Mapping'),
    # HTML-fragment source (rich hand-authored content, not markdown):
    ('08-cobol-java-patterns.html', '08-cobol-java-patterns.html', 'COBOL → Java Patterns'),
]

# src→(html, title) lookup
MD_TO_HTML = {src: (html, title) for src, html, title in ANALYSIS_PAGES}

# Internal link map: markdown filename → HTML filename
LINK_MAP = {src: html for src, html, _ in ANALYSIS_PAGES}
LINK_MAP.update({
    # Also map without leading path prefix
    Path(src).name: html for src, html, _ in ANALYSIS_PAGES
})


# ── Markdown → HTML converter ──────────────────────────────────────────────

def slugify(text: str) -> str:
    """Create a URL-safe id from heading text."""
    text = re.sub(r'[`*\[\]()#]', '', text)
    text = re.sub(r'\s+', '-', text.strip().lower())
    text = re.sub(r'[^a-z0-9\-]', '', text)
    return text or 'section'


def escape(text: str) -> str:
    return html_module.escape(text)


def _strip_tags(text: str) -> str:
    return re.sub(r'<[^>]+>', '', text).strip()


def html_fragment_toc(content: str) -> list[dict]:
    """Build the sidebar TOC for a pre-built HTML content fragment.

    Sections are anchored on <hr id="..."> immediately followed by an <h2>
    (level 2); individual patterns use <h3 id="...">  (level 3).
    """
    toc = []
    pattern = re.compile(
        r'<hr\s+id="([^"]+)">\s*<h2[^>]*>(.*?)</h2>'   # section: hr id + following h2
        r'|<h3[^>]*\bid="([^"]+)"[^>]*>(.*?)</h3>',     # pattern: h3 with id
        re.DOTALL,
    )
    for m in pattern.finditer(content):
        if m.group(1) is not None:
            toc.append({'id': m.group(1), 'text': _strip_tags(m.group(2)), 'level': 2})
        else:
            toc.append({'id': m.group(3), 'text': _strip_tags(m.group(4)), 'level': 3})
    return toc


def process_inline(text: str) -> str:
    """Apply inline markdown: code, bold, italic, links, source refs."""
    # Source file references like `app/cbl/PROGRAM.cbl:123` — styled as srcref
    text = re.sub(
        r'`(app/[^`]+\.(?:cbl|cpy|jcl|j2|py)(?::\d+)?)`',
        lambda m: f'<span class="srcref">{escape(m.group(1))}</span>',
        text
    )
    # Inline code
    text = re.sub(r'`([^`]+)`', lambda m: f'<code>{escape(m.group(1))}</code>', text)
    # Bold
    text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
    # Italic (single *)
    text = re.sub(r'(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)', r'<em>\1</em>', text)
    # Links: remap internal .md links to .html
    def rewrite_link(m):
        link_text = m.group(1)
        href = m.group(2)
        # Internal link to another analysis doc
        for md_name, html_name in LINK_MAP.items():
            if href.endswith(md_name) or href == html_name:
                return f'<a href="{html_name}">{link_text}</a>'
        # Link to COBOL source file (local filesystem) — show as srcref, no href
        if re.search(r'app/[^)]+\.(?:cbl|cpy|jcl)', href, re.I):
            path_display = re.sub(r'^\.\./', '', href)
            return f'<span class="srcref">{escape(path_display)}</span>'
        # External or other links — keep as-is
        return f'<a href="{href}">{link_text}</a>'

    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', rewrite_link, text)
    return text


def md_to_html(md_text: str) -> tuple[str, list[dict]]:
    """
    Convert markdown text to HTML.
    Returns (html_string, toc_list).
    toc_list items: {'id': str, 'text': str, 'level': int}
    """
    lines = md_text.splitlines()
    out = []
    toc = []
    i = 0
    slug_counts: dict[str, int] = {}

    def unique_slug(text):
        base = slugify(text)
        n = slug_counts.get(base, 0)
        slug_counts[base] = n + 1
        return base if n == 0 else f'{base}-{n}'

    def flush_paragraph(buf):
        if buf:
            content = process_inline(' '.join(buf))
            out.append(f'<p>{content}</p>')
            buf.clear()

    paragraph_buf: list[str] = []

    while i < len(lines):
        line = lines[i]

        # ── Fenced code block ─────────────────────────
        m = re.match(r'^```(\w*)', line)
        if m:
            flush_paragraph(paragraph_buf)
            lang = m.group(1) or 'text'
            code_lines = []
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                code_lines.append(escape(lines[i]))
                i += 1
            code_body = '\n'.join(code_lines)
            out.append(f'<pre><code class="language-{lang}">{code_body}</code></pre>')
            i += 1
            continue

        # ── Horizontal rule ───────────────────────────
        if re.match(r'^---+\s*$', line):
            flush_paragraph(paragraph_buf)
            out.append('<hr>')
            i += 1
            continue

        # ── Headings ──────────────────────────────────
        m = re.match(r'^(#{1,4})\s+(.*)', line)
        if m:
            flush_paragraph(paragraph_buf)
            level = len(m.group(1))
            raw_text = m.group(2).strip()
            # Strip inline code backticks for slug
            slug_text = re.sub(r'`[^`]+`', '', raw_text)
            slug = unique_slug(slug_text)
            html_text = process_inline(raw_text)
            out.append(f'<h{level} id="{slug}">{html_text}</h{level}>')
            if level in (2, 3):
                plain = re.sub(r'<[^>]+>', '', html_text)
                toc.append({'id': slug, 'text': plain, 'level': level})
            i += 1
            continue

        # ── Table ─────────────────────────────────────
        if '|' in line and i + 1 < len(lines) and re.match(r'^\|[\s\-|:]+\|', lines[i + 1]):
            flush_paragraph(paragraph_buf)
            # Header row
            headers = [c.strip() for c in line.strip().strip('|').split('|')]
            i += 2  # skip separator line
            rows = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                cells = [c.strip() for c in lines[i].strip().strip('|').split('|')]
                rows.append(cells)
                i += 1
            html_parts = ['<table>',
                          '<thead><tr>',
                          *[f'<th>{process_inline(h)}</th>' for h in headers],
                          '</tr></thead>',
                          '<tbody>']
            for row in rows:
                html_parts.append('<tr>')
                for cell in row:
                    html_parts.append(f'<td>{process_inline(cell)}</td>')
                html_parts.append('</tr>')
            html_parts.append('</tbody></table>')
            out.append('\n'.join(html_parts))
            continue

        # ── Blockquote ────────────────────────────────
        if line.startswith('> '):
            flush_paragraph(paragraph_buf)
            bq_lines = []
            while i < len(lines) and lines[i].startswith('> '):
                bq_lines.append(lines[i][2:])
                i += 1
            content = process_inline(' '.join(bq_lines))
            out.append(f'<blockquote>{content}</blockquote>')
            continue

        # ── Unordered list ────────────────────────────
        if re.match(r'^(\s*)[*\-]\s+', line):
            flush_paragraph(paragraph_buf)
            list_lines = []
            while i < len(lines) and re.match(r'^(\s*)[*\-]\s+', lines[i]):
                m2 = re.match(r'^(\s*)[*\-]\s+(.*)', lines[i])
                indent = len(m2.group(1))
                list_lines.append((indent, m2.group(2)))
                i += 1
            out.append('<ul>')
            for _, item_text in list_lines:
                out.append(f'<li>{process_inline(item_text)}</li>')
            out.append('</ul>')
            continue

        # ── Ordered list ──────────────────────────────
        if re.match(r'^\d+\.\s+', line):
            flush_paragraph(paragraph_buf)
            out.append('<ol>')
            while i < len(lines) and re.match(r'^\d+\.\s+', lines[i]):
                item = re.sub(r'^\d+\.\s+', '', lines[i])
                out.append(f'<li>{process_inline(item)}</li>')
                i += 1
            out.append('</ol>')
            continue

        # ── Empty line — flush paragraph ──────────────
        if line.strip() == '':
            flush_paragraph(paragraph_buf)
            i += 1
            continue

        # ── Regular paragraph text ────────────────────
        paragraph_buf.append(line.strip())
        i += 1

    flush_paragraph(paragraph_buf)
    return '\n'.join(out), toc


# ── Program pages list (for sidebar) ──────────────────────────────────────

def get_program_pages(data_dir: Path) -> list[dict]:
    pages = []
    for jf in sorted(data_dir.glob('*.json')):
        pages.append({'name': jf.stem, 'filename': f'{jf.stem}.html'})
    return pages


# ── Render one analysis page ───────────────────────────────────────────────

def render_page(
    src_file: Path,
    out_filename: str,
    page_title: str,
    nav_pages: list[dict],
    program_pages: list[dict],
    env: Environment,
    output_dir: Path,
) -> Path:
    if src_file.suffix.lower() == '.html':
        # Pre-built HTML content fragment (not markdown). Use verbatim and
        # derive the TOC from <hr id>+<h2> (level 2) and <h3 id> (level 3).
        content_html = src_file.read_text(encoding='utf-8')
        toc = html_fragment_toc(content_html)
    else:
        md_text = src_file.read_text(encoding='utf-8')
        content_html, toc = md_to_html(md_text)

    page = {
        'title':        page_title,
        'filename':     out_filename,
        'source_file':  str(src_file.name),
        'content_html': content_html,
        'toc':          toc,
    }

    tmpl = env.get_template('analysis.html.j2')
    html_out = tmpl.render(
        page=page,
        nav_pages=nav_pages,
        program_pages=program_pages,
        generated_at=datetime.now().strftime('%Y-%m-%d %H:%M'),
    )

    out_path = output_dir / out_filename
    out_path.write_text(html_out, encoding='utf-8')
    return out_path


# ── Main ───────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description='Markdown analysis docs → HTML (Jinja2)')
    parser.add_argument('--file',       help='Single markdown file to convert (filename only)')
    parser.add_argument('--input-dir',  type=Path, default=ANALYSE_DIR)
    parser.add_argument('--output-dir', type=Path, default=OUTPUT_DIR)
    parser.add_argument('--data-dir',   type=Path, default=DATA_DIR)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    env = Environment(
        loader=FileSystemLoader(str(TEMPLATE_DIR)),
        autoescape=False,
    )

    nav_pages = [
        {'filename': html, 'title': title}
        for _, html, title in ANALYSIS_PAGES
    ]
    program_pages = get_program_pages(args.data_dir)

    # Select files to process
    if args.file:
        targets = [(src, html, title)
                   for src, html, title in ANALYSIS_PAGES
                   if Path(src).name == args.file or src == args.file]
        if not targets:
            print(f'File not in ANALYSIS_PAGES: {args.file}')
            return
    else:
        targets = ANALYSIS_PAGES

    for src_name, out_name, title in targets:
        src_path = args.input_dir / src_name
        if not src_path.exists():
            print(f'  SKIP (not found): {src_path}')
            continue
        out = render_page(
            src_file=src_path,
            out_filename=out_name,
            page_title=title,
            nav_pages=nav_pages,
            program_pages=program_pages,
            env=env,
            output_dir=args.output_dir,
        )
        print(f'  → {out.name}')

    print(f'\nDone — {len(targets)} file(s) written to {args.output_dir}')


if __name__ == '__main__':
    main()
