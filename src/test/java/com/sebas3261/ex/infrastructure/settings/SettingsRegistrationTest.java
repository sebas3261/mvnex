package com.sebas3261.ex.infrastructure.settings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sebas3261.ex.application.ports.ReportSink;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsRegistrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T10:15:30Z"), ZoneOffset.UTC);
    private static final String GROUP_ENTRY = "<pluginGroup>com.sebas3261</pluginGroup>";

    @TempDir
    Path dir;

    private final List<String> messages = new ArrayList<>();
    private final ReportSink report = new ReportSink() {
        @Override
        public void info(String message) {
            messages.add(message);
        }

        @Override
        public void warn(String message) {
            messages.add("WARN " + message);
        }

        @Override
        public void error(String message) {
            messages.add("ERROR " + message);
        }
    };

    private SettingsRegistration registration(String eol) {
        return new SettingsRegistration(new SettingsFileWriter(CLOCK), eol);
    }

    private static byte[] fixture(String path) throws IOException {
        try (InputStream in = SettingsRegistrationTest.class.getResourceAsStream("/settings/" + path)) {
            if (in == null) {
                throw new IOException("missing fixture " + path);
            }
            return in.readAllBytes();
        }
    }

    private Path settingsFrom(String fixture) throws IOException {
        Path settings = dir.resolve("settings.xml");
        Files.write(settings, fixture(fixture + "/input.xml"));
        return settings;
    }

    private List<Path> backups() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".bak")).toList();
        }
    }

    // ---- setup ----

    @ParameterizedTest
    @ValueSource(strings = {"setup-maven-default", "setup-tabs", "setup-self-closing", "setup-no-plugin-groups",
            "setup-crlf", "setup-comment-only", "setup-no-children", "setup-iso-8859-1", "setup-single-line"})
    void setupMatchesTheExpectedBytes(String fixture) throws IOException {
        Path settings = settingsFrom(fixture);

        registration("\n").setup(settings, report);

        assertArrayEquals(fixture(fixture + "/setup.expected.xml"), Files.readAllBytes(settings), fixture);
        assertEquals(1, backups().size());
        assertArrayEquals(fixture(fixture + "/input.xml"), Files.readAllBytes(backups().get(0)), "backup = original");
        assertEquals(List.of(
                "Added plugin group com.sebas3261 to " + settings + ".",
                "Backup saved to " + dir.toRealPath().resolve("settings.xml.20260921101530.bak") + ".",
                "You can now run: mvn ex:init, mvn ex:add, mvn ex:help"), messages);
    }

    @Test
    void createsTheFileAndMissingDirectories() throws IOException {
        Path settings = dir.resolve("home/.m2/settings.xml");

        registration("\n").setup(settings, report);

        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 \
                https://maven.apache.org/xsd/settings-1.2.0.xsd">
                  <pluginGroups>
                    <pluginGroup>com.sebas3261</pluginGroup>
                  </pluginGroups>
                </settings>
                """, Files.readString(settings));
        assertEquals("Created " + settings + " with plugin group com.sebas3261.", messages.get(0));
    }

    @Test
    void newFileUsesThePlatformSeparatorGiven() throws IOException {
        Path settings = dir.resolve("settings.xml");
        registration("\r\n").setup(settings, report);
        String text = Files.readString(settings);
        assertEquals(text.split("\n", -1).length - 1, text.split("\r\n", -1).length - 1, "only CRLF breaks");
    }

    @Test
    void alreadyRegisteredIsANoOp() throws IOException {
        Path settings = settingsFrom("setup-already-active");
        Files.setLastModifiedTime(settings, FileTime.fromMillis(1_000_000_000_000L));

        registration("\n").setup(settings, report);

        assertArrayEquals(fixture("setup-already-active/input.xml"), Files.readAllBytes(settings));
        assertEquals(1_000_000_000_000L, Files.getLastModifiedTime(settings).toMillis());
        assertTrue(backups().isEmpty());
        assertEquals("Plugin group com.sebas3261 is already configured in " + settings + ". No changes made.",
                messages.get(0));
    }

    @Test
    void runningSetupTwiceChangesNothingTheSecondTime() throws IOException {
        Path settings = settingsFrom("setup-tabs");
        registration("\n").setup(settings, report);
        byte[] afterFirst = Files.readAllBytes(settings);

        registration("\n").setup(settings, report);

        assertArrayEquals(afterFirst, Files.readAllBytes(settings));
        assertEquals(1, backups().size());
    }

    @Test
    void malformedSettingsAreLeftUntouched() throws IOException {
        Path settings = settingsFrom("malformed");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registration("\n").setup(settings, report));

        assertTrue(error.getMessage().startsWith("Could not update " + settings + ": "), error.getMessage());
        assertTrue(error.getMessage().endsWith(". No changes made."));
        assertArrayEquals(fixture("malformed/input.xml"), Files.readAllBytes(settings));
        assertTrue(backups().isEmpty());
    }

    @Test
    void restrictedPermissionsArePreserved() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path settings = settingsFrom("setup-tabs");
        Files.setPosixFilePermissions(settings, PosixFilePermissions.fromString("rw-------"));

        registration("\n").setup(settings, report);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(settings)));
    }

    @Test
    void symlinkedSettingsUpdateTheTargetAndKeepTheLink() throws IOException {
        Path real = Files.createDirectories(dir.resolve("dotfiles")).resolve("maven-settings.xml");
        Files.write(real, fixture("setup-tabs/input.xml"));
        Path link = Files.createDirectories(dir.resolve(".m2")).resolve("settings.xml");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symlinks not supported here");
        }

        registration("\n").setup(link, report);

        assertTrue(Files.isSymbolicLink(link));
        assertArrayEquals(fixture("setup-tabs/setup.expected.xml"), Files.readAllBytes(real));
    }

    // ---- uninstall ----

    @ParameterizedTest
    @ValueSource(strings = {"uninstall-among-groups", "uninstall-among-groups-crlf", "uninstall-similar-group",
            "uninstall-commented-and-active", "uninstall-inline", "uninstall-duplicates", "uninstall-whitespace-value",
            "uninstall-full-settings"})
    void uninstallMatchesTheExpectedBytes(String fixture) throws IOException {
        Path settings = settingsFrom(fixture);

        registration("\n").uninstall(settings, null, "0.2.0", report);

        assertArrayEquals(fixture(fixture + "/uninstall.expected.xml"), Files.readAllBytes(settings), fixture);
        assertEquals(1, backups().size());
        assertEquals(List.of(
                "Removed plugin group com.sebas3261 from " + settings + ".",
                "Backup saved to " + dir.toRealPath().resolve("settings.xml.20260921101530.bak") + ".",
                "The ex: prefix is no longer registered. Run goals as "
                        + "mvn com.sebas3261:ex-maven-plugin:0.2.0:<goal>, or run setup again."), messages);
    }

    @Test
    void namespacePrefixedEntryIsNeverEditedBlindly() throws IOException {
        Path settings = settingsFrom("uninstall-namespace-prefixed");
        try {
            registration("\n").uninstall(settings, null, "0.2.0", report);
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().endsWith(". No changes made."));
        }
        assertArrayEquals(fixture("uninstall-namespace-prefixed/input.xml"), Files.readAllBytes(settings));
        assertTrue(backups().isEmpty());
    }

    @Test
    void noSettingsFileCreatesNothing() {
        Path settings = dir.resolve("missing/.m2/settings.xml");

        registration("\n").uninstall(settings, null, "0.2.0", report);

        assertFalse(Files.exists(dir.resolve("missing")));
        assertEquals(List.of("No settings file at " + settings + ". Nothing to uninstall."), messages);
    }

    @Test
    void notConfiguredIsANoOp() throws IOException {
        Path settings = settingsFrom("setup-tabs");
        registration("\n").uninstall(settings, null, "0.2.0", report);

        assertArrayEquals(fixture("setup-tabs/input.xml"), Files.readAllBytes(settings));
        assertEquals(List.of("Plugin group com.sebas3261 is not configured in " + settings + ". No changes made."),
                messages);
        assertTrue(backups().isEmpty());
    }

    @Test
    void globalRegistrationIsReported() throws IOException {
        Path settings = settingsFrom("uninstall-among-groups");
        Path global = dir.resolve("global-settings.xml");
        Files.writeString(global, "<settings><pluginGroups>" + GROUP_ENTRY + "</pluginGroups></settings>");

        registration("\n").uninstall(settings, global, "0.2.0", report);

        assertEquals("Note: com.sebas3261 is also configured in the global settings (" + global
                + "); the ex: prefix will keep working. Global settings were not modified.", messages.get(3));
        assertEquals("<settings><pluginGroups>" + GROUP_ENTRY + "</pluginGroups></settings>", Files.readString(global));
    }

    // ---- round trip ----

    @ParameterizedTest
    @ValueSource(strings = {"setup-maven-default", "setup-tabs", "setup-crlf", "setup-comment-only",
            "setup-iso-8859-1", "setup-single-line"})
    void uninstallUndoesSetupExactlyWhenPluginGroupsExisted(String fixture) throws IOException {
        Path settings = settingsFrom(fixture);

        registration("\n").setup(settings, report);
        registration("\n").uninstall(settings, null, "0.2.0", report);

        assertArrayEquals(fixture(fixture + "/input.xml"), Files.readAllBytes(settings), fixture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"setup-self-closing", "setup-no-plugin-groups", "setup-no-children"})
    void uninstallLeavesAnEmptyPluginGroupsWhenSetupCreatedIt(String fixture) throws IOException {
        Path settings = settingsFrom(fixture);

        registration("\n").setup(settings, report);
        registration("\n").uninstall(settings, null, "0.2.0", report);

        String expected = new String(fixture(fixture + "/setup.expected.xml"), StandardCharsets.UTF_8)
                .replaceAll("\\s*" + GROUP_ENTRY, "");
        String actual = Files.readString(settings);
        assertEquals(expected.replaceAll("\\s+", " "), actual.replaceAll("\\s+", " "));
        assertTrue(actual.contains("<pluginGroups>") && actual.contains("</pluginGroups>"));
        assertFalse(actual.contains("com.sebas3261"));
    }
}
