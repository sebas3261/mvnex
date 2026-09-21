package com.sebas3261.ex.infrastructure.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.junit.jupiter.api.Test;

class ProxyChooserTest {

    private static final URI SONATYPE = URI.create("https://central.sonatype.com/solrsearch/select?q=x");
    private static final URI SEARCH_MAVEN = URI.create("https://search.maven.org/solrsearch/select?q=x");
    private static final URI DEPS_DEV = URI.create("https://api.deps.dev/v3/systems/MAVEN/packages/a%3Ab");

    private static final ProxySelector NO_SETTINGS_PROXY = repository -> null;

    private static Optional<Proxy> choose(ProxySelector settings, Map<String, String> variables, Properties props,
            URI uri) {
        return new ProxyChooser(settings, variables, props).proxyFor(uri);
    }

    private static Optional<Proxy> choose(Map<String, String> variables, URI uri) {
        return choose(NO_SETTINGS_PROXY, variables, new Properties(), uri);
    }

    @Test
    void noProxyConfiguredMeansDirect() {
        assertTrue(choose(Map.of(), SONATYPE).isEmpty());
    }

    @Test
    void environmentProxyLikeTheCppTool() {
        Proxy proxy = choose(Map.of("HTTPS_PROXY", "http://user:secret@proxy.corp:3128"), SONATYPE).orElseThrow();
        assertEquals("proxy.corp", proxy.getHost());
        assertEquals(3128, proxy.getPort());
        assertEquals(Proxy.TYPE_HTTP, proxy.getType());

        ProxyChooser.VariableProxy parsed = ProxyChooser.parse("http://user:secret@proxy.corp:3128", "HTTPS_PROXY");
        assertEquals("user", parsed.user());
        assertEquals("secret", parsed.password());
    }

    @Test
    void settingsProxyWins() {
        ProxySelector settings = repository -> new Proxy(Proxy.TYPE_HTTP, "settings-proxy", 8080);
        Proxy proxy = choose(settings, Map.of("HTTPS_PROXY", "http://env-proxy:3128"), new Properties(), SONATYPE)
                .orElseThrow();
        assertEquals("settings-proxy", proxy.getHost());
        assertEquals(8080, proxy.getPort());
    }

    @Test
    void lowercaseTakesPrecedenceAndPortDefaultsTo1080() {
        Proxy proxy = choose(Map.of("https_proxy", "proxy-a", "HTTPS_PROXY", "http://proxy-b:9000"), SONATYPE)
                .orElseThrow();
        assertEquals("proxy-a", proxy.getHost());
        assertEquals(1080, proxy.getPort());
        assertEquals(Proxy.TYPE_HTTP, proxy.getType());
    }

    @Test
    void allProxyIsTheFallback() {
        Proxy proxy = choose(Map.of("ALL_PROXY", "http://all:8000"), SONATYPE).orElseThrow();
        assertEquals("all", proxy.getHost());
        Proxy https = choose(Map.of("ALL_PROXY", "http://all:8000", "HTTPS_PROXY", "http://https:9000"), SONATYPE)
                .orElseThrow();
        assertEquals("https", https.getHost());
    }

    @Test
    void noProxyExcludesMatchingHostsAndSubdomains() {
        Map<String, String> variables = Map.of("HTTPS_PROXY", "http://p:3128", "NO_PROXY", "deps.dev,.maven.org");
        assertTrue(choose(variables, DEPS_DEV).isEmpty(), "api.deps.dev is a subdomain of deps.dev");
        assertTrue(choose(variables, SEARCH_MAVEN).isEmpty(), "leading dot ignored");
        assertTrue(choose(variables, SONATYPE).isPresent());
    }

    @Test
    void noProxyWildcardAndLowercasePrecedence() {
        assertTrue(choose(Map.of("HTTPS_PROXY", "http://p:1", "NO_PROXY", "*"), SONATYPE).isEmpty());
        assertTrue(choose(Map.of("HTTPS_PROXY", "http://p:1", "no_proxy", "sonatype.com", "NO_PROXY", "other.org"),
                SONATYPE).isEmpty());
        assertFalse(ProxyChooser.matchesNoProxy("notsonatype.com", "sonatype.com"));
    }

    @Test
    void socksProxyIsRejectedWithTheSpecMessage() {
        LookupNotPossibleException error = assertThrows(LookupNotPossibleException.class,
                () -> choose(Map.of("ALL_PROXY", "socks5://127.0.0.1:1080"), SONATYPE));
        assertEquals("SOCKS proxy socks5://127.0.0.1:1080 (from ALL_PROXY) is not supported for dependency "
                + "lookups. Configure the JVM instead, e.g. MAVEN_OPTS=\"-DsocksProxyHost=127.0.0.1 "
                + "-DsocksProxyPort=1080\".", error.getMessage());
    }

    @Test
    void httpsProxyScheme() {
        assertEquals(Proxy.TYPE_HTTPS, choose(Map.of("HTTPS_PROXY", "https://secure:443"), SONATYPE)
                .orElseThrow().getType());
    }

    @Test
    void percentEncodedCredentials() {
        ProxyChooser.VariableProxy parsed = ProxyChooser.parse("http://d%40main:p%3Ass+w@h:1", "HTTPS_PROXY");
        assertEquals("d@main", parsed.user());
        assertEquals("p:ss+w", parsed.password());
    }

    @Test
    void urlWithoutCredentials() {
        assertNull(ProxyChooser.parse("http://h:1/", "HTTPS_PROXY").user());
    }

    @Test
    void jvmPropertiesAreTheLastResort() {
        Properties props = new Properties();
        props.setProperty("https.proxyHost", "jvm-proxy");
        props.setProperty("https.proxyPort", "8443");
        props.setProperty("http.nonProxyHosts", "*.deps.dev|localhost");

        Proxy proxy = choose(NO_SETTINGS_PROXY, Map.of(), props, SONATYPE).orElseThrow();
        assertEquals("jvm-proxy", proxy.getHost());
        assertEquals(8443, proxy.getPort());
        assertTrue(choose(NO_SETTINGS_PROXY, Map.of(), props, DEPS_DEV).isEmpty());

        Proxy fromVariable = choose(NO_SETTINGS_PROXY, Map.of("HTTPS_PROXY", "http://env:1"), props, SONATYPE)
                .orElseThrow();
        assertEquals("env", fromVariable.getHost());
    }
}
