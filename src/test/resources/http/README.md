# Recorded HTTP responses

Bodies for WireMock stubs. Recorded 2026-09-21 unless marked synthetic.

| File | Status | Source |
|---|---|---|
| `sonatype-a-lombok.json` | 200 | `https://central.sonatype.com/solrsearch/select?q=a%3Alombok&rows=25&wt=json` |
| `search-maven-a-lombok.json` | 200 | `https://search.maven.org/solrsearch/select?q=a%3Alombok&rows=25&wt=json` |
| `sonatype-a-guava.json` | 200 | `https://central.sonatype.com/solrsearch/select?q=a%3Aguava&rows=25&wt=json` |
| `sonatype-a-miss.json` | 200 | `https://central.sonatype.com/solrsearch/select?q=a%3Amvnexdoesnotexist12345&rows=25&wt=json` |
| `sonatype-freetext-guava.json` | 200 | `https://central.sonatype.com/solrsearch/select?q=guava&rows=25&wt=json` |
| `sonatype-ga-lombok.json` | 200 | `https://central.sonatype.com/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22&rows=25&wt=json` |
| `sonatype-gav-hit.json` | 200 | `https://central.sonatype.com/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22+AND+v%3A%221.18.32%22&rows=1&wt=json&core=gav` |
| `sonatype-gav-miss.json` | 200 | `https://central.sonatype.com/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22+AND+v%3A%229.9.9%22&rows=1&wt=json&core=gav` |
| `repo1-lombok-9.9.9.pom` | 404 | `https://repo1.maven.org/maven2/org/projectlombok/lombok/9.9.9/lombok-9.9.9.pom` |
| `repo1-lombok-1.18.32.pom` | 200 | `https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.32/lombok-1.18.32.pom` |
| `metadata-spring-core.xml` | 200 | `https://repo1.maven.org/maven2/org/springframework/spring-core/maven-metadata.xml` |
| `metadata-guava.xml` | 200 | `https://repo1.maven.org/maven2/com/google/guava/guava/maven-metadata.xml` |
| `depsdev-lombok.json` | 200 | `https://api.deps.dev/v3/systems/MAVEN/packages/org.projectlombok%3Alombok` |
| `depsdev-miss.json` | 404 | `https://api.deps.dev/v3/systems/MAVEN/packages/com.example.mvnex%3Adoesnotexist12345` |
| `search-maven-gav-hit.json` | 200 | `https://search.maven.org/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22+AND+v%3A%221.18.32%22&rows=1&wt=json&core=gav` |
| `search-maven-gav-miss.json` | 200 | same query with `v:"9.9.9"` |
| `metadata-out-of-order.xml` | synthetic | `1.10.0` listed before `1.9.0` |
| `metadata-internal-builds.xml` | synthetic | mirror-only `-acme.2` / `-redhat-00001` builds above `1.18.48` |
| `depsdev-no-default.json` | synthetic | trimmed from `depsdev-lombok.json`, all `isDefault=false`, plus `1.18.34-rc1` |

Observations at recording time:

- `central.sonatype.com` ignores `core` and returns `numFound: 0` for quoted `g:"…" AND a:"…"` queries, including `sonatype-gav-hit.json` for an existing version. Only `search.maven.org` answers gav queries.
- `metadata-spring-core.xml`: `<latest>` is the milestone `7.1.0-M1`.
- `depsdev-lombok.json`: versions are listed oldest first; `isDefault` is `1.18.48`.
- `metadata-lombok.xml`, `metadata-junit-jupiter.xml`, `depsdev-junit-jupiter.json`, `repo1-junit-jupiter-5.10.0.pom`: recorded 2026-09-21 for the `add` integration tests.
- `depsdev-postgresql.json`: recorded 2026-09-21 for the `add-multiple` integration test.
