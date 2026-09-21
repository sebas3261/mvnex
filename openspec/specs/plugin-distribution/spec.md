# plugin-distribution Specification

## Purpose
TBD - created by archiving change port-to-maven-plugin. Update Purpose after archive.
## Requirements
### Requirement: Plugin coordinates and goal prefix
The tool SHALL be published as a Maven plugin with groupId `com.sebas3261`, artifactId `ex-maven-plugin`, packaging `maven-plugin`, and goal prefix `ex`. The first plugin development version SHALL be `0.2.0-SNAPSHOT`, continuing from the C++ release line `0.1.0`.

#### Scenario: Fully qualified invocation
- **WHEN** a user runs `mvn com.sebas3261:ex-maven-plugin:<version>:init -Dex.name=demo`
- **THEN** the init goal executes

#### Scenario: Prefix invocation
- **WHEN** a user has added `com.sebas3261` to `<pluginGroups>` in `settings.xml` and runs `mvn ex:add -Dex.deps=lombok`
- **THEN** Maven resolves the prefix `ex` to this plugin and runs the add goal

### Requirement: Goal surface
The plugin SHALL expose exactly the goals `init`, `add`, `setup`, `uninstall`, and `help`. `init`, `setup`, and `uninstall` SHALL NOT require a project; `add` SHALL work both with and without a project loaded by Maven (see `dependency-add`). `init`, `add`, `setup`, and `uninstall` SHALL run once per invocation (aggregator semantics) and SHALL be thread-safe for parallel builds.

#### Scenario: Help goal lists goals
- **WHEN** a user runs `mvn ex:help`
- **THEN** the output lists `init`, `add`, `setup`, `uninstall`, and `help` with their descriptions (`Create a new Maven project`, `Add a dependency to the project`, `Register the ex plugin prefix in your Maven settings`, `Remove the ex plugin prefix from your Maven settings`)

#### Scenario: Detailed help
- **WHEN** a user runs `mvn ex:help -Ddetail -Dgoal=add`
- **THEN** every `add` parameter with its `ex.*` user property and description is shown

### Requirement: Help and version replace CLI flags
`mvnex`, `mvnex --help`/`-h`, `mvnex <command> --help`, and `mvnex --version`/`-v` SHALL be replaced by the generated `help` goal, which SHALL display the plugin's version. Command examples previously shown in CLI help SHALL appear in the goal descriptions using the `mvn ex:<goal> -Dex.<param>=...` syntax.

#### Scenario: Version visible
- **WHEN** a user runs `mvn ex:help`
- **THEN** the output header includes `ex-maven-plugin` and its version

### Requirement: Runtime baselines
The plugin SHALL declare `requiredJavaVersion` 17 and `requiredMavenVersion` 3.9.1 in its plugin descriptor so that Maven's own prerequisite checks reject unsupported environments with a clear message before loading plugin classes. The declared Maven floor SHALL be verified by CI. (It was lowered-bound empirically: Maven 3.9.0 fails every aggregator goal run without a POM because `MavenProject.getCollectedProjects()` returns null, while 3.9.1 passes the full suite.) It SHALL NOT require an external `mvn` executable, native libraries, or a TTY for batch-mode operation. Generated projects SHALL still support `maven.compiler.release` values 8–25 independent of the JDK running the plugin.

#### Scenario: Older JDK
- **WHEN** Maven runs on Java 11
- **THEN** Maven refuses to run the goal with its prerequisite message naming the required Java version 17, and no `UnsupportedClassVersionError` stack trace is shown

#### Scenario: Older Maven
- **WHEN** a goal is run with Maven 3.8.8
- **THEN** Maven refuses with its prerequisite message naming the required Maven version

#### Scenario: Floor verified
- **WHEN** CI runs the unit and integration tests on Maven 3.9.1
- **THEN** they all pass

#### Scenario: Maven 3.9.0 is rejected
- **WHEN** a goal is run with Maven 3.9.0
- **THEN** Maven refuses with its prerequisite message naming the required Maven version 3.9.1

### Requirement: Failure reporting
Every error condition that made the C++ CLI exit with status `1` SHALL fail the Maven build via `MojoFailureException` carrying the same message text (with CLI flag names replaced by `ex.*` property names). Informational output SHALL use Maven's logger at `INFO`; warnings at `WARN`; non-fatal errors at `ERROR`. ANSI styling SHALL rely on Maven's own message styling only.

#### Scenario: Error surfaced as build failure
- **WHEN** `mvn ex:init -Dex.name=Bad` is run
- **THEN** the build fails with `BUILD FAILURE` and the message `Invalid project name. Use lowercase letters, numbers and hyphens.`

### Requirement: Network configuration
All network access during dependency resolution SHALL go through Maven's resolver transport, using the proxy selection, mirror routing, and certificate-hint rules of the `dependency-resolution` capability. The plugin SHALL honor Maven offline mode (`-o`) by failing resolution immediately with `Dependency lookup requires network access; it is not available in offline mode (-o).`

#### Scenario: Offline mode
- **WHEN** a user runs `mvn -o ex:add -Dex.deps=lombok`
- **THEN** the goal fails with the offline message without attempting any network request, and the POM is unchanged

### Requirement: Unknown parameter detection
At the start of every goal, the plugin SHALL inspect all `ex.*` keys in the session's user properties (`-D` options and `.mvn/maven.config`) and system properties (e.g. `MAVEN_OPTS`). The known keys are the union of every goal's parameters (`ex.name`, `ex.groupId`, `ex.package`, `ex.java`, `ex.wrapper`, `ex.deps`, `ex.version`, `ex.scope`) plus the test-only prefix `ex.internal.`. Then:
- a key that no goal recognizes SHALL fail the goal with `Unknown parameter: <key>` and, when a known key is within edit distance 2, the suffix ` (did you mean <known key>?)`;
- a key recognized only by other goals SHALL produce the warning `Parameter <key> is not used by ex:<goal> and will be ignored.`

This replaces the C++ `Unknown option: <option>` error. Because Maven resolves repeated `-D` options itself (last one wins) and turns a bare `-Dkey` into `true`, the C++ `Option already provided` and `Option requires a value` errors have no equivalent.

#### Scenario: Typo fails
- **WHEN** a user runs `mvn ex:add -Dex.deps=junit-jupiter -Dex.scop=test`
- **THEN** the goal fails with `Unknown parameter: ex.scop (did you mean ex.scope?)` before any network request

#### Scenario: Shared configuration tolerated
- **WHEN** `.mvn/maven.config` contains `-Dex.groupId=com.acme` and the user runs `mvn ex:add -Dex.deps=lombok`
- **THEN** the goal logs `Parameter ex.groupId is not used by ex:add and will be ignored.` and proceeds

#### Scenario: Test overrides accepted
- **WHEN** an integration test passes `-Dex.internal.sonatypeUrl=http://localhost:8089/solrsearch/select`
- **THEN** no warning or failure is produced for that key

### Requirement: Automated verification
The build SHALL run unit tests for validation, parsing, ranking, version selection, and POM insertion, with HTTP providers tested against stubbed endpoints (no live network in CI). It SHALL run integration tests that invoke the packaged plugin through a real Maven for `init` (wrapper and no-wrapper) and `add` (against stubbed endpoints) and assert on generated files byte-for-byte.

#### Scenario: CI run
- **WHEN** `mvn verify` runs in CI
- **THEN** unit and integration tests execute and pass without contacting the internet except for Maven dependency downloads

### Requirement: Continuous integration and release
The CI workflow SHALL build and verify the plugin on every push and pull request across Linux, macOS, and Windows on at least Java 17 and 21. Pushing a `v*` tag SHALL build a release and attach the plugin JAR, POM, and sources/javadoc JARs to a GitHub prerelease. Publishing to Maven Central SHALL be a separate, explicitly triggered step.

#### Scenario: Tag release
- **WHEN** tag `v0.2.0` is pushed
- **THEN** a GitHub prerelease `v0.2.0` is created containing `ex-maven-plugin-0.2.0.jar` and its POM

### Requirement: Native CLI removed
The C++ sources, CMake build, and native-binary release packaging SHALL be removed, and the documentation SHALL describe only the plugin. The README SHALL start installation instructions with the one-time `setup` goal and SHALL include a migration table from every `mvnex` command/flag to its plugin equivalent.

#### Scenario: Repository state after the change
- **WHEN** the change is complete
- **THEN** no `CMakeLists.txt` or `*.cpp`/`*.h` files remain and `README.md` contains the migration table

