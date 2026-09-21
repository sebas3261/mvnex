## MODIFIED Requirements

### Requirement: Interactive collection of missing values
When Maven runs in interactive mode, the goal SHALL prompt for each missing value in this order, with these defaults applied when the user submits an empty answer:
1. `Project name` (text, default `my-project`) — only if `ex.name` is absent
2. `Group ID` (text, default `com.example`) — only if `ex.groupId` is absent
3. `Package` (text, default `<groupId>.<name with all hyphens removed>`, computed from the given or answered name and groupId) — only if `ex.package` is absent
4. `Java version` (choice of `8, 11, 17, 21, 25`, default `21`) — only if `ex.java` is absent

A package entered at the prompt SHALL be validated like an explicit `ex.package` (package-name messages) before any file is written. If the prompt input stream ends or the prompter fails (the equivalent of Ctrl+C/Ctrl+D), the goal SHALL fail with `Operation cancelled.` and SHALL NOT create any files.

#### Scenario: All values prompted with defaults accepted
- **WHEN** the user runs `mvn ex:init` interactively and presses Enter at every prompt
- **THEN** a project `my-project` is created with groupId `com.example`, package `com.example.myproject`, Java `21`, and Maven Wrapper

#### Scenario: Package default follows the answers
- **WHEN** the user answers `inventory-api` for the project name and `org.acme` for the group ID
- **THEN** the `Package` prompt offers `org.acme.inventoryapi` as its default

#### Scenario: Custom package entered
- **WHEN** the user enters `org.acme.inventory` at the `Package` prompt
- **THEN** `Main.java` is generated in `src/main/java/org/acme/inventory/`

#### Scenario: Invalid package entered
- **WHEN** the user enters `org.acme.Inventory` at the `Package` prompt
- **THEN** the goal fails with `Invalid package name. Use lowercase package segments separated by dots.` and creates nothing

#### Scenario: Package given as a parameter
- **WHEN** `-Dex.package=com.example.app` is given
- **THEN** no `Package` prompt is shown

#### Scenario: Prompted value fails final validation
- **WHEN** the user enters `My App` at the `Project name` prompt
- **THEN** the goal fails with the project-name validation error and creates nothing

#### Scenario: Input closed
- **WHEN** stdin reaches end-of-file at the `Group ID` prompt
- **THEN** the goal fails with `Operation cancelled.`
