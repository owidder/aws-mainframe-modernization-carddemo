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

### Step 3 — Build the COBOL→Java paragraph mapping

**Parse `// COBOL:` annotations** from every Java file. The annotation format is:

```
// COBOL: PROGRAM.cbl:LINE — PARAGRAPH-NAME
// COBOL: PROGRAM.cbl:START-END — PARAGRAPH-NAME
```

For each annotation:
- `cobol_line` = first line number from the annotation (the `START` part)
- `java_tab`   = 0-based index of the file containing the annotation
- `java_line`  = line number of the **next non-blank, non-comment** line after the annotation
- `paragraph`  = the paragraph name after the `—` separator

**Find the enclosing Java method** by scanning backwards from `java_line` to the nearest line matching `^\s+(public|private|protected|@Override)`. Use the method name in the Java cell.

**Build one mapping row per annotation**, deduplicating by paragraph name (keep lowest `cobol_line`). Sort by `cobol_line` ascending.

For each row produce **both** an HTML cell and a plain-text description (used in the CT tooltip object):

| Field | HTML (`cc`, `jc`) | Plain text (`cobol_desc`, `java_desc`) |
|-------|-------------------|----------------------------------------|
| COBOL | `<code>PARA-NAME</code><br><small>L{n}</small>` | `PARA-NAME — COBOL line {n}` |
| Java  | `<code>{ClassName}</code> — <code>{method}()</code>` | `{ClassName}.{method}()` |
| Notes | (same for both) | one-sentence description |

MAPPING tuple: `(cobol_line, java_tab, java_line, cc, jc, notes, cobol_desc, java_desc)`

---

### Step 3.5 — Build the VARS object (variable-name tooltips)

Read the COBOL **DATA DIVISION** (FILE SECTION, WORKING-STORAGE SECTION) and all referenced **copybooks** (`app/cpy/*.cpy`). For every data item (FD records, 01/05/10/88-level items, REDEFINES overlays, file names from SELECT entries) build a `VARS` Python dict:

```python
VARS = {
    # Key  = COBOL data-item name (uppercase, as written in source)
    # b    = business-meaning description (one sentence, plain English)
    # j    = Java equivalent note (class.field or "No Java equivalent — reason")
    # jt   = Java tab index (0-based, OMIT if no direct Java counterpart)
    # jl   = Java line number to navigate to (OMIT if no direct Java counterpart)
    'ACCT-ID': dict(
        b='Account number — 11-digit unique credit-card account identifier',
        j='Account.acctId (Long) — @Id, column acct_id',
        jt=4, jl=26),
    'END-OF-FILE': dict(
        b='Loop-termination flag — set to "Y" when the account file is exhausted',
        j='No Java equivalent — Spring Batch chunk loop ends when ItemReader returns null'),
    # ...
}
```

**Rules for `jt`/`jl`:**
- Include `jt`/`jl` when the variable maps to a **named Java field, method, or class declaration** in one of the Java files.
- Omit them for COBOL-specific constructs (file-status codes, REDEFINES overlays, 88-level conditions, abend parameters) that have no direct Java field.
- To find the line: search the Java file content for the camelCase equivalent of the COBOL name (e.g. `ACCT-ID` → `acctId`, `OUT-ACCT-REC` → class `OutRecord`). Use the field declaration line, not a usage line.

**Helper functions to include in the Python script:**

```python
def cobol_to_camel(name):
    parts = name.lower().split('-')
    return parts[0] + ''.join(p.capitalize() for p in parts[1:])

def find_java_field(java_files_content, cobol_name):
    """Return (jt, jl) of the field/class declaration or (None, None)."""
    camel = cobol_to_camel(cobol_name)
    patterns = [
        r'private\s+\S+\s+' + re.escape(camel) + r'\b',   # entity field
        r'\b' + re.escape(camel) + r'\s*[,)\[]',           # record component
        r'class\s+' + re.escape(camel[0].upper()+camel[1:]), # class name
    ]
    for jt, (name, path, content) in enumerate(java_files_content):
        for jl, line in enumerate(content.splitlines(), 1):
            if any(re.search(p, line) for p in patterns):
                return jt, jl
    return None, None
```

**Generate `vars_js`** from the VARS dict:

```python
def esc_sq(s): return s.replace("\\", "\\\\").replace("'", "\\'")

var_entries = []
for vn, d in VARS.items():
    b, j = esc_sq(d['b']), esc_sq(d['j'])
    if 'jt' in d:
        var_entries.append("  '%s':{b:'%s',j:'%s',jt:%d,jl:%d}" % (vn, b, j, d['jt'], d['jl']))
    else:
        var_entries.append("  '%s':{b:'%s',j:'%s'}" % (vn, b, j))
vars_js = "var VARS={\n" + ",\n".join(var_entries) + "\n};"
```

---

### Step 4 — Generate the HTML page

Write a Python script to `/tmp/compare_<MODULE_LOWER>.py` and execute it.

**The script must follow this exact pattern:**

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
java_files_content = [(n, p, read(p)) for n, p in java_files]

# MAPPING: (cobol_line, java_tab, java_line, cobol_html, java_html, notes, cobol_desc, java_desc)
MAPPING = [
    (140, 0, 57,
     "<code>PROCEDURE DIVISION</code><br><small>L140</small>",
     "<code>AccountExportJobConfig</code> — <code>accountExportJob()</code>",
     "Chunk-oriented step replaces PERFORM UNTIL END-OF-FILE loop",
     "PROCEDURE DIVISION main flow — PERFORM UNTIL END-OF-FILE",
     "Spring Batch Job + chunk-oriented Step (AccountExportJobConfig)"),
    # ...
]

# VARS: from Step 3.5 — built with cobol_to_camel / find_java_field helpers
def cobol_to_camel(name):
    parts = name.lower().split('-')
    return parts[0] + ''.join(p.capitalize() for p in parts[1:])

def find_java_field(jfc, cobol_name):
    camel = cobol_to_camel(cobol_name)
    patterns = [
        r'private\s+\S+\s+' + re.escape(camel) + r'\b',
        r'\b' + re.escape(camel) + r'\s*[,)\[]',
        r'class\s+' + re.escape(camel[0].upper()+camel[1:]),
    ]
    for jt, (name, path, content) in enumerate(jfc):
        for jl, line in enumerate(content.splitlines(), 1):
            if any(re.search(p, line) for p in patterns):
                return jt, jl
    return None, None

VARS = {
    'ACCT-ID': dict(b='Account number — 11-digit unique credit-card identifier',
                    j='Account.acctId (Long) — @Id, column acct_id', jt=4, jl=26),
    'END-OF-FILE': dict(b='Loop-termination flag set to "Y" when file is exhausted',
                        j='No Java equivalent — ItemReader returns null to signal EOF'),
    # ... one entry per data item from DATA DIVISION + copybooks + SELECT names
}

def esc_sq(s): return s.replace("\\", "\\\\").replace("'", "\\'")
var_entries = []
for vn, d in VARS.items():
    b, j = esc_sq(d['b']), esc_sq(d['j'])
    if 'jt' in d:
        var_entries.append("  '%s':{b:'%s',j:'%s',jt:%d,jl:%d}" % (vn, b, j, d['jt'], d['jl']))
    else:
        var_entries.append("  '%s':{b:'%s',j:'%s'}" % (vn, b, j))
vars_js = "var VARS={\n" + ",\n".join(var_entries) + "\n};"

# ── Build HTML fragments ──────────────────────────────────────────────────────
cobol_esc   = html.escape(cobol)
cobol_lines = len(cobol.splitlines())
total_java  = sum(len(c.splitlines()) for _, _, c in java_files_content)

tabs_html   = ""
panels_html = ""
for i, (name, path, src) in enumerate(java_files_content):
    src_esc = html.escape(src)
    act = ' class="active"' if i == 0 else ''
    vis = ' visible'        if i == 0 else ''
    tabs_html   += '      <button%s onclick="switchTab(%d)">%s</button>\n' % (act, i, name)
    panels_html += ('      <div class="panel%s" data-tab="%d">'
                    '<pre><code class="language-java" id="jc%d">%s</code></pre></div>\n'
                    % (vis, i, i, src_esc))

rows_html = ""
for cl, jt, jl, cc, jc, notes, _, _ in MAPPING:
    jname   = java_files[jt][0]
    tooltip = "Jump to COBOL L%d / %s L%d" % (cl, jname, jl)
    rows_html += ('<tr class="nav-row" data-cl="%d" data-jt="%d" data-jl="%d"'
                  ' onclick="navigate(this)" title="%s">\n'
                  '  <td>%s</td><td>%s</td><td>%s</td>\n</tr>\n'
                  % (cl, jt, jl, tooltip, cc, jc, notes))

# CT object: paragraph-line tooltips {cobol_line: {c, j, n}}
ct_entries = []
for cl, jt, jl, cc, jc, notes, cobol_desc, java_desc in MAPPING:
    c = esc_sq(cobol_desc)
    j = esc_sq(java_desc)
    n = esc_sq(notes)
    ct_entries.append("  %d:{c:'%s',j:'%s',n:'%s'}" % (cl, c, j, n))
ct_js = "var CT={\n" + ",\n".join(ct_entries) + "\n};"

# ── JavaScript ────────────────────────────────────────────────────────────────
N = len(java_files)

# IMPORTANT: never write </script> as a literal inside Python strings.
# Build every closing script tag via string concatenation.
JS_LINES = [
    "hljs.configure({tabReplace: '    '});",
    "document.querySelectorAll('pre code').forEach(function(el){hljs.highlightElement(el);});",
    "",
    "function wrapLines(codeEl, idPfx) {",
    "  var lines = codeEl.innerHTML.split('\\n');",
    "  if (lines.length && lines[lines.length-1]==='') lines.pop();",
    "  codeEl.innerHTML = lines.map(function(line,i){",
    "    var n=i+1, s=String(n);",
    "    while(s.length<4) s='\\u00a0'+s;",
    "    return '<span class=\"cl\" id=\"'+idPfx+'-L'+n+'\">'",
    "           +'<span class=\"ln\">'+s+'</span>'+(line||'\\u200b')+'</span>';",
    "  }).join('\\n');",
    "}",
    "wrapLines(document.getElementById('cc'), 'cobol');",
    "for(var i=0;i<%d;i++){var el=document.getElementById('jc'+i);if(el)wrapLines(el,'jc'+i);}" % N,
    "",
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
    "",
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
    "",
    ct_js,   # var CT={...};
    "",
    # TABS: class names from rendered tab buttons — used by both showCTip and showVTip
    "var TABS=(function(){",
    "  return Array.from(document.querySelectorAll('.tabs button')).map(function(b){return b.textContent.trim();});",
    "}());",
    "",
    # Floating tooltip div (shared by paragraph tooltips and variable tooltips)
    "var ctip=(function(){",
    "  var d=document.createElement('div');",
    "  d.id='ctip';",
    "  document.body.appendChild(d);",
    "  return d;",
    "}());",
    "",
    # showCTip: paragraph-line tooltip (COBOL desc + → ClassName.java:LINE + Java desc + note)
    "function showCTip(e,ln){",
    "  var d=CT[ln];if(!d)return;",
    "  var ref='';",
    "  var row=document.querySelector('.nav-row[data-cl=\"'+ln+'\"');",
    "  if(row){",
    "    var jt=parseInt(row.dataset.jt),jl=parseInt(row.dataset.jl);",
    "    var cls=TABS[jt]||('Tab '+jt);",
    "    ref='<div class=tj style=\"font-size:.72rem;margin-bottom:.1rem;opacity:.85\">\u2192 '+cls+':'+jl+'</div>';",
    "  }",
    "  ctip.innerHTML='<div class=tc>COBOL: '+d.c+'</div>'",
    "                +ref",
    "                +'<div class=tj>Java: '+d.j+'</div>'",
    "                +'<div class=tn>'+d.n+'</div>';",
    "  ctip.style.display='block';",
    "  posCTip(e);",
    "}",
    "function posCTip(e){",
    "  var x=e.clientX+14,y=e.clientY+14;",
    "  if(x+370>window.innerWidth)x=e.clientX-374;",
    "  if(y+160>window.innerHeight)y=e.clientY-164;",
    "  ctip.style.left=x+'px';ctip.style.top=y+'px';",
    "}",
    "function hideCTip(){ctip.style.display='none';}",
    "",
    # attachTips: paragraph lines — hover tooltip + click-to-navigate
    "(function attachTips(){",
    "  Object.keys(CT).forEach(function(ln){",
    "    var el=document.getElementById('cobol-L'+ln);",
    "    if(!el)return;",
    "    el.dataset.tip='1';",
    "    el.addEventListener('mouseenter',function(e){showCTip(e,+ln);});",
    "    el.addEventListener('mousemove',posCTip);",
    "    el.addEventListener('mouseleave',hideCTip);",
    "    var row=document.querySelector('.nav-row[data-cl=\"'+ln+'\"');",
    "    if(row){",
    "      el.addEventListener('click',function(){hideCTip();navigate(row);});",
    "    }",
    "  });",
    "}());",
    "",
    # VARS: variable-name tooltips — injected from Python vars_js
    vars_js,
    "",
    # navigateVar: jump Java pane to a specific tab+line (used by variable clicks)
    "function navigateVar(jt,jl){",
    "  if(actJ)actJ.classList.remove('hl');",
    "  switchTab(jt,true);",
    "  setTimeout(function(){",
    "    var je=document.getElementById('jc'+jt+'-L'+jl);",
    "    if(je){je.scrollIntoView({behavior:'smooth',block:'center'});je.classList.add('hl');actJ=je;}",
    "  },60);",
    "}",
    "",
    # showVTip: variable tooltip — name + business meaning + → ClassName:LINE + Java note
    "function showVTip(e,vn){",
    "  var d=VARS[vn];if(!d)return;",
    "  var ref='';",
    "  if(d.jt!==undefined){",
    "    var cls=TABS[d.jt]||('Tab '+d.jt);",
    "    ref='<div class=tj style=\"font-size:.72rem;margin-bottom:.1rem;opacity:.85\">\u2192 '+cls+':'+d.jl+'</div>';",
    "  }",
    "  ctip.innerHTML=",
    "    '<div class=tc style=\"font-family:\\'JetBrains Mono\\',monospace;letter-spacing:.02em\">'+vn+'</div>'",
    "    +'<div class=tj>\\u25b6 '+d.b+'</div>'",
    "    +ref",
    "    +'<div class=tn>'+d.j+(d.jt!==undefined?' \\u2014 click to navigate':'')+'</div>';",
    "  ctip.style.display='block';",
    "  posCTip(e);",
    "}",
    "",
    # wrapVars: TreeWalker post-processor — wraps variable names in <span class="vt"> / "vt vt-link"
    # Uses negative lookbehind/lookahead to avoid partial matches within longer names.
    # Runs AFTER wrapLines() so it works on already-highlighted DOM text nodes.
    # Skips .ln (line number) spans.
    "function wrapVars(codeEl){",
    "  var varList=Object.keys(VARS).sort(function(a,b){return b.length-a.length;});",
    "  var esc=varList.map(function(v){return v.replace(/-/g,'\\\\-');}).join('|');",
    "  var re=new RegExp('(?<![A-Za-z0-9-])('+esc+')(?![A-Za-z0-9-])','gi');",
    "  var walker=document.createTreeWalker(",
    "    codeEl,NodeFilter.SHOW_TEXT,",
    "    {acceptNode:function(n){",
    "      var p=n.parentNode;",
    "      while(p&&p!==codeEl){",
    "        if(p.className&&(p.className==='ln'||p.className.indexOf('ln ')===0)) return NodeFilter.FILTER_REJECT;",
    "        p=p.parentNode;",
    "      }",
    "      return NodeFilter.FILTER_ACCEPT;",
    "    }},false",
    "  );",
    "  var nodes=[],nd;",
    "  while((nd=walker.nextNode()))nodes.push(nd);",
    "  nodes.forEach(function(nd){",
    "    var txt=nd.textContent; re.lastIndex=0;",
    "    if(!re.test(txt))return;",
    "    re.lastIndex=0;",
    "    var frag=document.createDocumentFragment(),last=0,m;",
    "    while((m=re.exec(txt))!==null){",
    "      if(m.index>last)frag.appendChild(document.createTextNode(txt.slice(last,m.index)));",
    "      var sp=document.createElement('span');",
    "      var vn=m[1].toUpperCase();",
    "      var hasLink=VARS[vn]&&VARS[vn].jt!==undefined;",
    "      sp.className='vt'+(hasLink?' vt-link':'');",
    "      sp.dataset.vn=vn; sp.textContent=m[1];",
    "      (function(sp,vn,hasLink){",
    "        sp.addEventListener('mouseenter',function(e){showVTip(e,vn);});",
    "        sp.addEventListener('mousemove',posCTip);",
    "        sp.addEventListener('mouseleave',function(e){",
    "          var cl=sp.closest&&sp.closest('.cl[data-tip]');",
    "          if(cl&&cl.contains(e.relatedTarget)){",
    "            showCTip(e,parseInt(cl.id.replace('cobol-L','')));",
    "          } else hideCTip();",
    "        });",
    "        if(hasLink){",
    "          sp.addEventListener('click',function(e){",
    "            e.stopPropagation();hideCTip();navigateVar(VARS[vn].jt,VARS[vn].jl);",
    "          });",
    "        }",
    "      }(sp,vn,hasLink));",
    "      frag.appendChild(sp); last=m.index+m[0].length;",
    "    }",
    "    if(last<txt.length)frag.appendChild(document.createTextNode(txt.slice(last)));",
    "    nd.parentNode.replaceChild(frag,nd);",
    "  });",
    "}",
    "wrapVars(document.getElementById('cc'));",
]
js = "\n".join(JS_LINES)

# ── Assemble page ─────────────────────────────────────────────────────────────
MODULE   = "<MODULE>"        # e.g. CBACT01C
ARTIFACT = "<artifact-dir>"  # e.g. cbact01c

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
    margin-right:.5em;font-variant-numeric:tabular-nums;font-style:normal}
.cl.hl{background:rgba(137,220,235,.14);border-radius:2px;outline:1px solid rgba(137,220,235,.25)}
.cl.hl .ln{color:var(--cyan)}
/* Tooltip-bearing COBOL paragraph lines */
.cl[data-tip]{cursor:pointer;border-left:3px solid rgba(137,220,235,.4);background:rgba(137,220,235,.04)}
.cl[data-tip] .ln{color:#89dceb;font-weight:700}
.cl[data-tip] .ln::after{content:'';display:inline-block;width:5px;height:5px;
  background:rgba(137,220,235,.7);border-radius:50%;margin-left:.35em;
  vertical-align:middle;transition:all .15s}
.cl[data-tip]:hover{background:rgba(137,220,235,.13);border-left-color:#89dceb}
.cl[data-tip]:hover .ln{color:#89dceb}
.cl[data-tip]:hover .ln::after{content:'\u2192';width:auto;height:auto;background:none;
  border-radius:0;font-weight:400;font-size:.8em;letter-spacing:0}
/* Floating tooltip (shared by paragraph and variable tooltips) */
#ctip{position:fixed;display:none;max-width:360px;padding:.5rem .65rem;
      background:#24273a;border:1px solid #45475a;border-radius:6px;
      font-size:.72rem;line-height:1.5;color:#cdd6f4;
      pointer-events:none;z-index:9999;box-shadow:0 4px 16px rgba(0,0,0,.5)}
#ctip .tc{color:#f9e2af;font-weight:700;margin-bottom:.2rem}
#ctip .tj{color:#a6e3a1;font-weight:700;font-size:.68rem;margin-top:.28rem;margin-bottom:.18rem}
#ctip .tn{color:#a6adc8;font-size:.69rem;margin-top:.28rem;border-top:1px solid #45475a;padding-top:.25rem}
/* Variable-name tooltip spans */
.vt{background:rgba(249,226,175,.12);border-bottom:1px solid rgba(249,226,175,.55);
    color:#f9e2af;border-radius:2px;cursor:help;
    transition:background .12s,border-color .12s,color .12s;padding:0 1px}
.vt:hover{background:rgba(249,226,175,.25);border-bottom-color:#f9e2af;color:#fff}
.vt-link{cursor:pointer}
.vt-link:hover{border-bottom-width:2px}
</style>
</head>
<body>""")

# nav bar
parts.append("""<nav>
  <a href="../index.html">Home</a><span class="sep">/</span>
  <a href="../java-transformation.html">Java Transformation</a><span class="sep">/</span>
  <span>%(mod)s</span>
  <span class="badge-nav">COBOL \u2194 Java</span>
</nav>""" % {"mod": MODULE})

# header with badges — adjust badge classes per module type
parts.append("""<header>
  <h1>%(mod)s \u2014 COBOL \u2194 Java Comparison</h1>
  <div class="meta">
    <span class="mc"><code>app/cbl/%(mod)s.cbl</code></span>
    &nbsp;\u2192&nbsp;
    <span class="mj"><code>java/%(art)s/</code></span>
    &nbsp;&nbsp;%(cl)d COBOL lines &nbsp;|&nbsp; %(jl)d Java lines across %(jn)d files
  </div>
  <div class="badges">
    <span class="badge b-cobol">COBOL</span>
    <span class="badge b-batch">Spring Batch</span>
    <span class="badge b-spring">Spring Boot</span>
  </div>
</header>""" % {"mod": MODULE, "art": ARTIFACT, "cl": cobol_lines, "jl": total_java, "jn": len(java_files)})

# mapping table
parts.append("""<div class="map-wrap">
  <h2>Paragraph mapping &nbsp;<span class="nav-hint">(click row or highlighted COBOL line to navigate)</span></h2>
  <table class="mt">
    <thead><tr><th>COBOL paragraph</th><th>Java equivalent</th><th>Transformation notes</th></tr></thead>
    <tbody>
""" + rows_html + """    </tbody>
  </table>
</div>""")

# split panes
parts.append("""<div class="split">
  <div class="pane pane-cobol">
    <div class="pane-hdr">
      <span class="lang-tag lc">COBOL</span>
      <span class="pane-fn">app/cbl/%(mod)s.cbl</span>
      <span class="pane-ln">%(cl)d lines</span>
    </div>
    <div class="cs" id="cscroll">
      <pre><code class="language-cobol" id="cc">%(cobol)s</code></pre>
    </div>
  </div>
  <div class="pane pane-java">
    <div class="pane-hdr">
      <span class="lang-tag lj">Java</span>
      <span class="pane-fn" id="jfn">%(first_java)s</span>
      <span class="pane-ln" id="jln"></span>
    </div>
    <div class="tabs">
%(tabs)s    </div>
    <div class="cs" id="jscroll">
%(panels)s    </div>
  </div>
</div>""" % {
    "mod":        MODULE,
    "cl":         cobol_lines,
    "cobol":      cobol_esc,
    "first_java": java_files[0][0],
    "tabs":       tabs_html,
    "panels":     panels_html,
})

# inline script — close tag built via concatenation, never as a literal
parts.append('<script>\n' + js + '\n' + '</script>')
parts.append('\n</body>\n</html>')

page = "\n".join(parts)

dest = os.path.join(BASE, "documentation/html/comparison/%s.html" % MODULE)
os.makedirs(os.path.dirname(dest), exist_ok=True)
with open(dest, 'w', encoding='utf-8') as f:
    f.write(page)
vars_with_link = sum(1 for d in VARS.values() if 'jt' in d)
print("Written %d bytes -> %s" % (len(page), dest))
print("VARS: %d entries, %d with Java link" % (len(VARS), vars_with_link))
```

> **Critical rules for the Python script**
> - Never write `</script>` as a literal. Always use string concatenation:
>   `'<script src="...">' + '</script>'` and `'<script>\n' + js + '\n' + '</script>'`
> - Escape single quotes in all `b`/`j` strings via `esc_sq()` before injecting into JS.
> - Unicode arrows (`→` = `\u2192`, `▶` = `\u25b6`) must be written as Python unicode escapes
>   inside JS string literals to avoid encoding issues.
> - `wrapVars` must run **after** `wrapLines` in `JS_LINES` (order in the list is execution order).

---

### Step 5 — Run and verify

```bash
python3 /tmp/compare_<module_lower>.py
```

Verify the output file:
- Exists at `documentation/html/comparison/<MODULE>.html`
- Contains `id="cc"` (COBOL code block) and `id="jc0"` (first Java block)
- Contains `wrapLines` and `wrapVars` (both post-processors)
- Contains `navigate` (paragraph click) and `navigateVar` (variable click)
- Contains `showCTip` and `showVTip` (two tooltip renderers)
- Contains `attachTips` (paragraph tooltip wiring)
- Contains CSS `.cl[data-tip]`, `.vt`, `.vt-link`, `#ctip`
- Contains no broken `<\/script>` tags (grep for `<\\` should return nothing)

If verification fails, fix the script and re-run.

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
✓ Module    : <MODULE>
✓ Output    : documentation/html/comparison/<MODULE>.html
✓ COBOL     : <N> lines  (app/cbl/<MODULE>.cbl)
✓ Java      : <M> lines across <K> files  (java/<artifact-dir>/)
✓ Paragraphs: <P> mapping rows  (// COBOL: annotations → CT object)
✓ Variables : <V> tooltipped, <L> with click-to-navigate Java link  (VARS object)

Open: file:///…/documentation/html/comparison/<MODULE>.html
```

List any COBOL paragraphs without a `// COBOL:` annotation (omitted from mapping table) and any data items that could not be automatically matched to a Java field (set to tooltip-only, no `jt`/`jl`).
