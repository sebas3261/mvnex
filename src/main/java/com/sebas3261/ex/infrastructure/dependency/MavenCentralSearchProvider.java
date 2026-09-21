package com.sebas3261.ex.infrastructure.dependency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebas3261.ex.application.errors.DependencyNotFoundException;
import com.sebas3261.ex.application.errors.DependencyResolutionException;
import com.sebas3261.ex.application.ports.HttpClient;
import com.sebas3261.ex.infrastructure.transport.LookupUrls;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Solr-based search (Sonatype Central or search.maven.org). */
public final class MavenCentralSearchProvider implements SearchProvider {

    public static final String SONATYPE_CENTRAL_SEARCH = "https://central.sonatype.com/solrsearch/select";
    public static final String MAVEN_CENTRAL_SEARCH = "https://search.maven.org/solrsearch/select";

    private final HttpClient http;
    private final String searchUrl;
    private final RepositoryLookup repository;
    private final ObjectMapper json = new ObjectMapper();

    public MavenCentralSearchProvider(HttpClient http, String searchUrl, RepositoryLookup repository) {
        this.http = http;
        this.searchUrl = searchUrl;
        this.repository = repository;
    }

    @Override
    public List<Candidate> searchCandidates(String term) {
        List<JsonNode> docs = usableDocs("a:" + term);
        if (docs.isEmpty()) {
            docs = usableDocs(term);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (JsonNode doc : docs) {
            if (doc.path("a").asText().equals(term)) {
                candidates.add(new Candidate(doc.path("g").asText(), doc.path("a").asText(),
                        doc.path("latestVersion").asText()));
            }
        }
        if (candidates.isEmpty()) {
            throw new DependencyNotFoundException(term);
        }
        return candidates;
    }

    @Override
    public Candidate coordinate(String groupId, String artifactId) {
        for (JsonNode doc : usableDocs("g:\"" + groupId + "\" AND a:\"" + artifactId + "\"")) {
            if (doc.path("g").asText().equals(groupId) && doc.path("a").asText().equals(artifactId)) {
                return new Candidate(groupId, artifactId, doc.path("latestVersion").asText());
            }
        }
        throw new DependencyNotFoundException(groupId + ":" + artifactId);
    }

    @Override
    public void verifyVersion(String groupId, String artifactId, String version) {
        if (indexContains(groupId, artifactId, version)) {
            return;
        }
        if (!repository.pomExists(groupId, artifactId, version)) {
            throw new DependencyNotFoundException(groupId + ":" + artifactId + ":" + version);
        }
    }

    boolean indexContains(String groupId, String artifactId, String version) {
        String query = "g:\"" + groupId + "\" AND a:\"" + artifactId + "\" AND v:\"" + version + "\"";
        JsonNode response = query(query, 1, "gav");
        return response.path("response").path("numFound").asLong(0) > 0;
    }

    private List<JsonNode> usableDocs(String query) {
        List<JsonNode> docs = new ArrayList<>();
        for (JsonNode doc : query(query, 25, "").path("response").path("docs")) {
            if (!doc.path("g").asText().isEmpty() && !doc.path("a").asText().isEmpty()
                    && !doc.path("latestVersion").asText().isEmpty()) {
                docs.add(doc);
            }
        }
        return docs;
    }

    private JsonNode query(String query, int rows, String core) {
        HttpClient.HttpResponse response = http.get(LookupUrls.solrSearch(searchUrl, query, rows, core));
        if (!response.isSuccessful()) {
            throw new DependencyResolutionException("Maven Central request failed.");
        }
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new DependencyResolutionException("Maven Central request failed.", e);
        }
    }
}
