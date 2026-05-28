# annotate-vars

Add "Knowledge Source" and "Confidence" columns to the Variables & Data Structures table
in a CardDemo module documentation page, with individual per-row tooltips that cite exact
COBOL source lines, and make every line-number reference in a tooltip a clickable link to
the corresponding line in the module's comparison page.

## Usage

```
/annotate-vars <MODULE_NAME>
```

Examples:
- `/annotate-vars CBACT01C`
- `/annotate-vars COACCT01`

If called without an argument, list all HTML files in `documentation/html/` (excluding
`index.html`, `status/`, `migration/`, `comparison/`) and ask the user to pick one.

---

## Instructions

The argument is: **$ARGUMENTS**

### Step 0 — Resolve the module name

If `$ARGUMENTS` is empty or blank:
- List all `.html` files in `documentation/html/` (not in sub-directories)
- Show the list and ask the user to pick one, then stop

Otherwise, set MODULE = `$ARGUMENTS` (uppercase, strip `.html`/`.cbl` if present).

---

### Step 1 — Verify prerequisites (run in parallel)

1. **Documentation page** — check that `documentation/html/<MODULE>.html` exists.
   If not, abort with `ERROR: Documentation page for <MODULE> not found.`

2. **COBOL source** — find the first match:
   - `app/cbl/<MODULE>.cbl` or `app/cbl/<MODULE>.CBL`
   - `app/app-vsam-mq/cbl/<MODULE>.cbl`
   - `app/app-authorization-ims-db2-mq/cbl/<MODULE>.cbl`
   - `app/app-transaction-type-db2/cbl/<MODULE>.cbl`
   If not found, abort with `ERROR: COBOL source for <MODULE> not found.`

3. **Already annotated check** — grep for `class="var-source"` in the HTML.
   If found, warn the user and ask whether to overwrite before continuing.

---

### Step 2 — Read source materials (run in parallel)

1. Read the full COBOL source file found in Step 1.
2. Read `documentation/html/<MODULE>.html` and extract all variable names from
   `id="var-VARNAME"` attributes in the Variables & Data Structures table.
3. Read every copybook referenced via `COPY <name>` statements in the COBOL source
   from `app/cpy/<NAME>.cpy` (or `.CPY`).

---

### Step 3 — Classify each variable

For every variable name found in the HTML table, determine:

**A) Knowledge source** — pick exactly one:

| CSS class    | Label            | When to use |
|--------------|------------------|-------------|
| `src-code`   | Source code      | The field name, PIC clause, level number, VALUE, or OCCURS is literally written in the COBOL source or a copybook referenced by it. This is the default for any declared field. |
| `src-std`    | COBOL standard   | The meaning follows a well-known COBOL language convention, independent of this specific program — e.g. 2-byte file-status pairs (STAT1/STAT2), FILLER padding, REDEFINES overlays used for byte splitting, COMP/BINARY size conventions. |
| `src-name`   | Naming convention | The business meaning is read from the variable name alone (prefixes like WS-, suffixes like -BAL, -DT, -CTR, -IND) and is not explicitly stated anywhere in the source. |
| `src-domain` | Source + domain  | The structural PIC comes from the source but understanding the business meaning requires credit-card / banking domain knowledge (e.g. CYC = billing cycle, GROUP-ID = product portfolio, REISSUE = card renewal). |
| `src-infer`  | Usage inference  | The variable is not fully explained by its declaration — its purpose is inferred from how it is used in the PROCEDURE DIVISION. Descriptions may be partially speculative. |

**B) Confidence** — pick exactly one:

| CSS class     | Label  | When to use |
|---------------|--------|-------------|
| `conf-high`   | High   | The declaration is unambiguous and the description is fully supported by the source. |
| `conf-medium` | Medium | Some interpretation was needed (naming convention, domain knowledge, or partial usage inference). |
| `conf-low`    | Low    | The description is largely speculative — the variable's purpose is unclear from the source. |

**C) Individual tooltip text** — write one short paragraph (2–4 sentences max) that:
- Opens with the exact COBOL line reference: `L<n>:` or `L<n>-<m>:`
- Quotes the relevant COBOL snippet (field declaration, SELECT clause, VALUE, etc.)
- Explains what evidence (FD declaration, SELECT clause, naming convention,
  domain knowledge, usage in PROCEDURE DIVISION) backs the description
- For `src-std` fields: name the specific COBOL standard pattern (e.g.
  "standard 2-byte COBOL file-status mechanism (ANS-85 §9.1.6)")
- For `src-domain` fields: name the domain concept that is not in the source
- For `src-name` fields: identify the prefix/suffix convention used
- For `src-infer` fields: cite the PROCEDURE DIVISION line(s) where the
  variable is used and explain the inference

Collect results as a Python dict:

```python
META = {
    # (source_label, source_css, confidence_label, conf_css, tooltip_text)
    'FIELD-NAME': (
        'Source code', 'src-code', 'High', 'conf-high',
        'L53: "01 FD-ACCTFILE-REC." declared in FD ACCTFILE-FILE (L52). '
        'L29-33: SELECT clause defines ORGANIZATION IS INDEXED and RECORD KEY IS FD-ACCT-ID.'
    ),
    ...
}
```

---

### Step 4 — Generate and run the Python script

Write a self-contained Python script to `/tmp/annotate_vars_<MODULE_LOWER>.py` and execute it.

The script must perform **four operations in order**:

#### 4-A  Add CSS for badges and floating tooltip

Insert the following CSS block immediately after the line
`'.var-desc  { color: var(--text); line-height: 1.4; }'` in the `<style>` section:

```css
    .var-source, .var-conf { text-align: center; white-space: nowrap; }
    .src-badge, .conf-badge {
      display: inline-block; padding: .1rem .45rem; border-radius: 999px;
      font-size: .67rem; font-weight: 600; letter-spacing: .02em; cursor: help;
    }
    .src-code    { background: rgba(166,227,161,.13); color: #a6e3a1; border: 1px solid rgba(166,227,161,.35); }
    .src-std     { background: rgba(137,180,250,.13); color: #89b4fa; border: 1px solid rgba(137,180,250,.35); }
    .src-name    { background: rgba(249,226,175,.13); color: #f9e2af; border: 1px solid rgba(249,226,175,.35); }
    .src-domain  { background: rgba(250,179,135,.13); color: #fab387; border: 1px solid rgba(250,179,135,.35); }
    .src-infer   { background: rgba(203,166,247,.13); color: #cba6f7; border: 1px solid rgba(203,166,247,.35); }
    .conf-high   { background: rgba(166,227,161,.13); color: #a6e3a1; border: 1px solid rgba(166,227,161,.35); }
    .conf-medium { background: rgba(249,226,175,.13); color: #f9e2af; border: 1px solid rgba(249,226,175,.35); }
    .conf-low    { background: rgba(243,139,168,.13); color: #f38ba8; border: 1px solid rgba(243,139,168,.35); }
    #src-tip {
      display: none; position: fixed; z-index: 9999; max-width: 360px;
      background: #24273a; color: #cdd6f4; border: 1px solid #45475a;
      border-radius: 6px; padding: .5rem .75rem; font-size: .72rem;
      font-weight: 400; line-height: 1.55; white-space: normal;
      text-align: left; box-shadow: 0 4px 16px rgba(0,0,0,.5);
      pointer-events: auto;
    }
    #src-tip a { color: #89b4fa; text-decoration: none; border-bottom: 1px dotted rgba(137,180,250,.4); }
    #src-tip a:hover { border-bottom-style: solid; }
```

#### 4-B  Add two `<th>` columns

Replace the single closing Description header:
```html
        <th>Description</th>
      </tr>
    </thead>
```
with:
```html
        <th>Description</th>
        <th>Knowledge source</th>
        <th>Confidence</th>
      </tr>
    </thead>
```

#### 4-C  Inject two `<td>` cells into each variable row

Use `re.sub` on the pattern `r'<tr id="var-([^"]+)">(.*?)\n    </tr>'` (DOTALL).
For each row, append two cells before the closing `</tr>`:

```python
def inject_cells(m):
    varname  = m.group(1)
    row_body = m.group(2)
    if varname in META:
        src_lbl, src_css, conf_lbl, conf_css, tip = META[varname]
        tip_attr = ' data-tip="%s"' % tip.replace('"', '&quot;')
        cells = (
            '\n      <td class="var-source">'
            '<span class="src-badge %s"%s>%s</span></td>'
            '\n      <td class="var-conf">'
            '<span class="conf-badge %s">%s</span></td>'
        ) % (src_css, tip_attr, src_lbl, conf_css, conf_lbl)
    else:
        cells = '\n      <td class="var-source">—</td>\n      <td class="var-conf">—</td>'
    return '<tr id="var-%s">%s%s\n    </tr>' % (varname, row_body.rstrip(), cells)
```

#### 4-D  Add floating tooltip div + JS before `</body>`

Append the following before the closing `</body>` tag.

**CRITICAL**: never write `</script>` as a Python string literal — build it via concatenation
(`'</' + 'script>'`) to avoid the HTML parser interpreting it inside a Python heredoc.

```python
CLOSE_SCRIPT = '</' + 'script>'
js_block = (
    '\n<div id="src-tip"></div>\n'
    '<script>\n'
    '(function(){\n'
    '  var tip = document.getElementById(\'src-tip\');\n'
    '  var COMP = \'comparison/<MODULE>.html\';\n'
    '\n'
    '  function linkify(text) {\n'
    '    return text.replace(/L(\\d+)(?:-(\\d+))?/g, function(m, start) {\n'
    '      return \'<a href="\' + COMP + \'#cobol-L\' + start\n'
    '             + \'" target="_blank">\' + m + \'</a>\';\n'
    '    });\n'
    '  }\n'
    '\n'
    '  function posTip(e) {\n'
    '    var x = e.clientX + 16, y = e.clientY + 16;\n'
    '    if (x + 370 > window.innerWidth)  x = e.clientX - 374;\n'
    '    if (y + 220 > window.innerHeight) y = e.clientY - 224;\n'
    '    tip.style.left = x + \'px\';\n'
    '    tip.style.top  = y + \'px\';\n'
    '  }\n'
    '\n'
    '  var hideTimer = null;\n'
    '  function showTip(text, e) {\n'
    '    clearTimeout(hideTimer);\n'
    '    tip.innerHTML = linkify(text);\n'
    '    tip.style.display = \'block\';\n'
    '    posTip(e);\n'
    '  }\n'
    '  function hideTip() {\n'
    '    hideTimer = setTimeout(function() { tip.style.display = \'none\'; }, 120);\n'
    '  }\n'
    '\n'
    '  tip.addEventListener(\'mouseenter\', function() { clearTimeout(hideTimer); });\n'
    '  tip.addEventListener(\'mouseleave\', hideTip);\n'
    '\n'
    '  document.querySelectorAll(\'.src-badge[data-tip]\').forEach(function(el) {\n'
    '    el.addEventListener(\'mouseenter\', function(e) { showTip(el.dataset.tip, e); });\n'
    '    el.addEventListener(\'mousemove\',  posTip);\n'
    '    el.addEventListener(\'mouseleave\', hideTip);\n'
    '  });\n'
    '}());\n'
    + CLOSE_SCRIPT + '\n'
)
```

Replace `<MODULE>` in `COMP` with the actual module name (e.g. `CBACT01C`).

---

### Step 5 — Verify

After running the script, confirm:
- `class="var-source"` appears at least once in the HTML
- `id="src-tip"` appears exactly once
- The JS `linkify` function is present
- No variable rows have `—` in both new cells (warn if any are missing from META)

Report:
```
✓ Module  : <MODULE>
✓ Rows    : <N> annotated, <M> missing from META
✓ Tooltip : JS floating tooltip with L-link linkification installed
  Link target: comparison/<MODULE>.html#cobol-L{n}
```

List any variables that were present in the HTML table but missing from META so the
user can decide whether to add them manually.
