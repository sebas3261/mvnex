# dependency-add Specification

## Purpose
TBD - created by archiving change port-to-maven-plugin. Update Purpose after archive.
## Requirements
### Requirement: Add goal parameters
The plugin SHALL provide an `add` goal (`mvn ex:add`) accepting:

| Property | Meaning | C++ equivalent |
|---|---|---|
| `ex.deps` | Comma-separated list of dependency expressions; each item trimmed of surrounding whitespace | positional `<dependency>...` |
| `ex.version` | Version applied to the single dependency | `-v, --version` |
| `ex.scope` | Scope applied to the single dependency | `-s, --scope` |

The goal SHALL execute exactly once per invocation, even inside a multi-module reactor. When `ex.deps` is missing, interactive runs SHALL prompt for it (see "Interactive collection of add values"); batch runs SHALL fail with the usage message.

#### Scenario: Multiple dependencies
- **WHEN** the user runs `mvn ex:add -Dex.deps=lombok,org.postgresql:postgresql`
- **THEN** both dependencies are resolved and processed in the given order

#### Scenario: Missing dependency list in batch mode
- **WHEN** the user runs `mvn -B ex:add` with no `ex.deps`
- **THEN** the goal fails with `Missing dependency. Usage: mvn ex:add -Dex.deps=<dependency>[,<dependency>...] [-Dex.version=<version>] [-Dex.scope=<scope>]`

#### Scenario: Missing dependency list in interactive mode
- **WHEN** the user runs `mvn ex:add` interactively with no `ex.deps`
- **THEN** the goal prompts `Dependencies` instead of failing

### Requirement: Single-dependency options
`ex.version` and `ex.scope` SHALL only be allowed when exactly one dependency is given. Otherwise the goal SHALL fail with `--version can only be used when adding a single dependency.` / `--scope can only be used when adding a single dependency.` reworded to the property names (`ex.version can only be used when adding a single dependency.`, `ex.scope can only be used when adding a single dependency.`). The version check SHALL be performed before the scope check.

#### Scenario: Version with two dependencies
- **WHEN** `-Dex.deps=lombok,guava -Dex.version=1.0` is given
- **THEN** the goal fails with `ex.version can only be used when adding a single dependency.`

### Requirement: Scope validation
A non-empty scope SHALL be one of `compile`, `provided`, `runtime`, `test`, `system`, `import`; otherwise the goal SHALL fail with `Invalid dependency scope: <scope>`.

#### Scenario: Invalid scope
- **WHEN** `-Dex.scope=testing` is given
- **THEN** the goal fails with `Invalid dependency scope: testing`

### Requirement: Dependency expression grammar
Each expression SHALL be split on every `:` into parts. An expression with zero parts, more than three parts, or any empty part SHALL fail with `Invalid dependency format: <expression>`. Otherwise:

| Parts | Condition | Request type |
|---|---|---|
| `term` | — | search term (version from `ex.version`, if any) |
| `first:second` | `first` contains `.` | coordinate `first:second` (version from `ex.version`, if any) |
| `first:second` | `first` has no `.` | search term `first` with version `second` |
| `g:a:v` | — | coordinate with version `v` |

When both an inline version and `ex.version` are present they SHALL be identical, otherwise the goal SHALL fail with `Dependency version was provided twice with different values.`

#### Scenario: Search term with inline version
- **WHEN** the expression is `lombok:1.18.32`
- **THEN** it is parsed as search term `lombok` with version `1.18.32`

#### Scenario: Coordinate detection by dot
- **WHEN** the expression is `org.projectlombok:lombok`
- **THEN** it is parsed as coordinate `org.projectlombok:lombok` without version

#### Scenario: Dotless groupId is treated as a search term (preserved quirk)
- **WHEN** the expression is `junit:junit`
- **THEN** it is parsed as search term `junit` with version `junit`

#### Scenario: Too many parts
- **WHEN** the expression is `a:b:c:d`
- **THEN** the goal fails with `Invalid dependency format: a:b:c:d`

#### Scenario: Empty part
- **WHEN** the expression is `org.x::1.0`
- **THEN** the goal fails with `Invalid dependency format: org.x::1.0`

#### Scenario: Matching duplicate version allowed
- **WHEN** the expression is `lombok:1.18.32` with `-Dex.version=1.18.32`
- **THEN** parsing succeeds with version `1.18.32`

#### Scenario: Conflicting versions
- **WHEN** the expression is `lombok:1.18.32` with `-Dex.version=1.18.30`
- **THEN** the goal fails with `Dependency version was provided twice with different values.`

### Requirement: Locating the target POM
The goal SHALL target the POM file Maven was pointed at with `-f`/`--file` when given; otherwise it SHALL use the nearest `pom.xml` found by walking from the execution root directory up through its ancestors. If none is found the goal SHALL fail with `pom.xml not found. Run this command inside a Maven project.` The POM SHALL be located before any network request is made.

#### Scenario: Run from a nested directory without a POM
- **WHEN** the user runs `mvn ex:add -Dex.deps=lombok` from `my-app/src/main/java` where only `my-app/pom.xml` exists
- **THEN** `my-app/pom.xml` is modified

#### Scenario: No POM anywhere
- **WHEN** the goal runs in a directory tree without any `pom.xml`
- **THEN** it fails with `pom.xml not found. Run this command inside a Maven project.` and performs no dependency lookups

### Requirement: Resolution and scope attachment
Every parsed request SHALL be resolved (see the `dependency-resolution` capability) in list order before the POM is modified. The requested scope SHALL be attached to the resolved dependency after resolution; resolution SHALL never supply a scope. If any resolution fails (other than ambiguity) the goal SHALL fail with the resolution error message and the POM SHALL be left unchanged.

#### Scenario: Scope attached
- **WHEN** `-Dex.deps=junit-jupiter -Dex.scope=test` resolves to `org.junit.jupiter:junit-jupiter:X`
- **THEN** the added dependency has `<scope>test</scope>`

#### Scenario: Second dependency not found
- **WHEN** `-Dex.deps=lombok,doesnotexist12345` is given and the second term is not found
- **THEN** the goal fails and the POM is unchanged, including no entry for `lombok`

### Requirement: Interactive disambiguation
When resolving request *i* yields multiple candidates and Maven is interactive, the goal SHALL prompt `Select dependency for <query>` offering the first three candidates in ranked order, each formatted `groupId:artifactId:version`, followed by `Search again...`. Then:
- choosing a candidate SHALL replace request *i* with that exact coordinate and version (keeping its scope) and restart resolution of all requests;
- choosing `Search again...` SHALL prompt `Search dependency` with the previous query as the default; an empty answer SHALL use that previous query (repeating the search). Request *i* SHALL be replaced with a search term (keeping its scope, dropping any version) and resolution restarted;
- an ended/failed prompt (closed input, EOF) SHALL fail with `Operation cancelled.`

The loop SHALL repeat until resolution completes without ambiguity.

#### Scenario: Pick a candidate
- **WHEN** `lombok` resolves to `org.projectlombok:lombok:1.18.48`, `io.github.valuya:lombok:1.18.46.4`, `name.remal.gradle-plugins.lombok:lombok:3.2.0`, and one more
- **THEN** exactly those first three plus `Search again...` are offered, and choosing the first adds `org.projectlombok:lombok:1.18.48`

#### Scenario: Search again
- **WHEN** the user chooses `Search again...` and enters `lombok-maven-plugin`
- **THEN** resolution restarts with search term `lombok-maven-plugin` for that position

#### Scenario: Search again with empty answer
- **WHEN** the user chooses `Search again...` for `lombok` and submits an empty answer
- **THEN** resolution restarts with search term `lombok` and the selector is shown again

#### Scenario: Input closed at the selector
- **WHEN** stdin reaches end-of-file at `Select dependency for lombok`
- **THEN** the goal fails with `Operation cancelled.` and the POM is unchanged

### Requirement: Non-interactive disambiguation
When Maven runs in batch mode and resolution is ambiguous, the goal SHALL fail with a message containing `Multiple dependency matches found: <query>`, the first three candidates as `groupId:artifactId:version`, and a hint to pass an exact `groupId:artifactId` coordinate.

#### Scenario: Ambiguous term in CI
- **WHEN** `mvn -B ex:add -Dex.deps=lombok` finds several candidates
- **THEN** the goal fails listing the candidates and the POM is unchanged

### Requirement: Duplicate detection
A resolved dependency SHALL be skipped when the POM's **project-level** `<dependencies>` element (the direct child of `<project>`) already contains a `<dependency>` whose first `<groupId>` and first `<artifactId>` child values, trimmed, equal the resolved `groupId` and `artifactId`, or when an earlier dependency in the same invocation has the same `groupId:artifactId`. Versions and scopes SHALL NOT affect duplicate detection. Entries under `<dependencyManagement>`, plugin `<dependencies>`, and profile `<dependencies>` SHALL NOT count, and text inside XML comments and CDATA SHALL be ignored. (Behavior delta: the C++ tool matched `<dependency>` elements anywhere in the file.)

#### Scenario: Already declared
- **WHEN** the POM declares `org.projectlombok:lombok:1.18.30` and the user adds `lombok`
- **THEN** lombok is reported as skipped and the POM is not modified

#### Scenario: Managed-only dependency is added for real
- **WHEN** the POM manages `org.slf4j:slf4j-api` under `<dependencyManagement>` but does not declare it, and the user adds `org.slf4j:slf4j-api`
- **THEN** it is added to the project-level `<dependencies>` rather than skipped

#### Scenario: Commented-out dependency does not count
- **WHEN** the project-level `<dependencies>` contains `<!-- <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></dependency> -->` and the user adds `lombok`
- **THEN** lombok is added

#### Scenario: Duplicated in one invocation
- **WHEN** the user runs `-Dex.deps=org.projectlombok:lombok,lombok` and both resolve to `org.projectlombok:lombok`
- **THEN** the first is added and the second is reported as skipped

### Requirement: Format-preserving POM insertion
New dependencies SHALL be inserted as text, preserving every other byte of the POM. Each dependency SHALL be rendered as:
```
        <dependency>
            <groupId>G</groupId>
            <artifactId>A</artifactId>
            <version>V</version>
            <scope>S</scope>
        </dependency>
```
(the `<scope>` line only when a scope was requested), concatenated in order. Every inserted line, including the lines of a wrapping `<dependencies>` block, SHALL end with the POM's own line separator: CRLF if the file's first line break is CRLF, otherwise LF (LF also when the file has no line break). The target is the **project-level** `<dependencies>` element (the direct child of `<project>`), located on the POM text with XML comments and CDATA masked out; `<dependencies>` elements under `<dependencyManagement>`, plugins, or profiles SHALL never be targeted. Then, in the first matching case:
1. **Closing tag at the start of its line** (only whitespace before `</dependencies>` on that line): insert the block at the start of that line.
2. **Closing tag elsewhere on a line** (e.g. `<dependencies></dependencies>`): insert EOL + block immediately before `</dependencies>`.
3. **Self-closing `<dependencies/>`**: replace it with `<dependencies>` + EOL + block + `    </dependencies>`.
4. **No project-level `<dependencies>`**: wrap the block as `    <dependencies>` + EOL + block + `    </dependencies>` + EOL + EOL (EOL = the POM's line separator) and insert it immediately before the last `</project>`.

For the common layout (4-space project-level `<dependencies>`), case 1 produces exactly the C++ tool's output. (Behavior delta: the C++ tool inserted before the last occurrence of the text `    </dependencies>` anywhere, which placed entries inside `<dependencyManagement>` when that was the only `<dependencies>` element.) If `</project>` is absent the goal SHALL fail with `Invalid pom.xml: missing </project>.` If nothing is to be added the POM SHALL NOT be written. The POM SHALL be read and written byte-transparently (decoded and encoded as ISO-8859-1, so every byte round-trips unchanged whatever the file's actual encoding, BOM included); all searched markers and inserted text are ASCII.

#### Scenario: Existing dependencies section
- **WHEN** the POM has a project-level `    <dependencies>` block
- **THEN** the new entries appear just before its closing tag and the rest of the file is byte-identical

#### Scenario: Only dependencyManagement present
- **WHEN** the POM has `<dependencyManagement><dependencies>…</dependencies></dependencyManagement>` and no project-level `<dependencies>`, and the user adds `lombok`
- **THEN** a new project-level `<dependencies>` block is inserted before `</project>` and `<dependencyManagement>` is byte-identical

#### Scenario: Project-level dependencies after dependencyManagement
- **WHEN** the POM has both `<dependencyManagement>` and, later, a project-level `<dependencies>`
- **THEN** the entries are inserted into the project-level `<dependencies>` only

#### Scenario: Self-closing dependencies element
- **WHEN** the POM contains `    <dependencies/>` at project level
- **THEN** it is replaced by an open `<dependencies>` element containing the new entries and a closing `    </dependencies>`

#### Scenario: No dependencies section
- **WHEN** a freshly generated `ex:init` POM receives `lombok`
- **THEN** a `<dependencies>` block followed by a blank line is inserted immediately before `</project>`

#### Scenario: CRLF POM
- **WHEN** the POM uses CRLF line endings
- **THEN** every inserted line ends with CRLF and no other byte of the file changes

#### Scenario: Non-UTF-8 POM
- **WHEN** the POM is ISO-8859-1 encoded and contains `<name>Café</name>` (byte `0xE9`)
- **THEN** after adding a dependency that byte is unchanged and only the inserted ASCII text differs

#### Scenario: Malformed POM
- **WHEN** the POM lacks `</project>`
- **THEN** the goal fails with `Invalid pom.xml: missing </project>.`

### Requirement: Post-edit validation
After at least one dependency was written, the goal SHALL validate the modified project by building its model in-process with Maven's project builder and SHALL log either `Maven validate passed` or, at error level, `Maven validate failed. Check the project output with mvn validate.` A validation failure SHALL NOT fail the goal and SHALL NOT revert the edit. When nothing was added, no validation SHALL run.

#### Scenario: Valid result
- **WHEN** lombok is added to a valid project
- **THEN** the log shows `Maven validate passed` and the build succeeds

#### Scenario: Invalid result
- **WHEN** the edited POM fails model validation
- **THEN** the log shows the failure message at error level, the edit is kept, and the goal still succeeds

### Requirement: Add output
The goal SHALL log:
- when any were added: `Dependencies added` followed by one line per dependency `✓ G:A:V` plus ` [S]` when scoped;
- when any were skipped: `Dependencies skipped` followed by `- G:A:V[ [S]] already exists or was duplicated in this command`;
- when neither: `No dependencies changed.` at warning level.

#### Scenario: Mixed result
- **WHEN** one dependency is added with scope `test` and one is skipped
- **THEN** the log contains `✓ org.junit.jupiter:junit-jupiter:<v> [test]` under `Dependencies added` and the skipped one under `Dependencies skipped`

### Requirement: Execution directory POM must be loadable
Because Maven builds any `pom.xml` in the execution directory (or the file given with `-f`) before running a goal, `add` SHALL be documented as unavailable when Maven cannot load that POM (malformed XML, unresolvable parent or imported BOM); Maven then fails with `The build could not read 1 project` before the goal runs. The documented workaround SHALL be to run the goal from a subdirectory that has no `pom.xml` of its own, where the upward search still finds the intended POM.

#### Scenario: Unresolvable parent
- **WHEN** `mvn ex:add -Dex.deps=lombok` runs in a directory whose `pom.xml` declares a parent that cannot be resolved
- **THEN** Maven fails with `The build could not read 1 project` and the POM is unchanged

#### Scenario: Workaround from a subdirectory
- **WHEN** the same command runs from `src/` beneath that directory
- **THEN** the goal runs and edits the parent directory's `pom.xml` (post-edit validation then reports failure, and the edit is kept)

### Requirement: Interactive collection of add values
When Maven runs in interactive mode, `ex:add` SHALL locate the target POM first (so an absent POM fails before any question), then prompt for each value that was not provided, in this order:
1. `Dependencies` (text, no default) — only if `ex.deps` is absent. The answer uses the same comma-separated expression grammar as `ex.deps`. An empty answer SHALL print `At least one dependency is required.` and ask again.
2. `Version` (text, default `latest`) — only if exactly one dependency was given, `ex.version` is absent, and the expression has no inline version. An empty answer or `latest` (case-insensitive) SHALL mean no requested version, so the version is chosen as without `ex.version`. Any other answer SHALL be used exactly like `ex.version`.
3. `Scope` (choice of `none, compile, provided, runtime, test, system, import`, default `none`) — only if exactly one dependency was given and `ex.scope` is absent. `none` SHALL mean no `<scope>` element.

When more than one dependency is given, no version or scope prompt SHALL be shown. In batch mode no prompt SHALL be shown and missing optional values SHALL keep their defaults. If the input stream ends at any of these prompts the goal SHALL fail with `Operation cancelled.` and SHALL NOT modify the POM.

#### Scenario: Everything prompted, defaults accepted
- **WHEN** the user runs `mvn ex:add` interactively, answers `org.projectlombok:lombok` at `Dependencies`, and presses Enter at `Version` and `Scope`
- **THEN** lombok is added with the canonical version and no `<scope>`

#### Scenario: Version and scope answered
- **WHEN** the user answers `junit-jupiter` at `Dependencies`, `5.10.0` at `Version`, and `test` at `Scope`
- **THEN** the dependency is added as `org.junit.jupiter:junit-jupiter:5.10.0` with `<scope>test</scope>`

#### Scenario: Empty dependency answer
- **WHEN** the user presses Enter at `Dependencies`
- **THEN** `At least one dependency is required.` is shown and `Dependencies` is asked again

#### Scenario: Inline version skips the version prompt
- **WHEN** `-Dex.deps=lombok:1.18.32` is given interactively
- **THEN** no `Version` prompt is shown and the `Scope` prompt is

#### Scenario: Provided values are not asked again
- **WHEN** `-Dex.deps=lombok -Dex.version=1.18.32 -Dex.scope=provided` is given interactively
- **THEN** no prompt other than dependency disambiguation is shown

#### Scenario: Several dependencies skip version and scope
- **WHEN** the user answers `lombok,guava` at `Dependencies`
- **THEN** no `Version` or `Scope` prompt is shown

#### Scenario: No POM, no questions
- **WHEN** `mvn ex:add` runs interactively in a directory tree without a `pom.xml`
- **THEN** it fails with `pom.xml not found. Run this command inside a Maven project.` before any prompt

#### Scenario: Input closed at Scope
- **WHEN** stdin reaches end-of-file at the `Scope` prompt
- **THEN** the goal fails with `Operation cancelled.` and the POM is unchanged

