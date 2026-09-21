package com.sebas3261.ex.infrastructure.settings;

import com.sebas3261.ex.application.ports.ReportSink;
import com.sebas3261.ex.infrastructure.xml.XmlEncoding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The {@code setup} and {@code uninstall} flows over a user settings file, with their messages. */
public final class SettingsRegistration {

    public static final String SETUP_HINT = "You can now run: mvn ex:init, mvn ex:add, mvn ex:help";

    private static final String GROUP = SettingsPluginGroupEditor.GROUP;

    private final SettingsFileWriter writer;
    private final String lineSeparator;

    public SettingsRegistration(SettingsFileWriter writer, String lineSeparator) {
        this.writer = writer;
        this.lineSeparator = lineSeparator;
    }

    public void setup(Path settings, ReportSink report) {
        try {
            if (!Files.exists(settings)) {
                writer.create(settings, SettingsPluginGroupEditor.newFile(lineSeparator).getBytes(StandardCharsets.UTF_8));
                report.info("Created " + settings + " with plugin group " + GROUP + ".");
            } else {
                Content content = read(settings);
                if (registrations(settings, content) > 0) {
                    report.info("Plugin group " + GROUP + " is already configured in " + settings + ". No changes made.");
                } else {
                    String edited = edit(settings, () -> SettingsPluginGroupEditor.add(content.text()));
                    Path backup = writer.replace(settings, edited.getBytes(content.charset()));
                    report.info("Added plugin group " + GROUP + " to " + settings + ".");
                    report.info("Backup saved to " + backup + ".");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update " + settings + ": " + e.getMessage() + ". No changes made.", e);
        }
        report.info(SETUP_HINT);
    }

    public void uninstall(Path settings, Path globalSettings, String pluginVersion, ReportSink report) {
        if (!Files.exists(settings)) {
            report.info("No settings file at " + settings + ". Nothing to uninstall.");
            return;
        }
        try {
            Content content = read(settings);
            if (registrations(settings, content) == 0) {
                report.info("Plugin group " + GROUP + " is not configured in " + settings + ". No changes made.");
                return;
            }
            String edited = edit(settings, () -> SettingsPluginGroupEditor.remove(content.text()));
            Path backup = writer.replace(settings, edited.getBytes(content.charset()));
            report.info("Removed plugin group " + GROUP + " from " + settings + ".");
            report.info("Backup saved to " + backup + ".");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update " + settings + ": " + e.getMessage() + ". No changes made.", e);
        }
        report.info("The ex: prefix is no longer registered. Run goals as mvn com.sebas3261:ex-maven-plugin:"
                + pluginVersion + ":<goal>, or run setup again.");
        if (globalSettings != null && configuredIn(globalSettings)) {
            report.info("Note: " + GROUP + " is also configured in the global settings (" + globalSettings
                    + "); the ex: prefix will keep working. Global settings were not modified.");
        }
    }

    private record Content(String text, Charset charset) {
    }

    private static Content read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Charset charset = XmlEncoding.detect(bytes);
        return new Content(new String(bytes, charset), charset);
    }

    private static long registrations(Path settings, Content content) {
        return edit(settings, () -> SettingsPluginGroupEditor.registrations(content.text()));
    }

    private static boolean configuredIn(Path settings) {
        try {
            return Files.exists(settings) && SettingsPluginGroupEditor.registrations(read(settings).text()) > 0;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    private interface Edit<T> {
        T apply();
    }

    private static <T> T edit(Path settings, Edit<T> edit) {
        try {
            return edit.apply();
        } catch (SettingsPluginGroupEditor.SettingsEditException e) {
            throw new IllegalStateException("Could not update " + settings + ": " + e.getMessage() + ". No changes made.", e);
        }
    }
}
