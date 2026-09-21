package com.sebas3261.ex.infrastructure.settings;

import com.sebas3261.ex.infrastructure.xml.XmlText;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Adds or removes this plugin's {@code <pluginGroup>} in {@code settings.xml} text without touching
 * anything else (design D15). Registration is detected with Maven's own settings parser; edit
 * positions are found on text whose comments and CDATA are masked, so commented-out entries are
 * never matched. Every result is re-parsed and verified before it is returned.
 */
public final class SettingsPluginGroupEditor {

    public static final String GROUP = "com.sebas3261";

    private static final String ENTRY = "<pluginGroup>" + GROUP + "</pluginGroup>";
    private static final Pattern ACTIVE_ENTRY = Pattern.compile("<pluginGroup>\\s*com\\.sebas3261\\s*</pluginGroup>");

    /** The file content could not be parsed or edited safely; the message explains why. */
    public static final class SettingsEditException extends IllegalStateException {
        public SettingsEditException(String message) {
            super(message);
        }
    }

    private SettingsPluginGroupEditor() {
    }

    /** Content for a new user settings file. */
    public static String newFile(String eol) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + eol
                + "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.2.0\"" + eol
                + "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" + eol
                + "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.2.0 "
                + "https://maven.apache.org/xsd/settings-1.2.0.xsd\">" + eol
                + "  <pluginGroups>" + eol
                + "    " + ENTRY + eol
                + "  </pluginGroups>" + eol
                + "</settings>" + eol;
    }

    /** Number of active {@code com.sebas3261} plugin groups; throws if the text isn't well-formed settings. */
    public static long registrations(String text) {
        return parse(text).getPluginGroups().stream().filter(GROUP::equals).count();
    }

    /** Returns the text with the plugin group added; the caller checks it isn't registered yet. */
    public static String add(String text) {
        registrations(text); // fails fast on malformed input
        String result = insert(new XmlText(text));
        if (registrations(result) < 1) {
            throw new SettingsEditException("the edited settings do not contain the plugin group");
        }
        return result;
    }

    /** Returns the text with every active {@code com.sebas3261} plugin group removed. */
    public static String remove(String text) {
        Settings original = parse(text);
        long parsedCount = original.getPluginGroups().stream().filter(GROUP::equals).count();

        XmlText xml = new XmlText(text);
        List<int[]> ranges = new ArrayList<>();
        Matcher matcher = ACTIVE_ENTRY.matcher(xml.masked());
        while (matcher.find()) {
            ranges.add(removalRange(text, matcher.start(), matcher.end()));
        }
        if (ranges.size() != parsedCount) {
            throw new SettingsEditException("unable to locate the plugin group entry safely");
        }

        StringBuilder result = new StringBuilder(text);
        for (int i = ranges.size() - 1; i >= 0; i--) {
            result.delete(ranges.get(i)[0], ranges.get(i)[1]);
        }
        String edited = result.toString();

        Settings after = parse(edited);
        original.getPluginGroups().removeIf(GROUP::equals);
        if (after.getPluginGroups().contains(GROUP) || !write(original).equals(write(after))) {
            throw new SettingsEditException("unable to locate the plugin group entry safely");
        }
        return edited;
    }

    // ---- insertion ----

    private static String insert(XmlText xml) {
        String text = xml.original();
        String eol = xml.lineSeparator();

        List<XmlText.Element> groups = xml.find(List.of("settings"), "pluginGroups");
        if (!groups.isEmpty()) {
            XmlText.Element target = groups.get(0);
            if (target.selfClosing()) {
                // Case 2: <pluginGroups/>
                String indent = leadingWhitespace(text, target.openStart());
                return text.substring(0, target.openStart())
                        + "<pluginGroups>" + eol + indent + "  " + ENTRY + eol + indent + "</pluginGroups>"
                        + text.substring(target.openEnd());
            }
            // Case 1: existing <pluginGroups>
            int lineStart = lineStart(text, target.closeStart());
            if (!text.substring(lineStart, target.closeStart()).isBlank()) {
                return text.substring(0, target.closeStart()) + ENTRY + text.substring(target.closeStart());
            }
            List<XmlText.Element> children = xml.children(target, "pluginGroup");
            String indent = children.isEmpty()
                    ? leadingWhitespace(text, target.closeStart()) + "  "
                    : leadingWhitespace(text, children.get(children.size() - 1).openStart());
            return text.substring(0, lineStart) + indent + ENTRY + eol + text.substring(lineStart);
        }

        // Case 3: no <pluginGroups>
        List<XmlText.Element> roots = xml.find(List.of(), "settings");
        if (roots.isEmpty()) {
            throw new SettingsEditException("no <settings> element");
        }
        XmlText.Element settings = roots.get(0);
        String settingsIndent = leadingWhitespace(text, settings.openStart());
        String indent = settingsIndent + "  ";
        String unit = "  ";
        List<XmlText.Element> children = xml.elements().stream()
                .filter(e -> e.depth() == settings.depth() + 1 && e.openStart() > settings.openEnd()
                        && (settings.selfClosing() || e.openStart() < settings.closeStart()))
                .toList();
        if (!children.isEmpty()) {
            indent = leadingWhitespace(text, children.get(0).openStart());
            unit = indent.startsWith(settingsIndent) && indent.length() > settingsIndent.length()
                    ? indent.substring(settingsIndent.length())
                    : "  ";
        }
        String block = indent + "<pluginGroups>" + eol + indent + unit + ENTRY + eol + indent + "</pluginGroups>" + eol;

        if (settings.selfClosing()) {
            String openTag = text.substring(settings.openStart(), settings.openEnd());
            String opened = openTag.substring(0, openTag.lastIndexOf('/')).stripTrailing() + ">";
            return text.substring(0, settings.openStart()) + opened + eol + block + settingsIndent + "</settings>"
                    + text.substring(settings.openEnd());
        }
        int lineStart = lineStart(text, settings.closeStart());
        if (text.substring(lineStart, settings.closeStart()).isBlank()) {
            return text.substring(0, lineStart) + block + text.substring(lineStart);
        }
        return text.substring(0, settings.closeStart()) + eol + block + text.substring(settings.closeStart());
    }

    // ---- removal ----

    /** The whole line (with its line separator) if the entry is alone on it; otherwise just the element. */
    private static int[] removalRange(String text, int start, int end) {
        int lineStart = lineStart(text, start);
        int newline = text.indexOf('\n', end);
        int lineEnd = newline < 0 ? text.length() : newline + 1;
        String before = text.substring(lineStart, start);
        String after = text.substring(end, newline < 0 ? text.length() : newline);
        if (before.isBlank() && after.isBlank()) {
            return new int[] {lineStart, lineEnd};
        }
        return new int[] {start, end};
    }

    // ---- helpers ----

    private static int lineStart(String text, int index) {
        int lf = text.lastIndexOf('\n', index - 1);
        return lf < 0 ? 0 : lf + 1;
    }

    private static String leadingWhitespace(String text, int index) {
        int start = lineStart(text, index);
        int end = start;
        while (end < index && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return text.substring(start, end);
    }

    /** Strict parsing first; unknown elements from other tools are tolerated, malformed XML is not. */
    static Settings parse(String text) {
        try {
            return new SettingsXpp3Reader().read(new StringReader(text), true);
        } catch (XmlPullParserException | IOException strictFailure) {
            try {
                return new SettingsXpp3Reader().read(new StringReader(text), false);
            } catch (XmlPullParserException | IOException e) {
                throw new SettingsEditException(e.getMessage());
            }
        }
    }

    private static String write(Settings settings) {
        try {
            StringWriter out = new StringWriter();
            new SettingsXpp3Writer().write(out, settings);
            return out.toString();
        } catch (IOException e) {
            throw new SettingsEditException(e.getMessage());
        }
    }
}
