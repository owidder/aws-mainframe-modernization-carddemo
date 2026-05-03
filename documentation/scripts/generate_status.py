#!/usr/bin/env python3
"""
generate_status.py  –  Generates documentation/html/status/index.html

Shows per-program status for all COBOL programs in app/cbl/:
  • Complete          – JSON analysed, prelude annotated, HTML generated
  • Needs patch       – JSON exists but prelude annotation missing
  • Not yet analysed  – no JSON data file

Usage:
    python documentation/scripts/generate_status.py
"""

import json
import sys
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent.parent
CBL_DIR   = REPO_ROOT / 'app' / 'cbl'
DATA_DIR  = REPO_ROOT / 'documentation' / 'data'
HTML_DIR  = REPO_ROOT / 'documentation' / 'html'
OUT_DIR   = HTML_DIR / 'status'

# Status constants
COMPLETE   = 'complete'
NEEDS_PATCH = 'needs-patch'
NOT_STARTED = 'not-started'

MODULE_ORDER = [
    'Authentication', 'Customer', 'Account', 'Card',
    'Transaction', 'Reporting', 'Admin', 'Utility', 'Unknown', '',
]


def collect(cbl_dir: Path, data_dir: Path, html_dir: Path) -> list[dict]:
    rows = []
    for cbl in sorted(cbl_dir.glob('*.[Cc][Bb][Ll]')):
        name      = cbl.stem.upper()
        json_path = data_dir / f'{name}.json'
        html_path = html_dir / f'{name}.html'

        has_json = json_path.exists()
        has_html = html_path.exists()
        prelude  = False
        n_vars   = 0
        n_paras  = 0
        n_lines  = 0
        n_expl   = 0
        desc     = ''
        prog_type = ''
        module   = ''

        if has_json:
            try:
                doc      = json.loads(json_path.read_text(encoding='utf-8'))
                prelude  = doc.get('meta', {}).get('prelude_annotated', False)
                n_vars   = len(doc.get('variables', []))
                n_paras  = len(doc.get('paragraphs', []))
                n_lines  = len(doc.get('lines', []))
                n_expl   = sum(1 for l in doc.get('lines', []) if l.get('explanation'))
                desc     = doc.get('meta', {}).get('description', '')
                prog_type = doc.get('meta', {}).get('type', '')
                module   = doc.get('meta', {}).get('module', '')
            except Exception:
                pass

        if not has_json:
            status = NOT_STARTED
        elif prelude and has_html:
            status = COMPLETE
        else:
            status = NEEDS_PATCH

        expl_pct = round(100 * n_expl / n_lines) if n_lines else 0

        rows.append({
            'name': name, 'status': status,
            'has_json': has_json, 'has_html': has_html, 'prelude': prelude,
            'n_vars': n_vars, 'n_paras': n_paras, 'n_lines': n_lines,
            'n_expl': n_expl, 'expl_pct': expl_pct,
            'desc': desc, 'type': prog_type, 'module': module,
        })

    # Sort by module then name
    rows.sort(key=lambda r: (MODULE_ORDER.index(r['module']) if r['module'] in MODULE_ORDER else 99, r['name']))
    return rows


def badge(status: str) -> str:
    if status == COMPLETE:
        return '<span class="badge badge-ok">Complete</span>'
    if status == NEEDS_PATCH:
        return '<span class="badge badge-patch">Needs patch</span>'
    return '<span class="badge badge-missing">Not started</span>'


def action_html(row: dict) -> str:
    name = row['name']
    cbl  = f'app/cbl/{name}.cbl'
    if row['status'] == COMPLETE:
        return '<span class="action-done">Nothing to do</span>'
    if row['status'] == NEEDS_PATCH:
        return (
            f'<code class="cmd">'
            f'.venv/bin/python3 documentation/scripts/analyze_cobol.py {cbl} --patch-prelude'
            f'</code>'
        )
    # NOT_STARTED
    return (
        f'<code class="cmd">'
        f'.venv/bin/python3 documentation/scripts/analyze_cobol.py {cbl}'
        f'</code>'
    )


def bar(pct: int) -> str:
    color = '#a6e3a1' if pct >= 60 else '#f9e2af' if pct >= 40 else '#f38ba8'
    return (
        f'<div class="bar-wrap" title="{pct}% of lines annotated">'
        f'<div class="bar-fill" style="width:{pct}%;background:{color}"></div>'
        f'<span class="bar-label">{pct}%</span>'
        f'</div>'
    )


def render(rows: list[dict]) -> str:
    n_total    = len(rows)
    n_complete = sum(1 for r in rows if r['status'] == COMPLETE)
    n_patch    = sum(1 for r in rows if r['status'] == NEEDS_PATCH)
    n_missing  = sum(1 for r in rows if r['status'] == NOT_STARTED)
    generated  = datetime.now().strftime('%Y-%m-%d %H:%M')

    # Build table rows
    tbody = []
    current_module = None
    for r in rows:
        if r['module'] != current_module:
            current_module = r['module']
            label = r['module'] or 'Unknown'
            tbody.append(
                f'<tr class="module-sep"><td colspan="7">{label}</td></tr>'
            )

        html_link = f'<a href="../{r["name"]}.html" class="prog-link">{r["name"]}</a>' \
                    if r['has_html'] else r['name']
        tbody.append(f'''
        <tr class="prog-row status-{r["status"]}">
          <td class="td-name">{html_link}</td>
          <td class="td-type">{r["type"] or "–"}</td>
          <td>{badge(r["status"])}</td>
          <td class="td-stats">{r["n_paras"]}&nbsp;para / {r["n_vars"]}&nbsp;var</td>
          <td class="td-bar">{bar(r["expl_pct"])}</td>
          <td class="td-desc">{r["desc"] or "–"}</td>
          <td class="td-action">{action_html(r)}</td>
        </tr>''')

    # Commands section
    patch_names = [r['name'] for r in rows if r['status'] == NEEDS_PATCH]
    missing_names = [r['name'] for r in rows if r['status'] == NOT_STARTED]

    cmd_blocks = []
    if patch_names:
        cmd_blocks.append(f'''
        <div class="cmd-block">
          <div class="cmd-title">Add prelude annotations to all {len(patch_names)} programs at once
            <span class="cmd-est">~{len(patch_names)*1.5:.0f} – {len(patch_names)*2:.0f} min</span>
          </div>
          <pre class="cmd-pre">.venv/bin/python3 documentation/scripts/process_all.py --patch-prelude --app-dir app/cbl</pre>
          <div class="cmd-sub">This adds IDENTIFICATION / ENVIRONMENT / DATA DIVISION line annotations
          to existing JSON files without running a full re-analysis.</div>
        </div>''')
    if missing_names:
        cmd_blocks.append(f'''
        <div class="cmd-block">
          <div class="cmd-title">Analyse {len(missing_names)} programs that have no JSON yet
            <span class="cmd-est">~{len(missing_names)*2:.0f} – {len(missing_names)*3:.0f} min</span>
          </div>
          <pre class="cmd-pre">.venv/bin/python3 documentation/scripts/process_all.py --app-dir app/cbl</pre>
          <div class="cmd-sub">Runs the full 4-phase analysis (business context → variables → prelude → paragraphs)
          for each program not yet in <code>documentation/data/</code>.</div>
        </div>''')
    if not cmd_blocks:
        cmd_blocks.append('<div class="cmd-block cmd-done">All programs are fully documented.</div>')

    cmd_section = '\n'.join(cmd_blocks)

    return f'''<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Documentation Status – CardDemo app/cbl/</title>
  <style>
    :root {{
      --bg:      #1e1e2e; --bg2: #181825; --bg3: #313244;
      --border:  #45475a; --text: #cdd6f4; --sub:  #a6adc8;
      --comment: #6c7086; --gold: #f9e2af; --cyan: #89dceb;
      --green:   #a6e3a1; --red:  #f38ba8; --yellow: #f9e2af;
      --keyword: #cba6f7;
    }}
    *, *::before, *::after {{ box-sizing: border-box; margin: 0; padding: 0; }}
    body {{ background: var(--bg); color: var(--text);
            font-family: "Segoe UI", system-ui, sans-serif;
            font-size: .9rem; padding: 2rem; }}

    h1 {{ font-size: 1.6rem; color: var(--gold); margin-bottom: .3rem; }}
    .subtitle {{ color: var(--sub); font-size: .85rem; margin-bottom: 2rem; }}

    /* Summary cards */
    .summary {{ display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: 2rem; }}
    .stat-card {{
      background: var(--bg2); border: 1px solid var(--border);
      border-radius: .5rem; padding: 1rem 1.5rem; min-width: 140px;
    }}
    .stat-card .num  {{ font-size: 2rem; font-weight: 700; line-height: 1; }}
    .stat-card .lbl  {{ color: var(--sub); font-size: .78rem; margin-top: .3rem; }}
    .num-ok     {{ color: var(--green);  }}
    .num-patch  {{ color: var(--yellow); }}
    .num-miss   {{ color: var(--red);    }}
    .num-total  {{ color: var(--cyan);   }}

    /* Section title */
    .section-title {{
      font-size: 1rem; font-weight: 700; color: var(--gold);
      margin: 2rem 0 .75rem;
      padding-bottom: .35rem; border-bottom: 1px solid var(--border);
    }}

    /* Command blocks */
    .cmd-block {{
      background: var(--bg2); border: 1px solid var(--border);
      border-radius: .5rem; padding: 1rem 1.25rem; margin-bottom: .75rem;
    }}
    .cmd-done {{ color: var(--green); font-weight: 600; }}
    .cmd-title {{ font-weight: 600; color: var(--cyan); margin-bottom: .5rem; }}
    .cmd-est   {{ font-size: .75rem; color: var(--sub); font-weight: 400;
                  margin-left: .75rem; }}
    .cmd-pre   {{
      background: var(--bg3); border: 1px solid var(--border);
      border-radius: .3rem; padding: .6rem 1rem;
      font-family: "Cascadia Code", "Fira Code", monospace; font-size: .8rem;
      color: var(--green); overflow-x: auto; white-space: pre;
      margin-bottom: .5rem;
    }}
    .cmd-sub   {{ font-size: .78rem; color: var(--sub); line-height: 1.5; }}
    .cmd-sub code {{ background: var(--bg3); border-radius: .2rem;
                     padding: .05rem .3rem; font-size: .75rem; color: var(--keyword); }}

    /* Table */
    table {{ width: 100%; border-collapse: collapse; font-size: .82rem; }}
    th {{
      background: var(--bg3); color: var(--sub); text-align: left;
      padding: .45rem .7rem; font-size: .73rem; text-transform: uppercase;
      letter-spacing: .05em; border-bottom: 1px solid var(--border);
      position: sticky; top: 0; z-index: 1;
    }}
    td {{ padding: .4rem .7rem; border-bottom: 1px solid #31324433; vertical-align: middle; }}
    tr.prog-row:hover td {{ background: #31324422; }}

    tr.module-sep td {{
      background: var(--bg2); color: var(--keyword);
      font-size: .72rem; text-transform: uppercase; letter-spacing: .1em;
      padding: .5rem .7rem .2rem; border-bottom: 1px solid var(--border);
      font-weight: 700;
    }}

    .td-name   {{ white-space: nowrap; }}
    .td-type   {{ white-space: nowrap; color: var(--sub); font-size: .78rem; }}
    .td-stats  {{ white-space: nowrap; color: var(--sub); font-size: .78rem; }}
    .td-desc   {{ color: var(--sub); font-size: .78rem; line-height: 1.4; max-width: 30ch; }}
    .td-action {{ min-width: 32ch; }}
    .td-bar    {{ min-width: 90px; }}

    .prog-link {{ color: var(--cyan); text-decoration: none; font-family: monospace;
                  font-weight: 600; font-size: .85rem; }}
    .prog-link:hover {{ text-decoration: underline; }}

    /* Badges */
    .badge {{
      display: inline-block; padding: .15rem .55rem; border-radius: 1rem;
      font-size: .7rem; font-weight: 700; white-space: nowrap;
    }}
    .badge-ok      {{ background: #1a3324; color: var(--green);  border: 1px solid #a6e3a144; }}
    .badge-patch   {{ background: #2d2a18; color: var(--yellow); border: 1px solid #f9e2af44; }}
    .badge-missing {{ background: #2d1b1b; color: var(--red);    border: 1px solid #f38ba844; }}

    /* Progress bar */
    .bar-wrap  {{ position: relative; background: var(--bg3); border-radius: .2rem;
                  height: .9rem; width: 80px; overflow: hidden; }}
    .bar-fill  {{ height: 100%; border-radius: .2rem; transition: width .3s; }}
    .bar-label {{ position: absolute; right: 3px; top: 0; font-size: .62rem;
                  line-height: .9rem; color: var(--bg); font-weight: 700; mix-blend-mode: difference; }}

    .action-done {{ color: var(--green); font-size: .78rem; }}
    .cmd {{ font-family: "Cascadia Code", "Fira Code", monospace; font-size: .72rem;
            color: var(--green); background: var(--bg3); border-radius: .2rem;
            padding: .15rem .4rem; word-break: break-all; }}

    footer {{ margin-top: 3rem; color: var(--comment); font-size: .73rem; }}
  </style>
</head>
<body>

<h1>Documentation Status</h1>
<div class="subtitle">
  COBOL programs in <code>app/cbl/</code> — CardDemo Mainframe Modernisation &middot;
  Generated {generated}
</div>

<!-- Summary -->
<div class="summary">
  <div class="stat-card"><div class="num num-total">{n_total}</div><div class="lbl">Total programs</div></div>
  <div class="stat-card"><div class="num num-ok">{n_complete}</div><div class="lbl">Fully documented</div></div>
  <div class="stat-card"><div class="num num-patch">{n_patch}</div><div class="lbl">Need prelude patch</div></div>
  <div class="stat-card"><div class="num num-miss">{n_missing}</div><div class="lbl">Not yet analysed</div></div>
</div>

<!-- What to do -->
<div class="section-title">What to do</div>
{cmd_section}

<!-- Program table -->
<div class="section-title" style="margin-top:2rem;">All Programs</div>
<table>
  <thead>
    <tr>
      <th>Program</th>
      <th>Type</th>
      <th>Status</th>
      <th>Size</th>
      <th>Lines annotated</th>
      <th>Description</th>
      <th>Command to complete</th>
    </tr>
  </thead>
  <tbody>
    {''.join(tbody)}
  </tbody>
</table>

<footer>
  Generated by <code>generate_status.py</code> &middot;
  Source: <code>app/cbl/</code> &middot;
  Data: <code>documentation/data/</code>
  &middot; <a href="../index.html" style="color:var(--sub)">← Program overview</a>
</footer>

</body>
</html>'''


def main():
    rows = collect(CBL_DIR, DATA_DIR, HTML_DIR)
    html = render(rows)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / 'index.html'
    out.write_text(html, encoding='utf-8')
    n_complete = sum(1 for r in rows if r['status'] == COMPLETE)
    n_patch    = sum(1 for r in rows if r['status'] == NEEDS_PATCH)
    n_missing  = sum(1 for r in rows if r['status'] == NOT_STARTED)
    print(f'Generated: {out}')
    print(f'  {len(rows)} programs  |  {n_complete} complete  |  {n_patch} need patch  |  {n_missing} not started')
    print(f'\nOpen: file://{out}')


if __name__ == '__main__':
    main()
