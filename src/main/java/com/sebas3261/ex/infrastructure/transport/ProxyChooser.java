package com.sebas3261.ex.infrastructure.transport;

import com.sebas3261.ex.application.errors.LookupNotPossibleException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

/**
 * Chooses the proxy for a lookup URL: the Maven settings proxy first, then libcurl-style
 * environment variables (C++ parity), then JVM system properties.
 */
public final class ProxyChooser {

    private static final int LIBCURL_DEFAULT_PROXY_PORT = 1080;
    private static final int JVM_DEFAULT_HTTPS_PROXY_PORT = 443;

    private final ProxySelector settingsProxies;
    private final Map<String, String> variables;
    private final Properties systemProperties;

    /**
     * @param settingsProxies  the session's selector, built by Maven from {@code settings.xml}
     *                         (credentials decrypted, {@code nonProxyHosts} applied); may be null
     * @param variables        process environment variables
     * @param systemProperties JVM system properties
     */
    public ProxyChooser(ProxySelector settingsProxies, Map<String, String> variables, Properties systemProperties) {
        this.settingsProxies = settingsProxies;
        this.variables = Map.copyOf(variables);
        this.systemProperties = systemProperties;
    }

    public Optional<Proxy> proxyFor(URI uri) {
        if (settingsProxies != null) {
            RemoteRepository probe = new RemoteRepository.Builder("proxy-probe", "default", uri.toString()).build();
            Proxy fromSettings = settingsProxies.getProxy(probe);
            if (fromSettings != null) {
                return Optional.of(fromSettings);
            }
        }
        Optional<VariableProxy> fromVariables = variableProxy(uri);
        if (fromVariables.isPresent()) {
            return Optional.of(fromVariables.get().toAetherProxy());
        }
        return jvmProxy(uri);
    }

    // ---- environment variables (libcurl rules) ----

    private Optional<VariableProxy> variableProxy(URI uri) {
        String host = uri.getHost();
        if (host == null || matchesNoProxy(host, firstSet("no_proxy", "NO_PROXY"))) {
            return Optional.empty();
        }
        for (String name : List.of("https_proxy", "HTTPS_PROXY", "all_proxy", "ALL_PROXY")) {
            String value = variables.get(name);
            if (value != null && !value.isBlank()) {
                return Optional.of(parse(value.trim(), name));
            }
        }
        return Optional.empty();
    }

    private String firstSet(String lower, String upper) {
        String value = variables.get(lower);
        return value != null ? value : variables.get(upper);
    }

    static boolean matchesNoProxy(String host, String noProxy) {
        if (noProxy == null || noProxy.isBlank()) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String raw : noProxy.split(",")) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.equals("*")) {
                return true;
            }
            if (entry.startsWith(".")) {
                entry = entry.substring(1);
            }
            if (!entry.isEmpty() && (lowerHost.equals(entry) || lowerHost.endsWith("." + entry))) {
                return true;
            }
        }
        return false;
    }

    static VariableProxy parse(String value, String variable) {
        String scheme = "http";
        String rest = value;
        int schemeEnd = value.indexOf("://");
        if (schemeEnd >= 0) {
            scheme = value.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
            rest = value.substring(schemeEnd + 3);
        }
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }

        String user = null;
        String password = null;
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            String userInfo = rest.substring(0, at);
            rest = rest.substring(at + 1);
            int colon = userInfo.indexOf(':');
            user = percentDecode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            password = colon >= 0 ? percentDecode(userInfo.substring(colon + 1)) : "";
        }

        String host = rest;
        int port = LIBCURL_DEFAULT_PROXY_PORT;
        int portSeparator = rest.lastIndexOf(':');
        if (portSeparator >= 0 && !rest.endsWith("]")) {
            host = rest.substring(0, portSeparator);
            port = Integer.parseInt(rest.substring(portSeparator + 1));
        }

        if (scheme.startsWith("socks")) {
            throw new LookupNotPossibleException("SOCKS proxy " + scheme + "://" + host + ":" + port + " (from "
                    + variable + ") is not supported for dependency lookups. Configure the JVM instead, e.g. "
                    + "MAVEN_OPTS=\"-DsocksProxyHost=" + host + " -DsocksProxyPort=" + port + "\".");
        }
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new LookupNotPossibleException("Unsupported proxy scheme " + scheme + "://" + host + ":" + port
                    + " (from " + variable + ").");
        }
        return new VariableProxy(scheme, host, port, user, password);
    }

    private static String percentDecode(String value) {
        // Userinfo is percent-encoded as in libcurl; '+' stays literal.
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    record VariableProxy(String scheme, String host, int port, String user, String password) {

        Proxy toAetherProxy() {
            Authentication auth = user == null
                    ? null
                    : new AuthenticationBuilder().addUsername(user).addPassword(password).build();
            return new Proxy(scheme.equals("https") ? Proxy.TYPE_HTTPS : Proxy.TYPE_HTTP, host, port, auth);
        }
    }

    // ---- JVM system properties ----

    private Optional<Proxy> jvmProxy(URI uri) {
        String host = systemProperties.getProperty("https.proxyHost");
        if (host == null || host.isBlank()
                || matchesNonProxyHosts(uri.getHost(), systemProperties.getProperty("http.nonProxyHosts"))) {
            return Optional.empty();
        }
        int port = JVM_DEFAULT_HTTPS_PROXY_PORT;
        String portValue = systemProperties.getProperty("https.proxyPort");
        if (portValue != null && !portValue.isBlank()) {
            port = Integer.parseInt(portValue.trim());
        }
        return Optional.of(new Proxy(Proxy.TYPE_HTTP, host.trim(), port));
    }

    static boolean matchesNonProxyHosts(String host, String nonProxyHosts) {
        if (host == null || nonProxyHosts == null || nonProxyHosts.isBlank()) {
            return false;
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String entry : nonProxyHosts.split("\\|")) {
            String glob = entry.trim();
            if (!glob.isEmpty()) {
                patterns.add(Pattern.compile(Pattern.quote(glob).replace("*", "\\E.*\\Q"), Pattern.CASE_INSENSITIVE));
            }
        }
        return patterns.stream().anyMatch(pattern -> pattern.matcher(host).matches());
    }
}
