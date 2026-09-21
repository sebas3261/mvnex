## Context

The plugin's interactive behavior lives in two Maven-free flows, `plugin/InitFlow` and `plugin/AddFlow`, which talk to the user through the `Interaction` port (`ConsoleInteraction` in production, `ScriptedInteraction` in tests). Today:

- `InitFlow` prompts for name, group ID and Java version, and asks about the wrapper. The package is always derived.
- `AddMojo` hands `ex.deps`/`ex.version`/`ex.scope` straight to `DependencyArgumentsParser`, which throws `Missing dependency. Usage: …` when `ex.deps` is absent. `AddFlow` only prompts during disambiguation.
- `ex:setup` and `ex:uninstall` have no input parameters, so nothing changes there.

Batch mode comes from `session.getRequest().isInteractiveMode()`. That covers `-B`, a settings file's `interactiveMode`, and Maven 3.9's automatic batch mode under `CI=true`.

## Goals / Non-Goals

**Goals:**
- Every value a goal accepts is asked for interactively when it wasn't provided.
- Pressing Enter at a new prompt reproduces exactly what the goal did before this change.
- Batch mode is byte-for-byte unchanged in behavior and messages.

**Non-Goals:**
- Prompting in `ex:setup` / `ex:uninstall`, which have no values to collect.
- Changing the prompt UI (numbered choices, defaults in parentheses) or `ConsoleInteraction`.
- Prompting for the test-only `ex.internal.*` properties.

## Decisions

### D1 — Collect `add` values in the flow, before parsing
`AddMojo` passes the raw parameter values (null when absent) to `AddFlow`. A new step, `AddFlow.collect(...)`, runs only when interactive and fills in the missing values. The completed values then go through `DependencyArgumentsParser` exactly as `-D` values do, so prompted answers share the grammar, scope validation and conflict rules.
*Alternative:* prompting inside `DependencyArgumentsParser`. Rejected, because it would mix I/O into a pure parser.

### D2 — Locate the POM before asking anything
`AddMojo` already locates the POM before resolution. The order becomes: locate POM → collect values → parse → offline check → resolve. A user outside a project gets `pom.xml not found` immediately instead of after answering questions. The offline check stays after collection, so its message is unchanged. It could also run first; it's cheap and has no user-visible difference.

### D3 — Version and scope only for a single dependency, and only when missing
This mirrors the existing rule that `ex.version` and `ex.scope` apply to a single dependency. Before asking for a version, `collect` inspects the one expression. An inline version (`term:v` without a dot in the first part, or `g:a:v`) skips the prompt, which avoids the "provided twice" conflict. It uses the same split rules as the parser through a small shared helper (`DependencyArgumentsParser.hasInlineVersion`), so the two can't drift apart.

### D4 — "latest" and "none" as visible defaults
`Interaction.text` shows a non-empty default in parentheses, so the version prompt passes `latest` as its default and maps `latest` (case-insensitive) or an empty answer to "no version". The scope prompt is a `select` whose first option, `none`, is the default and maps to an empty scope. Both keep the prompts self-explanatory without changing `ConsoleInteraction`.

### D5 — Package prompt default computed after name and group ID
In `InitFlow` the package prompt comes after `Group ID` (and before `Java version`). Its default is computed from the values known at that point, whether given or answered, with the existing `ProjectNaming.toPackageName`. The answer is validated by the existing final `ProjectValidator.validate` call, which already uses "package name" messages. The early-validation block stays as is: it only validates values provided as parameters.

The wrapper-prompt rule is deliberately unchanged ("ask unless name, groupId and Java were all given"). The package is optional and shouldn't make the wrapper question appear or disappear.

### D6 — Empty dependency answer re-asks
There's no sensible default for the dependency list. Treating Enter as cancel would surprise users, and failing with the usage message would defeat the prompt. So the flow prints `At least one dependency is required.` and asks again. End of input still cancels, because `ConsoleInteraction` throws `OperationCancelledException`.

### D7 — Choice prompts write through `OutputHandler.write` (found during apply)
The new `Scope` choice rendered as blank lines in the `interactive-add` integration test. `DefaultOutputHandler.writeLine(String)` in plexus-interactivity-api 1.6.0 (the implementation the plugin ships) calls `println()` and ignores its argument. `write(String)` works. `ConsoleInteraction` now writes `text + line separator` through `write`. This also fixes the Java version, Maven Wrapper, and disambiguation menus, which had the same problem in 0.2.0. The unit-test fake now copies the real handler's behavior, and `interactive-init` asserts the menu text.

## Risks / Trade-offs

- **More keystrokes for fully specified interactive runs.** `mvn ex:add -Dex.deps=lombok` now asks two questions. Mitigation: Enter keeps the previous behavior, and scripts already need `-B`, which disables all prompts. This is also documented in the README.
- **Integration test `interactive-init` changes its stdin script.** Any new prompt shifts later answers. Mitigation: that test is updated in the same change, and an interactive `add` test is added so both prompt sequences are pinned.
- **Maven 4 RC.** Piped interactive input is already known to misbehave under Maven 4.0.0-rc-6. More prompts don't change that status, and the Maven 4 CI leg stays allowed to fail.
