# compare-module

Generate a COBOL ↔ Java side-by-side comparison HTML page for a transformed CardDemo module.

## Usage

```
/compare-module <MODULE_NAME>
```

Examples:
- `/compare-module CBACT01C`
- `/compare-module CBACT02C`
- `/compare-module COACCT01`

If called without an argument, list all transformed modules and ask the user to pick one.

---

## Instructions

The argument is: **$ARGUMENTS**

### Step 0 — Resolve the module name

If `$ARGUMENTS` is empty or blank:
- List all rows with `class="done"` in `documentation/html/java-transformation.html` to find transformed modules
- List any existing pages in `documentation/html/comparison/`
- Show both lists to the user and ask which module to compare, then stop

Otherwise, set MODULE = `$ARGUMENTS` (uppercase, strip `.html`/`.cbl` if present).

---

### Step 1 — Verify prerequisites (run in parallel)

1. **COBOL source** — find the first match:
   - `app/cbl/<MODULE>.cbl` or `app/cbl/<MODULE>.CBL`
   - `app/app-vsam-mq/cbl/<MODULE>.cbl`
   - `app/app-authorization-ims-db2-mq/cbl/<MODULE>.cbl`
   - `app/app-transaction-type-db2/cbl/<MODULE>.cbl`
   - If not found: abort with `ERROR: COBOL source for <MODULE> not found.`

2. **Java artifact directory** — derive from the module name:
   - Try `java/<module-lowercase>/src/main/java/` (e.g. `CBACT01C` → `java/cbact01c/`)
   - Also try common semantic overrides (e.g. `COACCT01` → `java/account-enquiry/`)
   - If not found: abort with `ERROR: No Java artifact found — run /transform-module <MODULE> first.`

3. **Existing page** — check if `documentation/html/comparison/<MODULE>.html` already exists.
   If yes, warn and confirm before overwriting.

---

### Step 2 — Discover and read all Java source files

Use Glob to find all `*.java` files under `java/<artifact-dir>/src/main/java/`. Read them all in parallel.

Order files with the most architecturally significant first:
1. `*JobConfig.java` / `*Application.java` — entry point
2. `*Processor.java`
3. `*Writer.java` / `*Reader.java`
4. `*Service.java`
5. Files in `domain/` (entities)
6. `*Repository.java`
7. Remaining files alphabetically

Build a list: `[(filename_short, full_path, source_content), ...]` — this list is 0-indexed and its index is the Java tab index.

---

### Step 3 — Build the COBOL→Java mapping

**Parse `// COBOL:` annotations** from every Java file. The annotation format is:

```
// COBOL: PROGRAM.cbl:LINE — PARAGRAPH-NAME
// COBOL: PROGRAM.cbl:START-END — PARAGRAPH-NAME
```

For each annotation:
- `cobol_line` = first line number from the annotation (the `START` part)
- `java_tab`   = 0-based index of the file containing the annotation
- `java_line`  = line number of the **next non-blank, non-comment** line after the annotation (the actual Java code being described)
- `paragraph`  = the paragraph name after the `—` separator

**Find the enclosing Java method** by scanning backwards from `java_line` to the nearest line matching `^\s+(public|private|protected|@Override)`. Use the method name in the Java cell.

**Build one mapping row per annotation**, deduplicating by paragraph name (keep the row with the lowest `cobol_line`). Sort rows by `cobol_line` ascending.

For rows, construct HTML cells:
- **COBOL cell**: `<code>PARAGRAPH-NAME</code><br><small>L{cobol_line}</small>`
- **Java cell**: `<code>{ClassName}</code> — <code>{methodName}()</code>`
- **Notes**: a one-sentence description inferred from the code context (what the COBOL construct does and what the Java replacement is)

---

### Step 4 — Generate the HTML page

Write a Python script to `/tmp/compare_<MODULE_LOWER>.py` and execute it.

**The script must follow this exact pattern** (copy structure from CBACT01C generation):

```python
import html, os, re

BASE = "/path/to/repo"  # use the actual repo root

def read(path):
    with open(os.path.join(BASE, path), encoding='utf-8') as f:
        return f.read()

# Read sources
cobol = read("app/cbl/<MODULE>.cbl")

java_files = [
    ("FileName.java", "java/<artifact>/src/main/java/.../<File>.java"),
    # ... one tuple per file, in the ordered list from Step 2
]

# Mapping data: list of (cobol_line, java_tab, java_line, cobol_cell_html, java_cell_html, notes)
MAPPING = [
    (140, 0, 57, "<code>...</code><br><small>L140</small>", "<code>...</code> — <code>method()</code>", "Notes..."),
    # ... one tuple per row from Step 3
]

# Build HTML fragments
cobol_esc   = html.escape(cobol)
cobol_lines = len(cobol.splitlines())
total_java  = sum(len(read(p).splitlines()) for _, p in java_files)

tabs_html   = ""
panels_html = ""
for i, (name, path) in enumerate(java_files):
    src = html.escape(read(path))
    act = ' class="active"' if i == 0 else ''
    vis = ' visible'        if i == 0 else ''
    tabs_html   += '      <button%s onclick="switchTab(%d)">%s</button>\n' % (act, i, name)
    panels_html += '      <div class="panel%s" data-tab="%d"><pre><code class="language-java" id="jc%d">%s</code></pre></div>\n' % (vis, i, i, src)

rows_html = ""
for cl, jt, jl, cc, jc, notes in MAPPING:
    jname   = java_files[jt][0]
    tooltip = "Jump to COBOL L%d / %s L%d" % (cl, jname, jl)
    rows_html += ('<tr class="nav-row" data-cl="%d" data-jt="%d" data-jl="%d"'
                  ' onclick="navigate(this)" title="%s">\n'
                  '  <td>%s</td><td>%s</td><td>%s</td>\n</tr>\n'
                  % (cl, jt, jl, tooltip, cc, jc, notes))

# JavaScript — written as list of strings to avoid </script> in literals
N = len(java_files)
JS = [
    "hljs.configure({tabReplace: '    '});",
    "document.querySelectorAll('pre code').forEach(function(el){hljs.highlightElement(el);});",
    "function wrapLines(codeEl, idPfx) {",
    "  var lines = codeEl.innerHTML.split('\\n');",
    "  if (lines.length && lines[lines.length-1]==='') lines.pop();",
    "  codeEl.innerHTML = lines.map(function(line,i){",
    "    var n=i+1, s=String(n);",
    "    while(s.length<4) s='\\u00a0'+s;",
    "    return '<span class=\"cl\" id=\"'+idPfx+'-L'+n+'\">'+'<span class=\"ln\">'+s+'</span>'+(line||'\\u200b')+'</span>';",
    "  }).join('\\n');",
    "}",
    "wrapLines(document.getElementById('cc'), 'cobol');",
    "for(var i=0;i<%d;i++){var el=document.getElementById('jc'+i);if(el)wrapLines(el,'jc'+i);}" % N,
    "var panels=document.querySelectorAll('.panel'),tabBtns=document.querySelectorAll('.tabs button');",
    "var fname=document.getElementById('jfn'),flines=document.getElementById('jln'),jscroll=document.getElementById('jscroll');",
    "function cntLines(i){var e=document.getElementById('jc'+i);return e?e.querySelectorAll('.cl').length:0;}",
    "function switchTab(idx,noReset){",
    "  panels.forEach(function(p){p.classList.remove('visible');});",
    "  tabBtns.forEach(function(b){b.classList.remove('active');});",
    "  panels[idx].classList.add('visible');tabBtns[idx].classList.add('active');",
    "  fname.textContent=tabBtns[idx].textContent;",
    "  flines.textContent=cntLines(idx)+' lines';",
    "  if(!noReset)jscroll.scrollTop=0;",
    "}",
    "window.addEventListener('load',function(){flines.textContent=cntLines(0)+' lines';});",
    "var actC=null,actJ=null,actRow=null;",
    "function navigate(row){",
    "  var cl=parseInt(row.dataset.cl),jt=parseInt(row.dataset.jt),jl=parseInt(row.dataset.jl);",
    "  if(actC)actC.classList.remove('hl');if(actJ)actJ.classList.remove('hl');if(actRow)actRow.classList.remove('row-active');",
    "  row.classList.add('row-active');actRow=row;",
    "  var ce=document.getElementById('cobol-L'+cl);",
    "  if(ce){ce.scrollIntoView({behavior:'smooth',block:'center'});ce.classList.add('hl');actC=ce;}",
    "  switchTab(jt,true);",
    "  setTimeout(function(){var je=document.getElementById('jc'+jt+'-L'+jl);",
    "    if(je){je.scrollIntoView({behavior:'smooth',block:'center'});je.classList.add('hl');actJ=je;}},60);",
    "}",
]
js = "\n".join(JS)

# Assemble page using list of string parts — never embed </script> as a literal
MODULE      = "<MODULE>"       # e.g. CBACT01C
ARTIFACT    = "<artifact-dir>" # e.g. cbact01c

parts = []
parts.append("""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>%(mod)s \u2014 COBOL \u2194 Java</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/base16/catppuccin-mocha.min.css">""" % {"mod": MODULE})

for url in [
    "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js",
    "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/cobol.min.js",
    "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/java.min.js",
]:
    parts.append('<script src="' + url + '">' + '</script>')

parts.append("""<style>
:root{--bg:#1e1e2e;--bg2:#181825;--bg3:#313244;--border:#45475a;--text:#cdd6f4;--sub:#a6adc8;
      --cyan:#89dceb;--gold:#f9e2af;--green:#a6e3a1;--blue:#89b4fa;--peach:#fab387}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--text);font-family:'Segoe UI',system-ui,sans-serif;
     display:flex;flex-direction:column;height:100vh;overflow:hidden}
nav{background:var(--bg2);border-bottom:1px solid var(--border);padding:.4rem 1.2rem;
    display:flex;gap:.7rem;align-items:center;flex-shrink:0;flex-wrap:wrap}
nav a{color:var(--cyan);text-decoration:none;font-size:.8rem}
nav a:hover{text-decoration:underline}
nav .sep{color:var(--border)}
.badge-nav{margin-left:auto;background:var(--bg3);color:var(--gold);
           padding:.1rem .5rem;border-radius:4px;font-size:.7rem;font-weight:700}
header{padding:.5rem 1.2rem .35rem;border-bottom:1px solid var(--border);flex-shrink:0}
header h1{font-size:1.1rem;color:var(--cyan)}
.meta{color:var(--sub);font-size:.74rem;margin-top:.15rem}
.meta code{font-family:monospace}.mc{color:var(--peach)}.mj{color:var(--green)}
.badges{display:flex;gap:.35rem;margin-top:.28rem;flex-wrap:wrap}
.badge{padding:.08rem .45rem;border-radius:999px;font-size:.66rem;font-weight:700;letter-spacing:.04em}
.b-batch{background:#1a2f4a;color:var(--blue);border:1px solid var(--blue)}
.b-spring{background:#1a3a1a;color:var(--green);border:1px solid var(--green)}
.b-cobol{background:#3a1a1a;color:var(--peach);border:1px solid var(--peach)}
.b-cics{background:#2a1a4a;color:var(--mauve,#cba6f7);border:1px solid var(--mauve,#cba6f7)}
.map-wrap{padding:.4rem 1.2rem;border-bottom:1px solid var(--border);flex-shrink:0;
          max-height:200px;overflow-y:auto;scrollbar-width:thin;
          scrollbar-color:var(--border) var(--bg2);background:var(--bg2)}
.map-wrap h2{font-size:.8rem;color:var(--gold);margin-bottom:.28rem}
.nav-hint{color:var(--border);font-size:.65rem}
.mt{width:100%;border-collapse:collapse;font-size:.7rem}
.mt th{color:var(--sub);text-align:left;padding:.24rem .5rem;border-bottom:1px solid var(--border);
       font-weight:600;position:sticky;top:0;background:var(--bg2);z-index:1}
.mt td{padding:.24rem .5rem;border-bottom:1px solid var(--border);vertical-align:top;line-height:1.35}
.mt td:nth-child(3){color:var(--sub)}.mt small{color:var(--sub);font-size:.65rem}
.mt code{background:var(--bg3);padding:.02rem .22rem;border-radius:3px;
         font-family:'JetBrains Mono',monospace;font-size:.68rem;color:var(--cyan)}
.nav-row{cursor:pointer;transition:background .1s}
.nav-row:hover td{background:rgba(255,255,255,.04)}
.nav-row.row-active td{background:rgba(137,220,235,.08);border-left:2px solid var(--cyan)}
.split{display:flex;flex:1;overflow:hidden}
.pane{display:flex;flex-direction:column;overflow:hidden}
.pane-cobol{flex:1 1 50%;min-width:0;border-right:2px solid var(--border)}
.pane-java{flex:1 1 50%;min-width:0}
.pane-hdr{background:var(--bg2);padding:.28rem .7rem;border-bottom:1px solid var(--border);
          display:flex;align-items:center;gap:.45rem;flex-shrink:0}
.lang-tag{font-size:.63rem;font-weight:800;letter-spacing:.1em;text-transform:uppercase;
          padding:.07rem .35rem;border-radius:3px}
.lc{background:#3a1a1a;color:var(--peach)}.lj{background:#1a3a1a;color:var(--green)}
.pane-fn{font-size:.75rem;color:var(--sub);font-family:monospace}
.pane-ln{margin-left:auto;font-size:.65rem;color:var(--border)}
.tabs{display:flex;flex-wrap:nowrap;overflow-x:auto;background:var(--bg2);
      border-bottom:1px solid var(--border);flex-shrink:0;
      scrollbar-width:thin;scrollbar-color:var(--border) var(--bg2)}
.tabs button{flex-shrink:0;background:none;border:none;border-bottom:2px solid transparent;
             color:var(--sub);padding:.28rem .55rem;font-size:.67rem;cursor:pointer;
             font-family:'JetBrains Mono',monospace;transition:color .12s,border-color .12s;
             white-space:nowrap}
.tabs button:hover{color:var(--text)}.tabs button.active{color:var(--green);border-bottom-color:var(--green)}
.cs{flex:1;overflow:auto;scrollbar-width:thin;scrollbar-color:var(--border) var(--bg)}
.panel{display:none}.panel.visible{display:block}
.cs pre{margin:0}
.cs pre code{display:block;padding:.5rem 0 .5rem .3rem !important;
             font-size:.72rem !important;line-height:1.6 !important;
             font-family:'JetBrains Mono','Cascadia Code','Fira Code',monospace !important}
.hljs{background:var(--bg) !important}
.cl{display:block;padding-right:.4rem}
.ln{display:inline-block;min-width:3em;color:var(--border);user-select:none;
    text-align:right;padding-right:.6em;border-right:1px solid var(--border);
    margin-right:.5em;font-variant-numeric:tabular-nums}
.cl.hl{background:rgba(137,220,235,.14);border-radius:2px;outline:1px solid rgba(137,220,235,.25)}
.cl.hl .ln{color:var(--cyan)}
</style>
</head>
<body>""")

# nav, header, map section, split, inline script — assemble with % substitution
# (see CBACT01C page for the exact pattern)
# ...

parts.append('<script>\n' + js + '\n' + '</script>')
parts.append('\n</body>\n</html>')

page = "\n".join(parts)

dest = os.path.join(BASE, "documentation/html/comparison/%s.html" % MODULE)
os.makedirs(os.path.dirname(dest), exist_ok=True)
with open(dest, 'w', encoding='utf-8') as f:
    f.write(page)
print("Written %d bytes -> %s" % (len(page), dest))
```

---

### Step 5 — Run and verify

Execute the Python script:
```bash
python3 /tmp/compare_<module_lower>.py
```

Verify the output file:
- Exists at `documentation/html/comparison/<MODULE>.html`
- Contains `id="cc"` (COBOL code block)
- Contains `id="jc0"` (first Java code block)
- Contains `wrapLines` (line number function)
- Contains `navigate` (click handler)
- Contains no broken `<\/script>` tags (grep for `<\\` should return nothing)

If the file fails verification, fix the script and re-run.

---

### Step 6 — Update java-transformation.html

Find the table row for `<MODULE>` in `documentation/html/java-transformation.html`.
If the row has `class="done"` and does **not** already contain a `[compare]` link, add one:

```html
&nbsp;<a href="comparison/<MODULE>.html" style="font-size:.75rem;color:#89dceb" title="COBOL ↔ Java side-by-side">[compare]</a>
```

Insert it immediately after the existing Java artifact `<a>` link, inside the same `<span class="status-done">`.

---

### Step 7 — Report

```
✓ Module   : <MODULE>
✓ Output   : documentation/html/comparison/<MODULE>.html
✓ COBOL    : <N> lines  (app/cbl/<MODULE>.cbl)
✓ Java     : <M> lines across <K> files  (java/<artifact-dir>/)
✓ Mapping  : <P> paragraph rows (from // COBOL: annotations)

Open: file:///…/documentation/html/comparison/<MODULE>.html
```

List any COBOL paragraphs that had no matching `// COBOL:` annotation and were left out of the mapping table.
