package com.sebas3261.ex.infrastructure.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class LookupUrlsTest {

    @Test
    void coordinateQueryEncoding() {
        assertEquals("g%3A%22org.x%22+AND+a%3A%22y%22", LookupUrls.encodeQueryValue("g:\"org.x\" AND a:\"y\""));
    }

    @Test
    void unreservedCharactersAreKept() {
        assertEquals("Az09-_.~", LookupUrls.encodeQueryValue("Az09-_.~"));
    }

    @Test
    void nonAsciiIsEncodedAsUtf8Bytes() {
        assertEquals("caf%C3%A9", LookupUrls.encodeQueryValue("café"));
    }

    @Test
    void pathSegmentsPercentEncodeSpaces() {
        assertEquals("a%20b%3Ac", LookupUrls.encodePathSegment("a b:c"));
    }

    @Test
    void solrSearchUrl() {
        assertEquals(URI.create("https://search.maven.org/solrsearch/select?q=a%3Alombok&rows=25&wt=json"),
                LookupUrls.solrSearch("https://search.maven.org/solrsearch/select", "a:lombok", 25, ""));
        assertEquals(URI.create("https://h/s?q=x&rows=1&wt=json&core=gav"),
                LookupUrls.solrSearch("https://h/s", "x", 1, "gav"));
    }

    @Test
    void depsDevPackageUrl() {
        assertEquals(URI.create("https://api.deps.dev/v3/systems/MAVEN/packages/org.projectlombok%3Alombok"),
                LookupUrls.depsDevPackage("https://api.deps.dev/v3/systems/MAVEN/packages/", "org.projectlombok",
                        "lombok"));
    }
}
