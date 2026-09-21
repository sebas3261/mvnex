## ADDED Requirements

### Requirement: Init goal runs without an existing project
The plugin SHALL provide an `init` goal (`mvn ex:init`) that does not require an existing `pom.xml`, executes exactly once per invocation even when started inside a multi-module reactor, and creates the new project directory relative to the Maven execution root directory (the directory Maven was invoked from).

#### Scenario: Run in an empty directory
- **WHEN** the user runs `mvn ex:init -Dex.name=my-app -Dex.groupId=com.example -Dex.java=21` in a directory with no `pom.xml`
- **THEN** a directory `my-app/` is created inside the invocation directory and the build succeeds

#### Scenario: Run inside a multi-module project
- **WHEN** the user runs `mvn ex:init -Dex.name=tool ...` from the root of a reactor with three modules
- **THEN** exactly one `tool/` directory is created in the invocation directory and the goal does not execute per-module

### Requirement: Init parameters
The `init` goal SHALL accept these parameters, each settable as a `-D` user property:

| Property | Meaning | C++ equivalent |
|---|---|---|
| `ex.name` | Project name (also the artifactId and directory name) | positional `[project-name]` |
| `ex.groupId` | Maven groupId | `-g, --group-id` |
| `ex.package` | Java package override | `-p, --package` |
| `ex.java` | Java release version | `-j, --java` |
| `ex.wrapper` | `true`/`false`; `false` means do not generate Maven Wrapper; unset means "not specified" | `--no-wrapper` |

#### Scenario: Fully specified non-interactive init
- **WHEN** the user runs `mvn -B ex:init -Dex.name=my-app -Dex.groupId=com.example -Dex.java=17 -Dex.package=com.example.app -Dex.wrapper=false`
- **THEN** the project is created with those exact values, no prompts are shown, and no Maven Wrapper files are generated

### Requirement: Project name validation
A project name SHALL match `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` (lowercase letters, digits, and single hyphens between segments, starting with a letter). After removing all hyphens, the result SHALL be a valid Java identifier segment (see "Java identifier segment rules") for the selected Java version. Violations SHALL fail the goal with:
- `Invalid project name. '<derived>' is a reserved Java keyword.` when the hyphen-stripped name is a reserved word;
- otherwise `Invalid project name. Use lowercase letters, numbers and hyphens.`

#### Scenario: Valid hyphenated name
- **WHEN** the name is `my-app`
- **THEN** validation passes and the derived package segment is `myapp`

#### Scenario: Uppercase rejected
- **WHEN** the name is `MyApp`
- **THEN** the goal fails with `Invalid project name. Use lowercase letters, numbers and hyphens.`

#### Scenario: Leading digit, double hyphen, or trailing hyphen rejected
- **WHEN** the name is `1app`, `my--app`, or `app-`
- **THEN** the goal fails with `Invalid project name. Use lowercase letters, numbers and hyphens.`

#### Scenario: Name that collapses to a keyword
- **WHEN** the name is `class` or `int`
- **THEN** the goal fails with `Invalid project name. 'class' is a reserved Java keyword.` (respectively `'int'`)

### Requirement: Java identifier segment rules
A package/groupId segment SHALL be valid when it matches `^[a-z_][a-z0-9_]*$`, is not in the reserved-word set, and is not the single character `_` unless the selected Java version is `8`. The reserved-word set SHALL be exactly: `abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null exports module non-sealed open opens permits provides record requires sealed to transitive uses var with yield`.

#### Scenario: Contextual keyword rejected
- **WHEN** a groupId is `com.record.app`
- **THEN** validation fails because `record` is reserved

#### Scenario: Underscore allowed only on Java 8
- **WHEN** a groupId is `com._` with Java `8`
- **THEN** validation passes
- **WHEN** the same groupId is used with Java `21`
- **THEN** validation fails with `Invalid group ID. Use lowercase package segments separated by dots.`

### Requirement: GroupId validation
A groupId SHALL be one or more dot-separated segments, each satisfying the Java identifier segment rules. Failures SHALL use, in this precedence per segment (left to right):
1. empty segment → `Invalid group ID. Package segments cannot be empty.`
2. reserved word → `Invalid group ID. '<segment>' is a reserved Java keyword.`
3. otherwise invalid → `Invalid group ID. Use lowercase package segments separated by dots.`

#### Scenario: Empty segment
- **WHEN** the groupId is `com..example` or `com.example.` or `.com`
- **THEN** the goal fails with `Invalid group ID. Package segments cannot be empty.`

#### Scenario: Uppercase segment
- **WHEN** the groupId is `com.Example`
- **THEN** the goal fails with `Invalid group ID. Use lowercase package segments separated by dots.`

### Requirement: Package validation
An explicit or derived Java package SHALL be validated with the same segment rules as groupId. Error messages SHALL name the package rather than the group ID (e.g. `Invalid package name. Package segments cannot be empty.`).

#### Scenario: Invalid explicit package
- **WHEN** `-Dex.package=com.example.New` is given
- **THEN** the goal fails with `Invalid package name. Use lowercase package segments separated by dots.`

### Requirement: Java version validation
The Java version SHALL be one of `8`, `11`, `17`, `21`, `25`. Any other value SHALL fail with `Invalid Java version. Supported: 8, 11, 17, 21, 25`.

#### Scenario: Unsupported version
- **WHEN** `-Dex.java=22` is given
- **THEN** the goal fails with `Invalid Java version. Supported: 8, 11, 17, 21, 25`

### Requirement: Early validation of provided values
Values supplied as parameters SHALL be validated before any prompt is shown, in the order: Java version, project name, groupId, package. When the Java version is not yet known, name/groupId/package SHALL be validated as if Java `21` were selected. After all values are collected, the complete configuration SHALL be validated again with the final Java version before any file is written.

#### Scenario: Bad groupId fails before prompting
- **WHEN** the user runs `mvn ex:init -Dex.groupId=com.Bad` interactively without a name
- **THEN** the goal fails with the groupId error and no prompt for the project name is shown

#### Scenario: Underscore segment with Java 8 chosen by prompt
- **WHEN** `-Dex.groupId=com._` is provided without `ex.java`
- **THEN** early validation (assuming Java 21) fails before the Java version prompt

### Requirement: Default package derivation
When no package is provided, the package SHALL be `<groupId>.<name with all hyphens removed>`.

#### Scenario: Derived package
- **WHEN** groupId is `com.example` and name is `my-cool-app`
- **THEN** the package is `com.example.mycoolapp`

### Requirement: Interactive collection of missing values
When Maven runs in interactive mode, the goal SHALL prompt for each missing value in this order, with these defaults applied when the user submits an empty answer:
1. `Project name` (text, default `my-project`) — only if `ex.name` is absent
2. `Group ID` (text, default `com.example`) — only if `ex.groupId` is absent
3. `Java version` (choice of `8, 11, 17, 21, 25`, default `21`) — only if `ex.java` is absent

The package SHALL never be prompted for. If the prompt input stream ends or the prompter fails (the equivalent of Ctrl+C/Ctrl+D), the goal SHALL fail with `Operation cancelled.` and SHALL NOT create any files.

#### Scenario: All values prompted with defaults accepted
- **WHEN** the user runs `mvn ex:init` interactively and presses Enter at every prompt
- **THEN** a project `my-project` is created with groupId `com.example`, package `com.example.myproject`, Java `21`, and Maven Wrapper

#### Scenario: Prompted value fails final validation
- **WHEN** the user enters `My App` at the `Project name` prompt
- **THEN** the goal fails with the project-name validation error and creates nothing

#### Scenario: Input closed
- **WHEN** stdin reaches end-of-file at the `Group ID` prompt
- **THEN** the goal fails with `Operation cancelled.`

### Requirement: Maven Wrapper choice
The goal SHALL prompt `Maven Wrapper` with choices `Yes`/`No` (default `Yes`) when, and only when, running interactively, `ex.wrapper` was not set to `false`, and at least one of `ex.name`, `ex.groupId`, `ex.java` was not provided as a parameter. Choosing `No` SHALL skip wrapper generation. When `ex.wrapper=true` is set explicitly it SHALL be treated the same as unset for this rule.

#### Scenario: Fully specified via parameters
- **WHEN** `ex.name`, `ex.groupId`, and `ex.java` are all provided and `ex.wrapper` is unset
- **THEN** no wrapper prompt is shown and the wrapper is generated

#### Scenario: Partially specified
- **WHEN** only `ex.name` is provided interactively
- **THEN** the user is prompted for group ID, Java version, and then `Maven Wrapper`

#### Scenario: Explicit opt-out
- **WHEN** `-Dex.wrapper=false` is provided with no other parameters
- **THEN** no wrapper prompt is shown and no wrapper files are generated

### Requirement: Batch-mode behavior
When Maven runs in batch mode (`-B` or `interactiveMode=false`), the goal SHALL NOT prompt. A missing `ex.name` SHALL fail the goal with `Missing project name. Provide -Dex.name=<name>.` (a directory is never created under a defaulted name). A missing `ex.groupId` SHALL default to `com.example`, a missing `ex.java` SHALL default to `21`, and a missing wrapper choice SHALL default to generating the wrapper — the same defaults the interactive prompts offer.

#### Scenario: Batch mode without name
- **WHEN** the user runs `mvn -B ex:init -Dex.groupId=com.example`
- **THEN** the goal fails with `Missing project name. Provide -Dex.name=<name>.` and no files are created

#### Scenario: Batch mode with defaults
- **WHEN** the user runs `mvn -B ex:init -Dex.name=svc`
- **THEN** a project is created with groupId `com.example`, package `com.example.svc`, Java `21`, and Maven Wrapper files

### Requirement: Existing directory protection
If a file or directory named `<name>` already exists in the execution root directory, the goal SHALL fail with `Project directory already exists: <name>` and SHALL NOT modify anything.

#### Scenario: Directory exists
- **WHEN** `my-app/` already exists and the user runs `mvn ex:init -Dex.name=my-app ...`
- **THEN** the goal fails with `Project directory already exists: my-app` and the existing directory is untouched

### Requirement: Generated project layout
The goal SHALL create:
- `<name>/src/main/java/<package path>/Main.java`
- `<name>/src/test/java/<package path>/` (empty directory)
- `<name>/pom.xml`

where `<package path>` is the package with `.` replaced by the platform path separator.

#### Scenario: Directory tree
- **WHEN** a project `my-app` with package `com.example.myapp` is created
- **THEN** `my-app/src/main/java/com/example/myapp/Main.java` and the empty directory `my-app/src/test/java/com/example/myapp/` exist

### Requirement: Generated Main.java content
`Main.java` SHALL contain exactly the following, with every line (including the last) terminated by the platform line separator (`System.lineSeparator()`: CRLF on Windows, LF elsewhere):
```
package <package>;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from <name>!");
    }
}
```

#### Scenario: Main content
- **WHEN** a project `my-app` with package `com.example.myapp` is created
- **THEN** `Main.java` matches the template byte-for-byte with those values substituted

### Requirement: Generated pom.xml content
`pom.xml` SHALL contain exactly the following, with every line (including the last) terminated by the platform line separator:
```
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId><groupId></groupId>
    <artifactId><name></artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release><java></maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
```

#### Scenario: POM content
- **WHEN** a project `my-app`, groupId `com.example`, Java `17` is created
- **THEN** `pom.xml` matches the template byte-for-byte and `mvn -f my-app/pom.xml validate` succeeds

### Requirement: Maven Wrapper generation
Unless wrapper generation is skipped, the goal SHALL write, inside the project directory, the same files `maven-wrapper-plugin:3.3.4:wrapper` produced when the C++ tool ran it with the user's installed Maven:
- `.mvn/wrapper/maven-wrapper.properties` containing exactly these three lines, each terminated by the platform line separator:
  ```
  wrapperVersion=3.3.4
  distributionType=only-script
  distributionUrl=<REPO>/org/apache/maven/apache-maven/<VER>/apache-maven-<VER>-bin.zip
  ```
  where:
  - `<VER>` is the version of the Maven runtime executing the goal (e.g. `3.9.16`, or a Maven 4 release candidate if that is what runs the goal);
  - `<REPO>` is the value of the `MVNW_REPOURL` environment variable, trimmed and with one trailing `/` removed, when it is set and longer than 4 characters; otherwise the URL of the first mirror in the effective settings whose `mirrorOf` is exactly `*`; otherwise `https://repo.maven.apache.org/maven2`.
- `mvnw` — byte-identical to `mvnw` in `org.apache.maven.wrapper:maven-wrapper-distribution:3.3.4:zip:only-script` (LF line endings), with execute permission added for owner, group, and others on filesystems that support POSIX permissions;
- `mvnw.cmd` — byte-identical to `mvnw.cmd` in the same artifact (CRLF line endings).

`mvnwDebug`/`mvnwDebug.cmd` SHALL NOT be generated.

#### Scenario: Default repository and running Maven version
- **WHEN** Maven 3.9.16 runs `ex:init` with no `MVNW_REPOURL` and no `mirrorOf="*"` mirror
- **THEN** `distributionUrl` is `https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip`

#### Scenario: Company mirror
- **WHEN** settings define a mirror with `mirrorOf` `*` and URL `https://nexus.acme.corp/repository/maven-public`, and Maven 3.9.9 runs the goal
- **THEN** `distributionUrl` is `https://nexus.acme.corp/repository/maven-public/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip`

#### Scenario: MVNW_REPOURL wins over mirrors
- **WHEN** `MVNW_REPOURL=https://repo.example.org/maven2/` is set and a `*` mirror is also configured
- **THEN** `distributionUrl` starts with `https://repo.example.org/maven2/org/apache/maven/apache-maven/`

#### Scenario: Mirror of a specific repository is ignored
- **WHEN** the only mirror has `mirrorOf` `central`
- **THEN** `distributionUrl` uses `https://repo.maven.apache.org/maven2`

#### Scenario: Windows line separators
- **WHEN** a project is created on Windows
- **THEN** `maven-wrapper.properties`, `pom.xml`, and `Main.java` use CRLF, `mvnw` uses LF, and `mvnw.cmd` uses CRLF

Generation SHALL NOT require network access or an external `mvn` executable. A failure SHALL fail the goal with `Failed to generate Maven Wrapper.`

#### Scenario: Wrapper files present and runnable
- **WHEN** a project is created with the wrapper on Linux/macOS
- **THEN** the three files exist, `mvnw` is executable, and `./mvnw -v` runs from the project directory

#### Scenario: Wrapper skipped
- **WHEN** a project is created with `-Dex.wrapper=false`
- **THEN** none of `.mvn/`, `mvnw`, `mvnw.cmd` exist

### Requirement: Init output
Before creating files the goal SHALL log a summary block `Creating Maven project` followed by rows `Project <name>`, `Group <groupId>`, `Package <package>`, `Java <java>`, `Wrapper Maven Wrapper|None`. On success it SHALL log `Project created successfully`, then `cd <name>`, then `./mvnw package` if the wrapper was generated or `mvn package` otherwise.

#### Scenario: Success output without wrapper
- **WHEN** a project `svc` is created with `-Dex.wrapper=false`
- **THEN** the log contains `Wrapper   None`, `Project created successfully`, `cd svc`, and `mvn package`

### Requirement: Execution directory POM must be loadable
Because Maven builds any `pom.xml` in the execution directory before running a goal, `init` SHALL be documented as unavailable in a directory whose `pom.xml` Maven cannot load (malformed XML, unresolvable parent or imported BOM); Maven then fails with `The build could not read 1 project` before the goal runs. The documented workaround SHALL be to run the goal from a directory without a `pom.xml`.

#### Scenario: Broken POM in the execution directory
- **WHEN** `mvn ex:init -Dex.name=demo` runs in a directory containing a malformed `pom.xml`
- **THEN** Maven fails with `The build could not read 1 project` and no `demo/` directory is created

#### Scenario: Workaround
- **WHEN** the same command runs from a directory without a `pom.xml`
- **THEN** the project is created normally
