#!/usr/bin/env python3
"""
analyze_cobol.py  –  Analyzes a COBOL file with the Claude API (Anthropic)
and saves the result as a JSON data file.

Goal of explanations: BUSINESS meaning of each line, not the technical
COBOL syntax. Example:
  Bad: "MOVE DALYTRAN-ID TO TRAN-ID copies the variable."
  Good: "The transaction number from the input record is copied into the posting
         record so the transaction can later be traced back to its original source."

Strategy:
  0. Qdrant index → load copybooks, called programs, similar programs
  1. Full program + index context → Claude → business context
  2. Variables + copybook contents + business context → Claude → business variable descriptions
  3. Each paragraph + copybook contents + business context → Claude → line-by-line business descriptions

Usage:
    python analyze_cobol.py <path/to/file.cbl> [--output-dir ../data]
    python analyze_cobol.py <path/to/file.cbl> --model gcp/claude-sonnet-4-6
"""

import json
import re
import sys
import argparse
from datetime import datetime
from pathlib import Path

import anthropic
import ollama
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue

DEFAULT_MODEL    = "gcp/claude-sonnet-4-6"
REPO_ROOT        = Path(__file__).parent.parent.parent
CLAUDE_SETTINGS  = Path.home() / ".claude" / "settings.json"
QDRANT_URL       = "http://localhost:6335"
COLLECTION_NAME  = "carddemo"
EMBEDDING_MODEL  = "nomic-embed-text"

_client: anthropic.Anthropic | None = None

def get_client() -> anthropic.Anthropic:
    global _client
    if _client is None:
        settings = json.loads(CLAUDE_SETTINGS.read_text()) if CLAUDE_SETTINGS.exists() else {}
        env = settings.get("env", {})
        api_key  = env.get("ANTHROPIC_AUTH_TOKEN") or env.get("ANTHROPIC_API_KEY")
        base_url = env.get("ANTHROPIC_BASE_URL")
        if not api_key:
            print("Error: No API key found in ~/.claude/settings.json.", file=sys.stderr)
            sys.exit(1)
        _client = anthropic.Anthropic(api_key=api_key, base_url=base_url)
    return _client

# COBOL-Spaltenstruktur (Standard COBOL 80-Spalten)
COL_INDICATOR = 6
COL_A_START   = 7
COL_B_START   = 11
COL_END       = 72

DIVISION_PATTERN  = re.compile(r'\b(IDENTIFICATION|ENVIRONMENT|DATA|PROCEDURE)\s+DIVISION\b', re.IGNORECASE)
SECTION_PATTERN   = re.compile(r'\b(WORKING-STORAGE|FILE|LINKAGE|LOCAL-STORAGE|COMMUNICATION|REPORT)\s+SECTION\b', re.IGNORECASE)
PARAGRAPH_PATTERN = re.compile(r'^([A-Z0-9][A-Z0-9-]{1,29})\s*\.\s*$', re.IGNORECASE)
LEVEL_PATTERN     = re.compile(r'^\s*(0[1-9]|[1-4][0-9]|49|66|77|88)\s+', re.IGNORECASE)
COPY_PATTERN      = re.compile(r'^\s*COPY\s+(\S+)', re.IGNORECASE)
CALL_PATTERN      = re.compile(r"\bCALL\s+['\"](\w+)['\"]", re.IGNORECASE)
EXEC_CICS_PATTERN = re.compile(r'EXEC\s+CICS', re.IGNORECASE)

NOT_PARAGRAPH = {
    'IDENTIFICATION', 'ENVIRONMENT', 'DATA', 'PROCEDURE',
    'WORKING-STORAGE', 'FILE', 'LINKAGE', 'LOCAL-STORAGE',
    'COMMUNICATION', 'REPORT', 'SECTION', 'DIVISION',
    'END', 'STOP', 'GO',
}

# ---------------------------------------------------------------------------
# Qdrant-Index-Kontext  (Phase 0)
# ---------------------------------------------------------------------------

def _build_stem_index(client: QdrantClient) -> dict[str, str]:
    """Loads STEM_UPPER -> file_path mapping from Qdrant (chunk_index=0 only)."""
    index: dict[str, str] = {}
    offset = None
    while True:
        result, next_offset = client.scroll(
            collection_name=COLLECTION_NAME,
            scroll_filter=Filter(
                must=[FieldCondition(key="chunk_index", match=MatchValue(value=0))]
            ),
            limit=500, offset=offset,
            with_payload=["file_name", "file_path"], with_vectors=False,
        )
        for point in result:
            fn = point.payload.get("file_name", "")
            fp = point.payload.get("file_path", "")
            if fn and fp:
                index[Path(fn).stem.upper()] = fp
        if next_offset is None:
            break
        offset = next_offset
    return index


def _fetch_file_content(client: QdrantClient, file_path: str) -> str:
    """Reconstructs the full file content from Qdrant chunks."""
    chunks = []
    offset = None
    while True:
        result, next_offset = client.scroll(
            collection_name=COLLECTION_NAME,
            scroll_filter=Filter(
                must=[FieldCondition(key="file_path", match=MatchValue(value=file_path))]
            ),
            limit=200, offset=offset,
            with_payload=["chunk_index", "content"], with_vectors=False,
        )
        chunks.extend(result)
        if next_offset is None:
            break
        offset = next_offset
    chunks.sort(key=lambda p: p.payload.get("chunk_index", 0))
    return "\n".join(p.payload.get("content", "") for p in chunks)


def _semantic_search(client: QdrantClient, query: str,
                     exclude_path: str, top_k: int = 3) -> list[dict]:
    """Searches semantically similar COBOL programs in the index."""
    try:
        response = ollama.embed(model=EMBEDDING_MODEL, input=[query[:4000]])
        vector = response.embeddings[0]
    except Exception as e:
        print(f"    [Index] Embedding error: {e}", file=sys.stderr)
        return []

    response = client.query_points(
        collection_name=COLLECTION_NAME,
        query=vector,
        limit=top_k * 6,
        with_payload=True,
    )
    seen: set[str] = set()
    similar = []
    for hit in response.points:
        fp  = hit.payload.get("file_path", "")
        ext = hit.payload.get("extension", "").lower()
        if fp == exclude_path or fp in seen:
            continue
        if ext not in {".cbl", ".cob"}:
            continue  # COBOL programs only, exclude copybooks and JCL
        seen.add(fp)
        similar.append({
            "file_name":  hit.payload.get("file_name", ""),
            "file_path":  fp,
            "score":      round(hit.score, 3),
            "excerpt":    hit.payload.get("content", "")[:400],
        })
        if len(similar) >= top_k:
            break
    return similar


def gather_index_context(content: str, copybook_names: list[str],
                         rel_path: str, data_dir: Path) -> dict:
    """
    Loads from the Qdrant index:
      - Copybook contents  (for variable and paragraph explanations)
      - Called programs (for paragraph explanations)
      - Semantically similar already-analysed programs (for initial context)
    Returns empty context dict if Qdrant is not reachable.
    """
    empty = {"copybooks": {}, "called_programs": {}, "similar_programs": []}
    try:
        client = QdrantClient(url=QDRANT_URL)
        # Connection test
        client.get_collections()
    except Exception as e:
        print(f"    [Index] Qdrant not reachable ({e}) – proceeding without index context", file=sys.stderr)
        return empty

    try:
        stem_index = _build_stem_index(client)

        # 1. Resolve copybooks
        copybooks: dict[str, str] = {}
        for name in copybook_names:
            stem = name.upper().rstrip(".")
            if stem in stem_index:
                copybooks[stem] = _fetch_file_content(client, stem_index[stem])

        # 2. Resolve called programs
        called_names = list(dict.fromkeys(CALL_PATTERN.findall(content)))
        called: dict[str, str] = {}
        for name in called_names:
            stem = name.upper()
            if stem in stem_index:
                called[stem] = _fetch_file_content(client, stem_index[stem])[:2000]

        # 3. Semantically similar programs (enriched with already-analysed JSON results)
        raw_similar = _semantic_search(client, content[:2000], rel_path, top_k=3)
        similar: list[dict] = []
        for s in raw_similar:
            prog_name = Path(s["file_name"]).stem.upper()
            json_path = data_dir / f"{prog_name}.json"
            entry: dict = {"name": prog_name, "score": s["score"]}
            if json_path.exists():
                try:
                    doc = json.loads(json_path.read_text(encoding="utf-8"))
                    entry["purpose"]  = doc["meta"].get("description", "")
                    entry["module"]   = doc["meta"].get("module", "")
                    entry["process"]  = doc["meta"].get("process_description", "")[:300]
                except Exception:
                    entry["excerpt"] = s["excerpt"]
            else:
                entry["excerpt"] = s["excerpt"]
            similar.append(entry)

        return {"copybooks": copybooks, "called_programs": called, "similar_programs": similar}

    except Exception as e:
        print(f"    [Index] Error loading context: {e}", file=sys.stderr)
        return empty


# ---------------------------------------------------------------------------
# Strukturelles Parsen  (regelbasiert, kein LLM)
# ---------------------------------------------------------------------------

def classify_line(raw: str, division: str, in_exec: bool) -> str:
    if not raw.strip():
        return 'empty'
    indicator = raw[COL_INDICATOR] if len(raw) > COL_INDICATOR else ' '
    if indicator in ('*', '/'):
        return 'comment'
    code = raw[COL_A_START:COL_END].rstrip() if len(raw) > COL_A_START else ''
    cu   = code.strip().upper()
    if not cu:
        return 'empty'
    if DIVISION_PATTERN.search(cu):  return 'division-header'
    if SECTION_PATTERN.search(cu):   return 'section-header'
    if COPY_PATTERN.match(cu):       return 'copy-statement'
    if division == 'DATA' and LEVEL_PATTERN.match(code):
        return 'variable-declaration'
    if division == 'PROCEDURE':
        if in_exec or EXEC_CICS_PATTERN.search(cu): return 'exec-cics'
        area_a = raw[COL_A_START:COL_B_START] if len(raw) > COL_B_START else ''
        if area_a and area_a[0] != ' ':
            if PARAGRAPH_PATTERN.match(cu) and cu.rstrip('. ').split()[0] not in NOT_PARAGRAPH:
                return 'paragraph-header'
        return 'statement'
    return 'code'


def parse_cobol_structure(content: str) -> dict:
    raw_lines      = content.splitlines()
    lines          = []
    divisions      = {'IDENTIFICATION': [], 'ENVIRONMENT': [], 'DATA': [], 'PROCEDURE': []}
    current_div    = None
    current_sec    = None
    current_para   = None
    paragraphs     = []
    copybooks      = []
    in_exec        = False

    for i, raw in enumerate(raw_lines):
        ln  = i + 1
        cu  = (raw[COL_A_START:COL_END] if len(raw) > COL_A_START else raw).strip().upper()

        if 'EXEC' in cu and 'CICS' in cu: in_exec = True
        if 'END-EXEC' in cu:              in_exec = False

        ltype = classify_line(raw, current_div, in_exec)

        dm = DIVISION_PATTERN.search(cu)
        if dm:
            current_div  = dm.group(1).upper()
            current_sec  = None
            current_para = None

        sm = SECTION_PATTERN.search(cu)
        if sm:
            current_sec = sm.group(0).upper()

        if current_div == 'PROCEDURE' and ltype == 'paragraph-header':
            if current_para:
                current_para['line_end'] = ln - 1
            pm   = re.match(r'^([A-Z0-9][A-Z0-9-]*)', cu.strip())
            name = pm.group(1) if pm else cu.strip().rstrip('.')
            current_para = {'name': name, 'line_start': ln, 'line_end': None, 'lines': []}
            paragraphs.append(current_para)

        cm = COPY_PATTERN.match(cu)
        if cm:
            cb = cm.group(1).rstrip('.')
            if cb not in copybooks:
                copybooks.append(cb)

        obj = {
            'number':    ln,
            'code':      raw.rstrip(),
            'type':      ltype,
            'division':  current_div,
            'section':   current_sec,
            'paragraph': current_para['name'] if current_para and current_div == 'PROCEDURE' else None,
            'explanation': '',
        }
        lines.append(obj)
        if current_div:
            divisions[current_div].append(obj)
        if current_para and current_div == 'PROCEDURE':
            current_para['lines'].append(obj)

    if current_para:
        current_para['line_end'] = len(raw_lines)

    return {
        'lines':             lines,
        'divisions':         divisions,
        'paragraphs':        [{'name': p['name'], 'line_start': p['line_start'], 'line_end': p['line_end']} for p in paragraphs],
        'paragraph_details': paragraphs,
        'copybooks':         copybooks,
    }

# ---------------------------------------------------------------------------
# Claude helper function
# ---------------------------------------------------------------------------

def call_claude_json(prompt: str, model: str = DEFAULT_MODEL) -> object:
    """Calls the Claude API and extracts JSON from the response. Returns None on error."""
    try:
        resp = get_client().messages.create(
            model=model,
            max_tokens=8192,
            temperature=0,
            messages=[{"role": "user", "content": prompt}],
        )
        text = resp.content[0].text.strip()
        if '```json' in text:
            text = text.split('```json')[1].split('```')[0].strip()
        elif '```' in text:
            text = text.split('```')[1].split('```')[0].strip()
        positions = [text.find(c) for c in ['[', '{'] if c in text]
        start = min(positions) if positions else 0
        text  = text[start:]
        last  = max(text.rfind(']'), text.rfind('}'))
        if last != -1:
            text = text[:last + 1]
        return json.loads(text)
    except anthropic.AuthenticationError:
        print('    [Error] Invalid ANTHROPIC_API_KEY.', file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f'    [Warning] JSON parse error: {e}', file=sys.stderr)
        return None

# ---------------------------------------------------------------------------
# Phase 1: Understand business context of the full program
# ---------------------------------------------------------------------------

def _format_similar_programs(similar: list[dict]) -> str:
    if not similar:
        return ""
    lines = ["SIMILAR PROGRAMS IN THE SYSTEM (already analysed):"]
    for s in similar:
        name = s.get("name", "?")
        purpose = s.get("purpose", s.get("excerpt", "")[:200])
        module = s.get("module", "")
        lines.append(f"  • {name} [{module}]: {purpose}")
    return "\n".join(lines)


def _format_copybook_list(copybooks: dict[str, str]) -> str:
    if not copybooks:
        return ""
    names = ", ".join(copybooks.keys())
    return f"REFERENCED COPYBOOKS (data structures): {names}"


def _format_copybook_content(copybooks: dict[str, str], max_per_cb: int = 2500,
                              max_total: int = 8000) -> str:
    """Formats copybook contents for prompts, with length limit."""
    if not copybooks:
        return ""
    parts = ["INCLUDED COPYBOOK DEFINITIONS (via COPY statement):"]
    total = 0
    for name, content in copybooks.items():
        excerpt = content[:max_per_cb]
        if total + len(excerpt) > max_total:
            remaining = max_total - total
            if remaining < 200:
                parts.append(f"\n=== {name} ===\n[... further content truncated ...]")
                break
            excerpt = content[:remaining]
        parts.append(f"\n=== COPY {name} ===\n{excerpt}")
        total += len(excerpt)
    return "\n".join(parts)


def _format_called_programs(called: dict[str, str], max_per: int = 600) -> str:
    if not called:
        return ""
    parts = ["CALLED SUBPROGRAMS (via CALL):"]
    for name, content in called.items():
        parts.append(f"\n  • {name}:\n{content[:max_per]}")
    return "\n".join(parts)


def extract_business_context(program_name: str, content: str,
                             ctx: dict, model: str) -> dict:
    print(f'    Analysing business context...')

    lines = content.splitlines()
    header = '\n'.join(lines[:100])
    skeleton_lines = []
    for l in lines[100:]:
        stripped = l.strip()
        indicator = l[6] if len(l) > 6 else ' '
        if stripped and indicator not in ('*', '/'):
            skeleton_lines.append(l.rstrip())
    skeleton = '\n'.join(skeleton_lines[:200])

    # Index context for phase 1
    ctx_block = "\n\n".join(filter(None, [
        _format_similar_programs(ctx.get("similar_programs", [])),
        _format_copybook_list(ctx.get("copybooks", {})),
        _format_called_programs(ctx.get("called_programs", {}), max_per=400),
    ]))

    prompt = f"""You are a Senior Business Analyst specialising in banking and financial services.
You are analysing the COBOL program "{program_name}" from the CardDemo credit card management system.

Your task: Understand the BUSINESS PROCESS of this program.
Not the technology – but: What does this program do from a business perspective?

{"SYSTEM CONTEXT:" + chr(10) + ctx_block + chr(10) if ctx_block else ""}
Program code (header + structure):
```
{header}
...
{skeleton}
```

Reply ONLY with this JSON object (all text in English):
{{
  "purpose": "One sentence: What is the business purpose of this program?",
  "process_description": "2-4 sentences: Describe the business process in detail.",
  "type": "CICS Online | Batch | Utility",
  "module": "Authentication | Customer | Account | Card | Transaction | Reporting | Admin | Utility",
  "files": {{
    "FILENAME": "Business meaning of this file"
  }},
  "variables": {{
    "VARNAME": "Business meaning"
  }},
  "business_rules": [
    "Important business rule or validation"
  ]
}}"""

    result = call_claude_json(prompt, model)
    if isinstance(result, dict):
        return result
    return {
        'purpose': f'COBOL program {program_name}',
        'process_description': '',
        'type': 'Unknown',
        'module': 'Unknown',
        'files': {},
        'variables': {},
        'business_rules': [],
    }

# ---------------------------------------------------------------------------
# Phase 2: Explain variables in business terms (with copybook context)
# ---------------------------------------------------------------------------

def explain_variables(data_lines: list[dict], program_name: str,
                      biz: dict, ctx: dict, model: str) -> list[dict]:
    var_lines = [l for l in data_lines if l['type'] == 'variable-declaration']
    if not var_lines:
        return []

    biz_summary = f"""Program: {program_name}
Purpose: {biz.get('purpose', '')}
Known files: {json.dumps(biz.get('files', {}), ensure_ascii=False)}
Known variable meanings: {json.dumps(biz.get('variables', {}), ensure_ascii=False)}
Business rules: {json.dumps(biz.get('business_rules', []), ensure_ascii=False)}"""

    # Copybook contents are essential for variable explanations
    copybook_block = _format_copybook_content(ctx.get("copybooks", {}),
                                               max_per_cb=2500, max_total=8000)

    all_vars = []
    chunk_size = 60

    for start in range(0, len(var_lines), chunk_size):
        chunk = var_lines[start:start + chunk_size]
        code_block = '\n'.join(l['code'] for l in chunk)

        prompt = f"""You are a COBOL expert and Business Analyst for the CardDemo credit card system.

BUSINESS CONTEXT:
{biz_summary}

{copybook_block}

TASK: Explain each variable declaration in BUSINESS terms – what does this variable mean in the business process?

Example BAD: "Working-Storage variable of type PIC X(10)"
Example GOOD: "Account number of the card holder, used to update the account balance in the master record"

Note: Variables included via COPY can be found in the copybook definitions above.

COBOL declarations:
```
{code_block}
```

Reply ONLY with a JSON array (all text in English):
[
  {{
    "name": "VARIABLENAME",
    "level": "01",
    "picture": "PIC clause or null",
    "value": "initial value or null",
    "redefines": "REDEFINES target or null",
    "occurs": "OCCURS clause or null",
    "is_group": false,
    "description": "Business meaning: What does this variable represent in the business process?"
  }}
]

Important:
- is_group=true if no PIC clause is present (group field/record structure)
- description describes the BUSINESS PURPOSE, not the technical definition
- Explain ALL variables, including FILLER and 88-level condition values"""

        result = call_claude_json(prompt, model)
        if isinstance(result, list):
            all_vars.extend(result)
        else:
            for l in chunk:
                m = re.search(r'(?:0[1-9]|[1-4][0-9]|49|66|77|88)\s+(\S+)', l['code'])
                if m:
                    all_vars.append({'name': m.group(1), 'level': None, 'picture': None,
                                     'value': None, 'redefines': None, 'occurs': None,
                                     'is_group': False, 'description': '(analysis not available)'})
    return all_vars

# ---------------------------------------------------------------------------
# Phase 3: Explain paragraphs in business terms (with copybook and CALL context)
# ---------------------------------------------------------------------------

def explain_paragraph(para: dict, program_name: str,
                      biz: dict, ctx: dict, model: str) -> dict:
    if not para['lines']:
        return {'description': '', 'line_explanations': {}}

    biz_ctx = f"""Program "{program_name}": {biz.get('purpose', '')}
Process: {biz.get('process_description', '')}
Files: {json.dumps(biz.get('files', {}), ensure_ascii=False)}
Variables (selection): {json.dumps(dict(list(biz.get('variables', {}).items())[:20]), ensure_ascii=False)}
Business rules: {json.dumps(biz.get('business_rules', []), ensure_ascii=False)}"""

    # For paragraphs: copybook names + first 800 chars per copybook
    copybook_brief = _format_copybook_content(ctx.get("copybooks", {}),
                                               max_per_cb=800, max_total=3000)
    called_brief   = _format_called_programs(ctx.get("called_programs", {}), max_per=300)

    ctx_block = "\n\n".join(filter(None, [copybook_brief, called_brief]))

    code_block = '\n'.join(f'{l["number"]:4d}  {l["code"]}' for l in para['lines'])

    prompt = f"""You are a Business Analyst documenting COBOL code for a bank.

PROGRAM BUSINESS CONTEXT:
{biz_ctx}

{ctx_block + chr(10) if ctx_block else ""}
TASK: Explain the COBOL paragraph "{para['name']}" from a BUSINESS PROCESS perspective.

Rules for good explanations:
- NOT: "Reads the next record from the file"
- YES: "Loads the account master record of the card holder to check credit limit and balance"
- NOT: "MOVE X TO Y copies the variable"
- YES: "Transfers the transaction number into the posting record for the output file"
- NOT: "IF condition checks the value"
- YES: "Checks whether the account's transaction limit has been exceeded"
- Comment lines: Explain their business content, not that it is a comment
- EXEC CICS: Explain the business action, not the CICS command
- CALL statements: Explain what the called program does in business terms (context above)

COBOL code:
```
{code_block}
```

Reply ONLY with this JSON (all text in English):
{{
  "description": "1-2 sentences: What is the business purpose of this paragraph?",
  "line_explanations": {{
    "<line number as string>": "<business explanation, max 150 chars>",
    "<line number as string>": "<business explanation>"
  }}
}}

Explain every non-empty line. Line number as string (e.g. "42")."""

    result = call_claude_json(prompt, model)
    if isinstance(result, dict):
        return {
            'description':       result.get('description', ''),
            'line_explanations': {str(k): v for k, v in result.get('line_explanations', {}).items()},
        }
    return {'description': '', 'line_explanations': {}}

# ---------------------------------------------------------------------------
# Hauptfunktion
# ---------------------------------------------------------------------------

def analyze_file(cobol_path: Path, output_dir: Path,
                 model: str = DEFAULT_MODEL, force: bool = False) -> Path:
    output_path = output_dir / f'{cobol_path.stem.upper()}.json'

    if output_path.exists() and not force:
        print(f'  Skipped (already exists): {output_path.name}')
        return output_path

    print(f'  Analysing: {cobol_path.name}')
    content      = cobol_path.read_text(encoding='utf-8', errors='replace')
    program_name = cobol_path.stem.upper()

    # 1. Parse structure
    print(f'    Parsing...')
    struct = parse_cobol_structure(content)

    # 2. Load index context (phase 0)
    rel_path = str(cobol_path.resolve().relative_to(REPO_ROOT))
    print(f'    Loading index context...')
    ctx = gather_index_context(content, struct['copybooks'], rel_path, output_dir)
    n_cb   = len(ctx['copybooks'])
    n_call = len(ctx['called_programs'])
    n_sim  = len(ctx['similar_programs'])
    print(f'      → {n_cb} copybooks resolved, {n_call} programs, {n_sim} similar')

    # 3. Business context for the full program
    biz = extract_business_context(program_name, content, ctx, model)

    # 4. Explain variables in business terms
    n_vars = len([l for l in struct['divisions']['DATA'] if l['type'] == 'variable-declaration'])
    print(f'    Variables ({n_vars} declarations)...')
    variables = explain_variables(struct['divisions']['DATA'], program_name, biz, ctx, model)

    # 5. Explain paragraphs in business terms
    para_details = struct['paragraph_details']
    print(f'    Paragraphs ({len(para_details)} total)...')
    all_line_expl: dict[str, str] = {}
    para_descriptions: dict[str, str] = {}

    for para in para_details:
        print(f'      → {para["name"]}')
        result = explain_paragraph(para, program_name, biz, ctx, model)
        para_descriptions[para['name']] = result['description']
        all_line_expl.update(result['line_explanations'])

    # 6. Embed explanations into lines
    for line in struct['lines']:
        line['explanation'] = all_line_expl.get(str(line['number']), '')
        if line['type'] == 'paragraph-header' and line['paragraph'] and not line['explanation']:
            line['explanation'] = para_descriptions.get(line['paragraph'], '')

    # 7. Paragraphs with descriptions
    paragraphs_enriched = [
        {**p, 'description': para_descriptions.get(p['name'], '')}
        for p in struct['paragraphs']
    ]

    # 8. JSON document
    doc = {
        'meta': {
            'program_name':        program_name,
            'file_path':           str(cobol_path.resolve().relative_to(REPO_ROOT)),
            'analyzed_at':         datetime.now().isoformat(),
            'model':               model,
            'description':         biz.get('purpose', ''),
            'process_description': biz.get('process_description', ''),
            'type':                biz.get('type', ''),
            'module':              biz.get('module', ''),
            'files':               biz.get('files', {}),
            'business_rules':      biz.get('business_rules', []),
            'index_context': {
                'copybooks_resolved': list(ctx['copybooks'].keys()),
                'programs_resolved':  list(ctx['called_programs'].keys()),
                'similar_programs':   [s['name'] for s in ctx['similar_programs']],
            },
        },
        'lines':      struct['lines'],
        'variables':  variables,
        'paragraphs': paragraphs_enriched,
        'copybooks':  struct['copybooks'],
    }

    output_dir.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'    → {output_path.name}  ({len(struct["lines"])} lines, {len(variables)} variables)')
    return output_path

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('cobol_file',   type=Path)
    parser.add_argument('--output-dir', type=Path, default=Path(__file__).parent.parent / 'data')
    parser.add_argument('--model',      default=DEFAULT_MODEL)
    parser.add_argument('--force',      action='store_true')
    args = parser.parse_args()

    if not args.cobol_file.exists():
        print(f'Fehler: {args.cobol_file} nicht gefunden', file=sys.stderr)
        sys.exit(1)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    analyze_file(args.cobol_file, args.output_dir, model=args.model, force=args.force)
