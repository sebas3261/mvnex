package com.sebas3261.ex.plugin;

import com.sebas3261.ex.application.init.InitUseCase;
import com.sebas3261.ex.application.ports.Interaction;
import com.sebas3261.ex.application.ports.ReportSink;
import com.sebas3261.ex.domain.project.ProjectConfig;
import com.sebas3261.ex.domain.project.ProjectNaming;
import com.sebas3261.ex.domain.project.ProjectValidator;
import java.util.List;

/** The {@code ex:init} conversation: validate, collect missing values, confirm the wrapper, create. */
public final class InitFlow {

    static final String DEFAULT_NAME = "my-project";
    static final String DEFAULT_GROUP_ID = "com.example";
    static final String DEFAULT_JAVA = "21";
    static final List<String> JAVA_CHOICES = List.of("8", "11", "17", "21", "25");

    /** Goal parameters as given; blank strings count as absent, {@code wrapper} is null when unset. */
    public record Parameters(String name, String groupId, String packageName, String java, Boolean wrapper) {
    }

    private final Interaction interaction;
    private final ReportSink report;
    private final InitUseCase useCase;

    public InitFlow(Interaction interaction, ReportSink report, InitUseCase useCase) {
        this.interaction = interaction;
        this.report = report;
        this.useCase = useCase;
    }

    public void run(Parameters parameters) {
        String name = blankToNull(parameters.name());
        String groupId = blankToNull(parameters.groupId());
        String packageName = blankToNull(parameters.packageName());
        String java = blankToNull(parameters.java());
        boolean nameGiven = name != null;
        boolean groupGiven = groupId != null;
        boolean javaGiven = java != null;

        // Early validation of provided values, before any prompt; Java 21 is assumed until chosen.
        String assumedJava = javaGiven ? java : DEFAULT_JAVA;
        if (javaGiven) {
            ProjectValidator.validateJavaVersion(java);
        }
        if (nameGiven) {
            ProjectValidator.validateProjectName(name, assumedJava);
        }
        if (groupGiven) {
            ProjectValidator.validateGroupId(groupId, assumedJava);
        }
        if (packageName != null) {
            ProjectValidator.validatePackageName(packageName, assumedJava);
        }

        if (interaction.isInteractive()) {
            if (!nameGiven) {
                name = interaction.text("Project name", DEFAULT_NAME);
            }
            if (!groupGiven) {
                groupId = interaction.text("Group ID", DEFAULT_GROUP_ID);
            }
            if (packageName == null) {
                // Enter keeps the derived package; validated with the rest of the configuration below.
                packageName = interaction.text("Package", derivedPackage(groupId, name));
            }
            if (!javaGiven) {
                java = interaction.select("Java version", JAVA_CHOICES, DEFAULT_JAVA);
            }
        } else {
            if (!nameGiven) {
                throw new IllegalArgumentException("Missing project name. Provide -Dex.name=<name>.");
            }
            if (!groupGiven) {
                groupId = DEFAULT_GROUP_ID;
            }
            if (!javaGiven) {
                java = DEFAULT_JAVA;
            }
        }

        if (packageName == null) {
            packageName = derivedPackage(groupId, name);
        }

        boolean skipWrapper = Boolean.FALSE.equals(parameters.wrapper());
        boolean askWrapper = interaction.isInteractive() && !skipWrapper && !(nameGiven && groupGiven && javaGiven);
        if (askWrapper) {
            skipWrapper = interaction.select("Maven Wrapper", List.of("Yes", "No"), "Yes").equals("No");
        }

        ProjectConfig config = new ProjectConfig(name, groupId, packageName, java);
        report.info("Creating Maven project");
        report.info(row("Project", config.name()));
        report.info(row("Group", config.groupId()));
        report.info(row("Package", config.packageName()));
        report.info(row("Java", config.javaVersion()));
        report.info(row("Wrapper", skipWrapper ? "None" : "Maven Wrapper"));

        useCase.execute(config, skipWrapper);

        report.info("Project created successfully");
        report.info("  cd " + config.name());
        report.info(skipWrapper ? "  mvn package" : "  ./mvnw package");
    }

    /** {@code <groupId>.<name without hyphens>}. */
    static String derivedPackage(String groupId, String name) {
        return groupId + "." + ProjectNaming.toPackageName(name);
    }

    /** {@code "  " + label padded to 10 + value}, as the C++ summary printed it. */
    static String row(String label, String value) {
        return "  " + String.format("%-10s", label) + value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
