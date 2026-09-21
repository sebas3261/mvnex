package com.sebas3261.ex.infrastructure.dependency;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.sebas3261.ex.application.errors.DependencyNotFoundException;
import com.sebas3261.ex.application.errors.DependencyResolutionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Providers against WireMock serving the responses recorded in {@code src/test/resources/http}. */
class ProvidersWireMockTest {

    private static WireMockServer server;
    private JdkTestHttpClient http;
    private final Map<String, Boolean> poms = new java.util.HashMap<>();

    private final RepositoryLookup repository = new RepositoryLookup() {
        @Override
        public List<String> listVersions(String groupId, String artifactId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean pomExists(String groupId, String artifactId, String version) {
            return poms.getOrDefault(groupId + ":" + artifactId + ":" + version, false);
        }
    };

    @BeforeAll
    static void start() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @BeforeEach
    void reset() {
        server.resetAll();
        http = new JdkTestHttpClient();
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = ProvidersWireMockTest.class.getResourceAsStream("/http/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void stub(String url, int status, String body) {
        server.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private MavenCentralSearchProvider solr() {
        return new MavenCentralSearchProvider(http, server.baseUrl() + "/solrsearch/select", repository);
    }

    private DepsDevProvider depsDev() {
        return new DepsDevProvider(http, server.baseUrl() + "/v3/systems/MAVEN/packages/");
    }

    private static final String EMPTY_SOLR = "{\"response\":{\"numFound\":0,\"docs\":[]}}";

    // ---- search terms ----

    @Test
    void searchTermCandidatesInProviderOrder() throws IOException {
        stub("/solrsearch/select?q=a%3Alombok&rows=25&wt=json", 200, fixture("sonatype-a-lombok.json"));

        List<Candidate> candidates = solr().searchCandidates("lombok");

        assertEquals(13, candidates.size());
        assertEquals(new Candidate("org.projectlombok", "lombok", "1.18.48"), candidates.get(0));
        assertTrue(candidates.stream().allMatch(c -> c.artifactId().equals("lombok")));
    }

    @Test
    void freeTextFallbackAndExactArtifactIdFilter() throws IOException {
        stub("/solrsearch/select?q=a%3Aguava&rows=25&wt=json", 200, EMPTY_SOLR);
        stub("/solrsearch/select?q=guava&rows=25&wt=json", 200, fixture("sonatype-freetext-guava.json"));

        List<Candidate> candidates = solr().searchCandidates("guava");

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().allMatch(c -> c.artifactId().equals("guava")),
                "failureaccess, com.google.guava bundles etc. are filtered out");
    }

    @Test
    void searchTermNotFound() throws IOException {
        stub("/solrsearch/select?q=a%3Amvnexdoesnotexist12345&rows=25&wt=json", 200, fixture("sonatype-a-miss.json"));
        stub("/solrsearch/select?q=mvnexdoesnotexist12345&rows=25&wt=json", 200, EMPTY_SOLR);

        DependencyNotFoundException error = assertThrows(DependencyNotFoundException.class,
                () -> solr().searchCandidates("mvnexdoesnotexist12345"));
        assertEquals("Dependency not found: mvnexdoesnotexist12345", error.getMessage());
    }

    @Test
    void searchHttpErrorIsAResolutionError() {
        stub("/solrsearch/select?q=a%3Alombok&rows=25&wt=json", 503, "");
        DependencyResolutionException error = assertThrows(DependencyResolutionException.class,
                () -> solr().searchCandidates("lombok"));
        assertEquals("Maven Central request failed.", error.getMessage());
    }

    // ---- coordinates ----

    @Test
    void coordinateFoundByExactGroupAndArtifact() {
        stub("/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22&rows=25&wt=json", 200,
                "{\"response\":{\"numFound\":1,\"docs\":[{\"g\":\"org.projectlombok\",\"a\":\"lombok\","
                        + "\"latestVersion\":\"1.18.48\"}]}}");

        assertEquals(new Candidate("org.projectlombok", "lombok", "1.18.48"),
                solr().coordinate("org.projectlombok", "lombok"));
    }

    @Test
    void sonatypeCannotAnswerQuotedCoordinateQueries() throws IOException {
        // Recorded behavior: central.sonatype.com returns numFound 0 for quoted g/a queries.
        stub("/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22&rows=25&wt=json", 200,
                fixture("sonatype-ga-lombok.json"));

        assertEquals("Dependency not found: org.projectlombok:lombok",
                assertThrows(DependencyNotFoundException.class,
                        () -> solr().coordinate("org.projectlombok", "lombok")).getMessage());
    }

    @Test
    void versionConfirmedByTheGavIndex() throws IOException {
        stub("/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22+AND+v%3A%221.18.32%22"
                + "&rows=1&wt=json&core=gav", 200, fixture("search-maven-gav-hit.json"));

        assertDoesNotThrow(() -> solr().verifyVersion("org.projectlombok", "lombok", "1.18.32"));
    }

    @Test
    void gavMissFallsBackToThePomCheck() throws IOException {
        String gav = "/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22+AND+v%3A%22"
                + "1.18.32%22&rows=1&wt=json&core=gav";
        stub(gav, 200, fixture("sonatype-gav-hit.json")); // Sonatype: numFound 0 even though it exists
        poms.put("org.projectlombok:lombok:1.18.32", true);

        assertDoesNotThrow(() -> solr().verifyVersion("org.projectlombok", "lombok", "1.18.32"));
    }

    @Test
    void missingVersion() throws IOException {
        stub("/solrsearch/select?q=g%3A%22org.projectlombok%22+AND+a%3A%22lombok%22+AND+v%3A%229.9.9%22"
                + "&rows=1&wt=json&core=gav", 200, fixture("search-maven-gav-miss.json"));

        assertEquals("Dependency not found: org.projectlombok:lombok:9.9.9",
                assertThrows(DependencyNotFoundException.class,
                        () -> solr().verifyVersion("org.projectlombok", "lombok", "9.9.9")).getMessage());
    }

    // ---- deps.dev ----

    @Test
    void depsDevNeverAnswersSearchTerms() {
        assertEquals("Dependency not found: lombok",
                assertThrows(DependencyNotFoundException.class, () -> depsDev().searchCandidates("lombok"))
                        .getMessage());
        assertTrue(http.requested.isEmpty(), "no network request for search terms");
    }

    @Test
    void depsDevDefaultVersion() throws IOException {
        stub("/v3/systems/MAVEN/packages/org.projectlombok%3Alombok", 200, fixture("depsdev-lombok.json"));

        assertEquals("1.18.48", depsDev().coordinate("org.projectlombok", "lombok").provisionalVersion());
    }

    @Test
    void depsDevWithoutDefaultUsesHighestStableNotOldest() throws IOException {
        stub("/v3/systems/MAVEN/packages/org.projectlombok%3Alombok", 200, fixture("depsdev-no-default.json"));

        assertEquals("1.18.32", depsDev().coordinate("org.projectlombok", "lombok").provisionalVersion());
    }

    @Test
    void depsDevListsEveryCentralVersion() throws IOException {
        stub("/v3/systems/MAVEN/packages/org.projectlombok%3Alombok", 200, fixture("depsdev-lombok.json"));

        Set<String> versions = depsDev().versions("org.projectlombok", "lombok");

        assertEquals(60, versions.size());
        assertTrue(versions.containsAll(Set.of("0.10.0", "1.18.38", "1.18.48")));
    }

    @Test
    void depsDevUnknownArtifactHasNoVersions() throws IOException {
        stub("/v3/systems/MAVEN/packages/com.example.mvnex%3Adoesnotexist12345", 404, fixture("depsdev-miss.json"));
        assertTrue(depsDev().versions("com.example.mvnex", "doesnotexist12345").isEmpty());
    }

    @Test
    void depsDevVersionCheck() throws IOException {
        stub("/v3/systems/MAVEN/packages/org.projectlombok%3Alombok", 200, fixture("depsdev-lombok.json"));

        assertDoesNotThrow(() -> depsDev().verifyVersion("org.projectlombok", "lombok", "1.18.32"));
        assertEquals("Dependency not found: org.projectlombok:lombok:9.9.9",
                assertThrows(DependencyNotFoundException.class,
                        () -> depsDev().verifyVersion("org.projectlombok", "lombok", "9.9.9")).getMessage());
    }

    @Test
    void depsDevNotFoundAndErrors() throws IOException {
        stub("/v3/systems/MAVEN/packages/com.example.mvnex%3Adoesnotexist12345", 404, fixture("depsdev-miss.json"));
        stub("/v3/systems/MAVEN/packages/org.x%3Abroken", 500, "");

        assertEquals("Dependency not found: com.example.mvnex:doesnotexist12345",
                assertThrows(DependencyNotFoundException.class,
                        () -> depsDev().coordinate("com.example.mvnex", "doesnotexist12345")).getMessage());
        assertEquals("deps.dev request failed.",
                assertThrows(DependencyResolutionException.class,
                        () -> depsDev().coordinate("org.x", "broken")).getMessage());
    }
}
