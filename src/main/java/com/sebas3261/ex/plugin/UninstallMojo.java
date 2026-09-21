package com.sebas3261.ex.plugin;

import com.sebas3261.ex.application.ports.ReportSink;
import com.sebas3261.ex.infrastructure.settings.SettingsFileWriter;
import com.sebas3261.ex.infrastructure.settings.SettingsRegistration;
import java.io.File;
import java.time.Clock;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Remove the ex plugin prefix from your Maven settings.
 *
 * <p>Removes only the {@code com.sebas3261} {@code <pluginGroup>} entry that {@code setup} added to
 * your user settings file ({@code ~/.m2/settings.xml}, or the file given with {@code -s}); every
 * other byte is left unchanged and a timestamped backup is written first. Afterwards run goals by
 * their full name, e.g. {@code mvn com.sebas3261:ex-maven-plugin:<version>:init}.
 * <pre>
 * mvn ex:uninstall
 * </pre>
 */
@Mojo(name = "uninstall", requiresProject = false, aggregator = true, threadSafe = true)
public class UninstallMojo extends AbstractExMojo {

    @Override
    protected String goal() {
        return "uninstall";
    }

    @Override
    protected void run(ReportSink report) {
        File global = session.getRequest().getGlobalSettingsFile();
        new SettingsRegistration(new SettingsFileWriter(Clock.systemDefaultZone()), System.lineSeparator())
                .uninstall(userSettingsFile(), global == null ? null : global.toPath(), pluginVersion(), report);
    }
}
