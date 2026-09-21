## 1. ex:init package prompt

- [x] 1.1 In `InitFlow`, prompt `Package` after `Group ID` and before `Java version` when `ex.package` is absent and the run is interactive, defaulting to `<groupId>.<name without hyphens>` computed from the given or answered values (design D5)
- [x] 1.2 Keep the wrapper-prompt rule and the batch-mode derivation unchanged; rely on the existing final validation for a prompted package
- [x] 1.3 Update `InitFlowTest`: prompt sequences now include `Package [...]`; add tests for the answer-dependent default, a custom package, an invalid package (`Invalid package name. ...`), no prompt when `ex.package` is given, and unchanged batch derivation

## 2. ex:add value collection

- [x] 2.1 Add `DependencyArgumentsParser.hasInlineVersion(String expression)` using the parser's own split rules (design D3)
- [x] 2.2 Add an interactive collection step to `AddFlow`: `Dependencies` (re-ask on empty with `At least one dependency is required.`), then for exactly one dependency `Version` (default `latest`; skipped with an inline version or `ex.version`) and `Scope` (select `none, compile, provided, runtime, test, system, import`, default `none`; skipped with `ex.scope`) (design D1, D4, D6)
- [x] 2.3 Change `AddMojo` to pass the raw parameter values to `AddFlow`, in the order: locate POM, collect, parse, offline check, resolve (design D2); batch mode skips collection so the existing `Missing dependency. Usage: ...` failure is unchanged
- [x] 2.4 Update `AddFlowTest`: everything prompted with defaults, version/scope answered, empty dependency answer re-asks, inline version skips `Version`, provided values not asked again, several dependencies skip version/scope, EOF at `Scope` cancels without writing, and batch mode still fails with the usage message
- [x] 2.5 (Found during apply) Make `ConsoleInteraction` print choice labels and options through `OutputHandler.write`: plexus-interactivity 1.6.0's `DefaultOutputHandler.writeLine` drops its text, so every choice prompt (including 0.2.0's Java version, Maven Wrapper and disambiguation menus) printed blank lines (design D7)

## 3. Integration tests

- [x] 3.1 Update `interactive-init`: stdin gains an Enter for the `Package` prompt; add a run that answers a custom package and assert the `Main.java` location
- [x] 3.2 Add `interactive-add` (WireMock lookups, forced interactive settings as in `interactive-init`): answers `org.projectlombok:lombok`, Enter, Enter → lombok added with the canonical version and no scope; a second run answers `junit-jupiter`, `5.10.0`, `test`
- [x] 3.3 Add `interactive-add-no-pom`: interactive `ex:add` in a directory without a POM fails with `pom.xml not found. ...` and the output shows no `Dependencies` prompt
- [x] 3.4 Run the full suite locally with and without `CI=true`; confirm all batch-mode ITs are unaffected

## 4. Documentation

- [x] 4.1 README: `ex:init` and `ex:add` sections describe the new prompts, their defaults, and that `-B` (or `CI=true`) disables them
- [x] 4.2 Update the Javadoc of `InitMojo.packageName` and `AddMojo`'s parameters so `mvn ex:help -Ddetail` mentions the prompts
- [x] 4.3 Push the branch and confirm CI is green on all required jobs
