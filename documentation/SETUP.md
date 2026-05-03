# CardDemo COBOL Documentation — Setup Guide

This guide describes everything needed to generate the HTML documentation from the COBOL source files,
starting from a fresh clone of the repository.

---

## Architecture Overview

The pipeline has three stages:

```
app/cbl/*.cbl          (COBOL source)
       │
       ▼
 analyze_cobol.py  ──► documentation/data/*.json    (structured analysis)
       │ (uses Claude API + Qdrant context)
       ▼
 generate_html.py  ──► documentation/html/*.html    (browsable documentation)
```

**Supporting services:**

| Service | Purpose | Port |
|---------|---------|------|
| **Claude API** | LLM for business context, variable, and line explanations | via HTTPS proxy |
| **Ollama** | Local embeddings (`nomic-embed-text`) for semantic search | 11434 |
| **Qdrant** | Vector database — stores COBOL source embeddings for context lookup | 6335 |

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Python | 3.12+ | [python.org](https://www.python.org/downloads/) or `brew install python@3.12` |
| Docker | any recent | [docker.com](https://www.docker.com/get-started/) or `brew install --cask docker` |
| Ollama | any recent | [ollama.ai](https://ollama.ai) or `brew install ollama` |

---

## Step-by-Step Setup

### 1. Clone the Repository

```bash
git clone <repo-url>
cd aws-mainframe-modernization-carddemo
```

### 2. Create the Python Virtual Environment

```bash
python3.12 -m venv .venv
.venv/bin/pip install --upgrade pip
.venv/bin/pip install -r documentation/scripts/requirements.txt
```

### 3. Configure the Claude API Key

The analysis scripts read the API key from `~/.claude/settings.json`.
Create or extend that file:

```json
{
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "YOUR_API_KEY_HERE",
    "ANTHROPIC_BASE_URL": "https://api.iteragpt.iteratec.de/"
  }
}
```

> **Note:** The `ANTHROPIC_BASE_URL` points to the iteratec API proxy.
> Only the model `gcp/claude-sonnet-4-6` is supported through this proxy.
> Contact the team for the API key.

### 4. Start Qdrant (Vector Database)

Qdrant runs as a Docker container. Choose a local directory for persistent storage:

```bash
mkdir -p ~/qdrant_storage

docker run -d \
  --name qdrant-cobol-6335 \
  --restart unless-stopped \
  -p 6335:6333 \
  -v ~/qdrant_storage:/qdrant/storage \
  qdrant/qdrant
```

Verify it is running:

```bash
curl http://localhost:6335/collections
# Expected: {"result":{"collections":[...]},"status":"ok","time":...}
```

### 5. Start Ollama and Pull the Embedding Model

```bash
# Start Ollama (runs as a background service after installation)
ollama serve &   # only needed if not already running as a system service

# Pull the embedding model (one-time, ~274 MB)
ollama pull nomic-embed-text
```

Verify:

```bash
ollama list
# Should show: nomic-embed-text:latest
```

### 6. Index the Source Files into Qdrant

This step embeds all COBOL, copybook, JCL, and other source files into the vector database.
The analysis scripts use this index to look up copybook definitions and similar programs as context.

```bash
.venv/bin/python index_code.py
```

Expected output:
```
Connecting to Qdrant at http://localhost:6335 ...
Warming up embedding model 'nomic-embed-text' via Ollama ...
Embedding dimension: 768
Found 250 source files on disk.
  New:     250  Changed: 0  Deleted: 0
  ADD  app/cbl/CBACT01C.cbl  (2 vector(s))
  ...
Done. 500 total vectors in 'carddemo'.
```

Re-run `index_code.py` whenever source files are added or modified — it is incremental and only processes changes.

---

## Generating the Documentation

All analysis and HTML generation is orchestrated by `process_all.py`.

### Full Run (First Time)

```bash
.venv/bin/python3 documentation/scripts/process_all.py
```

This analyses every COBOL file that does not yet have a JSON data file, then generates HTML.
A single program takes roughly **1–3 minutes** (several Claude API calls).
44 programs ≈ **1.5–2 hours** total.

### Add Prelude Annotations (IDENTIFICATION / ENVIRONMENT / DATA DIVISION)

Programs analysed before May 2026 do not have line annotations for the
IDENTIFICATION, ENVIRONMENT, and DATA DIVISION sections. Run this once to patch them:

```bash
.venv/bin/python3 documentation/scripts/process_all.py --patch-prelude
```

Patching a single program takes roughly **1–2 minutes**.
44 programs ≈ **40–60 minutes** total.

### Full Re-Analysis (Update Everything)

```bash
.venv/bin/python3 documentation/scripts/process_all.py --force
```

### Single Program

```bash
# Full analysis
.venv/bin/python3 documentation/scripts/analyze_cobol.py app/cbl/CBACT01C.cbl

# Force re-analysis
.venv/bin/python3 documentation/scripts/analyze_cobol.py app/cbl/CBACT01C.cbl --force

# Patch prelude only
.venv/bin/python3 documentation/scripts/analyze_cobol.py app/cbl/CBACT01C.cbl --patch-prelude
```

### Restrict to a Single Source Directory

```bash
.venv/bin/python3 documentation/scripts/process_all.py --app-dir app/cbl
```

### Generate HTML Without Re-Analysing

```bash
.venv/bin/python3 documentation/scripts/generate_html.py \
  --input-dir documentation/data \
  --output-dir documentation/html
```

---

## Viewing the Documentation

Open in a browser:

```
documentation/html/index.html
```

Or in the terminal:

```bash
open documentation/html/index.html          # macOS
xdg-open documentation/html/index.html     # Linux
```

---

## Directory Structure

```
aws-mainframe-modernization-carddemo/
├── app/
│   ├── cbl/                          COBOL source files (main programs)
│   ├── cpy/                          Copybook definitions
│   ├── app-authorization-ims-db2-mq/ Additional program variants
│   ├── app-transaction-type-db2/
│   └── app-vsam-mq/
├── documentation/
│   ├── SETUP.md                      ← this file
│   ├── data/                         JSON analysis results (one per program)
│   ├── html/                         Generated HTML documentation
│   │   ├── index.html                Program overview
│   │   └── CBACT01C.html             Per-program documentation
│   └── scripts/
│       ├── analyze_cobol.py          Core analysis script (calls Claude API)
│       ├── process_all.py            Batch orchestration script
│       ├── generate_html.py          HTML generation from JSON data
│       ├── generate_status.py        Generates the documentation status page
│       ├── translate_json.py         Translate German text in legacy JSONs
│       ├── requirements.txt          Python dependencies
│       └── templates/                Jinja2 HTML templates
├── index_code.py                     Indexes source files into Qdrant
└── .venv/                            Python virtual environment (not in git)
```

---

## What the Analysis Pipeline Does

For each COBOL program, `analyze_cobol.py` runs four phases using the Claude API:

| Phase | What it does | Claude calls |
|-------|-------------|-------------|
| **0. Index context** | Loads copybook definitions and similar programs from Qdrant | 0 (local) |
| **1. Business context** | Understands the program's business purpose, files, rules | 1 |
| **2. Variables** | Explains each `DATA DIVISION` variable in business terms | 1–3 (batches of 60) |
| **3a. Prelude** | Annotates IDENTIFICATION / ENVIRONMENT / DATA DIVISION lines | 1–2 (batches of 60) |
| **3b. Paragraphs** | Annotates each PROCEDURE DIVISION paragraph line-by-line | 1 per paragraph |

Results are saved to `documentation/data/<PROGRAM>.json`.
The JSON tracks whether prelude annotation has been done (`meta.prelude_annotated: true`).

---

## Status Check

### HTML Status Page

The clearest way to check status is the dedicated status page.
Generate (or refresh) it with:

```bash
.venv/bin/python3 documentation/scripts/generate_status.py
open documentation/html/status/index.html
```

The page lists every program in `app/cbl/` with:
- Current status (complete / needs patch / not started)
- Percentage of lines annotated
- The exact command to complete each program

It is also linked from `documentation/html/index.html` ("Documentation Status →").

### Command-Line Summary

```bash
.venv/bin/python3 documentation/scripts/process_all.py --no-html 2>&1 | head -10
```

```
CardDemo COBOL Documentation Pipeline
======================================
Programs found     : 44
  fully annotated  : 44
  missing prelude  : 0
  not yet analysed : 0
```

---

## Troubleshooting

### `No module named 'anthropic'`
The system Python is being used instead of the venv.
Always prefix commands with `.venv/bin/python3`:
```bash
.venv/bin/python3 documentation/scripts/process_all.py
```

### `Error: No API key found in ~/.claude/settings.json`
Create the settings file as described in Step 3.

### `Qdrant not reachable`
The analysis continues without Qdrant context (copybooks not resolved, no similar-program hints),
but quality will be lower. Start Qdrant as described in Step 4 and re-run with `--force`.

### `API Error 401 / model not found`
Only `gcp/claude-sonnet-4-6` is available through the iteratec proxy.
Verify the model setting in `process_all.py` (`--model gcp/claude-sonnet-4-6`).

### Qdrant container not starting after reboot
```bash
docker start qdrant-cobol-6335
```
Or use `--restart unless-stopped` in the `docker run` command (already included above).

### Index is empty after fresh start
```bash
.venv/bin/python index_code.py --full
```

---

## Recommended Workflow After a Fresh Clone

```bash
# 1. Set up environment
python3.12 -m venv .venv
.venv/bin/pip install -r documentation/scripts/requirements.txt

# 2. Configure API key in ~/.claude/settings.json  (see Step 3)

# 3. Start services
docker start qdrant-cobol-6335 || docker run -d --name qdrant-cobol-6335 \
  --restart unless-stopped -p 6335:6333 \
  -v ~/qdrant_storage:/qdrant/storage qdrant/qdrant
ollama pull nomic-embed-text

# 4. Index source files
.venv/bin/python3 index_code.py

# 5. Analyse all programs and generate HTML
.venv/bin/python3 documentation/scripts/process_all.py

# 6. Open documentation
open documentation/html/index.html
```
