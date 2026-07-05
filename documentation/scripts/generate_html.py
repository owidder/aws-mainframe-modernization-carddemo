#!/usr/bin/env python3
"""
generate_html.py  –  Render JSON analysis files to HTML via Jinja2.

Usage:
    python generate_html.py                        # all JSON files in ../data/
    python generate_html.py --program COSGN00C     # single program
    python generate_html.py --input-dir ../data --output-dir ../html
"""

import json
import argparse
from datetime import datetime
from pathlib import Path
from collections import defaultdict

from jinja2 import Environment, FileSystemLoader

SCRIPTS_DIR  = Path(__file__).parent
TEMPLATE_DIR = SCRIPTS_DIR / 'templates'
DEFAULT_DATA = SCRIPTS_DIR.parent / 'data'
DEFAULT_HTML = SCRIPTS_DIR.parent / 'html'


def load_doc(json_path: Path) -> dict:
    return json.loads(json_path.read_text(encoding='utf-8'))


def render_program(doc: dict, html_dir: Path, env: Environment) -> Path:
    tmpl = env.get_template('program.html.j2')
    out  = html_dir / f"{doc['meta']['program_name']}.html"
    out.write_text(tmpl.render(doc=doc), encoding='utf-8')
    return out


def render_index(docs: list[dict], html_dir: Path, env: Environment) -> Path:
    modules = defaultdict(list)
    for doc in sorted(docs, key=lambda d: d['meta']['program_name']):
        m = doc['meta'].get('module', 'Other')
        modules[m].append({
            'program_name': doc['meta']['program_name'],
            'description':  doc['meta'].get('description', ''),
            'type':         doc['meta'].get('type', ''),
            'lines':        len(doc.get('lines', [])),
            'variables':    len(doc.get('variables', [])),
            'paragraphs':   len(doc.get('paragraphs', [])),
            'filename':     f"{doc['meta']['program_name']}.html",
        })

    tmpl = env.get_template('index.html.j2')
    out  = html_dir / 'index.html'
    out.write_text(tmpl.render(
        programs=docs,
        modules=dict(sorted(modules.items())),
        generated_at=datetime.now().strftime('%d.%m.%Y %H:%M'),
    ), encoding='utf-8')
    return out


def main():
    parser = argparse.ArgumentParser(description='JSON → HTML rendering via Jinja2')
    parser.add_argument('--program',    help='Render only this program (e.g. COSGN00C)')
    parser.add_argument('--input-dir',  type=Path, default=DEFAULT_DATA)
    parser.add_argument('--output-dir', type=Path, default=DEFAULT_HTML)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    env = Environment(
        loader=FileSystemLoader(str(TEMPLATE_DIR)),
        autoescape=False,
    )

    json_files = sorted(args.input_dir.glob('*.json'))
    if not json_files:
        print(f'No JSON files found in {args.input_dir}')
        return

    if args.program:
        json_files = [f for f in json_files if f.stem.upper() == args.program.upper()]
        if not json_files:
            print(f'No JSON found for program {args.program}.')
            return

    docs = []
    for jf in json_files:
        doc = load_doc(jf)
        # Skip non-program data files (e.g. transformation.json) that have no
        # program_name; those are rendered by their own dedicated generators.
        if 'program_name' not in doc.get('meta', {}):
            continue
        out = render_program(doc, args.output_dir, env)
        print(f'  → {out.name}')
        docs.append(doc)

    # Always regenerate the index from all available program data
    all_docs = [d for d in (load_doc(f) for f in sorted(args.input_dir.glob('*.json')))
                if 'program_name' in d.get('meta', {})]
    idx = render_index(all_docs, args.output_dir, env)
    print(f'  → {idx.name}  ({len(all_docs)} programs)')


if __name__ == '__main__':
    main()
