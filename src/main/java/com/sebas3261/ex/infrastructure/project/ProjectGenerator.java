package com.sebas3261.ex.infrastructure.project;

import com.sebas3261.ex.application.ports.ProjectCreator;
import com.sebas3261.ex.domain.project.ProjectConfig;
import com.sebas3261.ex.infrastructure.wrapper.WrapperGenerator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a new standard Maven project, byte-compatible with the C++ tool apart from line separators. */
public final class ProjectGenerator implements ProjectCreator {

    private final Path executionRoot;
    private final WrapperGenerator wrapper;
    private final String lineSeparator;

    public ProjectGenerator(Path executionRoot, WrapperGenerator wrapper, String lineSeparator) {
        this.executionRoot = executionRoot;
        this.wrapper = wrapper;
        this.lineSeparator = lineSeparator;
    }

    @Override
    public void create(ProjectConfig config, boolean skipWrapper) {
        Path project = executionRoot.resolve(config.name());
        if (Files.exists(project)) {
            throw new IllegalArgumentException("Project directory already exists: " + config.name());
        }

        Path packagePath = Path.of("", config.packageName().split("\\."));
        try {
            Path mainPackage = Files.createDirectories(project.resolve("src/main/java").resolve(packagePath));
            Files.createDirectories(project.resolve("src/test/java").resolve(packagePath));
            write(mainPackage.resolve("Main.java"), mainJava(config));
            write(project.resolve("pom.xml"), pomXml(config));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + project, e);
        }

        if (!skipWrapper) {
            wrapper.generate(project);
        }
    }

    private void write(Path file, String lfText) throws IOException {
        Files.writeString(file, lfText.replace("\n", lineSeparator), StandardCharsets.UTF_8);
    }

    static String mainJava(ProjectConfig config) {
        return "package " + config.packageName() + ";\n"
                + "\n"
                + "public class Main {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello from " + config.name() + "!\");\n"
                + "    }\n"
                + "}\n";
    }

    static String pomXml(ProjectConfig config) {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
                + "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "\n"
                + "    <groupId>" + config.groupId() + "</groupId>\n"
                + "    <artifactId>" + config.name() + "</artifactId>\n"
                + "    <version>1.0-SNAPSHOT</version>\n"
                + "\n"
                + "    <properties>\n"
                + "        <maven.compiler.release>" + config.javaVersion() + "</maven.compiler.release>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "    </properties>\n"
                + "</project>\n";
    }
}
