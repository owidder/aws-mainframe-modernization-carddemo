#!/usr/bin/env python3
"""
generate_migration_html.py — Renders migration TODO HTML pages from JSON data.

Data source  : documentation/data/migration/<PROGRAM>.json
Templates    : documentation/scripts/templates/migration.html.j2
               documentation/scripts/templates/migration_index.html.j2
Output       : documentation/html/migration/<PROGRAM>.html
               documentation/html/migration/index.html

Usage:
    # Render all JSON files found in data/migration/:
    python documentation/scripts/generate_migration_html.py

    # Render a single program:
    python documentation/scripts/generate_migration_html.py --program CBACT01C

    # Override output directory:
    python documentation/scripts/generate_migration_html.py --output-dir /tmp/out
"""

import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path

try:
    from jinja2 import Environment, FileSystemLoader, select_autoescape
except ImportError:
    print("Error: jinja2 is not installed. Run: pip install jinja2", file=sys.stderr)
    sys.exit(1)

# ---------------------------------------------------------------------------
# Paths (resolved relative to this script's location)
# ---------------------------------------------------------------------------

SCRIPT_DIR   = Path(__file__).parent
REPO_ROOT    = SCRIPT_DIR.parent.parent
DATA_DIR     = REPO_ROOT / "documentation" / "data" / "migration"
TEMPLATE_DIR = SCRIPT_DIR / "templates"
DEFAULT_OUT  = REPO_ROOT / "documentation" / "html" / "migration"


# ---------------------------------------------------------------------------
# Jinja2 custom filters
# ---------------------------------------------------------------------------

def id_prefix(task_id: str) -> str:
    """Extract the letter prefix from a task ID, e.g. 'AD-1' → 'AD'."""
    m = re.match(r'^([A-Z]+)', str(task_id))
    return m.group(1) if m else 'AD'


def truncate_filter(s: str, length: int = 55, killwords: bool = False, end: str = '…') -> str:
    s = str(s)
    return s if len(s) <= length else s[:length].rstrip() + end


# ---------------------------------------------------------------------------
# Jinja2 environment
# ---------------------------------------------------------------------------

def make_env() -> 'Environment':
    env = Environment(
        loader=FileSystemLoader(str(TEMPLATE_DIR)),
        autoescape=select_autoescape(['html']),
        trim_blocks=True,
        lstrip_blocks=True,
    )
    env.filters['id_prefix'] = id_prefix
    env.filters['truncate']  = truncate_filter
    return env


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_json(path: Path) -> dict:
    with path.open(encoding='utf-8') as fh:
        return json.load(fh)


def count_open_tasks(data: dict) -> int:
    total = 0
    for section in data.get('sections', []):
        for task in section.get('tasks', []):
            if not task.get('done', False):
                total += 1
    return total


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

def render_program(data: dict, env: 'Environment', out_dir: Path) -> Path:
    """Render one migration page and return the output path."""
    template = env.get_template('migration.html.j2')
    html = template.render(**data)

    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"{data['program']}.html"
    out_path.write_text(html, encoding='utf-8')
    return out_path


def render_index(programs: list[dict], env: 'Environment', out_dir: Path) -> Path:
    """Render the migration index page and return the output path."""
    template = env.get_template('migration_index.html.j2')
    html = template.render(
        programs=programs,
        generated_at=datetime.now().strftime('%Y-%m-%d %H:%M'),
    )

    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / 'index.html'
    out_path.write_text(html, encoding='utf-8')
    return out_path


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description='Render migration TODO HTML from JSON data files.'
    )
    parser.add_argument(
        '--program', metavar='NAME',
        help='Render only this program (e.g. CBACT01C). Default: render all.'
    )
    parser.add_argument(
        '--output-dir', type=Path, default=DEFAULT_OUT,
        help=f'Output directory (default: {DEFAULT_OUT})'
    )
    parser.add_argument(
        '--data-dir', type=Path, default=DATA_DIR,
        help=f'Directory with migration JSON files (default: {DATA_DIR})'
    )
    args = parser.parse_args()

    if not args.data_dir.is_dir():
        print(f"Error: data directory not found: {args.data_dir}", file=sys.stderr)
        sys.exit(1)

    env = make_env()

    # Discover JSON files
    if args.program:
        json_files = [args.data_dir / f"{args.program.upper()}.json"]
        if not json_files[0].exists():
            print(f"Error: {json_files[0]} not found", file=sys.stderr)
            sys.exit(1)
    else:
        json_files = sorted(args.data_dir.glob('*.json'))
        if not json_files:
            print(f"No JSON files found in {args.data_dir}", file=sys.stderr)
            sys.exit(1)

    # Render program pages
    all_programs: list[dict] = []
    for jf in json_files:
        data = load_json(jf)
        out_path = render_program(data, env, args.output_dir)
        open_tasks = count_open_tasks(data)
        print(f"Generated: {out_path}")

        all_programs.append({
            'program':    data['program'],
            'type':       data.get('type', 'Batch'),
            'module':     data.get('module', ''),
            'summary':    data.get('summary', ''),
            'open_tasks': open_tasks,
        })

    # Always regenerate the index (includes all known programs, not just those
    # rendered in this run)
    if not args.program:
        # Full run: index reflects exactly what we just rendered
        index_programs = all_programs
    else:
        # Single-program run: collect metadata from all existing JSON files
        index_programs = []
        for jf in sorted(args.data_dir.glob('*.json')):
            d = load_json(jf)
            index_programs.append({
                'program':    d['program'],
                'type':       d.get('type', 'Batch'),
                'module':     d.get('module', ''),
                'summary':    d.get('summary', ''),
                'open_tasks': count_open_tasks(d),
            })

    idx_path = render_index(index_programs, env, args.output_dir)
    print(f"Generated: {idx_path}")
    print(f"Open: file://{args.output_dir / (args.program + '.html') if args.program else idx_path}")


if __name__ == '__main__':
    main()
