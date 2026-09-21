package com.sebas3261.ex.infrastructure.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Writes the Maven Wrapper exactly as {@code maven-wrapper-plugin:3.3.4:wrapper} would when the
 * C++ tool ran it (design D11), without network access or an external {@code mvn}: the official
 * scripts are bundled in the plugin jar, and the properties pin the running Maven version.
 */
public final class WrapperGenerator {

    public static final String WRAPPER_VERSION = "3.3.4";
    public static final String DEFAULT_REPOSITORY_URL = "https://repo.maven.apache.org/maven2";
    public static final String FAILURE_MESSAGE = "Failed to generate Maven Wrapper.";

    private static final String SCRIPT_RESOURCE_DIR = "/com/sebas3261/ex/wrapper/";

    /** A settings mirror, reduced to what the repository URL rule needs. */
    public record Mirror(String mirrorOf, String url) {
    }

    private final String mavenVersion;
    private final String repositoryUrl;
    private final String lineSeparator;

    public WrapperGenerator(String mavenVersion, String repositoryUrl, String lineSeparator) {
        this.mavenVersion = mavenVersion;
        this.repositoryUrl = repositoryUrl;
        this.lineSeparator = lineSeparator;
    }

    /**
     * The download repository, following {@code WrapperMojo.determineRepoUrl} in maven-wrapper-plugin
     * 3.3.4: {@code MVNW_REPOURL} if longer than 4 characters (trimmed, one trailing slash removed),
     * else the first mirror whose {@code mirrorOf} is exactly {@code *}, else Maven Central.
     */
    public static String repositoryUrl(String mvnwRepoUrl, List<Mirror> mirrors) {
        if (mvnwRepoUrl != null && !mvnwRepoUrl.trim().isEmpty() && mvnwRepoUrl.length() > 4) {
            String url = mvnwRepoUrl.trim();
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        for (Mirror mirror : mirrors) {
            if ("*".equals(mirror.mirrorOf())) {
                return mirror.url();
            }
        }
        return DEFAULT_REPOSITORY_URL;
    }

    public String properties() {
        return "wrapperVersion=" + WRAPPER_VERSION + lineSeparator
                + "distributionType=only-script" + lineSeparator
                + "distributionUrl=" + repositoryUrl + "/org/apache/maven/apache-maven/" + mavenVersion
                + "/apache-maven-" + mavenVersion + "-bin.zip" + lineSeparator;
    }

    public void generate(Path projectDirectory) {
        try {
            Path wrapperDir = Files.createDirectories(projectDirectory.resolve(".mvn").resolve("wrapper"));
            Files.writeString(wrapperDir.resolve("maven-wrapper.properties"), properties(), StandardCharsets.UTF_8);
            Path mvnw = projectDirectory.resolve("mvnw");
            copyScript("mvnw", mvnw);
            copyScript("mvnw.cmd", projectDirectory.resolve("mvnw.cmd"));
            addExecutePermissions(mvnw);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(FAILURE_MESSAGE, e);
        }
    }

    private static void copyScript(String name, Path target) throws IOException {
        try (InputStream in = WrapperGenerator.class.getResourceAsStream(SCRIPT_RESOURCE_DIR + name)) {
            if (in == null) {
                throw new IOException("Bundled wrapper script missing: " + name);
            }
            Files.write(target, in.readAllBytes());
        }
    }

    private static void addExecutePermissions(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix == null) {
            return; // e.g. Windows: no POSIX permissions to set
        }
        Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(file));
        permissions.addAll(Set.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_EXECUTE));
        posix.setPermissions(permissions);
    }
}
