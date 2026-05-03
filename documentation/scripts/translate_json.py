#!/usr/bin/env python3
"""
translate_json.py – Translate German text in JSON data files to English via Claude API.

Usage:
    python translate_json.py                         # all JSON files in ../data/
    python translate_json.py --file CBACT01C.json    # single file
    python translate_json.py --data-dir ../data
"""

import json
import argparse
import sys
from pathlib import Path

import anthropic

SCRIPTS_DIR = Path(__file__).parent
DATA_DIR    = SCRIPTS_DIR.parent / 'data'
CLAUDE_SETTINGS = Path.home() / '.claude' / 'settings.json'

BATCH_SIZE = 60   # texts per API call
TRANSLATE_MODEL = 'gcp/claude-sonnet-4-6'


def get_client() -> anthropic.Anthropic:
    settings = json.loads(CLAUDE_SETTINGS.read_text()) if CLAUDE_SETTINGS.exists() else {}
    env = settings.get('env', {})
    api_key  = env.get('ANTHROPIC_AUTH_TOKEN') or env.get('ANTHROPIC_API_KEY')
    base_url = env.get('ANTHROPIC_BASE_URL')
    if not api_key:
        print('Error: No API key in ~/.claude/settings.json', file=sys.stderr)
        sys.exit(1)
    return anthropic.Anthropic(api_key=api_key, base_url=base_url)


def translate_batch(client: anthropic.Anthropic, texts: list[str]) -> list[str]:
    """Translate a batch of German texts to English. Returns same-length list."""
    if not texts:
        return []

    # Build numbered input so the model preserves order
    numbered = '\n'.join(f'{i+1}. {t}' for i, t in enumerate(texts))
    prompt = f"""Translate the following numbered German texts to English.
These are business/technical descriptions of COBOL program logic for a credit card management system.
Keep the same meaning, concise style, and technical terms (COBOL variable names, file names, etc.) unchanged.

Reply ONLY with a JSON array of translated strings in the same order:
["translation1", "translation2", ...]

Texts to translate:
{numbered}"""

    resp = client.messages.create(
        model=TRANSLATE_MODEL,
        max_tokens=4096,
        temperature=0,
        messages=[{'role': 'user', 'content': prompt}],
    )
    text = resp.content[0].text.strip()
    # Extract JSON array
    if '```json' in text:
        text = text.split('```json')[1].split('```')[0].strip()
    elif '```' in text:
        text = text.split('```')[1].split('```')[0].strip()
    start = text.find('[')
    end   = text.rfind(']')
    if start != -1 and end != -1:
        text = text[start:end+1]
    result = json.loads(text)
    if isinstance(result, list) and len(result) == len(texts):
        return result
    # Fallback: return originals
    print(f'  [Warning] Translation batch mismatch: got {len(result)}, expected {len(texts)}')
    return texts


def translate_strings(client: anthropic.Anthropic, items: list[str]) -> list[str]:
    """Translate a list of strings in batches."""
    results = []
    for start in range(0, len(items), BATCH_SIZE):
        batch = items[start:start + BATCH_SIZE]
        translated = translate_batch(client, batch)
        results.extend(translated)
        print(f'    translated {min(start + BATCH_SIZE, len(items))}/{len(items)}')
    return results


def needs_translation(text: str) -> bool:
    """Heuristic: detect German text."""
    if not text or text.strip() in ('', '(analysis not available)', '(Analyse nicht verfügbar)'):
        return False
    german_markers = [
        ' und ', ' der ', ' die ', ' das ', ' ist ', ' wird ', ' werden ',
        ' für ', ' mit ', ' von ', ' aus ', ' eine ', ' eines ', ' einen ',
        ' werden ', ' durch ', ' nicht ', ' oder ', ' wenn ', ' dass ',
        ' Programm', ' Datei', ' Konto', ' Transaktion', ' Benutzer',
        'ä', 'ö', 'ü', 'Ä', 'Ö', 'Ü', 'ß',
    ]
    return any(m in text for m in german_markers)


def translate_file(json_path: Path, client: anthropic.Anthropic) -> None:
    print(f'  {json_path.name}')
    doc = json.loads(json_path.read_text(encoding='utf-8'))
    changed = False

    # --- meta fields ---
    meta = doc.get('meta', {})
    meta_fields = ['description', 'process_description']
    for field in meta_fields:
        val = meta.get(field, '')
        if needs_translation(val):
            [translated] = translate_strings(client, [val])
            meta[field] = translated
            changed = True

    # business_rules (list of strings)
    rules = meta.get('business_rules', [])
    to_translate = [(i, r) for i, r in enumerate(rules) if needs_translation(r)]
    if to_translate:
        idxs, texts = zip(*to_translate)
        translated = translate_strings(client, list(texts))
        for i, t in zip(idxs, translated):
            rules[i] = t
        changed = True

    # files dict (values)
    files_dict = meta.get('files', {})
    keys_to_translate = [k for k, v in files_dict.items() if needs_translation(v)]
    if keys_to_translate:
        translated = translate_strings(client, [files_dict[k] for k in keys_to_translate])
        for k, t in zip(keys_to_translate, translated):
            files_dict[k] = t
        changed = True

    # --- variables ---
    variables = doc.get('variables', [])
    var_indices = [i for i, v in enumerate(variables) if needs_translation(v.get('description', ''))]
    if var_indices:
        print(f'    variables: {len(var_indices)} to translate')
        texts = [variables[i]['description'] for i in var_indices]
        translated = translate_strings(client, texts)
        for i, t in zip(var_indices, translated):
            variables[i]['description'] = t
        changed = True

    # --- paragraphs ---
    paragraphs = doc.get('paragraphs', [])
    para_indices = [i for i, p in enumerate(paragraphs) if needs_translation(p.get('description', ''))]
    if para_indices:
        print(f'    paragraphs: {len(para_indices)} to translate')
        texts = [paragraphs[i]['description'] for i in para_indices]
        translated = translate_strings(client, texts)
        for i, t in zip(para_indices, translated):
            paragraphs[i]['description'] = t
        changed = True

    # --- line explanations ---
    lines = doc.get('lines', [])
    line_indices = [i for i, l in enumerate(lines) if needs_translation(l.get('explanation', ''))]
    if line_indices:
        print(f'    lines: {len(line_indices)} explanations to translate')
        texts = [lines[i]['explanation'] for i in line_indices]
        translated = translate_strings(client, texts)
        for i, t in zip(line_indices, translated):
            lines[i]['explanation'] = t
        changed = True

    if changed:
        json_path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f'    → saved {json_path.name}')
    else:
        print(f'    → no changes needed')


def main():
    parser = argparse.ArgumentParser(description='Translate German JSON data files to English')
    parser.add_argument('--file',     help='Single JSON file to translate (filename only)')
    parser.add_argument('--data-dir', type=Path, default=DATA_DIR)
    args = parser.parse_args()

    client = get_client()

    if args.file:
        targets = [args.data_dir / args.file]
    else:
        targets = sorted(args.data_dir.glob('*.json'))

    for path in targets:
        if not path.exists():
            print(f'  Not found: {path}')
            continue
        try:
            translate_file(path, client)
        except Exception as e:
            print(f'  [Error] {path.name}: {e}')

    print('\nDone.')


if __name__ == '__main__':
    main()
