#!/usr/bin/env python3
"""
generate_transformation.py  –  Render the Java Transformation Status page.

Reads the self-contained data file documentation/data/transformation.json and
renders it via templates/transformation.html.j2 to
documentation/html/java-transformation.html.

Usage:
    python documentation/scripts/generate_transformation.py
"""

import json
from pathlib import Path

from jinja2 import Environment, FileSystemLoader

SCRIPTS_DIR  = Path(__file__).parent
TEMPLATE_DIR = SCRIPTS_DIR / 'templates'
DATA_FILE    = SCRIPTS_DIR.parent / 'data' / 'transformation.json'
OUT_FILE     = SCRIPTS_DIR.parent / 'html' / 'java-transformation.html'


def main():
    env = Environment(
        loader=FileSystemLoader(str(TEMPLATE_DIR)),
        autoescape=False,
    )
    doc = json.loads(DATA_FILE.read_text(encoding='utf-8'))
    tmpl = env.get_template('transformation.html.j2')
    OUT_FILE.write_text(tmpl.render(doc=doc), encoding='utf-8')

    done = sum(1 for m in doc['modules'] if m['status'] == 'done')
    print(f'  → {OUT_FILE.name}  ({len(doc["modules"])} modules, {done} transformed)')


if __name__ == '__main__':
    main()
