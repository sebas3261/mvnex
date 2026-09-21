package com.sebas3261.ex.plugin.support;

import com.sebas3261.ex.application.ports.ReportSink;
import org.apache.maven.plugin.logging.Log;

/** Sends user-visible output to Maven's logger. */
public final class LogReportSink implements ReportSink {

    private final Log log;

    public LogReportSink(Log log) {
        this.log = log;
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void warn(String message) {
        log.warn(message);
    }

    @Override
    public void error(String message) {
        log.error(message);
    }
}
