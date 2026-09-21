package com.sebas3261.ex.application.ports;

import java.util.List;

/**
 * Prompts the user. Both prompt methods return the default for an empty answer and throw
 * {@link com.sebas3261.ex.application.errors.OperationCancelledException} when input ends.
 */
public interface Interaction {

    boolean isInteractive();

    String text(String label, String defaultValue);

    String select(String label, List<String> options, String defaultValue);
}
