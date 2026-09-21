# mvnex

**A modern developer experience for Maven.**

`mvnex` is an open-source Maven plugin, `com.sebas3261:ex-maven-plugin`, that brings a simpler, more interactive, npm-like developer experience to Maven projects without replacing Maven.

```bash
mvn ex:init -Dex.name=my-app
cd my-app

mvn ex:add -Dex.deps=lombok
mvn ex:add -Dex.deps=org.postgresql:postgresql
```

> **mvnex enhances Maven. It doesn't replace it.**

Projects created or modified with `mvnex` remain standard Maven projects.

---

## Why mvnex?

Maven is powerful, mature, and widely used throughout the Java ecosystem.

However, many everyday operations still involve verbose commands, manual `pom.xml` editing, or knowledge of exact Maven coordinates.

For example, adding a dependency normally requires finding its coordinates and manually editing the POM:

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.48</version>
</dependency>
```

With `mvnex` it is one command:

```bash
mvn ex:add -Dex.deps=lombok
```

and the result is still a completely standard Maven project.

---

## Design philosophy

### Maven stays Maven

`mvnex` is not a new build system. It does not replace Maven dependency resolution, repositories, the lifecycle, `pom.xml`, or other plugins. It is a developer-experience layer that runs inside Maven.

The `pom.xml` remains the source of truth. You can stop using `mvnex` at any time and continue with `mvn compile`, `mvn test`, and `mvn package`.

### No lock-in

A project created with `mvn ex:init` is a normal Maven project, and `mvn ex:add` writes normal dependency declarations. `mvnex` improves the workflow without creating an ecosystem you depend on.

---

# Installation

**Requirements:** Maven 3.9.1 or newer, running on Java 17 or newer. Generated projects can target any Java release from 8 to 25.

The plugin is not yet published to Maven Central. Install it into your local Maven repository in one of two ways:

**From a release**: download `ex-maven-plugin-<version>.jar` and `ex-maven-plugin-<version>.pom` from the [GitHub releases](https://github.com/sebas3261/mvnex/releases), then:

```bash
mvn install:install-file -Dfile=ex-maven-plugin-<version>.jar -DpomFile=ex-maven-plugin-<version>.pom
```

**From source**:

```bash
git clone https://github.com/sebas3261/mvnex.git
cd mvnex
mvn install
```

### Enable the `ex:` prefix

Maven only resolves short plugin prefixes for groups listed in your settings. Run this once, with the full plugin name (`<version>` is the release version, or the `<version>` in `pom.xml` when you built from source, e.g. `0.2.0-SNAPSHOT`):

```bash
mvn com.sebas3261:ex-maven-plugin:<version>:setup
```

`setup` adds `com.sebas3261` to the `<pluginGroups>` of your user settings file (`~/.m2/settings.xml`, or the file given with `-s`), creating the file if it doesn't exist. Nothing else in the file changes, and a timestamped backup is written before an existing file is modified. Afterwards `mvn ex:init`, `mvn ex:add`, and `mvn ex:help` work.

To undo it:

```bash
mvn ex:uninstall
```

`uninstall` removes only that `<pluginGroup>` entry and leaves everything else untouched.

You can also edit `settings.xml` yourself:

```xml
<settings>
  <pluginGroups>
    <pluginGroup>com.sebas3261</pluginGroup>
  </pluginGroups>
</settings>
```

Without the prefix, every goal still works by its full name, for example `mvn com.sebas3261:ex-maven-plugin:<version>:init`.

---

# Goals

Parameters are passed as `-D` user properties. Run `mvn ex:help -Ddetail -Dgoal=<goal>` for the full reference.

## `mvn ex:init`

Create a new Maven project in a new directory under the current directory:

```bash
mvn ex:init                                   # interactive
mvn ex:init -Dex.name=my-app                  # prompt only for what's missing
mvn ex:init -Dex.name=my-app -Dex.groupId=com.example -Dex.java=21
mvn ex:init -Dex.name=my-app -Dex.package=com.example.app
mvn ex:init -Dex.name=my-app -Dex.wrapper=false
```

| Property | Meaning | Default |
|---|---|---|
| `ex.name` | Project name: lowercase letters, digits and single hyphens. Also the artifactId and directory name. | prompted (`my-project`) |
| `ex.groupId` | Maven groupId | prompted (`com.example`) |
| `ex.package` | Java package of the generated `Main` class | prompted (groupId + name without hyphens) |
| `ex.java` | `maven.compiler.release`: 8, 11, 17, 21, or 25 | prompted (21) |
| `ex.wrapper` | `false` skips the Maven Wrapper | asked when not everything was given |

Prompts come in the order name, groupId, package, Java version, then the wrapper. Pressing Enter accepts the default shown in parentheses; the package default is built from the name and groupId you just gave. In batch mode (`mvn -B`) nothing is prompted: `ex.name` is required and everything else uses its default. Maven 3.9 also switches to batch mode by itself when the `CI` environment variable is `true`, as on GitHub Actions.

The generated project contains `pom.xml`, `src/main/java/<package>/Main.java`, an empty `src/test/java/<package>/`, and, unless disabled, the Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`). The wrapper pins the Maven version you ran `ex:init` with and downloads it from `MVNW_REPOURL`, else from your `mirrorOf="*"` mirror, else from Maven Central, like `maven-wrapper-plugin` does. Generating it needs no network access.

```text
my-app/
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src/
    ├── main/java/com/example/myapp/Main.java
    └── test/java/com/example/myapp/
```

## `mvn ex:add`

Add dependencies to the nearest `pom.xml`, searching upward from the current directory:

```bash
mvn ex:add                                    # interactive
mvn ex:add -Dex.deps=lombok
mvn ex:add -Dex.deps=lombok:1.18.48
mvn ex:add -Dex.deps=org.projectlombok:lombok
mvn ex:add -Dex.deps=org.projectlombok:lombok:1.18.48
mvn ex:add -Dex.deps=junit-jupiter -Dex.scope=test
mvn ex:add -Dex.deps=lombok,org.postgresql:postgresql
```

| Property | Meaning |
|---|---|
| `ex.deps` | Comma-separated dependency expressions |
| `ex.version` | Version, when adding a single dependency |
| `ex.scope` | `compile`, `provided`, `runtime`, `test`, `system`, or `import`, when adding a single dependency |

When run interactively, `ex:add` first finds the `pom.xml`, then asks for anything missing:

- **Dependencies**, when `ex.deps` is absent. It uses the same comma-separated expressions; an empty answer asks again.
- **Version**, for a single dependency without `ex.version` or an inline version. Enter (or `latest`) keeps the automatic version choice.
- **Scope**, for a single dependency without `ex.scope`. `none` (the default) adds no `<scope>`.

In batch mode (`mvn -B`, or `CI=true`) nothing is prompted, and a missing `ex.deps` fails with the usage message.

How it works:

- **Lookups.** Search terms and coordinates are looked up on Sonatype Central, search.maven.org, and deps.dev.
- **Ambiguous names.** When a name matches several artifacts, you pick from the top three or search again. In batch mode the goal fails and lists the candidates, so you can pass an exact `groupId:artifactId`.
- **Version choice.** Without a version, `mvnex` chooses the highest stable version published on Maven Central, and the version list is read through your mirrors. The result is the same whichever search service answers first, and internal builds that only your mirror serves are skipped.
- **Duplicates.** Dependencies already declared in the project-level `<dependencies>` are skipped. Entries under `<dependencyManagement>`, in plugins, or in comments don't count.
- **Editing the POM.** New entries are inserted as text, so formatting, comments, encoding, and line endings are preserved.
- **Validation.** The project is validated afterwards, with the same model checks as `mvn validate`. A validation failure is reported but doesn't undo the change.

---

# Network configuration

Dependency lookups use Maven's own HTTP transport, so they share your Maven TLS settings, and repository lookups go through your mirrors.

The proxy is chosen from the first source that applies:

1. **Maven settings:** the active `<proxy>` in `settings.xml`, including credentials and `nonProxyHosts`.
2. **Environment variables**, following curl's rules: `https_proxy`/`HTTPS_PROXY`, then `all_proxy`/`ALL_PROXY`, lowercase first. The value has the form `[scheme://][user:password@]host[:port]`; a missing port means 1080. `no_proxy`/`NO_PROXY` excludes hosts.
3. **JVM properties:** `https.proxyHost`, `https.proxyPort`, `http.nonProxyHosts`.

SOCKS proxies in environment variables are not supported and fail with an explanation. Configure SOCKS for the JVM instead, for example `MAVEN_OPTS="-DsocksProxyHost=<host> -DsocksProxyPort=<port>"`.

Offline mode (`mvn -o`) makes `ex:add` fail immediately, because lookups need network access.

## Certificate errors

If lookups fail with a certificate error (`PKIX path building failed`), your network probably inspects HTTPS traffic with an organization certificate that Java doesn't trust. Import your organization's root certificate into the JDK truststore, or tell Java to use the operating system's store by adding this to `.mvn/jvm.config` or `MAVEN_OPTS`:

- macOS: `-Djavax.net.ssl.trustStoreType=KeychainStore`
- Windows: `-Djavax.net.ssl.trustStoreType=Windows-ROOT`

---

# Known limitations

- **Maven must be able to load the `pom.xml` in the current directory.** Maven reads that file before any goal runs, so if it is malformed or its parent or BOM can't be resolved, `ex:add` and `ex:init` stop with `The build could not read 1 project`. Workaround: run the goal from a subdirectory without its own `pom.xml`, such as `src/`. `ex:add` still finds and edits the POM above it.
- **Validation after `ex:add` checks the POM model only.** Plugins bound to the `validate` phase, such as enforcer, don't run. Run `mvn validate` yourself if you rely on them.
- **Maven 4** release candidates are not supported yet. With Maven 4.0.0-rc-6, interactive prompts misread answers piped through standard input.
- **`setup` with `-s`**: Maven rejects a `-s` file that doesn't exist, so `setup` only creates `~/.m2/settings.xml`.
- **POM editing** is text-based. It targets the project-level `<dependencies>` and doesn't yet understand parent POMs, `dependencyManagement` versions, or multi-module layouts.

---

# Migrating from the `mvnex` CLI

Earlier versions of `mvnex` were a native command-line tool. Everything it did is available as a goal:

| `mvnex` CLI | Maven plugin |
|---|---|
| `mvnex` / `mvnex --help` / `-h` | `mvn ex:help` |
| `mvnex <command> --help` | `mvn ex:help -Ddetail -Dgoal=<goal>` |
| `mvnex --version` / `-v` | `mvn ex:help` (shows the version) |
| `mvnex init` | `mvn ex:init` |
| `mvnex init my-app` | `mvn ex:init -Dex.name=my-app` |
| `--group-id` / `-g <id>` | `-Dex.groupId=<id>` |
| `--package` / `-p <name>` | `-Dex.package=<name>` |
| `--java` / `-j <version>` | `-Dex.java=<version>` |
| `--no-wrapper` | `-Dex.wrapper=false` |
| `mvnex add <dep> [<dep>...]` | `mvn ex:add -Dex.deps=<dep>[,<dep>...]` |
| `--version` / `-v <version>` (add) | `-Dex.version=<version>` |
| `--scope` / `-s <scope>` (add) | `-Dex.scope=<scope>` |

Behavior differences from the CLI:

- **Batch mode.** `mvn -B` never prompts.
- **Wrapper generation.** The wrapper is always generated in-process, with no network and no spinner.
- **Line endings.** Generated files use the platform line separator, and `mvnw.cmd` is always CRLF.
- **POM edits.** They keep the file's own line endings and never land inside `<dependencyManagement>`.
- **Versions.** Versionless lookups pick the same version whichever service answers first.
- **Mistyped parameters.** A misspelled `ex.*` property fails with a suggestion, where the CLI said `Unknown option`.

---

# Planned

The following commands describe the direction of the project and are **not implemented yet**.

- Dependencies: `ex:remove`, `ex:update` (change a dependency's version, up or down), `ex:outdated`.
- Workflow: `ex:install` to prepare a project for development, and project scripts in an `mvnex.toml`.
- Java management: discover, list, install, and select JDKs.
- Maven integration: structural POM parsing and editing, `dependencyManagement` and parent POM awareness, multi-module support.
- Distribution: publishing to Maven Central.

---

# Building from source

```bash
mvn verify            # unit and integration tests
mvn test -Dtest=AddFlowTest
mvn verify -Dinvoker.test=add-basic
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the internal architecture.

---

# License

Copyright 2026 Sebastián Sánchez.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
