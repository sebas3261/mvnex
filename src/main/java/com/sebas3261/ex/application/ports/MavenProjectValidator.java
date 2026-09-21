package com.sebas3261.ex.application.ports;

/** Validates the project after its POM was edited. */
public interface MavenProjectValidator {

    Status validate();

    enum Status {
        PASSED,
        FAILED
    }
}
