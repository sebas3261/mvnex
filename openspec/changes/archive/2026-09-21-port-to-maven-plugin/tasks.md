## 1. Golden fixtures from the C++ oracle

- [x] 1.1 Build the current C++ binary for recording expected output, with replxx pinned to a fixed commit (the locally checked-out `build/_deps` commit if present, else `master` HEAD at capture time) via `-DFETCHCONTENT_SOURCE_DIR_REPLXX` or a temporary `GIT_TAG`. Record the mvnex commit, the replxx commit, and cpr `1.11.2` in `src/test/resources/golden/README.md`
- [x] 1.2 Capture `init` goldens (pom.xml, Main.java, directory tree listing) for: `my-app/com.example/21`, `my-cool-app/org.acme/8` with `--package org.acme.custom`, `svc/com.example/25 --no-wrapper`; store with LF under `src/test/resources/golden/init/`; tests expand LF to `System.lineSeparator()` when comparing (issue 4b: platform separator)
- [x] 1.3 Capture the C++ usual-path `maven-wrapper.properties` by running the C++ `init` with `mvn` installed in three environments: plain, with a `mirrorOf="*"` mirror in settings, and with `MVNW_REPOURL` set. Store them as goldens with the Maven version and repository URL as placeholders (design D11)
- [x] 1.4 Store the official `mvnw` and `mvnw.cmd` from `maven-wrapper-distribution-3.3.4-only-script.zip` as wrapper goldens and add a test note that `mvnw.cmd` intentionally differs from the C++ LF output (design delta table)
- [x] 1.5 Capture `add` POM-insertion goldens by running the C++ `add` with pinned coordinates (`org.projectlombok:lombok:1.18.32`, `org.junit.jupiter:junit-jupiter:5.10.0 --scope test`) against: fresh init POM (no `<dependencies>`), POM with an existing 4-space `<dependencies>`, POM with the dependency already present, POM with `dependencyManagement` only. Add hand-written goldens for the deltas: a CRLF POM (inserted lines CRLF), an ISO-8859-1 POM with non-ASCII bytes, and a UTF-8 POM with BOM
- [x] 1.6 Record real HTTP responses (Sonatype Central `a:lombok`, `a:guava` free-text fallback, gav `numFound` hit/miss, repo1 POM 404, `maven-metadata.xml` for an artifact whose latest is a milestone and one listed out of version order, deps.dev package JSON with and without an `isDefault` version) into `src/test/resources/http/` for WireMock

## 2. Plugin project scaffolding

- [x] 2.1 Create root `pom.xml`: `com.sebas3261:ex-maven-plugin:0.2.0-SNAPSHOT`, packaging `maven-plugin`, `maven.compiler.release` 17, UTF-8, Apache-2.0 license, SCM/URL metadata
- [x] 2.2 Add provided deps `maven-plugin-api` 3.9.16, `maven-core` 3.9.16, `maven-plugin-annotations` 3.16.0, `maven-resolver-api`/`-spi`/`-util` 1.9.27 (the version bundled with Maven 3.9.16); compile deps `plexus-interactivity-api` 1.6.0, `jackson-databind` 2.22.2; test deps `junit-jupiter` 6.1.3, `wiremock` 3.13.2, `maven-plugin-testing-harness` 3.5.1
- [x] 2.3 Configure `maven-plugin-plugin` 3.16.0 with `<goalPrefix>ex</goalPrefix>`, `helpmojo` generation, `<extractors><extractor>java-annotations</extractor></extractors>` (design D16), `requiredJavaVersion` 17 and `requiredMavenVersion` 3.9.0 (design D14); verify `mvn ex:help` lists `help` from a local install, and that a throwaway mojo using only a Javadoc `@goal` tag does not appear in the generated `plugin.xml`
- [x] 2.4 Configure `maven-dependency-plugin:unpack` of `maven-wrapper-distribution:3.3.4:zip:only-script` (`mvnw`, `mvnw.cmd` only) into `target/generated-resources/wrapper/` and register it as a resource directory (design D11)
- [x] 2.5 Configure `maven-invoker-plugin` 3.10.1 (`src/it`, batch mode, `install` + `run` goals) and a WireMock lifecycle for ITs
- [x] 2.6 Add `.gitignore` entries for `target/`; confirm `mvn -B verify` passes on the empty skeleton while CMake still builds

## 3. Domain layer

- [x] 3.1 Port `ProjectConfig` (record) and `ProjectNaming.toPackageName` (strip all `-`)
- [x] 3.2 Port `ProjectValidator`: name regex, segment rules, exact reserved-word set, `_` Java-8 exception, error-message precedence; package validation with "package name" messages (delta)
- [x] 3.3 Unit tests: every reserved word, `_` × {8, 21}, name/groupId/package/version tables from `specs/project-init`
- [x] 3.4 Port `DependencyRequest` (sealed: SearchTerm, SearchTermWithVersion, Coordinate, CoordinateWithVersion, each with scope) and `ResolvedDependency` (record with optional scope)

## 4. Application layer

- [x] 4.1 Define ports: `DependencyResolver`, `HttpClient`, `ProjectCreator`, `ProjectDependencyRepository`, `MavenProjectValidator` (PASSED/FAILED), `Interaction`, `ReportSink`
- [x] 4.2 Define errors: `DependencyResolutionException` base, `DependencyNotFoundException` (`Dependency not found: <q>`), `DependencyResolverUnavailableException`, `MultipleDependencyMatchesException` (query, ranked candidates, dependency index)
- [x] 4.3 Port `InitUseCase` (validate → create) without the Maven-missing check
- [x] 4.4 Port `AddUseCase`: resolve all in order with index-tagged ambiguity, attach requested scope, dedupe against POM keys (loaded once) and within the invocation, add, validate only when something was added
- [x] 4.5 Unit tests for both use cases with fakes, covering every add scenario in `specs/dependency-add` that does not involve I/O

## 5. Dependency resolution

- [x] 5.1 Spike (design D6): inject `TransporterProvider` in a throwaway mojo and verify against WireMock and the live endpoints that (1) query strings survive `GetTask` resolution, (2) 404 is classified `ERROR_NOT_FOUND`, (3) a session copy overrides `aether.connector.requestTimeout` per call, (4) JSON bodies arrive unmodified, and (5) `aether.connector.userAgent` is honored. Record the results in `design.md`; if (1) fails, switch the search endpoints to an Apache HttpClient 5.6.4 adapter behind the same port and update the `dependency-resolution` spec's transport statement ("Resolution providers" requirement) to match
- [x] 5.2 Implement `ResolverHttpClient`: a synthetic `RemoteRepository` per search host (no mirror), per-call session copy with the provider timeout, User-Agent and retries disabled (spike finding), 404/other/transport error classification (reflective `getStatusCode()` in the cause chain), and the certificate hint when a PKIX/`SSLHandshakeException` cause is present
- [x] 5.3 Implement `ProxyChooser`: the settings proxy (decrypted, `nonProxyHosts`); then env vars by libcurl rules (lowercase first; `https_proxy`/`HTTPS_PROXY` then `all_proxy`/`ALL_PROXY`; default scheme `http`, default port 1080; `user:password@` → Aether `Authentication`; `no_proxy`/`NO_PROXY` with `*`, leading-dot and subdomain matching); then JVM properties. SOCKS schemes raise the exact spec error; offline mode short-circuits with the offline message
- [x] 5.4 Implement query/path encoding and URL builders; unit-test the `specs/dependency-resolution` encoding scenario
- [x] 5.5 Implement `MavenCentralDependencyResolver` (configurable search base URL): `a:` search with free-text fallback, exact-artifactId filter, candidates with provisional `latestVersion`, coordinate existence (gav `numFound` → POM `ArtifactRequest` through mirror-routed central), coordinate identity for versionless coordinates
- [x] 5.6 Implement `DepsDevDependencyResolver`: not-found for terms; `versions[].versionKey.version`; exact version check; provisional version `isDefault` → highest stable → highest (never oldest)
- [x] 5.7 Implement `CanonicalVersionSelector`: `VersionRangeRequest` `[0,)` against `newResolutionRepositories(session, [central])` with `UPDATE_POLICY_ALWAYS` and `GenericVersionScheme` ordering; walk non-pre-releases (spec markers) from highest down and take the first one in deps.dev's version list for the artifact (one request; `search.maven.org`'s index is frozen in 2025), with fallbacks (list unavailable or nothing confirmed → highest non-pre-release; no non-pre-releases → same walk over all versions; listing failure → provider version); bounded pool of 4 for multiple candidates
- [x] 5.8 Implement `CompositeDependencyResolver`: the race decides identity/candidates with failure precedence and executor cleanup; then canonical versions (or requested version + existence check), then ranking, then the ambiguity outcome
- [x] 5.9 Unit tests: ranking table, pre-release markers, canonical selection (milestone latest, out-of-order metadata, all pre-releases, listing failure → provider fallback, deps.dev without default, mirror-only internal builds skipped, Guava `-jre`/`-android`, confirmation failure fallback), determinism across simulated race winners, the proxy-chooser table (every scenario in the spec), composite ordering with fake latency, and WireMock tests for all providers using the recorded responses from 1.6

## 6. POM repository and validation

- [x] 6.1 Implement `PomLocator` (explicit request POM, else upward walk from execution root; exact not-found message)
- [x] 6.2 Port `PomProjectDependencyRepository`: comment/CDATA-masked, depth-tracking location of the project-level `<dependencies>`; duplicate keys from that element only; rendering; insertion cases 1–4 from the spec (line start, inline closing tag, self-closing, wrap before last `</project>`); missing `</project>` error, ISO-8859-1 byte-transparent IO, inserted lines using the file's own line separator (first line break CRLF → CRLF)
- [x] 6.3 Golden tests against the fixtures from 1.5 (byte-for-byte), plus spec-derived cases for project-level `<dependencies>` after `<dependencyManagement>`, inline `<dependencies></dependencies>`, self-closing `<dependencies/>`, a managed-only dependency added for real, and a commented-out duplicate
- [x] 6.4 Implement `ProjectBuilderMavenProjectValidator` (session-derived request, no dependency resolution, ERROR problems → FAILED)

## 7. Project generation and wrapper

- [x] 7.1 Port `ProjectGenerator`: existing-path check, main/test package dirs, `Main.java` and `pom.xml` templates written with `System.lineSeparator()`
- [x] 7.2 Implement `WrapperGenerator`: properties with wrapper 3.3.4, Maven version from `RuntimeInformation`, repository URL from `MVNW_REPOURL` (trim, strip one trailing `/`, length > 4), then the first `mirrorOf="*"` mirror, then Central, and `System.lineSeparator()`; copy bundled `mvnw`/`mvnw.cmd` bytes, POSIX execute bits when supported, `Failed to generate Maven Wrapper.` on error
- [x] 7.3 Golden tests for init output (with/without wrapper) against fixtures from 1.2–1.4

## 8. Mojos and interaction

- [x] 8.1 Implement `ConsoleInteraction` over plexus `InputHandler`/`OutputHandler` (text with default, numbered select accepting number or text, EOF → cancelled; `DefaultPrompter` can't detect EOF, see D5) and `LogReportSink`
- [x] 8.2 Implement `InitMojo` (`init`, requiresProject=false, aggregator, threadSafe): parameters `ex.name/groupId/package/java/wrapper`, early validation with Java-21 assumption, interactive collection order and defaults, wrapper-prompt rule, batch-mode defaults and `Missing project name` failure, summary and success output
- [x] 8.3 Implement `AddMojo` (`add`, requiresProject=false, aggregator, threadSafe): `ex.deps/version/scope`, usage error, single-dependency option checks, scope validation, expression grammar (port `DependencyArgumentsParser`), POM located before resolution, disambiguation loop (top 3 + `Search again...`), batch-mode ambiguity failure, result and validation output
- [x] 8.4 Implement `ParameterGuard` (design D17) and call it first in every mojo: a shared parameter-name registry, the `ex.internal.` allowance, a typo failure with Levenshtein ≤ 2 did-you-mean, and the other-goal warning; unit-test every scenario in the spec's unknown-parameter requirement
- [x] 8.5 Write plain-prose Javadoc descriptions on the annotated mojo classes and `@Parameter` fields (no legacy `@goal`/`@parameter` tags) so `mvn ex:help -Ddetail` shows descriptions and `mvn ex:<goal> -Dex.*` examples (replacing CLI help examples)
- [x] 8.6 Unit tests for mojo-level flows with a scripted `Interaction` (every interactive scenario in the init/add specs, including cancellation, the empty "Search again" answer reusing the previous term, and EOF at the selector)

## 9. Setup and uninstall goals

- [x] 9.1 Capture settings fixtures under `src/test/resources/settings/`: Maven 3.9.16 `conf/settings.xml` verbatim, file with existing groups (tab-indented), self-closing `<pluginGroups/>`, no `<pluginGroups>` (4-space indent), CRLF file, group only in a comment, group already active, `<settings>` with no children, non-UTF-8 declared encoding, malformed XML; write expected-output goldens for each
- [x] 9.2 Implement comment/CDATA masking and `SettingsPluginGroupEditor` (detect via `SettingsXpp3Reader`, locate on the masked text, insert with the derived indentation and line separator, cases 1–3, re-parse verification)
- [x] 9.3 Implement `SettingsFileWriter`: symlink resolution, encoding from the XML declaration, timestamped `.bak`, temp file + atomic move with fallback, POSIX permission preservation
- [x] 9.4 Implement `SetupMojo` (`setup`, requiresProject=false, aggregator, threadSafe): target `session.getRequest().getUserSettingsFile()`, create the new file from the template, the exact log messages, and the completion hint
- [x] 9.5 Add the setup tip to `InitMojo` and `AddMojo`, based on `session.getSettings().getPluginGroups()` and the running plugin version
- [x] 9.6 Unit tests: every fixture from 9.1 byte-for-byte, idempotency (second run leaves bytes and mtime unchanged), malformed input leaves no backup, permission preservation on POSIX
- [x] 9.7 Add uninstall fixtures: entry among other groups (LF and CRLF), similar group `com.sebas3261.tools`, commented entry plus active entry, inline single-line `<pluginGroups>`, duplicate active entries, entry with surrounding whitespace in the value, namespace-prefixed element (expects safe failure), a full settings file with mirrors/servers/proxies/profiles; write expected-output goldens
- [x] 9.8 Implement `SettingsPluginGroupEditor.remove`: masked-text matching, whole-line versus element-only range widening, count check against the parser, and a model-equality check via `SettingsXpp3Writer` (design D15)
- [x] 9.9 Implement `UninstallMojo` (`uninstall`, requiresProject=false, aggregator, threadSafe): same target-file resolution and `SettingsFileWriter` as setup, the no-file and not-configured no-ops (no file/dir creation, no backup), removal and completion messages, and the global-settings note
- [x] 9.10 Unit tests: every fixture from 9.7 byte-for-byte; property test that `uninstall(setup(x)) == x` for every case-1 fixture and leaves only an empty `<pluginGroups>` for cases 2–3; idempotency; malformed input and count mismatch leave no backup

## 10. Integration tests (`src/it`)

- [x] 10.1 `init-wrapper`: batch init with defaults; assert files vs goldens, `mvnw` executable, `./mvnw -v` on Unix
- [x] 10.2 `init-no-wrapper`, `init-existing-dir`, `init-invalid-name`, `init-batch-missing-name`
- [x] 10.3 `init-in-reactor`: run from a 3-module project root; assert a single project directory
- [x] 10.4 `add-basic` and `add-scope` against WireMock; assert POM vs goldens and `Maven validate passed`
- [x] 10.5 `add-nested-dir`, `add-duplicate`, `add-ambiguous-batch`, `add-offline`, `add-no-pom`
- [x] 10.6 `interactive-init`: feed stdin to verify Prompter wiring end-to-end
- [x] 10.7 `setup-new-file` and `setup-existing-file`: run setup with `-s <it dir>/settings.xml`, assert the goldens, then run `mvn -s <file> ex:help` to prove the prefix resolves; `setup-idempotent` runs setup twice
- [x] 10.8 `uninstall-roundtrip`: setup then uninstall on a copied Maven default `settings.xml` with `-s`; assert the result is byte-identical to the original, the fully qualified `help` still works, and `mvn -s <file> ex:help` fails with the no-plugin-for-prefix error; `uninstall-missing-file` asserts no file or directory is created
- [x] 10.9 `init-wrapper-mirror` and `init-wrapper-repourl`: assert `distributionUrl` uses the `mirrorOf="*"` mirror, respectively `MVNW_REPOURL`, and the running Maven version; `init-wrapper-mirror-central-only` asserts a `mirrorOf=central` mirror is ignored
- [x] 10.10 `add-via-mirror`: IT settings with a `mirrorOf="*"` mirror pointing at WireMock; assert version listings and POM checks hit the mirror and search requests hit the search stubs
- [x] 10.11 `add-env-proxy` (a lightweight forwarding proxy with Basic auth in the IT harness; assert the requests traverse it) and `add-socks-rejected` (assert the SOCKS error and that no direct request reaches WireMock)
- [x] 10.12 `add-unknown-param` (typo fails with did-you-mean) and `add-shared-config` (a `.mvn/maven.config` with `ex.groupId` → warning, success)
- [x] 10.13 `add-broken-pom` and `init-broken-pom` (assert `The build could not read 1 project`, nothing changed) plus `add-broken-pom-subdir-workaround` (runs from `src/`, POM edited)
- [x] 10.14 `add-crlf-pom` (CRLF preserved, inserted lines CRLF) and, on the Windows CI leg, `init-windows-eol` (CRLF `pom.xml`/`Main.java`/properties, LF `mvnw`, CRLF `mvnw.cmd`)

## 11. CI and release

- [x] 11.1 Add a plugin job to `.github/workflows/build.yml`: {ubuntu, macos, windows} × Java {17, 21}, `mvn -B verify`; keep the CMake job until section 12
- [x] 11.2 Add an allowed-to-fail job on the latest Maven 4 release candidate, and a required Linux/Java 17 job running the integration tests on Maven 3.9.0; if it fails, raise `requiredMavenVersion` to the lowest 3.9.x that passes and update the spec (done: 3.9.0 fails with a core NPE for POM-less aggregator goals; floor raised to 3.9.1, which passes the full suite)
- [x] 11.3 Replace the tag release flow: on `v*`, build and attach JAR, POM, sources and javadoc JARs to a GitHub prerelease
- [x] 11.4 Tag `v0.2.0` and verify both install paths on a clean machine: (a) released JAR + POM via `mvn install:install-file` → fully qualified goals work, and `mvn ex:help` works after `setup` (verified locally with the release-built artifacts: Maven 3.9 writes the plugin-prefix metadata on install); (b) `git clone` + `mvn install` + `setup` → `mvn ex:help` works. Correct the README if either result differs

## 12. Remove the C++ implementation and update docs

- [x] 12.1 Confirm every scenario in the four specs is covered by a passing unit or integration test (checklist in the PR description; see `coverage.md` — all 159 mapped and verified)
- [x] 12.2 Delete `src/application`, `src/domain`, `src/infrastructure`, `src/main.cpp`, `CMakeLists.txt`, `.vscode/c_cpp_properties.json`, and the CMake CI job/packaging steps
- [x] 12.3 Rewrite `README.md`: installation while the plugin is not on Maven Central (`install:install-file` of the release JAR + POM, or `mvn install` from source, then the one-time fully qualified `…:setup` for the `ex:` prefix, with `mvn ex:uninstall` to undo it; manual `pluginGroups` snippet as a fallback), goal usage, Java 17/Maven 3.9 requirements, network configuration (proxy precedence settings → env → JVM, SOCKS via `-DsocksProxyHost`, mirrors used for repository lookups), a certificate-trust troubleshooting section, "Known limitations" (goals can't run where Maven can't load the POM, with the run-from-a-subdirectory workaround; in-process validation doesn't run validate-phase plugins), migration table from every `mvnex` command/flag to its `mvn ex:*` equivalent, updated roadmap/distribution section
- [x] 12.4 Rewrite `docs/ARCHITECTURE.md` for the Java package layout and mojo flows; update the distribution section of `docs/VISION.md`
- [x] 12.5 Update `CLAUDE.md` (build/test commands: `mvn verify`, single test via `-Dtest=...`, ITs via `-Dinvoker.test=...`; architecture) and fill `openspec/config.yaml` context with the new tech stack
