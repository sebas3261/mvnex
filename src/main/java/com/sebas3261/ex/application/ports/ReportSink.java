package com.sebas3261.ex.application.ports;

/** User-visible output. */
public interface ReportSink {

    void info(String message);

    void warn(String message);

    void error(String message);
}
