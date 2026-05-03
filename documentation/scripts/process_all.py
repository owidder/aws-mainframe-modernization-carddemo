#!/usr/bin/env python3
"""
process_all.py  –  Analyse all COBOL files and generate HTML documentation.

Usage:
    python process_all.py                    # analyse new programs only (incremental)
    python process_all.py --force            # re-analyse all programs from scratch
    python process_all.py --patch-prelude    # add prelude annotations to existing JSONs
    python process_all.py --limit 5          # first 5 programs only (for testing)
    python process_all.py --app-dir app/cbl  # restrict to one source directory

The pipeline for each program:
  1. analyze_cobol.py  → documentation/data/<PROGRAM>.json
  2. generate_html.py  → documentation/html/<PROGRAM>.html
  3. generate_html.py  → documentation/html/index.html  (after all programs)
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path
from datetime import datetime

BASE_DIR    = Path(__file__).parent.parent.parent   # repo root
APP_DIR     = BASE_DIR / 'app'
SCRIPTS_DIR = Path(__file__).parent
DATA_DIR    = SCRIPTS_DIR.parent / 'data'
HTML_DIR    = SCRIPTS_DIR.parent / 'html'

COBOL_EXTENSIONS = {'.cbl', '.CBL', '.cob', '.COB'}


# ---------------------------------------------------------------------------
# Discovery
# ---------------------------------------------------------------------------

def find_cobol_files(app_dir: Path) -> list[Path]:
    """Recursively find all COBOL source files, excluding EBCDIC data."""
    files = []
    for f in app_dir.rglob('*'):
        if f.is_file() and f.suffix in COBOL_EXTENSIONS:
            if 'EBCDIC' not in str(f) and 'data' not in f.parts:
                files.append(f)
    return sorted(files)


# ---------------------------------------------------------------------------
# Status helpers
# ---------------------------------------------------------------------------

def _needs_prelude_patch(json_path: Path) -> bool:
    """Returns True if the JSON was analysed before explain_prelude_lines() was added."""
    try:
        doc = json.loads(json_path.read_text(encoding='utf-8'))
        return not doc.get('meta', {}).get('prelude_annotated', False)
    except Exception:
        return True


def program_status(cbl: Path, data_dir: Path) -> str:
    """Returns 'missing' | 'needs-prelude' | 'ok'."""
    json_path = data_dir / f'{cbl.stem.upper()}.json'
    if not json_path.exists():
        return 'missing'
    if _needs_prelude_patch(json_path):
        return 'needs-prelude'
    return 'ok'


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description='Analyse all COBOL programs and generate HTML documentation.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python process_all.py                    # analyse new programs (skip existing)
  python process_all.py --patch-prelude    # add prelude annotations to all existing JSONs
  python process_all.py --force            # full re-analysis of every program
  python process_all.py --app-dir app/cbl  # restrict to main source directory
  python process_all.py --limit 3          # process 3 programs only (testing)
""")
    parser.add_argument('--force',         action='store_true', help='Re-analyse even if JSON exists')
    parser.add_argument('--patch-prelude', action='store_true', help='Add prelude annotations to existing JSONs without full re-analysis')
    parser.add_argument('--limit',         type=int, default=0,  help='Maximum number of files to process (0 = all)')
    parser.add_argument('--model',         default='gcp/claude-sonnet-4-6', help='Claude model to use')
    parser.add_argument('--app-dir',       type=Path, default=APP_DIR,   help='Root directory to search for COBOL files')
    parser.add_argument('--data-dir',      type=Path, default=DATA_DIR,  help='Directory for JSON data files')
    parser.add_argument('--html-dir',      type=Path, default=HTML_DIR,  help='Directory for generated HTML')
    parser.add_argument('--no-html',       action='store_true', help='Skip HTML generation (analyse only)')
    args = parser.parse_args()

    args.data_dir.mkdir(parents=True, exist_ok=True)
    args.html_dir.mkdir(parents=True, exist_ok=True)

    files = find_cobol_files(args.app_dir)

    # Status overview
    status_counts = {'missing': 0, 'needs-prelude': 0, 'ok': 0}
    for cbl in files:
        status_counts[program_status(cbl, args.data_dir)] += 1

    print('CardDemo COBOL Documentation Pipeline')
    print('======================================')
    print(f'Programs found     : {len(files)}')
    print(f'  fully annotated  : {status_counts["ok"]}')
    print(f'  missing prelude  : {status_counts["needs-prelude"]}')
    print(f'  not yet analysed : {status_counts["missing"]}')
    print(f'Source directory   : {args.app_dir}')
    print(f'Data directory     : {args.data_dir}')
    print(f'HTML directory     : {args.html_dir}')
    print(f'Model              : {args.model}')
    if args.force:         print(f'Mode               : FULL RE-ANALYSIS (--force)')
    elif args.patch_prelude: print(f'Mode               : PATCH PRELUDE only')
    else:                  print(f'Mode               : Incremental (skip fully annotated)')
    print(f'Start              : {datetime.now().strftime("%H:%M:%S")}')
    print()

    if args.limit:
        files = files[:args.limit]

    python          = sys.executable
    analyze_script  = SCRIPTS_DIR / 'analyze_cobol.py'
    generate_script = SCRIPTS_DIR / 'generate_html.py'

    ok = 0
    skipped = 0
    failed = []

    for i, cbl in enumerate(files, 1):
        status = program_status(cbl, args.data_dir)
        json_path = args.data_dir / f'{cbl.stem.upper()}.json'

        # Decide what to do
        if args.force:
            action = 'analyse'
        elif args.patch_prelude:
            if status == 'missing':   action = 'analyse'   # analyse from scratch if no JSON yet
            elif status == 'needs-prelude': action = 'patch'
            else:                     action = 'skip'
        else:
            # Default: only analyse programs with no JSON yet; skip everything else
            action = 'analyse' if status == 'missing' else 'skip'

        if action == 'skip':
            print(f'[{i:3d}/{len(files)}] OK (skipped): {cbl.name}')
            skipped += 1
            continue

        label = 'Patching' if action == 'patch' else 'Analysing'
        print(f'[{i:3d}/{len(files)}] {label}: {cbl.name}')

        cmd = [
            python, str(analyze_script),
            str(cbl),
            '--output-dir', str(args.data_dir),
            '--model', args.model,
        ]
        if action == 'patch':
            cmd.append('--patch-prelude')
        elif args.force:
            cmd.append('--force')

        result = subprocess.run(cmd, capture_output=False)
        if result.returncode == 0:
            ok += 1
            if not args.no_html:
                subprocess.run([
                    python, str(generate_script),
                    '--program', cbl.stem.upper(),
                    '--input-dir', str(args.data_dir),
                    '--output-dir', str(args.html_dir),
                ], capture_output=True)
        else:
            failed.append(cbl.name)
            print(f'  [ERROR] {cbl.name}')

    # Regenerate full index
    if not args.no_html and (ok > 0 or skipped > 0):
        print(f'\nGenerating index...')
        subprocess.run([
            python, str(generate_script),
            '--input-dir', str(args.data_dir),
            '--output-dir', str(args.html_dir),
        ])

    print(f'\n======================================')
    print(f'Done   : {ok} processed, {skipped} skipped, {len(failed)} errors')
    print(f'End    : {datetime.now().strftime("%H:%M:%S")}')
    if failed:
        print('Failed:')
        for f in failed:
            print(f'  - {f}')
    print(f'\nHTML: file://{args.html_dir}/index.html')


if __name__ == '__main__':
    main()
