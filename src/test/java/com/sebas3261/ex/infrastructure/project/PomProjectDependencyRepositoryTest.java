package com.sebas3261.ex.infrastructure.project;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PomProjectDependencyRepositoryTest {

    private static final ResolvedDependency LOMBOK = new ResolvedDependency("org.projectlombok", "lombok", "1.18.32");
    private static final ResolvedDependency JUPITER =
            new ResolvedDependency("org.junit.jupiter", "junit-jupiter", "5.10.0", "test");

    @TempDir
    Path dir;

    private static byte[] golden(String path) throws IOException {
        try (InputStream in = PomProjectDependencyRepositoryTest.class.getResourceAsStream("/golden/add/" + path)) {
            return in.readAllBytes();
        }
    }

    private Path pom(byte[] content) throws IOException {
        Path pom = dir.resolve("pom.xml");
        Files.write(pom, content);
        return pom;
    }

    private Path pom(String content) throws IOException {
        return pom(content.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Replays the recorded C++ sequence: add lombok, then add junit-jupiter with scope test. */
    private static void addLikeTheRecording(PomProjectDependencyRepository repository) {
        for (ResolvedDependency dependency : List.of(LOMBOK, JUPITER)) {
            if (!repository.existingDependencyKeys().contains(dependency.key())) {
                repository.addDependencies(List.of(dependency));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fresh", "existing-deps", "already-present", "dependency-management-only",
            "crlf", "iso-8859-1", "utf8-bom"})
    void goldenFixturesByteForByte(String fixture) throws IOException {
        Path pom = pom(golden(fixture + "/input.pom.xml"));

        addLikeTheRecording(new PomProjectDependencyRepository(pom));

        assertArrayEquals(golden(fixture + "/expected.pom.xml"), Files.readAllBytes(pom), fixture);
    }

    @Test
    void existingKeysComeFromProjectLevelDependenciesOnly() throws IOException {
        Path pom = pom("""
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
                        </dependencies>
                    </dependencyManagement>
                    <build><plugins><plugin>
                        <dependencies>
                            <dependency><groupId>p.g</groupId><artifactId>plugin-dep</artifactId></dependency>
                        </dependencies>
                    </plugin></plugins></build>
                    <profiles><profile>
                        <dependencies>
                            <dependency><groupId>prof.g</groupId><artifactId>profile-dep</artifactId></dependency>
                        </dependencies>
                    </profile></profiles>
                    <dependencies>
                        <!-- <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></dependency> -->
                        <dependency>
                            <groupId> com.example </groupId>
                            <artifactId>real</artifactId>
                            <exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);

        assertEquals(Set.of("com.example:real"), new PomProjectDependencyRepository(pom).existingDependencyKeys());
    }

    @Test
    void managedOnlyDependencyIsAddedForReal() throws IOException {
        byte[] input = golden("dependency-management-only/input.pom.xml");
        PomProjectDependencyRepository repository = new PomProjectDependencyRepository(pom(input));

        assertFalse(repository.existingDependencyKeys().contains("org.slf4j:slf4j-api"));
    }

    @Test
    void projectLevelDependenciesAfterDependencyManagement() throws IOException {
        Path pom = pom("""
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency><groupId>m</groupId><artifactId>managed</artifactId></dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                    </dependencies>
                </project>
                """);
        new PomProjectDependencyRepository(pom).addDependencies(List.of(LOMBOK));

        String text = Files.readString(pom);
        int managementEnd = text.indexOf("</dependencyManagement>");
        assertTrue(text.indexOf("<artifactId>lombok</artifactId>") > managementEnd);
        assertTrue(text.startsWith("""
                <project>
                    <dependencyManagement>
                        <dependencies>
                            <dependency><groupId>m</groupId><artifactId>managed</artifactId></dependency>
                        </dependencies>
                    </dependencyManagement>
                """), "dependencyManagement is byte-identical");
    }

    @Test
    void inlineClosingTag() throws IOException {
        Path pom = pom("<project>\n    <dependencies></dependencies>\n</project>\n");
        new PomProjectDependencyRepository(pom).addDependencies(List.of(LOMBOK));

        assertEquals("""
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.32</version>
                        </dependency>
                </dependencies>
                </project>
                """, Files.readString(pom));
    }

    @Test
    void selfClosingDependencies() throws IOException {
        Path pom = pom("<project>\n    <dependencies/>\n</project>\n");
        new PomProjectDependencyRepository(pom).addDependencies(List.of(JUPITER));

        assertEquals("""
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """, Files.readString(pom));
    }

    @Test
    void commentedOutDuplicateDoesNotCountAndCommentedTagsAreNotTargets() throws IOException {
        Path pom = pom("""
                <project>
                    <!-- old block:
                    <dependencies>
                    </dependencies>
                    -->
                </project>
                """);
        PomProjectDependencyRepository repository = new PomProjectDependencyRepository(pom);
        assertTrue(repository.existingDependencyKeys().isEmpty());

        repository.addDependencies(List.of(LOMBOK));

        String text = Files.readString(pom);
        assertTrue(text.contains("    -->\n    <dependencies>\n        <dependency>"), text);
    }

    @Test
    void differentIndentationStillInsertsAtLineStart() throws IOException {
        Path pom = pom("<project>\n  <dependencies>\n  </dependencies>\n</project>\n");
        new PomProjectDependencyRepository(pom).addDependencies(List.of(LOMBOK));

        assertTrue(Files.readString(pom).endsWith("        </dependency>\n  </dependencies>\n</project>\n"));
    }

    @Test
    void missingProjectCloseTag() throws IOException {
        Path pom = pom("<project>\n    <modelVersion>4.0.0</modelVersion>\n");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new PomProjectDependencyRepository(pom).addDependencies(List.of(LOMBOK)));
        assertEquals("Invalid pom.xml: missing </project>.", error.getMessage());
    }

    @Test
    void nothingToAddLeavesTheFileUntouched() throws IOException {
        Path pom = pom("<project></project>");
        long modified = Files.getLastModifiedTime(pom).toMillis();
        new PomProjectDependencyRepository(pom).addDependencies(List.of());

        assertEquals(modified, Files.getLastModifiedTime(pom).toMillis());
    }

    // ---- locating the POM ----

    @Test
    void explicitPomWins() throws IOException {
        Path explicit = pom("<project/>");
        assertEquals(explicit, PomLocator.locate(explicit, dir.resolve("elsewhere")));
    }

    @Test
    void nearestPomAboveTheExecutionRoot() throws IOException {
        Path pom = pom("<project/>");
        Path nested = Files.createDirectories(dir.resolve("src/main/java"));

        assertEquals(pom, PomLocator.locate(null, nested));
    }

    @Test
    void noPomAnywhere() throws IOException {
        Path empty = Files.createDirectories(dir.resolve("a/b"));
        // The temp dir's ancestors could in theory contain a pom.xml; skip if so.
        if (Files.exists(dir.getParent().resolve("pom.xml"))) {
            return;
        }
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> PomLocator.locate(null, empty));
        assertEquals("pom.xml not found. Run this command inside a Maven project.", error.getMessage());
    }
}
