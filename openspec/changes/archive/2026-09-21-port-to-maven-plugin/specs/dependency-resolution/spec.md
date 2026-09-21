## ADDED Requirements

### Requirement: Resolution providers
Dependency resolution SHALL consult three search providers:

| Provider | Endpoint | Request timeout | Handles |
|---|---|---|---|
| Sonatype Central search | `https://central.sonatype.com/solrsearch/select` | 5000 ms | search terms and coordinates |
| Maven Central search | `https://search.maven.org/solrsearch/select` | 1500 ms | search terms and coordinates |
| deps.dev | `https://api.deps.dev/v3/systems/MAVEN/packages/` | 5000 ms | coordinates only (always "not found" for search terms) |

These endpoints are not Maven repositories and SHALL always be contacted at the URLs above, never through repository mirrors. All HTTP requests (search endpoints and repository lookups) SHALL be GETs made through Maven's own resolver transport, follow redirects, and send a `User-Agent` identifying the plugin and its version. A transport-level failure (DNS, connect, timeout, TLS) SHALL be classified as "resolver unavailable".

#### Scenario: One provider offline
- **WHEN** `search.maven.org` times out but Sonatype Central answers
- **THEN** resolution succeeds using the Sonatype Central result

### Requirement: Repository lookups go through the user's mirrors
Lookups that read the Maven repository — version listings (`maven-metadata.xml`) and POM existence checks — SHALL be resolved against Maven Central (`central`, `https://repo.maven.apache.org/maven2`) after applying the session's mirror, proxy, and authentication configuration, exactly as a Maven download would. They SHALL use Maven's configured connector timeouts and SHALL always check the remote repository for updated metadata (no stale cached listing). An artifact POM already present in the local repository SHALL count as existing.

#### Scenario: Company mirror
- **WHEN** settings define a mirror with `mirrorOf` `*` pointing at `https://nexus.acme.corp/repository/maven-public`
- **THEN** version listings and POM checks are requested from the Nexus mirror, while the three search endpoints are still requested at their public URLs

#### Scenario: No mirror
- **WHEN** no mirror is configured
- **THEN** repository lookups go to `https://repo.maven.apache.org/maven2`

### Requirement: Proxy selection
For every request, the proxy SHALL be chosen by the first source that applies, in this order:
1. **Maven settings**: the active proxy from `settings.xml` (with decrypted credentials and `nonProxyHosts`) as Maven would apply it to that URL.
2. **Environment variables**, following libcurl's rules (as documented in the curl manual): for HTTPS URLs use `https_proxy`, else `HTTPS_PROXY`, else `all_proxy`, else `ALL_PROXY`, with the lowercase name taking precedence. The value has the form `[scheme://][user:password@]host[:port]`; a missing scheme means `http`, and a missing port means `1080`. `no_proxy`/`NO_PROXY` (lowercase first) disables the proxy for matching hosts: `*` matches every host, and each comma-separated entry (surrounding whitespace and a single leading `.` ignored) matches that host name or any subdomain of it.
3. **JVM system properties** (`https.proxyHost`, `https.proxyPort`, `http.nonProxyHosts`).

Credentials from an environment-variable URL SHALL be used for proxy authentication, including Basic authentication through HTTPS tunnels. If the selected environment proxy uses a `socks4`, `socks4a`, `socks5`, or `socks5h` scheme, lookups SHALL fail with `SOCKS proxy <scheme>://<host>:<port> (from <VARIABLE>) is not supported for dependency lookups. Configure the JVM instead, e.g. MAVEN_OPTS="-DsocksProxyHost=<host> -DsocksProxyPort=<port>".` and SHALL NOT fall back to a direct connection.

#### Scenario: Environment proxy like the C++ tool
- **WHEN** no settings proxy is active and `HTTPS_PROXY=http://user:secret@proxy.corp:3128` is set
- **THEN** every lookup goes through `proxy.corp:3128` authenticating as `user`

#### Scenario: Settings proxy wins
- **WHEN** settings define an active proxy `settings-proxy:8080` and `HTTPS_PROXY=http://env-proxy:3128` is also set
- **THEN** lookups use `settings-proxy:8080`

#### Scenario: Lowercase precedence and default port
- **WHEN** `https_proxy=proxy-a` and `HTTPS_PROXY=http://proxy-b:9000` are both set
- **THEN** lookups use `proxy-a:1080` over HTTP

#### Scenario: NO_PROXY
- **WHEN** `HTTPS_PROXY` is set and `NO_PROXY=deps.dev,.maven.org`
- **THEN** requests to `api.deps.dev` and `search.maven.org` connect directly and requests to `central.sonatype.com` use the proxy

#### Scenario: SOCKS proxy in the environment
- **WHEN** `ALL_PROXY=socks5://127.0.0.1:1080` is the selected proxy
- **THEN** the goal fails with the SOCKS message and no direct connection is attempted

### Requirement: Untrusted certificate hint
When a lookup fails because the server certificate is not trusted by the JVM (a PKIX path-building or certificate-validation failure anywhere in the cause chain), the resulting error message SHALL be followed by: `The server certificate is not trusted by Java. If your network inspects HTTPS traffic, import your organization's root certificate into the JDK truststore, or use the operating system trust store by adding -Djavax.net.ssl.trustStoreType=KeychainStore (macOS) or -Djavax.net.ssl.trustStoreType=Windows-ROOT (Windows) to .mvn/jvm.config or MAVEN_OPTS.`

#### Scenario: TLS-inspecting proxy
- **WHEN** all providers fail with certificate path-building errors
- **THEN** the goal fails with the unavailable message followed by the certificate hint

### Requirement: Concurrent first-result resolution
For each request, all providers SHALL be queried concurrently. The first provider to complete with a decisive outcome — one or more candidates for a search term, or confirmation that a coordinate exists — SHALL determine the candidate set, and other providers' results SHALL be ignored. Only if every provider fails SHALL resolution fail, with this precedence: if any provider reported "not found", fail with that provider's not-found message; else if any reported "unavailable", fail with that message; else fail with the last other error message; else `Dependency resolver failed.` A failure that no provider could avoid (offline mode, or a rejected SOCKS proxy) SHALL abort resolution immediately with its own message, even if another provider reported "not found" (deps.dev answers search terms with "not found" without any network request, which would otherwise mask the real cause). Versions are then chosen by "Canonical version selection", independent of which provider won.

#### Scenario: Fast provider decides the candidate set
- **WHEN** Maven Central search returns two `lombok` candidates first and Sonatype Central would later return three
- **THEN** the two candidates from Maven Central are used

#### Scenario: SOCKS failure is not masked by deps.dev
- **WHEN** `ALL_PROXY=socks5://127.0.0.1:1080` is set and the user adds `lombok`
- **THEN** the goal fails with the SOCKS message, not `Dependency not found: lombok`

#### Scenario: All providers fail
- **WHEN** Sonatype Central and Maven Central report not found and deps.dev is unreachable
- **THEN** resolution fails with the message `Dependency not found: <query>`

### Requirement: Search-term resolution
A search provider SHALL resolve a term by querying `a:<term>` (25 rows), falling back to a free-text query `<term>` when the first returns no usable documents. A document is usable only if it has non-empty `g`, `a`, and `latestVersion`. Only documents whose artifactId equals the term exactly SHALL become candidates, in the provider's document order, each carrying the document's `latestVersion` as its provisional version. With no candidates the provider SHALL report `Dependency not found: <term>`.

After the race, each candidate's version SHALL be the requested version when one was given, otherwise the canonical version. Candidates SHALL then be stable-sorted by ascending score (see "Candidate ranking"); more than one candidate SHALL produce a multiple-matches outcome with the sorted list. With exactly one candidate and a requested version, the version SHALL be verified to exist (see "Coordinate resolution"), otherwise `Dependency not found: <g>:<a>:<version>`.

#### Scenario: Exact artifactId filter
- **WHEN** searching `guava` returns artifacts `guava`, `guava-testlib`, `failureaccess`
- **THEN** only `guava` artifacts become candidates

#### Scenario: Single match with missing version
- **WHEN** term `postgresql` has one candidate `org.postgresql:postgresql` and version `0.0.1` is requested
- **THEN** resolution fails with `Dependency not found: org.postgresql:postgresql:0.0.1`

### Requirement: Canonical version selection
When no version was requested, the version of every candidate (search terms) or of the resolved coordinate SHALL be chosen by one deterministic procedure, independent of which provider won the race:
1. List all versions of `groupId:artifactId` via the repository (see "Repository lookups go through the user's mirrors"), ordered by Maven's own version ordering.
2. Walk the non-pre-release versions (see "Pre-release detection") from highest to lowest and choose the first one that is published on Maven Central according to **deps.dev's version list** for `groupId:artifactId` (`https://api.deps.dev/v3/systems/MAVEN/packages/<g:a>`, one request per artifact), regardless of which provider won the race. Versions served only by the mirror (internal or vendor builds) are thereby skipped. The search endpoints are not used for this: `search.maven.org`'s index stopped updating in early/mid 2025 (its newest lombok entry is 1.18.38 while Central has 1.18.48, observed 2026-09-21), and `central.sonatype.com` returns `numFound: 0` for quoted gav queries. If the version list cannot be fetched (transport error or non-2xx), or if no listed version appears in it, choose the highest non-pre-release version from the listing.
3. If no non-pre-release version exists, apply step 2 to all versions (pre-releases included).
4. Only if the listing fails or is empty, use the winning provider's provisional version (for deps.dev: see "Coordinate resolution").

Canonical selection for multiple candidates SHALL run concurrently with at most 4 lookups in flight.

#### Scenario: Latest is a milestone
- **WHEN** the listing for `org.springframework:spring-core` contains `6.2.10` and `7.0.0-M3` and the provider's `latestVersion` is `7.0.0-M3`
- **THEN** the chosen version is `6.2.10`

#### Scenario: Same answer for term and coordinate
- **WHEN** the user adds `lombok` in one project and `org.projectlombok:lombok` in another at the same moment
- **THEN** both receive the same version

#### Scenario: Independent of the race winner
- **WHEN** a versionless coordinate is resolved repeatedly while deps.dev and the two search providers alternate in winning the race
- **THEN** every run chooses the same version

#### Scenario: Version ordering instead of document order
- **WHEN** `maven-metadata.xml` lists `1.10.0` before `1.9.0` (out of order)
- **THEN** `1.10.0` is chosen as the highest stable version

#### Scenario: Internal build on a company mirror is skipped
- **WHEN** the mirror lists `1.18.48-redhat-00001`, `1.18.48-acme.2`, and `1.18.48` for `org.projectlombok:lombok`, and only `1.18.48` exists on Maven Central
- **THEN** `1.18.48` is chosen, even though Maven version ordering ranks the internal builds higher

#### Scenario: Qualified public versions are kept
- **WHEN** Central lists `com.google.guava:guava` versions `33.4.0-android` and `33.4.0-jre`
- **THEN** `33.4.0-jre` is chosen (highest by Maven ordering, confirmed on Central)

#### Scenario: Central version list unavailable
- **WHEN** the deps.dev version-list request fails with a transport error
- **THEN** the highest non-pre-release version from the mirror listing is chosen

#### Scenario: Confirmation independent of the race winner
- **WHEN** Sonatype Central wins the race for `lombok`
- **THEN** confirmation uses deps.dev's version list and `1.18.48` is chosen

#### Scenario: Stale search index is not used
- **WHEN** `search.maven.org` only knows lombok versions up to `1.18.38` but Central (and deps.dev) list `1.18.48`
- **THEN** `1.18.48` is chosen

#### Scenario: No version confirmed
- **WHEN** none of the listed versions appears in deps.dev's version list (for example, a release published minutes ago that deps.dev hasn't indexed yet, with no older version listed)
- **THEN** the highest non-pre-release version from the listing is chosen

#### Scenario: Only pre-releases exist
- **WHEN** an artifact has only `1.0.0-M1` and `1.0.0-M2`
- **THEN** `1.0.0-M2` is chosen

### Requirement: Candidate ranking
A candidate's score SHALL be `knownGroupRank × 10 + suspiciousPenalty + preReleasePenalty`, computed after each candidate's version has been chosen, where:
- `knownGroupRank` is the index of the first entry in this ordered list whose term prefix the search term starts with AND whose group prefix the candidate groupId starts with, or `1000` if none: `lombok→org.projectlombok`, `spring-→org.springframework`, `spring-boot-→org.springframework.boot`, `junit→org.junit.jupiter`, `slf4j-→org.slf4j`, `logback-→ch.qos.logback`, `jackson-→com.fasterxml.jackson`, `postgresql→org.postgresql`, `mysql→com.mysql`, `guava→com.google.guava`;
- `suspiciousPenalty` is `100` when the groupId starts with `io.github.` or `com.github.`, else `0`;
- `preReleasePenalty` is `50` when the candidate version is a pre-release, else `0`.

Ties SHALL keep the provider's original order.

#### Scenario: Known group first
- **WHEN** candidates for `lombok` are `io.github.valuya:lombok` and `org.projectlombok:lombok`
- **THEN** `org.projectlombok:lombok` is ranked first

### Requirement: Pre-release detection
A version SHALL be considered a pre-release when its lowercase form contains any of `-m`, `-rc`, `-alpha`, `-beta`, `-snapshot`.

#### Scenario: Markers
- **WHEN** versions `6.0.0-M1`, `2.0-rc1`, `1.0.0-SNAPSHOT`, `3.1-beta` are checked
- **THEN** all are pre-releases, and `1.18.48` is not

### Requirement: Coordinate resolution
For a coordinate with a version, a search provider SHALL verify existence: query `g:"<g>" AND a:"<a>" AND v:"<v>"` (core `gav`, 1 row) and accept when `numFound > 0`; otherwise check for the POM `<group path>/<a>/<v>/<a>-<v>.pom` through the repository (see "Repository lookups go through the user's mirrors"), where "not found" means `Dependency not found: <g>:<a>:<v>` and any other failure is an error. For a coordinate without a version, a search provider SHALL confirm existence through a document matching both groupId and artifactId exactly (provisional version: its `latestVersion`), or report `Dependency not found: <g>:<a>`; the version is then chosen by "Canonical version selection".

deps.dev SHALL GET `<base><url-encoded "g:a">` and read `versions[].versionKey.version` and `versions[].isDefault`; 404 or an empty version list means not found. With a requested version it SHALL require an exact match. Without one, its provisional version SHALL be the version flagged `isDefault`, else the highest non-pre-release version by Maven version ordering, else the highest version (never the first-listed, which deps.dev orders oldest first).

#### Scenario: Explicit coordinate and version
- **WHEN** `org.projectlombok:lombok:1.18.32` is requested
- **THEN** it resolves to exactly that GAV after the existence check

#### Scenario: Non-existent version
- **WHEN** `org.projectlombok:lombok:9.9.9` is requested
- **THEN** resolution fails with `Dependency not found: org.projectlombok:lombok:9.9.9`

#### Scenario: deps.dev without a default version
- **WHEN** deps.dev lists `0.10.0`, `1.18.30`, `1.18.32`, `1.18.34-rc1` with no `isDefault` and the repository listing is unavailable
- **THEN** the provisional version `1.18.32` is used

### Requirement: Query encoding
Search query values SHALL be encoded by keeping `A–Z a–z 0–9 - _ . ~`, turning space into `+`, and percent-encoding every other byte in uppercase hex; URLs SHALL be built as `<base>?q=<q>&rows=<n>&wt=json[&core=<core>]`. deps.dev path segments SHALL use the same rule except space is percent-encoded.

#### Scenario: Coordinate query encoding
- **WHEN** the query is `g:"org.x" AND a:"y"`
- **THEN** the `q` value is `g%3A%22org.x%22+AND+a%3A%22y%22`

### Requirement: HTTP error classification
A non-2xx response from a search endpoint SHALL be classified as a resolution error (`Maven Central request failed.`; `Maven Central artifact request failed.` for the POM check; `deps.dev request failed.` for deps.dev), distinct from "not found" and "unavailable".

#### Scenario: Server error
- **WHEN** Sonatype Central returns HTTP 503 and the other providers report not found
- **THEN** resolution fails with the not-found message (not-found takes precedence)
