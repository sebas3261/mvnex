package com.sebas3261.ex.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.sebas3261.ex.infrastructure.transport.LookupUrls;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Starts and stops WireMock for integration test projects from the maven-invoker-plugin hook scripts.
 *
 * <p>The hook scripts may run in different class loaders, so the running server is kept as a JDK
 * {@link Runnable} in the system properties rather than in a static field. The chosen port is written
 * to {@code <basedir>/wiremock.port}.
 */
public final class WireMockSupport {

    private static final String STOPPER_KEY_PREFIX = "ex.it.wiremock.stopper.";
    private static final String EMPTY_SOLR = "{\"response\":{\"numFound\":0,\"docs\":[]}}";

    private WireMockSupport() {
    }

    /** Starts WireMock with the stubs in {@code <basedir>/wiremock/mappings} and {@code __files}. */
    public static int start(File basedir) throws IOException {
        return register(basedir, new WireMockServer(options()
                .dynamicPort()
                .usingFilesUnderDirectory(new File(basedir, "wiremock").getAbsolutePath())));
    }

    /**
     * Starts WireMock serving every dependency lookup from the recorded responses in
     * {@code src/test/resources/http}, and points the plugin at it through {@code .mvn/maven.config}:
     * Sonatype Central under {@code /sonatype}, search.maven.org under {@code /smo}, deps.dev under
     * {@code /depsdev}, and the Maven Central repository under {@code /maven2}.
     */
    public static int startLookups(File basedir) throws IOException {
        return startLookups(basedir, true);
    }

    /** @param overrideCentral false to leave the central repository alone, e.g. to test mirror routing */
    public static int startLookups(File basedir, boolean overrideCentral) throws IOException {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        int port = register(basedir, server);
        String base = "http://localhost:" + port;
        String sonatype = base + "/sonatype/solrsearch/select";
        String smo = base + "/smo/solrsearch/select";
        String depsDev = base + "/depsdev/v3/systems/MAVEN/packages/";

        // Anything not stubbed: empty search results, 404 for repository and deps.dev paths.
        server.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(404)));
        server.stubFor(get(com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching("/(sonatype|smo)/.*"))
                .atPriority(9).willReturn(aResponse().withStatus(200).withBody(EMPTY_SOLR)));

        stubSolr(server, sonatype, "a:lombok", 25, "", fixture("sonatype-a-lombok.json"));
        stubSolr(server, sonatype, "g:\"org.projectlombok\" AND a:\"lombok\"", 25, "", fixture("sonatype-ga-lombok.json"));
        stubSolr(server, smo, "a:lombok", 25, "", fixture("search-maven-a-lombok.json"));
        stubSolr(server, smo, "g:\"org.projectlombok\" AND a:\"lombok\"", 25, "",
                doc("org.projectlombok", "lombok", "1.18.38"));
        stubSolr(server, smo, "a:junit-jupiter", 25, "", doc("org.junit.jupiter", "junit-jupiter", "5.13.0-M3"));
        stubSolr(server, smo, "g:\"org.junit.jupiter\" AND a:\"junit-jupiter\"", 25, "",
                doc("org.junit.jupiter", "junit-jupiter", "5.13.0-M3"));
        stubSolr(server, smo, "g:\"org.projectlombok\" AND a:\"lombok\" AND v:\"1.18.32\"", 1, "gav",
                fixture("search-maven-gav-hit.json"));
        stubSolr(server, smo, "g:\"org.junit.jupiter\" AND a:\"junit-jupiter\" AND v:\"5.10.0\"", 1, "gav",
                "{\"response\":{\"numFound\":1,\"docs\":[{\"g\":\"org.junit.jupiter\",\"a\":\"junit-jupiter\","
                        + "\"v\":\"5.10.0\"}]}}");

        stubDepsDev(server, "org.projectlombok", "lombok", fixture("depsdev-lombok.json"));
        stubDepsDev(server, "org.junit.jupiter", "junit-jupiter", fixture("depsdev-junit-jupiter.json"));
        stubDepsDev(server, "org.postgresql", "postgresql", fixture("depsdev-postgresql.json"));

        stub(server, "/maven2/org/projectlombok/lombok/maven-metadata.xml", fixture("metadata-lombok.xml"));
        stub(server, "/maven2/org/junit/jupiter/junit-jupiter/maven-metadata.xml", fixture("metadata-junit-jupiter.xml"));
        stub(server, "/maven2/org/projectlombok/lombok/1.18.32/lombok-1.18.32.pom", fixture("repo1-lombok-1.18.32.pom"));
        stub(server, "/maven2/org/junit/jupiter/junit-jupiter/5.10.0/junit-jupiter-5.10.0.pom",
                fixture("repo1-junit-jupiter-5.10.0.pom"));

        appendMavenConfig(basedir,
                "-Dex.internal.sonatypeUrl=" + sonatype,
                "-Dex.internal.searchMavenUrl=" + smo,
                "-Dex.internal.depsDevUrl=" + depsDev);
        if (overrideCentral) {
            appendMavenConfig(basedir, "-Dex.internal.centralUrl=" + base + "/maven2");
        }
        return port;
    }

    /** Adds lines to {@code <basedir>/.mvn/maven.config}. */
    public static void appendMavenConfig(File basedir, String... lines) throws IOException {
        Path config = Files.createDirectories(basedir.toPath().resolve(".mvn")).resolve("maven.config");
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append('\n');
        }
        Files.writeString(config, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Number of requests WireMock received whose URL starts with {@code prefix}. */
    public static long requests(File basedir, String prefix) throws IOException {
        int port = Integer.parseInt(Files.readString(new File(basedir, "wiremock.port").toPath()).trim());
        try (InputStream in = new java.net.URL("http://localhost:" + port + "/__admin/requests").openStream()) {
            // Count top-level request URLs only; matched entries also embed their stub's URL.
            long count = 0;
            for (com.fasterxml.jackson.databind.JsonNode entry
                    : new com.fasterxml.jackson.databind.ObjectMapper().readTree(in).path("requests")) {
                if (entry.path("request").path("url").asText().startsWith(prefix)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static void stop(File basedir) {
        Object stopper = System.getProperties().remove(STOPPER_KEY_PREFIX + basedir.getAbsolutePath());
        if (stopper instanceof Runnable runnable) {
            runnable.run();
        }
    }

    private static int register(File basedir, WireMockServer server) throws IOException {
        server.start();
        Runnable stopper = server::stop;
        System.getProperties().put(STOPPER_KEY_PREFIX + basedir.getAbsolutePath(), stopper);
        Files.writeString(new File(basedir, "wiremock.port").toPath(), Integer.toString(server.port()),
                StandardCharsets.US_ASCII);
        return server.port();
    }

    private static void stubSolr(WireMockServer server, String searchUrl, String query, int rows, String core,
            String body) {
        String url = LookupUrls.solrSearch(searchUrl, query, rows, core).toString();
        String path = url.substring(url.indexOf('/', "http://".length()));
        stub(server, path, body);
    }

    /**
     * deps.dev package URLs are requested as {@code g%3Aa}, but Maven's transport normalizes the path
     * and sends {@code g:a}; the stub accepts both so a lookup never silently falls through to 404.
     */
    private static void stubDepsDev(WireMockServer server, String groupId, String artifactId, String body) {
        String path = "/depsdev/v3/systems/MAVEN/packages/" + java.util.regex.Pattern.quote(groupId) + "(%3A|:)"
                + java.util.regex.Pattern.quote(artifactId);
        server.stubFor(get(com.github.tomakehurst.wiremock.client.WireMock.urlMatching(path)).atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(body)));
    }

    private static void stub(WireMockServer server, String path, String body) {
        server.stubFor(get(urlEqualTo(path)).atPriority(1).willReturn(aResponse().withStatus(200).withBody(body)));
    }

    private static String doc(String groupId, String artifactId, String latestVersion) {
        return "{\"response\":{\"numFound\":1,\"docs\":[{\"g\":\"" + groupId + "\",\"a\":\"" + artifactId
                + "\",\"latestVersion\":\"" + latestVersion + "\"}]}}";
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = WireMockSupport.class.getResourceAsStream("/http/" + name)) {
            if (in == null) {
                throw new IOException("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
