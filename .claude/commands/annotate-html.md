# annotate-html

Check a CardDemo COBOL documentation HTML page for missing line-by-line
explanations (empty `expl-cell` columns) and fill them in by reading the
corresponding COBOL source file.

## Usage

```
/annotate-html <MODULE_NAME>
```

Examples:
- `/annotate-html CBACT01C`
- `/annotate-html CBACT03C`
- `/annotate-html COACCT01`

If called without an argument, list all `.html` files in
`documentation/html/` (excluding sub-directories) and ask the user to pick one.

---

## Instructions

The argument is: **$ARGUMENTS**

---

### Step 0 — Resolve the module name

If `$ARGUMENTS` is empty or blank:
- List all `.html` files in `documentation/html/` (not in sub-directories,
  not `index.html`, not `java-transformation.html`)
- Show the list and ask the user to pick one, then stop.

Otherwise set `MODULE` = `$ARGUMENTS` uppercased, stripping `.html` / `.cbl`
if present.

---

### Step 1 — Verify prerequisites (run in parallel)

1. **HTML page** — confirm `documentation/html/<MODULE>.html` exists.
   Abort if missing.

2. **COBOL source** — find the source file, checking in order:
   - `app/cbl/<MODULE>.cbl`
   - `app/cbl/<MODULE>.CBL`
   - `app/app-vsam-mq/cbl/<MODULE>.cbl`
   - `app/app-transaction-type-db2/cbl/<MODULE>.cbl`
   Abort if not found.

---

### Step 2 — Analyse the HTML page

Run the following Python analysis to find all empty `expl-cell` cells that
**should** have an explanation. Write the script to
`/tmp/check_<MODULE_LOWER>.py` and execute it.

```python
import re, json

html_path = "documentation/html/<MODULE>.html"
with open(html_path, encoding="utf-8") as f:
    html = f.read()

# Rows that never need an explanation
SKIP_CLASSES = {"comment", "empty"}
# Code-cell types that never need an explanation
SKIP_TYPES = {"t-comment", "t-paragraph-header"}

row_re   = re.compile(r'<tr class="([^"]+)"[^>]*>(.*?)</tr>', re.DOTALL)
ln_re    = re.compile(r'<td class="ln">(\d+)</td>')
code_re  = re.compile(r'<td class="code-cell ([^"]+)">(.*?)</td>', re.DOTALL)
expl_re  = re.compile(r'<td class="expl-cell">(.*?)</td>', re.DOTALL)

missing = []   # list of {lineno, row_class, code_type, code_text}
filled  = 0

for m in row_re.finditer(html):
    row_class = m.group(1).strip()
    body      = m.group(2)

    # Skip rows that never need comments
    if any(cls in row_class for cls in SKIP_CLASSES):
        continue

    ln_m    = ln_re.search(body)
    code_m  = code_re.search(body)
    expl_m  = expl_re.search(body)

    if not ln_m or not code_m or not expl_m:
        continue

    code_type = code_m.group(1).strip()
    if any(t in code_type for t in SKIP_TYPES):
        continue

    content = expl_m.group(1).strip()
    if content:
        filled += 1
    else:
        # Strip HTML tags from code text for display
        code_text = re.sub(r'<[^>]+>', '', code_m.group(2)).strip()
        missing.append({
            "lineno":     int(ln_m.group(1)),
            "row_class":  row_class,
            "code_type":  code_type,
            "code_text":  code_text,
        })

print(json.dumps({"filled": filled, "missing": missing}, indent=2))
```

Parse the JSON output:
- If `missing` is empty → report "All expl-cells are already filled." and stop.
- Otherwise print a summary:
  ```
  Module : <MODULE>
  Filled : <N> lines already annotated
  Missing: <M> lines need comments
  ```
  List the first 20 missing lines with their line number and code text so the
  user can see what will be filled.

---

### Step 3 — Read the COBOL source (run in parallel with Step 2)

Read the full COBOL source file found in Step 1. Keep it in memory as a
list of `(lineno, text)` tuples.

Also read any copybooks referenced via `COPY <name>` statements
(`app/cpy/<NAME>.cpy` or `.CPY`), noting which lines each copybook covers.

---

### Step 4 — Generate explanations

For each entry in `missing`, derive a concise English explanation by examining:

1. **The COBOL line itself** (`code_text` from Step 2, cross-referenced
   against the source read in Step 3)
2. **The surrounding context** (the lines immediately before and after in the
   source to understand purpose)
3. **The `row_class` / `code_type`** to calibrate the wording:

| `row_class` / `code_type`      | Explanation style |
|--------------------------------|-------------------|
| `variable-declaration` / level 01 | What the group item represents and its total size |
| `variable-declaration` / level 05+ | What the field holds; mention PIC clause meaning |
| `variable-declaration` / 88-level  | What condition this name tests; the threshold value |
| `statement` / PERFORM          | What paragraph is called and why |
| `statement` / MOVE / ADD / etc.| What is being computed or transferred and why |
| `statement` / IF / ELSE        | What condition is being tested and what branch handles |
| `statement` / END-IF / END-PERFORM | What block is being closed |
| `statement` / DISPLAY          | What message is written and when it appears |
| `statement` / OPEN / CLOSE / READ / WRITE / REWRITE | File operation purpose |
| `statement` / CALL             | What external routine is called and its effect |
| `copy-statement`               | What the copybook defines and how it is used here |
| `section-header`               | What the section encompasses |

**Rules for explanation text:**
- English only, one sentence (maximum two for complex lines).
- Refer to variable names and paragraph names using `<code>NAME</code>` tags.
- Do **not** just restate the code — explain the *purpose* and *effect*.
- For END-IF / END-PERFORM / ELSE: name the condition or loop they close/branch.
- For file I/O verbs: mention the file name, access mode, and what happens on
  success vs. failure.
- Keep each explanation under 200 characters where possible.

Collect all explanations in a Python dict:
```python
EXPLANATIONS = {
    <lineno>: "<explanation text>",
    ...
}
```

---

### Step 5 — Apply the changes

Write a self-contained Python script to
`/tmp/apply_annotations_<MODULE_LOWER>.py` and execute it.

The script must:

1. Read `documentation/html/<MODULE>.html`.

2. For each `(lineno, explanation)` pair in `EXPLANATIONS`, replace the
   empty expl-cell on that COBOL line number using this pattern:

   Find the unique anchor:
   ```
   <td class="ln">{lineno}</td>
   ```
   Then within the same `<tr>` block, replace:
   ```
   <td class="expl-cell"></td>
   ```
   with:
   ```
   <td class="expl-cell">{explanation}</td>
   ```

   Use a regex that is scoped to the `<tr>…</tr>` block for that line number
   to avoid false matches.

3. Write the result back to `documentation/html/<MODULE>.html`.

4. Print a count of how many replacements were made.

---

### Step 6 — Verify

After Step 5, re-run the analysis script from Step 2.
Report:
```
✓ Module  : <MODULE>
✓ Filled  : <total filled> / <total code lines> lines annotated
  Remaining empty: <N>    (0 = complete)
```

If any cells are still empty, list them so the user can decide whether to
re-run or handle them manually.

---

### Style constraints

- Explanations follow the conventions in `documentation/html/CBACT02C.html`,
  which is the reference implementation.
- Do not add explanations to rows with class `comment` or `empty`, or cells
  with code-type `t-comment` or `t-paragraph-header`.
- All output text is English (per `CLAUDE.md`).
