# CardDemo – Project Configuration for Claude Code

## Analysis Rules

### Language
All generated text, comments, headings, table content, and HTML output must be in **English**.
No German text anywhere — not in Markdown analysis files, not in inline comments, not in generated HTML.

### Source Code Links in Analysis Documents
When documenting a COBOL paragraph or section, always include a direct reference to the source file and line number using the format `app/cbl/PROGRAM.cbl:LINE`. Example:

```
### `PARAGRAPH-NAME` — `app/cbl/CBTRN02C.cbl:370`
```

This allows the reader to jump directly to the relevant code location.

### Line Number Annotations in Code Blocks
When describing what COBOL code does inside a code block, prefix each logical step with the source line number using `L<number>` or a range `L<start>-<end>`. Example:

```
L370  1500-VALIDATE-TRAN:
L371      PERFORM 1500-A-LOOKUP-XREF
L372-376  IF WS-VALIDATION-FAIL-REASON = 0 → PERFORM 1500-B-LOOKUP-ACCT
```

This makes it unambiguous which line in the original COBOL source a description refers to.

### Paragraph Reference Table Format
Each program's section in an analysis document must contain a paragraph index table before the detailed descriptions:

| Paragraph | Line | Purpose |
|-----------|------|---------|
| `PARAGRAPH-NAME` | [L370](app/cbl/PROGRAM.cbl) | One-line purpose |

### Analysis Document Structure
For each documented COBOL program include:
1. **Program header**: Program-ID, type (Batch/CICS), source path, function
2. **File access table**: DDName, access mode, purpose
3. **Paragraph index**: Table of all documented paragraphs with line links
4. **Paragraph details**: Each paragraph with annotated pseudo-code (line numbers inline)
5. **Java migration notes**: Specific gotchas, edge cases, stubs

## Project Context
- **Goal**: Modernize CardDemo COBOL/JCL/Assembler → Java (Spring Boot + Spring Batch + PostgreSQL)
- **Source**: `app/` directory (COBOL), `app/cpy/` (Copybooks), JCL in various subdirectories
- **Analysis output**: `analyse/` directory (Markdown documents 00–07+)
- **Target stack**: Spring Boot, Spring Batch, Spring Security, JPA/Hibernate, PostgreSQL
