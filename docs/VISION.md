# mvnex — Vision

## The idea

`mvnex` aims to provide a modern developer experience for Maven.

Maven is not the problem `mvnex` is trying to solve.

Maven provides a mature build lifecycle, dependency resolution system, plugin ecosystem, repository model, and project format used throughout the Java ecosystem.

`mvnex` exists to make interacting with those capabilities simpler.

The guiding idea is:

> **mvnex enhances Maven. It doesn't replace it.**

---

# The problem

Developers coming from ecosystems such as npm, pnpm, Cargo, or modern developer CLIs are accustomed to workflows such as:

```bash
pnpm add axios
cargo add serde
```

Maven frequently requires a more manual workflow.

Adding a dependency may involve:

1. searching Maven Central
2. finding the correct artifact
3. determining the appropriate version
4. copying its Maven coordinates
5. opening `pom.xml`
6. finding or creating `<dependencies>`
7. inserting the dependency
8. returning to the terminal

The underlying Maven dependency model is powerful.

The interaction can be improved.

`mvnex` should make the common case:

```bash
mvnex add lombok
```

---

# Core principles

## 1. Maven remains the engine

`mvnex` should not implement a competing Maven build system.

Where Maven already has mature functionality, `mvnex` should orchestrate or configure Maven instead of reimplementing it.

This includes:

- dependency resolution
- transitive dependencies
- Maven repositories
- mirrors
- credentials
- build lifecycle
- plugins
- BOMs
- packaging

---

## 2. Standard Maven projects

Using `mvnex` must not require adopting a proprietary project format.

The following:

```bash
mvnex init my-app
```

must generate a standard Maven project.

The following:

```bash
mvnex add lombok
```

must modify the Maven project in a standards-compatible way.

At any point, a developer should be able to uninstall `mvnex` and continue using:

```bash
mvn compile
mvn test
mvn package
```

---

## 3. `pom.xml` remains the source of truth

`mvnex` may eventually introduce optional configuration such as:

```text
mvnex.toml
```

but dependency declarations must not be moved away from Maven.

For example, this would be undesirable:

```toml
[dependencies]
lombok = "1.18.48"
```

if it caused `pom.xml` to stop being authoritative.

Maven tools, IDEs, CI systems, and developers should continue understanding the project without requiring `mvnex`.

---

## 4. No unnecessary lock-in

A developer should be able to:

```bash
mvnex init my-api
mvnex add lombok
```

and then never use `mvnex` again.

The project must continue working normally.

This makes trying `mvnex` low-risk.

---

## 5. Optimize common workflows

`mvnex` should not expose every Maven capability through a second syntax.

Commands should exist when they meaningfully improve the developer experience.

Examples:

```bash
mvnex init
mvnex add
mvnex remove
mvnex update
mvnex outdated
mvnex install
mvnex run
```

There is no requirement for every:

```bash
mvn ...
```

command to have an:

```bash
mvnex ...
```

equivalent.

---

# Dependency experience

One of the primary goals of `mvnex` is improving dependency management.

## Short names

Desired:

```bash
mvnex add lombok
```

Possible flow:

```text
lombok
   │
   ▼
Maven Central search
   │
   ▼
org.projectlombok:lombok
   │
   ▼
Maven repository metadata
   │
   ▼
latest stable release
   │
   ▼
pom.xml
```

---

## Explicit versions

```bash
mvnex add lombok:1.18.48
```

should resolve the artifact and validate the requested version.

---

## Maven coordinates

Experienced Maven users should be able to bypass search:

```bash
mvnex add org.projectlombok:lombok
```

or specify everything:

```bash
mvnex add org.projectlombok:lombok:1.18.48
```

The more information the developer supplies, the less discovery `mvnex` should perform.

---

## Ambiguous dependencies

`mvnex` must never silently select an arbitrary artifact simply because it appeared first in a search result.

For:

```bash
mvnex add guice
```

multiple artifacts may exist.

The user should be able to select the intended dependency interactively.

Correctness is more important than pretending the CLI can magically infer every intention.

---

# Dependency resolution

`mvnex` should distinguish between two concepts.

## Artifact discovery

Question:

> Which Maven artifact does "lombok" refer to?

This can use Maven Central search.

## Version metadata

Question:

> Which versions exist for `org.projectlombok:lombok`?

This should use Maven repository metadata where appropriate.

For example:

```text
org.projectlombok:lombok
        │
        ▼
maven-metadata.xml
        │
        ├── latest
        ├── release
        └── versions
```

These responsibilities should remain separate internally.

---

# POM manipulation

`pom.xml` must be treated as XML, not as a string template once modifying existing projects.

`mvnex` should not rely on operations such as:

```text
find "</dependencies>"
insert text before it
```

Real Maven projects may contain:

- parents
- properties
- dependency management
- plugins
- profiles
- repositories
- modules
- inherited configuration
- BOM imports

POM modifications should therefore be structural.

---

# `mvnex install`

`mvnex install` intentionally does not mean exactly the same thing as:

```bash
mvn install
```

In Maven, `install` is a lifecycle phase that installs the project's built artifact into the local Maven repository.

The intended `mvnex install` experience is closer to:

> Prepare this project and its dependencies so I can work on it.

Maven should still perform Maven dependency resolution.

`mvnex` should orchestrate rather than reimplement that process.

Exact behavior will be defined before implementation.

---

# Project scripts

An optional future configuration file may provide project-level developer commands.

Example:

```toml
[java]
version = 21

[scripts]
dev = "mvn spring-boot:run"
build = "mvn clean package"
test = "mvn test"
```

Then:

```bash
mvnex run dev
mvnex run build
mvnex run test
```

This configuration is intended for developer convenience.

It should not replace Maven's POM.

---

# JDK management

A future version may help developers manage Java installations:

```bash
mvnex java install 21
mvnex java use 21
mvnex java list
```

Potential responsibilities include:

- detecting installed JDKs
- determining project Java requirements
- downloading supported JDK distributions
- selecting a JDK for the current project

JDK management should remain modular and optional.

---

# Project initialization

`mvnex init` should evolve into a polished project generator.

Potential project types:

```text
Java
Spring Boot
Library
CLI
```

Interactive configuration should remain simple and terminal-native.

Example:

```text
◆  Create a new Maven project

◇  Project name (my-project)
│  ›

◇  Project type
│  Java

◇  Group ID (com.example)
│  ›

◇  Java version (21)
│  ›

◆  Project created successfully!
```

The CLI should be visually polished without becoming a full-screen TUI.

---

# Terminal experience

The CLI should have a consistent visual language.

Examples:

```text
◆  action / success

◇  question / progress

│  visual flow

›  user input

✖  error
```

Colors should be restrained.

Suggested semantics:

```text
cyan     interaction / primary action
green    success
red      errors
dim      secondary information
bold     important values
```

Terminal styling must never make the CLI difficult to use in:

- CI
- redirected output
- unsupported terminals
- accessibility-oriented environments

A future `--no-color` option should be considered.

---

# Maven plugin

`mvnex` is distributed as a Maven plugin, `com.sebas3261:ex-maven-plugin`, with the goal prefix `ex`.

Running inside Maven fits the core principle directly. Maven is already installed wherever `mvnex` is useful, so the plugin reuses Maven's repositories, mirrors, proxies, credentials, TLS configuration, and offline mode instead of reimplementing them. The earlier native C++ CLI proved the workflows; the plugin keeps its behavior while removing the per-platform native build.

Using the plugin requires Java 17+ and Maven 3.9.1+. Generated projects can still target any supported Java release.

---

# Distribution

The installation experience should stay simple:

```bash
mvn com.sebas3261:ex-maven-plugin:<version>:setup   # once, registers the ex: prefix
mvn ex:init
```

Today, releases are published as GitHub prereleases containing the plugin JAR and POM, which users install with `mvn install:install-file` (or build from source with `mvn install`).

Publishing to Maven Central is the next distribution step. It would make `mvn com.sebas3261:ex-maven-plugin:<version>:setup` work with no manual install.

Release creation is automated by CI: pushing a `v*` tag builds and attaches the release files.

---

# Open source

`mvnex` is intended to be developed openly.

Desired outcomes include:

- external contributors
- issues from real users
- pull requests
- community-created integrations
- package-maintainer contributions
- forks experimenting with new ideas

Forks are a feature of open-source development, not a failure of the original project.

The canonical project should maintain clear authorship, licensing, release history, and project identity.

---

# What mvnex should NOT become

`mvnex` should avoid becoming:

### Another Maven

Do not rebuild Maven's dependency resolver, lifecycle, or plugin system merely to own the implementation.

### A proprietary Maven format

Do not require developers to convert existing Maven projects into an `mvnex`-specific format.

### A giant wrapper

Do not create `mvnex` aliases for every Maven command unless there is meaningful UX improvement.

### Magic that sacrifices correctness

Do not silently guess dependencies, versions, modules, or project configuration when ambiguity could materially change the project.

Ask the developer when necessary.

---

# Success

The project is successful if a Maven developer thinks:

> "I could do this manually with Maven, but mvnex makes it easier."

The ultimate goal is not to make Maven disappear.

It is to make working with Maven feel better.