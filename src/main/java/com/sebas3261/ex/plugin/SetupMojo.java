package com.sebas3261.ex.plugin;

import com.sebas3261.ex.application.ports.ReportSink;
import com.sebas3261.ex.infrastructure.settings.SettingsFileWriter;
import com.sebas3261.ex.infrastructure.settings.SettingsRegistration;
import java.time.Clock;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Register the ex plugin prefix in your Maven settings.
 *
 * <p>Adds {@code com.sebas3261} to the {@code <pluginGroups>} of your user settings file
 * ({@code ~/.m2/settings.xml}, or the file given with {@code -s}), creating the file if needed, so
 * that the short form {@code mvn ex:<goal>} works. Nothing else in the file is changed, and a
 * timestamped backup is written before any existing file is modified. Run it once with the fully
 * qualified name:
 * <pre>
 * mvn com.sebas3261:ex-maven-plugin:&lt;version&gt;:setup
 * </pre>
 */
@Mojo(name = "setup", requiresProject = false, aggregator = true, threadSafe = true)
public class SetupMojo extends AbstractExMojo {

    @Override
    protected String goal() {
        return "setup";
    }

    @Override
    protected void run(ReportSink report) {
        new SettingsRegistration(new SettingsFileWriter(Clock.systemDefaultZone()), System.lineSeparator())
                .setup(userSettingsFile(), report);
    }
}
