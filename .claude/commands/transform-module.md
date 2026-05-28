# transform-module

Transform a CardDemo COBOL module into a runnable Java Spring Boot microservice.

## Usage

```
/transform-module <MODULE_NAME>
```

Examples:
- `/transform-module CBACT02C`
- `/transform-module CBTRN02C`
- `/transform-module COACCT01`

If called without an argument, list all available modules and ask the user to pick one.

---

## Instructions

The argument is: **$ARGUMENTS**

### Step 0 — Resolve the module name

If `$ARGUMENTS` is empty or blank:
- List all HTML files in `documentation/html/` (excluding `index.html`, `status/`, `migration/`)
- Show them to the user grouped by prefix (CB = Batch, CO = CICS Online)
- Ask the user which module to transform and stop

Otherwise, set MODULE = `$ARGUMENTS` (uppercase, strip `.html`/`.cbl` if present).

### Step 1 — Read source materials (do these in parallel)

1. **HTML analysis**: `documentation/html/<MODULE>.html`
   - Extract: program type (Batch / CICS / Utility), function summary, paragraph list, file/dataset references, copybooks used, MQ queues if any
2. **COBOL source**: search in this order and read the first match:
   - `app/cbl/<MODULE>.cbl` or `app/cbl/<MODULE>.CBL`
   - `app/app-vsam-mq/cbl/<MODULE>.cbl`
   - `app/app-authorization-ims-db2-mq/cbl/<MODULE>.cbl`
   - `app/app-transaction-type-db2/cbl/<MODULE>.cbl`
3. **Copybooks**: for every copybook referenced (COPY statements), read from `app/cpy/<NAME>.cpy` or `app/cpy/<NAME>.CPY`
4. **Existing Java reference**: `java/account-enquiry/` — use as the structural template
5. **Check if already done**: does `java/<module-kebab>/` already exist? If yes, warn the user and ask whether to overwrite

### Step 2 — Analyse and plan

Based on the source, determine:

| Question | Determines |
|----------|-----------|
| Is it a Batch program? (PROCEDURE DIVISION, no EXEC CICS) | Use Spring Batch `Job` / `Step` |
| Is it CICS? (EXEC CICS commands) | Use Spring Boot REST controller (synchronous HTTP replaces COMMAREA/MQ) |
| Does it use IBM MQ? (EXEC CICS WRITEQ / GET) | Map to REST POST request/reply |
| Which VSAM files does it read/write? | Map each to a JPA entity + repository |
| Which copybooks define record layouts? | Map each to a JPA entity or DTO record |
| Does it write output files? (sequential, reports)? | Use `FlatFileItemWriter` or simple file output |
| Does it do batch looping? (PERFORM UNTIL END-OF-FILE) | Use `ItemReader` + `ItemProcessor` + `ItemWriter` |
| Are there COMPUTE / arithmetic operations? | Use `BigDecimal` with scale matching PIC clause |
| Does it call other programs? (CALL / EXEC CICS LINK)? | Create a service interface per called program |

### Step 3 — Derive the target layout

From the module name, derive:
- **Artifact ID**: `<module>` lowercased with hyphens (e.g. `CBACT02C` → `cbact02c`, or use semantic name when obvious: `CBTRN02C` → `transaction-processor`)
- **Java package**: `com.carddemo.<artifact-id-underscored>`
- **Output directory**: `java/<artifact-id>/`

Apply the same package/directory structure as `java/account-enquiry/`:
```
src/main/java/com/carddemo/<pkg>/
  <Module>Application.java
  domain/          ← JPA entities (one per VSAM file / copybook record)
  repository/      ← Spring Data JPA repositories
  service/         ← business logic (mirrors COBOL paragraphs)
  web/             ← REST controllers + DTOs  (CICS/MQ programs)
  batch/           ← Job, Step, Reader, Processor, Writer (Batch programs)
  exception/       ← GlobalExceptionHandler
src/main/resources/
  application.yml
  db/schema.sql
  db/data.sql      ← sample seed rows from app/data/ASCII/ if available
src/test/java/com/carddemo/<pkg>/
  <Module>IntegrationTest.java
  service/<Service>Test.java
  web/<Controller>Test.java  (if REST)
  batch/<Job>Test.java       (if Batch)
src/test/resources/application.yml
build.gradle
settings.gradle
gradle/wrapper/gradle-wrapper.properties  ← copy from java/account-enquiry/
```

### Step 4 — Generate all files

Write every file. For each COBOL paragraph or section, add a comment citing the source:
```java
// COBOL: <MODULE>.cbl:<LINENO> — <PARAGRAPH-NAME>
```

**Data type mapping rules:**
| COBOL PIC | Java type |
|-----------|-----------|
| PIC 9(n) / PIC S9(n) | `Long` / `Integer` depending on size |
| PIC S9(n)V99 / COMP-3 | `BigDecimal` with scale=2 |
| PIC X(n) (n≤10, date-like) | `String` |
| PIC X(n) | `String` |
| OCCURS n TIMES | `List<T>` or array — document the denormalisation choice |

**Framework mapping rules:**
| COBOL construct | Java equivalent |
|----------------|----------------|
| EXEC CICS READ DATASET(...) RIDFLD(...) | `repository.findById(key)` |
| EXEC CICS WRITE DATASET(...) | `repository.save(entity)` |
| EXEC CICS REWRITE DATASET(...) | `repository.save(entity)` (JPA merge) |
| EXEC CICS DELETE DATASET(...) | `repository.deleteById(key)` |
| EXEC CICS STARTBR / READNEXT / ENDBR | `repository.findAll()` or custom `@Query` |
| DFHRESP(NORMAL) | `Optional.isPresent()` |
| DFHRESP(NOTFND) | `Optional.isEmpty()` |
| PERFORM UNTIL END-OF-FILE | `ItemReader.read()` returning `null` at EOF |
| EXEC CICS WRITEQ TD / READQ TD | REST POST to downstream service (stub) |
| ABEND / PERFORM 9000-ERROR | throw unchecked exception → `GlobalExceptionHandler` |
| EXEC CICS RETURN | method return |
| EVALUATE … WHEN | `switch` expression |
| COMPUTE ws-field = expr | `BigDecimal` arithmetic |

**build.gradle** — start from `java/account-enquiry/build.gradle`; add `spring-batch` if Batch program.

**schema.sql** — derive DDL from the copybook record layout. Use `IF NOT EXISTS`. Honour PIC types → SQL types:
- PIC 9(n) → `BIGINT` or `INT`
- PIC S9(n)V99 → `DECIMAL(n+2, 2)`
- PIC X(n) → `CHAR(n)` or `VARCHAR(n)`

**data.sql** — if `app/data/ASCII/` contains a matching flat file, parse the first 5–10 rows using the copybook layout and emit `MERGE INTO … KEY(pk) VALUES (…)` statements. Otherwise emit commented-out placeholder rows.

**Tests** — write at least:
- 1 unit test per service method branch (found / not found / invalid input / inactive record)
- 1 MockMvc or `@WebMvcTest` test per controller endpoint (if REST)
- 1 `@SpringBootTest` full-stack integration test per happy-path scenario

### Step 5 — Generate the Gradle wrapper

After writing all files, run:
```bash
cd java/<artifact-id> && gradle wrapper --gradle-version 8.8
```

### Step 6 — Run the tests

```bash
cd java/<artifact-id> && ./gradlew test
```

If tests fail:
1. Read the failure reason from `build/test-results/test/*.xml`
2. Fix the source (not the tests unless the test itself is wrong)
3. Re-run until all tests pass
4. Report the final test counts

### Step 7 — Report

Print a concise summary:
```
✓ Module : <MODULE>
✓ Type   : <Batch | CICS Online | Utility>
✓ Output : java/<artifact-id>/
✓ Tests  : <N> passed, 0 failed
  - <N> unit (Mockito)
  - <N> web  (@WebMvcTest)
  - <N> integration (@SpringBootTest)
```

List any COBOL features that could not be mapped automatically and need manual follow-up (e.g. BMS maps, IMS DB, MQ broker dependency, ABEND codes).
