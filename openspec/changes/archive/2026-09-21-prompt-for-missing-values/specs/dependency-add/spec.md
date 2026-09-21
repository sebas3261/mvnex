## MODIFIED Requirements

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

## ADDED Requirements

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
