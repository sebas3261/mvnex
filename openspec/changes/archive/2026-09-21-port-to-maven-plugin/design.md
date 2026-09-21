## Context

mvnex is currently a ~4k-line C++20 CLI (`src/`, built with CMake, depending on replxx for line editing and cpr/libcurl for HTTP) with two commands, `init` and `add`, organized as domain / application (use cases + ports) / infrastructure (CLI, HTTP, resolvers, POM editing, Maven, filesystem). The behavior to preserve is captured in the four capability specs of this change; the C++ source is the reference oracle until it is deleted.

Facts established while writing this design (as of 2026-09-21, from Maven Central metadata):
- Latest stable Maven is 3.9.16; Maven 4 is still at 4.0.0-rc-6.
- Latest stable plugin tooling: `maven-plugin-plugin` / `maven-plugin-annotations` 3.16.0, `maven-plugin-testing-harness` 3.5.1, `maven-invoker-plugin` 3.10.1.
- `plexus-interactivity-api` 1.6.0 ships `DefaultPrompter` registered in `META-INF/sisu/javax.inject.Named`, so a plugin can `@Inject Prompter`.
- `maven-wrapper-distribution:3.3.4:zip:only-script` contains `mvnw` (LF) and `mvnw.cmd` (CRLF). The C++ embedded `mvnw` is byte-identical to it; the embedded `mvnw.cmd` is identical only after CRLF→LF normalization (the C++ tool therefore wrote LF `mvnw.cmd` on Unix and CRLF on Windows).
- Other test/runtime libraries: JUnit Jupiter 6.1.3 (requires Java 17), WireMock 3.13.2 (latest stable 3.x), Jackson Databind 2.22.2.
- Maven 3.9.16 bundles Maven Resolver 1.9.27, and its `META-INF/maven/extension.xml` exports `org.eclipse.aether.*` to plugins, **including `org.eclipse.aether.spi`** (so plugins can inject `TransporterProvider`).
- `maven-wrapper-plugin` 3.3.4 (`WrapperMojo`): the Maven version defaults to the running `maven-core` version; the repository URL is `MVNW_REPOURL` (trimmed, trailing `/` stripped, used if longer than 4 characters), else the first mirror whose `mirrorOf` is exactly `*`, else `https://repo.maven.apache.org/maven2`; properties are written with `System.lineSeparator()`. This was the C++ tool's usual path whenever `mvn` was installed.
- Temurin 25's `conf/net.properties` sets `jdk.http.auth.tunneling.disabledSchemes=Basic`, and a probe showed `java.net.http.HttpClient` silently ignores SOCKS proxies (it connected directly). Both rule out the JDK client for libcurl proxy parity.
- The curl 8.7.1 manual: proxy env vars may be lowercase or uppercase with lowercase taking precedence (`http_proxy` lowercase only); `HTTPS_PROXY`, then `ALL_PROXY`; `NO_PROXY` supports `*` and domain matching; a proxy without a port uses 1080.
- Maven 3.9.16 (tested locally): any goal, even one with `requiresProject = false`, fails with `The build could not read 1 project` when the execution directory's `pom.xml` is malformed or has an unresolvable parent; `-f` pointing elsewhere does not avoid it; running from a directory without a `pom.xml` does.
- Live checks: all three search endpoints respond; deps.dev returns `versions[].versionKey.version` and `isDefault`, listing versions oldest first.

Stakeholders: the upstream author (plugin coordinates `com.sebas3261:ex-maven-plugin`, chosen by the user) and end users who currently invoke `mvnex`.

## Goals / Non-Goals

**Goals:**
- Reproduce every observable behavior in `specs/` as Maven goals `ex:init`, `ex:add`, `ex:help`, plus a one-time `setup` goal that makes the `ex:` prefix work.
- Keep generated files and POM edits byte-compatible with the C++ tool (except the enumerated deltas below).
- Keep the ports-and-adapters layering so the dependency engine is testable without Maven or the network.
- Make batch mode (`-B`, CI) a first-class, deterministic path.
- Replace the three-OS native build with a single JVM artifact, then delete the C++ code.

**Non-Goals:**
- Roadmap features: `remove`, `update`, `outdated`, `install`, `run`, `mvnex.toml`, Java management, project templates, transactional rollback.
- Structural (DOM-based) POM editing, `dependencyManagement`/parent/multi-module awareness — the textual algorithm is ported as-is.
- Fixing ranking heuristics or parsing quirks beyond what is listed under "Behavior deltas".
- Maven 4 API (`org.apache.maven.api.*`) support; the plugin targets the Maven 3 plugin API, which Maven 4 also runs.
- Publishing to Maven Central (deferred to a future change; GitHub prereleases only here — see Resolved Decisions).

## Decisions

### D1 — Single-module Maven plugin at the repository root
The root becomes a `maven-plugin` project (`pom.xml` at root; Java under `src/main/java/com/sebas3261/ex/...`). The C++ tree lives in `src/application`, `src/domain`, `src/infrastructure`, `src/main.cpp`, which do not collide with `src/main/java`, `src/test/java`, `src/it`, so both coexist until the removal phase.
*Alternatives:* a `plugin/` sub-directory (keeps C++ untouched but leaves a stray layout after deletion); a new repository (loses history and issues). Rejected.

### D2 — Preserve the hexagonal layering in Java packages
```
com.sebas3261.ex.domain.project       ProjectConfig (record), ProjectNaming, ProjectValidator
com.sebas3261.ex.domain.dependency    DependencyRequest (sealed interface + records), ResolvedDependency (record)
com.sebas3261.ex.application.init     InitUseCase
com.sebas3261.ex.application.add      AddUseCase
com.sebas3261.ex.application.ports    DependencyResolver, HttpClient, ProjectCreator, ProjectDependencyRepository,
                                      MavenProjectValidator, Interaction (new: prompts), ReportSink (new: output)
com.sebas3261.ex.application.errors   DependencyResolutionException hierarchy (NotFound, Unavailable, MultipleMatches)
com.sebas3261.ex.infrastructure.*     transport (Resolver-backed HttpClient, ProxyChooser), dependency (Composite/MavenCentral/
                                      DepsDev, CanonicalVersionSelector), project (Pom repository,
                                      ProjectGenerator), wrapper, maven (ProjectBuilder validator, Prompter adapter)
com.sebas3261.ex.plugin               InitMojo, AddMojo, SetupMojo, UninstallMojo  (replace the CLI layer: routing, commands, arg parsing, help)
com.sebas3261.ex.infrastructure.settings  SettingsPluginGroupEditor (pure text), SettingsFileWriter (backup + atomic write)
com.sebas3261.ex.plugin.support       ParameterGuard (unknown ex.* keys, did-you-mean)
```
Mojos play the role of `InitCommand`/`AddCommand`: map parameters → requests, drive the interactive loops, render results. `InitConfigCollector` and `DependencyArgumentsParser` move to `plugin` as plain classes. `CommandRouter`, `CommandRegistry`, `CommandMetadata`, `ArgumentParser`, `HelpPrinter`, `Style`, `ConsoleProgressReporter`, and `MavenChecker` have no plugin equivalent and are dropped (Maven provides routing, parameter binding, and help).
*Why:* the specs map 1:1 onto existing use cases, and unit tests can target use cases with fakes exactly as the ports were designed for.

### D3 — Parameters are `ex.*` user properties
All goal parameters are bound via `@Parameter(property = "ex.<name>")`. A plain `-Dversion` / `-DgroupId` would collide with conventions used by other plugins (archetype) and with POM interpolation in the loaded project. Positional arguments become `ex.name` and `ex.deps` (`List<String>`, comma-separated). The dependency list is named `ex.deps` rather than `ex.dependencies` because it is typed on every `add`; there is deliberately no alias, since `@Parameter` binds one property and a second spelling would need conflict rules and extra guard entries. `ex.wrapper` is a `Boolean` with no default so "not specified" (`null`) is distinguishable from `true`, which the wrapper-prompt rule needs.

### D4 — Mojo execution semantics
`InitMojo`, `AddMojo`, `SetupMojo`, and `UninstallMojo`: `requiresProject = false`, `aggregator = true`, `threadSafe = true`, `requiresDependencyResolution = NONE`.
- `init` resolves paths against `session.getRequest().getBaseDirectory()` (the execution root), never `user.dir` directly.
- `add` targets `session.getRequest().getPom()` when set (covers `-f` and "pom.xml in cwd"); otherwise it walks up from the execution root. This preserves the C++ "run from any nested directory" behavior, which `requiresProject = true` would break.

### D5 — Interaction through an `Interaction` port backed by Plexus interactivity
`Interaction` exposes `text(label, default)`, `select(label, options, default)` and `isInteractive()`. The Maven adapter, `ConsoleInteraction`, uses the injected `InputHandler` and `OutputHandler` from `plexus-interactivity-api` 1.6.0 rather than its `Prompter`. Reading `DefaultPrompter`'s source showed that it returns the default reply when input ends (`line == null` is treated like an empty answer), so EOF could not be told apart from Enter: `mvn ex:init < /dev/null` would silently create a default project instead of failing. `InputHandler.readLine()` returns `null` at EOF, which maps to `Operation cancelled.`; an empty line selects the default. `select` prints a numbered list and accepts either the number or the exact option text, re-prompting with `Invalid selection.` otherwise. `isInteractive()` comes from `session.getRequest().isInteractiveMode()`. Arrow-key navigation and the preloaded editable buffer from replxx are not reproducible; "Search again" uses the previous query as the default instead; as in C++, an empty answer reuses that query and only closed input cancels.
*Alternatives:* JLine directly (fights Maven's own terminal handling and logging); `System.console()` (unavailable under many IDE/CI launchers). Rejected.

### D6 — All HTTP through Maven Resolver's transport
Every request goes through the `TransporterProvider` that Maven itself uses (Resolver 1.9.27 in Maven 3.9.16, exported to plugins via `org.eclipse.aether.spi`), injected with `@Inject`. The `HttpClient` port gets one adapter, `ResolverHttpClient`:
- **Search endpoints** (Sonatype Central, search.maven.org, deps.dev): a synthetic `RemoteRepository` per host (e.g. `https://central.sonatype.com/`) with no mirror applied, and a `GetTask` for the relative path plus query string. The proxy comes from `ProxyChooser`, which applies spec precedence settings → env → JVM properties. Env-var URLs are parsed with libcurl's rules into an Aether `Proxy`, with `Authentication` for `user:password@`. A SOCKS scheme raises the SOCKS error.
- **Repository lookups** (version listings, POM existence): `RepositorySystem.newResolutionRepositories(session, [central])` applies the user's mirror, proxy, and authentication selectors, then Resolver's standard requests (D8). No hand-built URLs.
- **Timeouts:** a per-call copy of the session (`new DefaultRepositorySystemSession(session)`) sets `aether.connector.requestTimeout` to 5000/1500/5000 ms for the search endpoints. Repository lookups keep Maven's configured timeouts.
- **User-Agent:** `aether.connector.userAgent` is set to `ex-maven-plugin/<version>` on that session copy.
- **Errors:** `transporter.classify(e) == ERROR_NOT_FOUND` is treated as a 404. Other transport exceptions map to "unavailable"; an `SSLHandshakeException`/`CertPathBuilderException` in the cause chain adds the certificate hint. Offline mode short-circuits before any request.

This gives Basic auth through HTTPS tunnels, `settings.xml` credentials, Maven's TLS configuration, and mirror routing with **no new dependency**, behaving exactly like Maven's own downloads.

**Spike results (task 5.1, 2026-09-21, Maven 3.9.16 / Resolver 1.9.27, native HTTP transport).** A throwaway mojo injecting `TransporterProvider` was run against WireMock and the live endpoints:
1. **Query strings survive** `GetTask` resolution: an exact-URL stub for `/solrsearch/select?q=a%3Alombok&rows=25&wt=json` matched, percent-encoding intact. deps.dev's encoded colon (`org.projectlombok%3Alombok`) also works live.
2. **404 is classified** `Transporter.ERROR_NOT_FOUND` (WireMock and live repo1).
3. **Per-call timeouts work:** a session copy with `aether.connector.requestTimeout=1000` failed a 3 s response after 1107 ms (`SocketTimeoutException`); without the override it succeeded after 3118 ms.
4. **Bodies are unmodified:** the SHA-256 of the WireMock body and of the live Sonatype response both equal the recorded fixture.
5. **The User-Agent override is honored:** the request journal shows `ex-maven-plugin/0.2.0-SNAPSHOT` instead of Maven's `Apache-Maven/3.9.16 (...)`.

Two findings beyond the planned checks:
- **Maven's transport retries 503/429 with backoff**: a 503 took 30.1 s. The C++ tool never retried. The per-call session copy therefore sets `aether.connector.http.retryHandler.count=0` and `aether.connector.http.retryHandler.serviceUnavailable=""`, after which a 503 fails in 95 ms.
- **HTTP status versus transport failure:** errors are Maven-internal types (`org.apache.http.client.HttpResponseException` for statuses, `HttpHostConnectException` etc. for transport) that plugins cannot import. The adapter reads the status reflectively via a `getStatusCode()` method anywhere in the cause chain (present on Apache's exception and on Resolver 2.x's `HttpTransporterException`): a status means an HTTP error response (404 → not found via `classify`), no status means a transport failure ("unavailable").

No fallback is needed; the Apache HttpClient 5 option is dropped.

*Alternatives:*
- `java.net.http.HttpClient`: Basic tunneling auth is disabled by default JDK-wide, SOCKS is ignored silently, and settings proxies and mirrors would have to be re-implemented.
- Apache HttpClient 5 for everything: full proxy parity, but it duplicates Maven's settings, mirror, and TLS handling and adds two jars.

Rejected.

### D7 — Real parsers instead of regex scanning
Solr and deps.dev responses are read with Jackson's tree model (`docs[].g/a/latestVersion`, `response.numFound`, `versions[].versionKey.version`/`isDefault`, a path confirmed against a live response on 2026-09-21). `maven-metadata.xml` is no longer parsed by hand: version listings come from Resolver (D8). The C++ regex approach is fragile (escaped quotes, nested objects), and the spec defines semantics, not mechanism.

### D8 — Concurrent first-result resolution
`CompositeDependencyResolver` submits one task per provider to a small per-invocation executor and completes on the first success or multiple-matches outcome (`CompletableFuture.anyOf` over futures that only complete normally on "decisive" outcomes, plus a join on all for the failure path). Remaining tasks are cancelled and the executor shut down before returning. Failure precedence is implemented exactly as specified (not-found > unavailable > other > generic).

**The race decides identity, not version.** Providers return candidates (`groupId:artifactId` + a provisional version) or "coordinate exists". A `CanonicalVersionSelector` then picks versions with a Resolver `VersionRangeRequest` for `[0,)` against the mirror-routed central repository. The session copy uses `RepositoryPolicy.UPDATE_POLICY_ALWAYS` for metadata so listings are never stale. The result is ordered by Maven's `GenericVersionScheme`. The selector fetches **deps.dev's version list** for the artifact once and walks the non-pre-releases from highest down, taking the first one deps.dev lists. That skips internal or vendor builds that only a company mirror serves. Two alternatives failed:
- `central.sonatype.com` ignores `core` and returns `numFound: 0` for every quoted gav query (found while recording fixtures, task 1.6).
- `search.maven.org`'s index stopped updating in early/mid 2025. Its newest entries were lombok 1.18.38, guava 33.4.8-jre and junit-jupiter 5.13.0-M3, while Central had 1.18.48, 33.7.1-jre and 6.1.3 (observed during task 8, when a live `ex:add` chose lombok 1.18.38).

For lombok, guava, junit-jupiter, slf4j-api and spring-core, deps.dev listed every version in Central's `maven-metadata.xml`. One request per candidate also replaces a gav query per version. If the list can't be fetched or contains none of the listed versions, the selector takes the highest non-pre-release; if there are no non-pre-releases, it applies the same walk to all versions. A test against Resolver 1.9.27 showed why confirmation is needed: `1.18.48-acme.2` and `1.18.48-redhat-00001` rank above `1.18.48`. A "prefer unqualified versions" rule was rejected because Guava publishes only qualified versions (`-jre`/`-android`). For multiple candidates the selections run on a bounded pool (4 in flight). Ranking happens after selection because `preReleasePenalty` depends on the chosen version. Only if the listing fails or is empty is the provider's provisional version used; deps.dev's is `isDefault`, then the highest stable, then the highest. This replaces the C++ "metadata in reverse document order → hard-coded search.maven.org gav query → latestVersion" chain. The result is identical for a search term and its coordinate, and independent of network timing.

POM existence checks use `ArtifactRequest` for `g:a:pom:v` through the same repositories. The POM is cached in the local repository, where Maven would put it anyway, and an already-cached POM counts as existing.

### D9 — Textual POM editing, scoped to the project-level `<dependencies>`
`PomProjectDependencyRepository` keeps the C++ approach (text insertion, fixed 8/12-space rendering, wrap before the last `</project>` when there is no block), but locates the **project-level** `<dependencies>` by scanning the comment/CDATA-masked text with a depth-tracking tag scanner (the same masking technique as D15), instead of `rfind("    </dependencies>")`. Duplicate keys are read from that element only. For the common 4-space layout the output is byte-identical to C++. The change fixes a C++ bug found while recording fixtures (task 1.5): when a POM only had `<dependencyManagement>`, the inner `        </dependencies>` contained the 4-space marker, so entries were inserted inside `<dependencyManagement>` with broken indentation, and later `add` calls reported them as already present. The file is read and written as **ISO-8859-1**, which maps every byte to one character and back, so any encoding (including a BOM) round-trips exactly like the C++ byte-oriented I/O; all markers and inserted text are ASCII. Inserted lines use the file's own line separator (CRLF if its first line break is CRLF, else LF). That matches C++ on Windows for CRLF files and avoids the mixed endings C++ produced on Unix; C++'s Windows side effect of rewriting LF files as CRLF is intentionally not reproduced. The repository loads existing keys once per use-case execution (the C++ re-read the file for every `containsDependency` call; behavior is identical).
*Alternative:* `MavenXpp3Reader/Writer` — drops comments and formatting, violating "pom.xml stays the user's file". Rejected until the roadmap's structural editing work.

### D10 — Post-edit validation with `ProjectBuilder`, in-process
After writing, `MavenProjectValidator` builds the edited POM with `ProjectBuilder.build(pomFile, request)` using a `ProjectBuildingRequest` derived from the session (same repositories, `VALIDATION_LEVEL_MAVEN_3_0`, `resolveDependencies = false`). Success → `PASSED`; `ProjectBuildingException` or any `ERROR` model problem → `FAILED`. The `MAVEN_NOT_FOUND` status disappears.
*Alternatives:* Maven Invoker running `mvnw`/`mvn validate` (needs an external Maven and duplicates JVM start-up, which is exactly what the plugin form removes); executing the lifecycle in-process (fragile). Trade-off: plugins bound to the `validate` phase (e.g. enforcer) are not run — see Risks.

### D11 — Wrapper output matches the C++ tool's usual path, without the network
The plugin build unpacks `mvnw` and `mvnw.cmd` from `org.apache.maven.wrapper:maven-wrapper-distribution:3.3.4:zip:only-script` (via `maven-dependency-plugin:unpack` into `target/generated-resources`) and bundles them as classpath resources. At runtime `WrapperGenerator` copies those bytes and writes `maven-wrapper.properties` exactly as `maven-wrapper-plugin` 3.3.4 would:
- **Maven version:** from the injected `RuntimeInformation.getMavenVersion()`.
- **Repository URL:** `MVNW_REPOURL`, then the first effective-settings mirror with `mirrorOf` `*`, then `https://repo.maven.apache.org/maven2`.
- **Line separator:** `System.lineSeparator()`.

The result is byte-identical to what the C++ tool produced whenever `mvn` was installed. Executable bits are set via `PosixFileAttributeView` when supported. No network or external `mvn` is needed, and there's no spinner (the operation is instantaneous).

*Alternatives:*
- Invoking `maven-wrapper-plugin:wrapper` via `BuildPluginManager`: needs a loaded project and network access.
- Pinning a fixed Maven version: the previously chosen 3.9.16, superseded by the owner's parity decision.
- Pasting the C++ string literals into Java: reintroduces the `mvnw.cmd` LF defect and duplicates Apache-licensed sources in-tree.

Rejected.

### D12 — Output through a `ReportSink` port over Maven `Log`
All user-visible text from the specs is emitted through a small port whose Maven adapter maps info/warn/error to `getLog()`. Failures throw `MojoFailureException(message)`. The `✓`/`◆` glyphs are kept only where the spec lists them (dependency lines); section headers become plain text. Unicode is emitted as-is; Maven's console encoding is the user's concern.

### D13 — Testing strategy: C++ is the oracle until deleted
1. **Golden fixtures first:** before any Java code, run the C++ binary to capture `init` outputs (several name/groupId/java/package/wrapper combinations) and POM-insertion cases into `src/test/resources/golden/`. `mvnw.cmd` goldens use the official CRLF file (intentional delta).
2. **Unit tests (JUnit 6):** validator tables (every reserved word, `_` × Java version), expression grammar table, ranking, pre-release detection, canonical version selection (ordering, all-pre-release, listing failure fallbacks), proxy-env parsing (libcurl rules), unknown-parameter detection, URL encoding, composite precedence (fake resolvers with controllable latency), POM insertion vs goldens, and use cases with fakes.
3. **HTTP adapters:** WireMock stubs with recorded real responses from Sonatype Central, search.maven.org, repo1, and deps.dev.
4. **Integration tests:** `maven-invoker-plugin` projects under `src/it/` run the packaged plugin through real Maven in batch mode: init with/without wrapper (verify files vs goldens, then `./mvnw -v` on Unix), add against WireMock (search endpoints overridden via internal, undocumented `ex.internal.*Url` properties; repository lookups routed to WireMock with a `mirrorOf="*"` mirror in the IT `settings.xml`, which also exercises mirror routing and the wrapper URL rule), env-var proxy and SOCKS rejection, unknown-parameter failure, broken-POM limitation and subdirectory workaround, add from a nested directory, multi-module aggregator run-once, offline failure, ambiguous-in-batch failure.
5. **Interactive paths** are unit-tested through a scripted `Interaction` fake; one IT feeds stdin to verify the Prompter wiring.

### D14 — Build and release
`maven-plugin-plugin` 3.16.0 with `<goalPrefix>ex</goalPrefix>`, the generated `help` mojo, and `<requiredJavaVersion>17</requiredJavaVersion>` / `<requiredMavenVersion>3.9.1</requiredMavenVersion>`, which Maven 3.9.16 enforces through `MavenPluginJavaPrerequisiteChecker` / `MavenPluginMavenPrerequisiteChecker` (confirmed in the `maven-core` jar); `maven-plugin-api`, `maven-core`, `maven-plugin-annotations` at `provided` scope (3.9.16 / 3.16.0); `maven.compiler.release` 17. CI matrix: {ubuntu, macos, windows} × {17, 21} running `mvn -B verify`, plus a Linux/Java 17 leg that runs the full suite with Maven 3.9.1 to prove the declared floor. The floor started at 3.9.0 and was raised during task 11.2: Maven 3.9.0 throws `NullPointerException` (`MavenProject.getCollectedProjects()` is null) for every aggregator goal run without a POM, while 3.9.1–3.9.4 and 3.9.16 pass. Tag `v*` → build (no `deploy` to a repository), then `softprops/action-gh-release` uploads the JAR, POM, sources and javadoc JARs as a prerelease (keeping the current workflow's prerelease convention).

### D15 — `setup` / `uninstall` goals: textual, comment-aware settings edit
`SetupMojo` (`requiresProject = false`, `aggregator = true`, `threadSafe = true`) targets `session.getRequest().getUserSettingsFile()`, which Maven already sets to the `-s` file or `<user.home>/.m2/settings.xml`, then resolves symlinks with `Path.toRealPath()` so the link survives the atomic move. The work lives in a Maven-free `SettingsPluginGroupEditor` (pure `String` in → `String` out) so every insertion case is unit-testable:
1. **Detect** registration with `SettingsXpp3Reader` (strict) → `getPluginGroups()` contains the group after trimming. A real parser is the only reliable way to ignore commented entries. Maven 3.9.16's own `conf/settings.xml` has an active `<pluginGroups>` whose only `<pluginGroup>` is commented out, so a naive text search gets it wrong.
2. **Locate** insertion points on a *masked* copy of the text in which every character inside `<!-- … -->` and `<![CDATA[ … ]]>` is replaced with a space. Offsets stay identical to the original, so plain index searches for `<pluginGroups`, `</pluginGroups>`, `<pluginGroups/>`, and `</settings>` never match commented text.
3. **Insert** into the original text at those offsets, deriving indentation and the line separator from the surrounding lines (spec cases 1–3).
4. **Verify** by re-parsing the result and checking that the group is present, then back up, write to a temp file in the same directory, copy POSIX permissions, and `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.

The encoding comes from the XML declaration (`XmlStreamReader` from plexus-utils, which Maven already provides), defaulting to UTF-8.

*Alternatives:*
- Rewriting with `SettingsXpp3Writer` would drop every comment and reformat the user's global configuration. Rejected for the same reason as D9.
- DOM + `Transformer` loses the formatting inside elements and the attribute layout. Rejected.
- Only printing a snippet for the user to paste, as the original README plan did, puts the burden on the user. This is the problem the goal exists to remove.

**`uninstall`** reuses the same pieces in reverse. `SettingsPluginGroupEditor.remove(text)` finds active `<pluginGroup>` elements on the masked text with `<pluginGroup>\s*com\.sebas3261\s*</pluginGroup>`. It then widens each match to the whole line only when the rest of the line (to the left back to the previous line separator, to the right up to and including the next one) is whitespace, and deletes those ranges from the original. Three safety checks go beyond what `setup` needs, because removal is the riskier edit:
- **Count check:** located matches must equal the parser's count of `com.sebas3261` groups. A mismatch (e.g. a namespace-prefixed `<s:pluginGroup>` the text search cannot see) aborts rather than guessing.
- **Model-equality check:** after removal, `new SettingsXpp3Writer` output of the parsed original *with the group removed from the model* must equal the writer output of the parsed result. This proves that nothing except that entry changed semantically.
- **Byte check in tests:** the output must equal the input with exactly the removed ranges cut out.

`<pluginGroups>` is intentionally left in place even when empty, and the file is never deleted. Removing the container would mean touching bytes the user did not ask to change, and an empty `<pluginGroups>` is valid settings. `uninstall` does not delete anything from the local repository: the name refers to the prefix registration, and the plugin JAR in `~/.m2/repository` is a normal cached artifact Maven manages.

The setup tip in `init`/`add` reads `session.getSettings().getPluginGroups()` (the effective user + global settings), so a group configured by an administrator in the global file suppresses the tip. `setup` itself only ever writes the user file.

### D16 — Java 5 annotation-based mojos only (no legacy Javadoc tags)
All mojos are declared with `org.apache.maven.plugins.annotations`:
- `@Mojo(name = ..., requiresProject = false, aggregator = true, threadSafe = true)` on each goal class;
- `@Parameter(property = "ex.<name>")` for user parameters;
- `@Parameter(defaultValue = "${session}", readonly = true, required = true)` for the `MavenSession`.

Maven components (`InputHandler`, `OutputHandler`, `ProjectBuilder`, `RepositorySystem`, `TransporterProvider`, `RuntimeInformation`) are obtained by JSR-330 constructor injection (`@Inject`), not Plexus `@Component` fields or `@Requirement`. The legacy Javadoc-tag style (`@goal`, `@parameter`, `@requiresProject`, `@component` inside comments) is not used anywhere. Javadoc comments on mojo classes and parameter fields remain, but only as plain prose. `maven-plugin-plugin` copies that prose into `plugin.xml` as descriptions for `ex:help`.

The build enforces this by limiting `maven-plugin-plugin`'s `descriptor` goal to `<extractors><extractor>java-annotations</extractor></extractors>`. A mojo that relied on Javadoc tags is then not registered as a goal at all, instead of being silently picked up by the deprecated Javadoc extractor. The `ex:help` goal-list check and the integration tests catch any such missing goal.

*Alternatives:* the legacy Javadoc-tag extractor (deprecated in the maven-plugin-tools 3.x line and fragile across refactors); Plexus `@Component` injection (still supported, but JSR-330 constructor injection is what the Maven project recommends for new plugins and makes the mojos unit-testable without a container). Rejected.

### D17 — Unknown `ex.*` parameter guard
`ParameterGuard` runs first in every mojo. It collects `ex.*` keys from `session.getUserProperties()` and `session.getSystemProperties()`, compares them with a single registry of all goals' parameter names (generated from a shared constants class so it cannot drift from the `@Parameter` declarations), and allows the `ex.internal.` prefix. A key unknown to every goal fails with a did-you-mean suggestion (Levenshtein distance ≤ 2); a key belonging only to other goals logs a warning. Failing only for keys no goal recognizes keeps the C++ `Unknown option` protection against typos while tolerating team-wide defaults in `.mvn/maven.config`.
*Alternatives:* warn only (typos like `ex.scop` would still silently drop the scope); strict per-goal failure (breaks shared `maven.config`). Rejected.

### Behavior deltas (intentional, all reflected in the specs)

| Area | C++ behavior | Plugin behavior | Reason |
|---|---|---|---|
| Invocation | `mvnex init my-app -g ...` | `mvn ex:init -Dex.name=my-app -Dex.groupId=...` | Maven goal model |
| Prefix registration | Not applicable (native `mvnex` binary on PATH) | New `…:setup` goal registers `com.sebas3261` in user settings, and `ex:uninstall` removes it; `init`/`add` show a tip when missing | Maven prefix resolution requires `pluginGroups` |
| Help/version | `--help`, `-h`, `--version`, `-v`, per-command help | `mvn ex:help [-Ddetail -Dgoal=...]` | Generated help mojo |
| Batch mode | No batch mode; missing values were always prompted | `-B`: init requires `ex.name`, defaults the rest; ambiguous add fails with candidates | CI determinism |
| Selector UX | Arrow keys, editable preloaded search | Numbered choice; previous query as default | Prompter capabilities |
| Missing-Maven warning on `--no-wrapper` | Printed when `mvn` not on PATH | Removed | Maven is by definition running |
| Wrapper generation | `mvn wrapper:wrapper` if Maven installed (running Maven version, `MVNW_REPOURL`/`*` mirror URL), else embedded files pinned to 3.9.11, with spinner | Always the "Maven installed" output, produced in-process from bundled official scripts; no spinner | Parity with the usual C++ path without network or external `mvn` |
| `mvnw.cmd` line endings | Usual path (`mvn` installed): official CRLF bytes, confirmed by the recorded fixtures; fallback path (embedded strings): LF on Unix, CRLF on Windows | Always CRLF (official bytes) | Correctness on Windows |
| `mvnw` line endings | CRLF on Windows (text-mode streams), breaking it in Git Bash/WSL | Always LF (official bytes) | Correctness |
| `add` placement and duplicates | Inserted before the last `    </dependencies>` text anywhere (could land inside `<dependencyManagement>`); duplicates counted anywhere, including managed/plugin/commented entries | Project-level `<dependencies>` only, comments ignored, for both insertion and duplicate detection | Fixes a silent functional bug (managed-only entries never reach the classpath) |
| Package error text | Said "group ID" for invalid `--package` | Says "package name" | Misleading message |
| Validate after add | `./mvnw`/`mvn validate` subprocess; "Maven not found" warning | In-process ProjectBuilder; no "not found" state | No external Maven needed |
| POM lookup timing | After all network resolution | Before any network request | Fail fast, no wasted calls |
| Proxy / offline | libcurl env vars only (incl. SOCKS) | settings.xml → env vars (libcurl rules) → JVM properties; SOCKS env proxies rejected with a hint; `-o` honored | Maven-native plus C++ parity |
| Repository lookups (metadata, POM check) | Direct to `repo1.maven.org` | Through the user's mirrors via Resolver | Works behind mirror-only firewalls |
| Versionless version choice | First provider to answer: Solr `latestVersion` (coordinates unfiltered), or deps.dev `isDefault` else oldest; search terms via a metadata document-order chain | Canonical: highest non-pre-release by Maven ordering from the mirror-routed listing, for terms and coordinates alike | Deterministic; term and coordinate agree |
| POM edit line endings | Windows: CRLF throughout (LF files converted); Unix: inserted LF mixed into CRLF files | Inserted lines use the file's own separator; other bytes untouched | Consistent endings without reformatting |
| Unknown options | `Unknown option` error; duplicate/valueless option errors | Unknown `ex.*` key fails with did-you-mean; another goal's key warns; duplicates/valueless handled by Maven | Maven property model |
| TLS trust | OS certificate store | JVM/Maven truststore, plus a targeted hint on certificate failures | Same trust as Maven's own downloads |
| `◆ mvnex <command>` header | Printed | Dropped (Maven prints the goal execution line) | Redundant |
| Output under `mvn -q` | Not applicable | INFO results hidden, as for any plugin | Maven convention |
| Resolver parsing | Regex over JSON/XML | Real JSON/XML parsers | Robustness; same semantics |

Preserved quirks (documented, not fixed): `junit:junit` parses as term `junit` + version `junit`; pre-release check matches the substring `-m` anywhere; "Search again" drops an explicit version. (The earlier quirks "coordinate-without-version uses unfiltered `latestVersion`" and "deps.dev falls back to the oldest version" are fixed by the canonical version step; see the delta table.)

## Risks / Trade-offs

- **Prefix `ex` needs `pluginGroups`.** Maven only resolves prefixes for `org.apache.maven.plugins`, `org.codehaus.mojo`, and groups listed in `settings.xml`. Without that, `mvn ex:init` fails with "No plugin found for prefix 'ex'" before the plugin loads, so the plugin cannot print a hint for that failure itself. The README leads with the one-time fully qualified `…:setup` command, and `init`/`add` print the setup tip when invoked in full form without the group.
- **`setup`/`uninstall` edit a file the user owns globally.** A bug could break every Maven build on the machine. Mitigations: the file is never touched when there is nothing to do, removal requires the count and model-equality checks, the result is re-parsed before writing, a timestamped backup is always made, the write is atomic, and golden tests cover Maven's default template, CRLF, tabs, the self-closing form, commented groups, and a missing element.
- **Settings files that Maven accepts but strict parsing rejects** (e.g. unknown elements added by other tools). Strict `SettingsXpp3Reader` would refuse to update them. Decision: fall back to non-strict parsing for *detection*, but still require well-formedness. Revisit if users report false failures.
- **Goals can't run where Maven can't load the POM.** Maven builds the execution directory's `pom.xml` before any goal (verified locally with Maven 3.9.16), so a malformed POM or unresolvable parent blocks `init` and `add` with `The build could not read 1 project`. The C++ tool edited such files blindly. This can't be fixed from inside a plugin → documented with the "run from a subdirectory without a `pom.xml`" workaround and pinned by integration tests.
- **Resolver transport SPI** changes in Resolver 2.x (Maven 4) → isolated in `ResolverHttpClient` behind the `HttpClient` port; covered by the allowed-to-fail Maven 4 CI job; the D6 spike defines the Apache HttpClient 5 fallback.
- **Canonical version step adds lookups.** Search terms with many candidates need one listing each (13 for `lombok` today) → bounded concurrency (4), mirror-served and cached for the session; acceptable next to Maven start-up time.
- **TLS-inspecting networks.** The three search endpoints are reached directly, so a corporate root CA missing from the JDK truststore breaks them where libcurl (OS store) worked → targeted hint plus README section; repository lookups are unaffected because they go through the trusted mirror.
- **ProjectBuilder ≠ `mvn validate`.** Enforcer or other validate-phase plugins no longer run after `add` → documented; users can run `mvn validate` themselves. Revisit if users report missed failures.
- **Endpoint behavior drift** (Solr on central.sonatype.com is undocumented for third parties; deps.dev schema changes) → recorded-response tests make drift visible; composite keeps working if one provider breaks.
- **Maven 4 runtime.** Maven 4.0.0-rc-6 passes 32 of 33 integration tests. The exception is `interactive-init`: with answers piped on stdin, Maven 4's JLine-based console hands the plexus `InputHandler` misaligned lines (the Java-version prompt reads an invalid answer, then input ends). Interactive use on a real terminal under Maven 4 is unverified → allowed-to-fail CI job; revisit when Maven 4 is released.
- **Loss of zero-dependency native binary.** Users must now have a JDK 17+ and Maven → acceptable given the product is Maven-specific; noted in the README.
- **Prompt output interleaving with Maven logging** (e.g. `-T` parallel builds) → mojos are aggregators and prompt only on the main thread before any concurrent work.
- **Golden fixtures capture C++ bugs as expectations** → deltas table is the single list of allowed differences; every golden deviation must map to a row.

## Migration Plan

1. Land plugin scaffolding and golden fixtures while C++ stays buildable (both CI jobs run).
2. Implement domain → resolution → add → init → wrapper, each gated by unit tests and goldens.
3. Add ITs and the plugin CI job; reach parity on all spec scenarios.
4. Cut `v0.2.0` as a GitHub prerelease of the plugin; the last native release (`v0.1.x` assets) remains downloadable.
5. Remove C++ sources, CMake, native CI steps; rewrite README (with migration table), ARCHITECTURE, CLAUDE.md.

Rollback: revert the removal commit(s) to restore the C++ build; users can keep using the v0.1.x binaries meanwhile. No data migration is involved — generated projects are plain Maven projects in both versions.

## Resolved Decisions

Recorded 2026-09-21 from the change owner's answers:

- **Maven Central publishing is deferred to a future change.** This is an initial port, so releases are GitHub prereleases only. Consequence for installation: users install the release JAR and POM with `mvn install:install-file` (or build from source with `mvn install`) and then run `setup`. An earlier reading of maven-install-plugin 3.2.0's source suggested `install-file` writes no plugin-prefix metadata, but a local check during task 11.4 disproved it: Maven 3.9's `maven-resolver-provider` (`PluginsMetadataGenerator`) writes `<prefix>ex</prefix>` group metadata whenever a `maven-plugin` artifact is installed, so the short `ex:` prefix works after both install paths. The namespace choice (`com.sebas3261` needs proof of domain ownership; `io.github.sebas3261` is verifiable via GitHub) is left to the Central change.
- **Preserved quirks stay as-is.** This includes `junit:junit` parsing as search term `junit` with version `junit`, and every other quirk listed under "Behavior deltas".
- **In-process `ProjectBuilder` validation after `add` is accepted** for the initial port. Running validate-phase plugins (e.g. enforcer) is a future enhancement.
- **`setup` gets a dedicated `ex:uninstall` goal** that removes only the `com.sebas3261` `<pluginGroup>` entry and leaves everything else in `settings.xml` untouched (see D15).
- **Wrapper properties match the C++ usual path** (running Maven version; `MVNW_REPOURL`, then a `mirrorOf="*"` mirror, then Central; platform line separator). This supersedes the earlier "fixed Maven 3.9.16" decision, which was made before we learned the C++ tool normally delegated to `maven-wrapper-plugin`.
- **Broken POMs in the execution directory are a documented limitation** (README, spec note, integration tests), not worked around.
- **HTTP goes through Maven Resolver's transport** (spike first). Proxy precedence is settings → env vars (libcurl rules) → JVM properties; SOCKS env proxies fail with a `-DsocksProxyHost` hint; repository lookups go through the user's mirrors.
- **POM edits are byte-exact (ISO-8859-1) and use the file's own line separator; `init` writes `pom.xml`, `Main.java`, and wrapper properties with the platform separator.**
- **Unknown `ex.*` keys:** fail when no goal knows them (with did-you-mean), warn when they belong to another goal.
- **Untrusted certificates** get a targeted hint and a README section; the plugin does not switch trust stores itself.
- **Versionless resolution is made deterministic** by the canonical version step for both coordinates and search terms (a deliberate exception to "keep quirks as-is").
- **Empty "Search again" answer reuses the previous term** (C++ parity); `-q` is respected; the command header line is dropped.
- **Canonical versions must be confirmed on Central** (walk down the mirror listing; mirror-only internal or vendor builds are skipped; fall back to the mirror's highest only if confirmation fails).
- **The dependency list property is `ex.deps`** (no alias).
- **Canonical-version confirmation uses deps.dev's version list** (decided during implementation, replacing the earlier `search.maven.org` choice after its index turned out to be frozen in 2025); if the list is unavailable or confirms nothing, the mirror's highest stable version is used.
- **`add` works on the project-level `<dependencies>` only** for both insertion and duplicate detection. This fixes the C++ `dependencyManagement` insertion bug found while recording fixtures (an exception to "keep quirks as-is").
- **Prerequisites are declared**: `requiredJavaVersion` 17 and `requiredMavenVersion` **3.9.1** (raised from 3.9.0 after the floor check found a Maven 3.9.0 core bug with POM-less aggregator goals), with a CI leg on Maven 3.9.1.
- **Coordinates stay `com.sebas3261`** (confirmed by the change owner).
- **The C++ oracle build pins replxx** to a recorded commit instead of `master`.
- **The plugin requires Java 17 at runtime** (records, sealed types, JUnit 6). Generated projects still target any of Java 8–25 via `maven.compiler.release`.
- **Batch-mode `init` defaults are accepted.** `ex.name` is required; group ID `com.example`, Java `21`, and the wrapper default the same way the interactive prompts do. Further refinement is a future enhancement.

## Open Questions

None at this time.
