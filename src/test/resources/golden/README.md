# Golden fixtures

Expected outputs recorded from the C++ `mvnex` CLI, which serves as the
behavioral oracle for the Maven plugin port (see
`openspec/changes/archive/2026-09-21-port-to-maven-plugin/design.md`, D13). Every intentional
difference between these files and the plugin's output must map to a row in the
design's "Behavior deltas" table.

## Oracle build

| Component | Version |
|---|---|
| mvnex | `0.1.0`, commit `2f938c446b07435ad74fe473f8e19e341f77b530` |
| replxx | `1f149bfe20bf6e49c1afd4154eaf0032c8c2fda2` (pinned; `CMakeLists.txt` tracks `master`) |
| cpr | `1.11.2` |
| libcurl | system `8.7.1` (macOS SDK) |
| Compiler | Apple clang 16.0.0 |
| CMake | 4.4.3 |

Build commands:

```bash
git clone https://github.com/AmokHuginnsson/replxx.git /tmp/mvnex-oracle/replxx
git -C /tmp/mvnex-oracle/replxx checkout 1f149bfe20bf6e49c1afd4154eaf0032c8c2fda2
cmake -S . -B /tmp/mvnex-oracle/build -DCMAKE_BUILD_TYPE=Release \
      -DFETCHCONTENT_SOURCE_DIR_REPLXX=/tmp/mvnex-oracle/replxx
cmake --build /tmp/mvnex-oracle/build --config Release
```

## Line endings

All text fixtures are stored with LF line endings. Tests that compare generated
files expand LF to `System.lineSeparator()` where the spec requires the
platform separator (`pom.xml`, `Main.java`, `maven-wrapper.properties`). The
wrapper scripts are compared byte-for-byte: `mvnw` is LF and `mvnw.cmd` is CRLF
on every platform.

## `init/`

Recorded with the oracle build, batch-style (all values passed as options):

| Directory | Command |
|---|---|
| `my-app` | `mvnex init my-app --group-id com.example --java 21` |
| `my-cool-app` | `mvnex init my-cool-app --group-id org.acme --java 8 --package org.acme.custom` |
| `svc` | `mvnex init svc -g com.example -j 25 --no-wrapper` |

`tree.txt` lists every generated path relative to the project directory
(directories end with `/`). The C++ tool on macOS wrote `pom.xml` and
`Main.java` with LF; the plugin writes the platform separator (behavior delta).

## `wrapper/`

`maven-wrapper.properties.{plain,mirror,repourl}` are the files the C++ tool
produced on its usual path (Maven 3.9.16 installed, so it ran
`maven-wrapper-plugin:3.3.4:wrapper`):

| File | Environment | `${repoUrl}` |
|---|---|---|
| `.plain` | no mirror, no `MVNW_REPOURL` | `https://repo.maven.apache.org/maven2` |
| `.mirror` | settings mirror `mirrorOf="*"` → `https://repo1.maven.org/maven2` | `https://repo1.maven.org/maven2` |
| `.repourl` | `MVNW_REPOURL=https://repo.example.org/maven2/` | `https://repo.example.org/maven2` |

`maven-wrapper.properties.template` is the same content with `${repoUrl}` and
`${mavenVersion}` placeholders; tests substitute the expected values and the
running Maven version.

`mvnw` (LF) and `mvnw.cmd` (CRLF) are the official files from
`org.apache.maven.wrapper:maven-wrapper-distribution:3.3.4:zip:only-script`.
The C++ usual path produced exactly these bytes. Only the C++ fallback path
(no `mvn` on `PATH`) wrote an LF `mvnw.cmd` from embedded strings; the plugin
always writes the official bytes (behavior delta).

## `add/`

Each case has `input.pom.xml` and `expected.pom.xml`. The recorded cases ran,
in order, `mvnex add org.projectlombok:lombok:1.18.32` and
`mvnex add org.junit.jupiter:junit-jupiter:5.10.0 --scope test`:

| Case | Source | Notes |
|---|---|---|
| `fresh` | recorded | `init` POM without `<dependencies>`; a block is wrapped before `</project>` |
| `existing-deps` | recorded | project-level 4-space `<dependencies>`; entries inserted before its closing tag |
| `already-present` | recorded | lombok already declared (1.18.30) → skipped; junit added |
| `dependency-management-only` | spec-derived | only `<dependencyManagement>` exists → a new project-level block is wrapped before `</project>`. `cpp-recorded.pom.xml` keeps the C++ output for reference: the inner `        </dependencies>` contained the 4-space marker, so the C++ tool inserted both entries inside `<dependencyManagement>` (managed, not real dependencies). Fixed by design (behavior delta) |
| `crlf` | spec-derived | `fresh` with CRLF endings; inserted lines use CRLF (behavior delta) |
| `iso-8859-1` | spec-derived | `existing-deps` plus `<name>Café</name>` as byte `0xE9`; bytes round-trip (behavior delta) |
| `utf8-bom` | spec-derived | `fresh` with a UTF-8 BOM; BOM preserved |
