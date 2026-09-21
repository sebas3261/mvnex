package com.sebas3261.ex.plugin;

import com.sebas3261.ex.application.errors.OperationCancelledException;
import com.sebas3261.ex.application.ports.Interaction;
import com.sebas3261.ex.application.ports.ReportSink;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Replays answers for prompts. An empty answer selects the default; running out of answers
 * behaves like end of input (cancel). Every prompt and every output line is recorded.
 */
final class ScriptedInteraction implements Interaction, ReportSink {

    private final boolean interactive;
    private final Deque<String> answers = new ArrayDeque<>();
    final List<String> prompts = new ArrayList<>();
    final List<String> output = new ArrayList<>();

    ScriptedInteraction(boolean interactive, String... answers) {
        this.interactive = interactive;
        this.answers.addAll(List.of(answers));
    }

    static ScriptedInteraction batch() {
        return new ScriptedInteraction(false);
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }

    @Override
    public String text(String label, String defaultValue) {
        prompts.add(label + " [" + defaultValue + "]");
        String answer = next();
        return answer.isEmpty() ? defaultValue : answer;
    }

    @Override
    public String select(String label, List<String> options, String defaultValue) {
        prompts.add(label + " " + options + " [" + defaultValue + "]");
        String answer = next();
        if (answer.isEmpty()) {
            return defaultValue;
        }
        if (options.contains(answer)) {
            return answer;
        }
        // Like ConsoleInteraction: a 1-based number picks an option.
        return options.get(Integer.parseInt(answer) - 1);
    }

    private String next() {
        if (answers.isEmpty()) {
            throw new OperationCancelledException();
        }
        return answers.pop();
    }

    @Override
    public void info(String message) {
        output.add("INFO " + message);
    }

    @Override
    public void warn(String message) {
        output.add("WARN " + message);
    }

    @Override
    public void error(String message) {
        output.add("ERROR " + message);
    }
}
