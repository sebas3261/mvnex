# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`mvnex` is a Maven plugin, `com.sebas3261:ex-maven-plugin` (goal prefix `ex`), that adds an npm-like developer experience to Maven: `mvn ex:init`, `mvn ex:add`, plus `setup`/`uninstall` to register the `ex:` prefix in `settings.xml`. Core rule: **mvnex enhances Maven, it doesn't replace it**: `pom.xml` stays the source of truth and generated/edited projects stay plain Maven projects. It is a port of an earlier C++ CLI; the behavior spec lives in `openspec/specs/`, and the design and history of the port in `openspec/changes/archive/2026-09-21-port-to-maven-plugin/`.

## Build & test

Requires Java 17+ and Maven 3.9.1+ (the declared floor; Maven 3.9.0 can't run POM-less aggregator goals).

```bash
mvn verify                                # unit tests + all integration tests
mvn test                                  # unit tests only
mvn test -Dtest=AddFlowTest               # one unit test class (add #method to narrow)
mvn verify -Dinvoker.test=add-basic       # one integration test (comma-separate or glob: "add-*")
mvn verify -Dinvoker.skip                 # skip integration tests
```

Try the plugin locally without touching `~/.m2`:

```bash
mvn install -DskipTests -Dinvoker.skip -Dmaven.repo.local=/tmp/ex-repo
cd "$(mktemp -d)" && mvn -Dmaven.repo.local=/tmp/ex-repo com.sebas3261:ex-maven-plugin:0.2.0-SNAPSHOT:init -Dex.name=demo
```

## Architecture

Layered, dependencies pointing inward: `plugin → infrastructure → application → domain` (see `docs/ARCHITECTURE.md`).

- `plugin/`: annotation-based mojos only (no Javadoc `@goal` tags; the build restricts extraction to `java-annotations`), with JSR-330 constructor injection. Mojos are thin adapters; the logic lives in Maven-free `InitFlow`/`AddFlow`, which are unit-tested with a scripted `Interaction`. `AbstractExMojo` runs `ParameterGuard` and maps expected exceptions to `MojoFailureException`.
- `infrastructure/dependency`: the providers (Sonatype, search.maven.org, deps.dev) race; the first decisive answer picks *which artifact*. `CanonicalVersionSelector` then picks the version from a mirror-routed Resolver listing, confirmed against deps.dev's Central version list. search.maven.org's index has been frozen since 2025; don't use it for versions.
- `infrastructure/transport`: all HTTP goes through Maven Resolver's `TransporterProvider` (per-call session copy: timeout, User-Agent, 503 retries off). HTTP status is read reflectively (`getStatusCode()`); proxies come from settings, then curl-style env vars, then JVM properties.
- `infrastructure/xml/XmlText`: comment/CDATA-masked, offset-preserving scanner used by both the POM editor and the settings editor. Never re-serialize `pom.xml` or `settings.xml`; edit the original text at scanned offsets. POMs are read/written as ISO-8859-1 for byte transparency.

## Tests and fixtures

- `src/test/resources/golden/`: expected outputs recorded from the original C++ CLI (see its README); tests compare bytes. `.gitattributes` keeps fixtures from being line-ending normalized.
- `src/test/resources/http/`: recorded responses served by WireMock; `src/test/resources/settings/`: hand-derived settings fixtures.
- `src/it/`: maven-invoker-plugin tests. Most are directories without a `pom.xml`. Hook scripts use `com.sebas3261.ex.it.ItSupport` / `WireMockSupport` from the test classpath; `WireMockSupport.startLookups` points the plugin at WireMock via test-only `ex.internal.*Url` properties in `.mvn/maven.config`. Gotchas: `invoker.goals` splits on commas; hook scripts must `return true`; nested Maven runs (`ItSupport.mvn`) get the outer local repository via `-gs`.

## Workflow notes

- Conventional Commits with optional scope (`feat(add): ...`, `fix(resolution): ...`, `test(it): ...`, `ci: ...`, `docs(openspec): ...`).
- Changes are spec-driven with OpenSpec (`/opsx:propose`, `/opsx:apply`, `/opsx:archive`); keep `README.md`, `docs/ARCHITECTURE.md` and the specs in sync.
