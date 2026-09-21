package com.sebas3261.ex.infrastructure.project;

import com.sebas3261.ex.application.ports.ProjectDependencyRepository;
import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import com.sebas3261.ex.infrastructure.xml.XmlText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and edits the project-level {@code <dependencies>} of a POM as text (design D9).
 *
 * <p>The file is read and written as ISO-8859-1, which maps every byte to one character and back,
 * so any encoding and BOM round-trips exactly; all markers and inserted text are ASCII. Inserted
 * lines use the file's own line separator. Only the {@code <dependencies>} element that is a direct
 * child of {@code <project>} is read or edited; {@code <dependencyManagement>}, plugin and profile
 * dependencies are never touched, and commented-out markup is ignored.
 */
public final class PomProjectDependencyRepository implements ProjectDependencyRepository {

    private static final Pattern GROUP_ID = Pattern.compile("<groupId>\\s*([^<]+?)\\s*</groupId>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>");
    private static final String CLOSE_TAG = "</dependencies>";

    private final Path pom;

    public PomProjectDependencyRepository(Path pom) {
        this.pom = pom;
    }

    @Override
    public Set<String> existingDependencyKeys() {
        XmlText xml = new XmlText(read());
        Set<String> keys = new LinkedHashSet<>();
        for (XmlText.Element dependencies : xml.find(List.of("project"), "dependencies")) {
            for (XmlText.Element dependency : xml.children(dependencies, "dependency")) {
                String body = xml.content(dependency);
                Matcher groupId = GROUP_ID.matcher(body);
                Matcher artifactId = ARTIFACT_ID.matcher(body);
                if (groupId.find() && artifactId.find()) {
                    keys.add(groupId.group(1).trim() + ":" + artifactId.group(1).trim());
                }
            }
        }
        return keys;
    }

    @Override
    public void addDependencies(List<ResolvedDependency> dependencies) {
        if (dependencies.isEmpty()) {
            return;
        }
        write(insert(read(), dependencies));
    }

    static String insert(String pomText, List<ResolvedDependency> dependencies) {
        XmlText xml = new XmlText(pomText);
        String eol = xml.lineSeparator();
        String block = render(dependencies, eol);

        List<XmlText.Element> projectDependencies = xml.find(List.of("project"), "dependencies");
        if (!projectDependencies.isEmpty()) {
            XmlText.Element target = projectDependencies.get(projectDependencies.size() - 1);

            if (target.selfClosing()) {
                // Case 3: <dependencies/>
                return pomText.substring(0, target.openStart())
                        + "<dependencies>" + eol + block + "    " + CLOSE_TAG
                        + pomText.substring(target.openEnd());
            }

            int lineStart = lineStart(pomText, target.closeStart());
            if (pomText.substring(lineStart, target.closeStart()).isBlank()) {
                // Case 1: closing tag at the start of its line
                return pomText.substring(0, lineStart) + block + pomText.substring(lineStart);
            }
            // Case 2: closing tag elsewhere on a line
            return pomText.substring(0, target.closeStart()) + eol + block + pomText.substring(target.closeStart());
        }

        // Case 4: no project-level <dependencies>
        int projectClose = xml.masked().lastIndexOf("</project>");
        if (projectClose < 0) {
            throw new IllegalStateException("Invalid pom.xml: missing </project>.");
        }
        String wrapped = "    <dependencies>" + eol + block + "    " + CLOSE_TAG + eol + eol;
        return pomText.substring(0, projectClose) + wrapped + pomText.substring(projectClose);
    }

    static String render(List<ResolvedDependency> dependencies, String eol) {
        StringBuilder xml = new StringBuilder();
        for (ResolvedDependency dependency : dependencies) {
            xml.append("        <dependency>").append(eol)
                    .append("            <groupId>").append(dependency.groupId()).append("</groupId>").append(eol)
                    .append("            <artifactId>").append(dependency.artifactId()).append("</artifactId>")
                    .append(eol)
                    .append("            <version>").append(dependency.version()).append("</version>").append(eol);
            if (!dependency.scope().isEmpty()) {
                xml.append("            <scope>").append(dependency.scope()).append("</scope>").append(eol);
            }
            xml.append("        </dependency>").append(eol);
        }
        return xml.toString();
    }

    private static int lineStart(String text, int index) {
        int lf = text.lastIndexOf('\n', index - 1);
        return lf < 0 ? 0 : lf + 1;
    }

    private String read() {
        try {
            return new String(Files.readAllBytes(pom), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + pom, e);
        }
    }

    private void write(String text) {
        try {
            Files.write(pom, text.getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + pom, e);
        }
    }
}
