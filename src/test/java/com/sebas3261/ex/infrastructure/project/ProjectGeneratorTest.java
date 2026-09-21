package com.sebas3261.ex.infrastructure.project;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sebas3261.ex.domain.project.ProjectConfig;
import com.sebas3261.ex.infrastructure.wrapper.WrapperGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectGeneratorTest {

    @TempDir
    Path root;

    private static byte[] golden(String path) throws IOException {
        try (InputStream in = ProjectGeneratorTest.class.getResourceAsStream("/golden/" + path)) {
            if (in == null) {
                throw new IOException("missing golden " + path);
            }
            return in.readAllBytes();
        }
    }

    private static String goldenText(String path) throws IOException {
        return new String(golden(path), StandardCharsets.UTF_8);
    }

    private static WrapperGenerator wrapper(String eol) {
        return new WrapperGenerator("3.9.16", WrapperGenerator.DEFAULT_REPOSITORY_URL, eol);
    }

    private List<String> tree(Path project) throws IOException {
        try (Stream<Path> paths = Files.walk(project)) {
            return paths.filter(p -> !p.equals(project))
                    .map(p -> project.relativize(p).toString().replace('\\', '/') + (Files.isDirectory(p) ? "/" : ""))
                    .sorted().toList();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "my-app, com.example, com.example.myapp, 21, false",
            "my-cool-app, org.acme, org.acme.custom, 8, false",
            "svc, com.example, com.example.svc, 25, true"})
    void matchesTheCppGoldens(String name, String groupId, String packageName, String java, boolean skipWrapper)
            throws IOException {
        new ProjectGenerator(root, wrapper("\n"), "\n")
                .create(new ProjectConfig(name, groupId, packageName, java), skipWrapper);
        Path project = root.resolve(name);

        assertEquals(goldenText("init/" + name + "/tree.txt").lines().sorted().toList(), tree(project));
        assertArrayEquals(golden("init/" + name + "/pom.xml"), Files.readAllBytes(project.resolve("pom.xml")));
        Path main = project.resolve("src/main/java").resolve(packageName.replace('.', '/')).resolve("Main.java");
        assertArrayEquals(golden("init/" + name + "/Main.java"), Files.readAllBytes(main));
    }

    @Test
    void windowsLineSeparators() throws IOException {
        new ProjectGenerator(root, wrapper("\r\n"), "\r\n")
                .create(new ProjectConfig("my-app", "com.example", "com.example.myapp", "21"), false);
        Path project = root.resolve("my-app");

        assertEquals(goldenText("init/my-app/pom.xml").replace("\n", "\r\n"),
                Files.readString(project.resolve("pom.xml")));
        assertEquals(goldenText("wrapper/maven-wrapper.properties.plain").replace("\n", "\r\n"),
                Files.readString(project.resolve(".mvn/wrapper/maven-wrapper.properties")));
        assertArrayEquals(golden("wrapper/mvnw"), Files.readAllBytes(project.resolve("mvnw")), "mvnw stays LF");
        assertArrayEquals(golden("wrapper/mvnw.cmd"), Files.readAllBytes(project.resolve("mvnw.cmd")),
                "mvnw.cmd stays CRLF");
    }

    @Test
    void wrapperScriptsAreTheOfficialBytesAndMvnwIsExecutable() throws IOException {
        new ProjectGenerator(root, wrapper("\n"), "\n")
                .create(new ProjectConfig("my-app", "com.example", "com.example.myapp", "21"), false);
        Path project = root.resolve("my-app");

        assertArrayEquals(golden("wrapper/mvnw"), Files.readAllBytes(project.resolve("mvnw")));
        assertArrayEquals(golden("wrapper/mvnw.cmd"), Files.readAllBytes(project.resolve("mvnw.cmd")));
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        assertTrue(Files.getPosixFilePermissions(project.resolve("mvnw")).containsAll(Set.of(
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_EXECUTE)));
    }

    @Test
    void existingDirectoryIsLeftUntouched() throws IOException {
        Path existing = Files.createDirectories(root.resolve("my-app"));
        Files.writeString(existing.resolve("keep.txt"), "mine");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new ProjectGenerator(root, wrapper("\n"), "\n")
                        .create(new ProjectConfig("my-app", "com.example", "com.example.myapp", "21"), false));

        assertEquals("Project directory already exists: my-app", error.getMessage());
        assertEquals(List.of("keep.txt"), tree(existing));
    }

    @Test
    void existingFileWithTheNameAlsoBlocks() throws IOException {
        Files.writeString(root.resolve("svc"), "a file");
        assertThrows(IllegalArgumentException.class, () -> new ProjectGenerator(root, wrapper("\n"), "\n")
                .create(new ProjectConfig("svc", "com.example", "com.example.svc", "21"), true));
    }

    // ---- wrapper properties (recorded from maven-wrapper-plugin via the C++ tool) ----

    @ParameterizedTest
    @CsvSource({
            "plain, '', ''",
            "mirror, '', https://repo1.maven.org/maven2",
            "repourl, https://repo.example.org/maven2/, https://repo1.maven.org/maven2"})
    void propertiesMatchTheRecordedEnvironments(String environment, String mvnwRepoUrl, String starMirror)
            throws IOException {
        List<WrapperGenerator.Mirror> mirrors = starMirror.isEmpty()
                ? List.of()
                : List.of(new WrapperGenerator.Mirror("*", starMirror));
        String url = WrapperGenerator.repositoryUrl(mvnwRepoUrl.isEmpty() ? null : mvnwRepoUrl, mirrors);

        assertEquals(goldenText("wrapper/maven-wrapper.properties." + environment),
                new WrapperGenerator("3.9.16", url, "\n").properties());
    }

    @Test
    void templateMatchesAnyMavenVersion() throws IOException {
        String expected = goldenText("wrapper/maven-wrapper.properties.template")
                .replace("${repoUrl}", "https://nexus.acme.corp/repository/maven-public")
                .replace("${mavenVersion}", "3.9.9");
        assertEquals(expected, new WrapperGenerator("3.9.9", "https://nexus.acme.corp/repository/maven-public", "\n")
                .properties());
    }

    @Test
    void repositoryUrlRules() {
        List<WrapperGenerator.Mirror> centralOnly = List.of(new WrapperGenerator.Mirror("central", "https://c"));
        assertEquals(WrapperGenerator.DEFAULT_REPOSITORY_URL, WrapperGenerator.repositoryUrl(null, centralOnly),
                "a mirrorOf=central mirror is ignored");
        assertEquals("https://first", WrapperGenerator.repositoryUrl(null, List.of(
                new WrapperGenerator.Mirror("central", "https://c"),
                new WrapperGenerator.Mirror("*", "https://first"),
                new WrapperGenerator.Mirror("*", "https://second"))));
        assertEquals(WrapperGenerator.DEFAULT_REPOSITORY_URL, WrapperGenerator.repositoryUrl("abc", List.of()),
                "MVNW_REPOURL must be longer than 4 characters");
        assertEquals("https://r", WrapperGenerator.repositoryUrl(" https://r/ ", List.of()));
    }

    @Test
    void wrapperFailureMessage() throws IOException {
        Path blocker = Files.writeString(root.resolve("not-a-dir"), "x");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> wrapper("\n").generate(blocker));
        assertEquals("Failed to generate Maven Wrapper.", error.getMessage());
        assertFalse(Files.isDirectory(blocker));
    }
}
