#!/usr/bin/env python3
"""
process_all.py  –  Analyse all COBOL files and generate HTML documentation.

Usage:
    python process_all.py                   # all programs (incremental)
    python process_all.py --force           # re-analyse everything
    python process_all.py --limit 5         # first 5 programs only
    python process_all.py --model qwen2:7b  # different Ollama model
"""

import argparse
import subprocess
import sys
from pathlib import Path
from datetime import datetime

BASE_DIR    = Path(__file__).parent.parent.parent  # repo root
APP_DIR     = BASE_DIR / 'app'
SCRIPTS_DIR = Path(__file__).parent
DATA_DIR    = SCRIPTS_DIR.parent / 'data'
HTML_DIR    = SCRIPTS_DIR.parent / 'html'

COBOL_EXTENSIONS = {'.cbl', '.CBL', '.cob', '.COB'}


def find_cobol_files(app_dir: Path) -> list[Path]:
    files = []
    for f in app_dir.rglob('*'):
        if f.is_file() and f.suffix in COBOL_EXTENSIONS:
            # Skip EBCDIC data files
            if 'EBCDIC' not in str(f) and 'data' not in f.parts:
                files.append(f)
    return sorted(files)


def main():
    parser = argparse.ArgumentParser(description='Analyse all COBOL files and generate documentation')
    parser.add_argument('--force',  action='store_true', help='Re-analyse already-processed files')
    parser.add_argument('--limit',  type=int, default=0,  help='Maximum number of files to process (0=all)')
    parser.add_argument('--model',  default='gcp/claude-sonnet-4-6', help='Claude model')
    parser.add_argument('--app-dir', type=Path, default=APP_DIR)
    parser.add_argument('--data-dir', type=Path, default=DATA_DIR)
    parser.add_argument('--html-dir', type=Path, default=HTML_DIR)
    args = parser.parse_args()

    args.data_dir.mkdir(parents=True, exist_ok=True)
    args.html_dir.mkdir(parents=True, exist_ok=True)

    files = find_cobol_files(args.app_dir)
    if args.limit:
        files = files[:args.limit]

    print(f'CardDemo COBOL Documentation')
    print(f'============================')
    print(f'Programs found     : {len(files)}')
    print(f'Data directory     : {args.data_dir}')
    print(f'HTML directory     : {args.html_dir}')
    print(f'Model              : {args.model}')
    print(f'Start              : {datetime.now().strftime("%H:%M:%S")}')
    print()

    python = sys.executable
    analyze_script  = SCRIPTS_DIR / 'analyze_cobol.py'
    generate_script = SCRIPTS_DIR / 'generate_html.py'

    ok = 0
    failed = []

    for i, cbl in enumerate(files, 1):
        json_path = args.data_dir / f"{cbl.stem.upper()}.json"
        if json_path.exists() and not args.force:
            print(f'[{i:3d}/{len(files)}] Skipped: {cbl.name}')
            ok += 1
            continue

        print(f'[{i:3d}/{len(files)}] {cbl.name}')
        cmd = [
            python, str(analyze_script),
            str(cbl),
            '--output-dir', str(args.data_dir),
            '--model', args.model,
        ]
        if args.force:
            cmd.append('--force')

        result = subprocess.run(cmd, capture_output=False)
        if result.returncode == 0:
            ok += 1
            # Generate HTML immediately
            subprocess.run([
                python, str(generate_script),
                '--program', cbl.stem.upper(),
                '--input-dir', str(args.data_dir),
                '--output-dir', str(args.html_dir),
            ], capture_output=True)
        else:
            failed.append(cbl.name)
            print(f'  [ERROR] {cbl.name}')

    # Final index
    print(f'\nGenerating index...')
    subprocess.run([
        python, str(generate_script),
        '--input-dir', str(args.data_dir),
        '--output-dir', str(args.html_dir),
    ])

    print(f'\n============================')
    print(f'Done: {ok} OK, {len(failed)} errors')
    print(f'End : {datetime.now().strftime("%H:%M:%S")}')
    if failed:
        print('Failed:')
        for f in failed:
            print(f'  - {f}')
    print(f'\nHTML documentation: {args.html_dir}/index.html')


if __name__ == '__main__':
    main()
