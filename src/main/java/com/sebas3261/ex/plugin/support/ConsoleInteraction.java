package com.sebas3261.ex.plugin.support;

import com.sebas3261.ex.application.errors.OperationCancelledException;
import com.sebas3261.ex.application.ports.Interaction;
import java.io.IOException;
import java.util.List;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;

/**
 * Prompts on Maven's console (design D5). Uses {@link InputHandler} directly rather than
 * {@code Prompter}: {@code DefaultPrompter} answers the default when input ends, while here end of
 * input cancels the operation and only an empty line selects the default.
 */
public final class ConsoleInteraction implements Interaction {

    private final InputHandler input;
    private final OutputHandler output;
    private final boolean interactive;

    public ConsoleInteraction(InputHandler input, OutputHandler output, boolean interactive) {
        this.input = input;
        this.output = output;
        this.interactive = interactive;
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }

    @Override
    public String text(String label, String defaultValue) {
        write(label + (defaultValue == null || defaultValue.isEmpty() ? "" : " (" + defaultValue + ")") + ": ");
        String line = readLine();
        return line.isEmpty() && defaultValue != null ? defaultValue : line;
    }

    @Override
    public String select(String label, List<String> options, String defaultValue) {
        int defaultIndex = Math.max(0, defaultValue == null ? 0 : options.indexOf(defaultValue));
        writeLine(label);
        for (int i = 0; i < options.size(); i++) {
            writeLine("  " + (i + 1) + ") " + options.get(i) + (i == defaultIndex ? " (default)" : ""));
        }
        while (true) {
            write("Choose 1-" + options.size() + ": ");
            String answer = readLine().trim();
            if (answer.isEmpty()) {
                return options.get(defaultIndex);
            }
            if (options.contains(answer)) {
                return answer;
            }
            try {
                int number = Integer.parseInt(answer);
                if (number >= 1 && number <= options.size()) {
                    return options.get(number - 1);
                }
            } catch (NumberFormatException ignored) {
                // fall through to the invalid-selection message
            }
            writeLine("Invalid selection.");
        }
    }

    private String readLine() {
        try {
            String line = input.readLine();
            if (line == null) {
                throw new OperationCancelledException();
            }
            return line;
        } catch (IOException e) {
            throw new OperationCancelledException();
        }
    }

    private void write(String text) {
        try {
            output.write(text);
        } catch (IOException e) {
            throw new OperationCancelledException();
        }
    }

    /**
     * Not {@link OutputHandler#writeLine}: plexus-interactivity's {@code DefaultOutputHandler}
     * (1.6.0) prints only the line separator and drops the text.
     */
    private void writeLine(String text) {
        write(text + System.lineSeparator());
    }
}
