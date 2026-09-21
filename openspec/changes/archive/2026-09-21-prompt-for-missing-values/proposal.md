## Why

The goals are inconsistent about missing input. `ex:init` prompts for most missing values, but `mvn ex:add` without `-Dex.deps` fails with a usage error instead of asking. Neither goal offers the optional values (`ex:init`'s package, `ex:add`'s version and scope) interactively, so users have to know the `-D` property names to use them. Every goal should ask for anything that wasn't provided when it runs interactively, and keep today's behavior in batch mode.

## What Changes

- `ex:add` prompts for the dependency list when `ex.deps` is missing in interactive mode, instead of failing with the usage message.
- When a single dependency is added interactively, `ex:add` also prompts for:
  - **Version**, when not given by `ex.version` or inline: Enter keeps today's behavior of choosing the latest stable version.
  - **Scope**, when `ex.scope` is not given: a choice with `none` as the default, which keeps today's behavior.
- `ex:init` prompts for the Java package when `ex.package` is missing in interactive mode. The derived name (`<groupId>.<name without hyphens>`) is the default, so Enter keeps today's result.
- Batch mode (`-B`, or Maven's automatic batch mode when `CI=true`) is unchanged: `ex:add` without `ex.deps` still fails with the usage message, and the optional values keep their defaults silently.
- `ex:setup`, `ex:uninstall`, and `ex:help` take no input values and are unchanged.
- **Behavior change for interactive users:** fully specified interactive runs gain up to two quick prompts (package for `init`; version and scope for single-dependency `add`). Enter at each prompt reproduces the previous result. Scripts should run with `-B`, which they already need to avoid prompts.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `project-init`: the interactive collection of missing values now includes the package, defaulting to the derived name.
- `dependency-add`: a missing dependency list is prompted for interactively instead of failing, and interactive single-dependency adds prompt for the version and scope when they weren't provided.

## Impact

- **Code**: `plugin/InitFlow` (package prompt), `plugin/AddFlow` / `plugin/AddMojo` (collect dependencies, version and scope before parsing), `DependencyArgumentsParser` (called with the collected values). No infrastructure or domain changes.
- **Tests**: `InitFlowTest`, `AddFlowTest` and the `interactive-init` integration test change their prompt sequences. A new interactive `add` integration test is added. Batch-mode integration tests are unaffected.
- **Docs**: README goal sections (prompt behavior), `mvn ex:help` parameter descriptions.
