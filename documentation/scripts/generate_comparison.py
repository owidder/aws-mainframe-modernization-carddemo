#!/usr/bin/env python3
"""
generate_comparison.py  –  Render the COBOL ↔ Java side-by-side comparison pages.

Data source : documentation/data/comparison/<PROGRAM>.json
Template     : documentation/scripts/templates/comparison.html.j2
Output       : documentation/html/comparison/<PROGRAM>.html

Usage:
    python documentation/scripts/generate_comparison.py                 # all
    python documentation/scripts/generate_comparison.py --program CBACT01C
"""

import json
import argparse
from pathlib import Path

from jinja2 import Environment, FileSystemLoader

SCRIPTS_DIR  = Path(__file__).parent
TEMPLATE_DIR = SCRIPTS_DIR / 'templates'
DATA_DIR     = SCRIPTS_DIR.parent / 'data' / 'comparison'
OUT_DIR      = SCRIPTS_DIR.parent / 'html' / 'comparison'


def main():
    parser = argparse.ArgumentParser(description='Render COBOL ↔ Java comparison pages')
    parser.add_argument('--program', help='Render only this program (e.g. CBACT01C)')
    parser.add_argument('--data-dir', type=Path, default=DATA_DIR)
    parser.add_argument('--output-dir', type=Path, default=OUT_DIR)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    env = Environment(loader=FileSystemLoader(str(TEMPLATE_DIR)), autoescape=False)
    # Preserve the data file's key order in tojson output (Jinja defaults to sort_keys=True)
    env.policies['json.dumps_kwargs'] = {'sort_keys': False}
    tmpl = env.get_template('comparison.html.j2')

    files = sorted(args.data_dir.glob('*.json'))
    if args.program:
        files = [f for f in files if f.stem.upper() == args.program.upper()]
    if not files:
        print('No comparison data files found.')
        return

    for jf in files:
        doc = json.loads(jf.read_text(encoding='utf-8'))
        out = args.output_dir / f"{doc['program']}.html"
        out.write_text(tmpl.render(**doc), encoding='utf-8')
        print(f"  → {out.name}  ({doc['cobol_lines']} COBOL / {doc['java_lines']} Java lines, "
              f"{len(doc['java_files'])} files, {len(doc['mapping'])} mappings)")


if __name__ == '__main__':
    main()
