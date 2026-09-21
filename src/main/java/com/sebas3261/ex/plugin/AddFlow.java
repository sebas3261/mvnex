package com.sebas3261.ex.plugin;

import com.sebas3261.ex.application.add.AddUseCase;
import com.sebas3261.ex.application.errors.MultipleDependencyMatchesException;
import com.sebas3261.ex.application.ports.Interaction;
import com.sebas3261.ex.application.ports.MavenProjectValidator;
import com.sebas3261.ex.application.ports.ReportSink;
import com.sebas3261.ex.domain.dependency.DependencyRequest;
import com.sebas3261.ex.domain.dependency.ResolvedDependency;
import java.util.ArrayList;
import java.util.List;

/** The {@code ex:add} conversation: collect missing values, resolve, disambiguate, report. */
public final class AddFlow {

    static final String SEARCH_AGAIN = "Search again...";
    static final int VISIBLE_CANDIDATES = 3;
    static final String LATEST = "latest";
    static final String NO_SCOPE = "none";
    static final List<String> SCOPE_CHOICES =
            List.of(NO_SCOPE, "compile", "provided", "runtime", "test", "system", "import");

    private final Interaction interaction;
    private final ReportSink report;
    private final AddUseCase useCase;

    public AddFlow(Interaction interaction, ReportSink report, AddUseCase useCase) {
        this.interaction = interaction;
        this.report = report;
        this.useCase = useCase;
    }

    /**
     * Turns the goal parameters into requests. Interactive runs first ask for whatever is missing:
     * the dependency list, then, for a single dependency, its version and scope. Batch runs parse
     * the parameters as given, so a missing list fails with the usage message.
     *
     * @param dependencies {@code ex.deps}, or null
     * @param version      {@code ex.version}, or null/blank
     * @param scope        {@code ex.scope}, or null/blank
     */
    public List<DependencyRequest> requests(List<String> dependencies, String version, String scope) {
        if (!interaction.isInteractive()) {
            return DependencyArgumentsParser.parse(dependencies, version, scope);
        }
        if (dependencies == null || dependencies.isEmpty()) {
            dependencies = askDependencies();
        }
        // Reject a malformed expression before asking anything about it.
        DependencyArgumentsParser.parse(dependencies, null, null);
        if (dependencies.size() == 1) {
            if (isBlank(version) && !DependencyArgumentsParser.hasInlineVersion(dependencies.get(0))) {
                String answer = interaction.text("Version", LATEST).trim();
                version = answer.isEmpty() || answer.equalsIgnoreCase(LATEST) ? null : answer;
            }
            if (isBlank(scope)) {
                String answer = interaction.select("Scope", SCOPE_CHOICES, NO_SCOPE);
                scope = answer.equals(NO_SCOPE) ? null : answer;
            }
        }
        return DependencyArgumentsParser.parse(dependencies, version, scope);
    }

    private List<String> askDependencies() {
        while (true) {
            String answer = interaction.text("Dependencies", "").trim();
            if (!answer.isEmpty()) {
                return List.of(answer.split(",", -1));
            }
            report.warn("At least one dependency is required.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public void run(List<DependencyRequest> requests) {
        List<DependencyRequest> pending = new ArrayList<>(requests);
        AddUseCase.Result result;
        while (true) {
            try {
                result = useCase.execute(pending);
                break;
            } catch (MultipleDependencyMatchesException ambiguous) {
                int index = ambiguous.dependencyIndex();
                if (index < 0 || index >= pending.size()) {
                    throw ambiguous;
                }
                if (!interaction.isInteractive()) {
                    throw new IllegalStateException(batchAmbiguityMessage(ambiguous));
                }
                pending.set(index, choose(ambiguous, pending.get(index).scope()));
            }
        }
        report(result);
    }

    private DependencyRequest choose(MultipleDependencyMatchesException ambiguous, String scope) {
        List<ResolvedDependency> visible = visible(ambiguous);
        List<String> options = new ArrayList<>();
        for (ResolvedDependency candidate : visible) {
            options.add(candidate.coordinates());
        }
        options.add(SEARCH_AGAIN);

        String selected = interaction.select("Select dependency for " + ambiguous.query(), options, options.get(0));
        if (selected.equals(SEARCH_AGAIN)) {
            String term = interaction.text("Search dependency", ambiguous.query());
            return new DependencyRequest.SearchTerm(term.isEmpty() ? ambiguous.query() : term, scope);
        }
        ResolvedDependency chosen = visible.get(options.indexOf(selected));
        return new DependencyRequest.CoordinateWithVersion(chosen.groupId(), chosen.artifactId(), chosen.version(),
                scope);
    }

    static String batchAmbiguityMessage(MultipleDependencyMatchesException ambiguous) {
        StringBuilder message = new StringBuilder(ambiguous.getMessage());
        for (ResolvedDependency candidate : visible(ambiguous)) {
            message.append(System.lineSeparator()).append("  ").append(candidate.coordinates());
        }
        ResolvedDependency first = ambiguous.candidates().get(0);
        message.append(System.lineSeparator())
                .append("Specify an exact groupId:artifactId, for example -Dex.deps=")
                .append(first.groupId()).append(':').append(first.artifactId());
        return message.toString();
    }

    private static List<ResolvedDependency> visible(MultipleDependencyMatchesException ambiguous) {
        List<ResolvedDependency> candidates = ambiguous.candidates();
        return candidates.subList(0, Math.min(VISIBLE_CANDIDATES, candidates.size()));
    }

    private void report(AddUseCase.Result result) {
        if (!result.added().isEmpty()) {
            report.info("Dependencies added");
            for (ResolvedDependency dependency : result.added()) {
                report.info("  ✓ " + format(dependency));
            }
        }
        if (!result.skipped().isEmpty()) {
            report.info("Dependencies skipped");
            for (ResolvedDependency dependency : result.skipped()) {
                report.info("  - " + format(dependency) + " already exists or was duplicated in this command");
            }
        }
        if (result.added().isEmpty() && result.skipped().isEmpty()) {
            report.warn("No dependencies changed.");
        }
        result.validation().ifPresent(status -> {
            if (status == MavenProjectValidator.Status.PASSED) {
                report.info("Maven validate passed");
            } else {
                report.error("Maven validate failed. Check the project output with mvn validate.");
            }
        });
    }

    static String format(ResolvedDependency dependency) {
        return dependency.coordinates() + (dependency.scope().isEmpty() ? "" : " [" + dependency.scope() + "]");
    }
}
