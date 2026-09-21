package com.sebas3261.ex.application.errors;

/** The user ended input at a prompt. */
public class OperationCancelledException extends RuntimeException {

    public OperationCancelledException() {
        super("Operation cancelled.");
    }
}
