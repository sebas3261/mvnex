package com.sebas3261.ex.infrastructure.xml;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Structural view of XML text for format-preserving edits. Comments, CDATA sections and
 * processing instructions are masked (replaced by spaces, keeping line breaks), so offsets in the
 * masked text equal offsets in the original and commented-out markup is never matched.
 */
public final class XmlText {

    /** An element found by the scanner; {@code closeStart}/{@code closeEnd} are -1 for self-closing tags. */
    public record Element(String name, int depth, int openStart, int openEnd, int closeStart, int closeEnd) {

        public boolean selfClosing() {
            return closeStart < 0;
        }
    }

    private final String original;
    private final String masked;
    private final List<Element> elements;

    public XmlText(String original) {
        this.original = original;
        this.masked = mask(original);
        this.elements = scan(masked);
    }

    /** The line separator of the text: CRLF if its first line break is CRLF, otherwise LF. */
    public String lineSeparator() {
        int lf = original.indexOf('\n');
        return lf > 0 && original.charAt(lf - 1) == '\r' ? "\r\n" : "\n";
    }

    /** Elements with the given local name whose ancestors (outermost first) have exactly the given local names. */
    public List<Element> find(List<String> ancestorPath, String name) {
        List<Element> matches = new ArrayList<>();
        for (Element element : elements) {
            if (element.name().equals(name) && element.depth() == ancestorPath.size()
                    && ancestors(element).equals(ancestorPath)) {
                matches.add(element);
            }
        }
        return matches;
    }

    /** Direct children of {@code parent} with the given local name. */
    public List<Element> children(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        if (parent.selfClosing()) {
            return matches;
        }
        for (Element element : elements) {
            if (element.name().equals(name) && element.depth() == parent.depth() + 1
                    && element.openStart() > parent.openEnd() && element.openStart() < parent.closeStart()) {
                matches.add(element);
            }
        }
        return matches;
    }

    /** Masked text content of an element (between its tags), trimmed. */
    public String content(Element element) {
        return element.selfClosing() ? "" : masked.substring(element.openEnd(), element.closeStart()).trim();
    }

    /** The original text. */
    public String original() {
        return original;
    }

    /** The text with comments, CDATA, processing instructions and DOCTYPE blanked out (same offsets). */
    public String masked() {
        return masked;
    }

    /** All elements, in document order. */
    public List<Element> elements() {
        return elements;
    }

    private List<String> ancestors(Element target) {
        List<String> names = new ArrayList<>();
        for (Element element : elements) {
            boolean encloses = !element.selfClosing() && element.openEnd() <= target.openStart()
                    && element.closeStart() >= target.openEnd();
            if (encloses && element.depth() < target.depth()) {
                names.add(element.name());
            }
        }
        return names;
    }

    private static String mask(String text) {
        StringBuilder masked = new StringBuilder(text);
        maskBetween(masked, text, "<!--", "-->");
        maskBetween(masked, text, "<![CDATA[", "]]>");
        maskBetween(masked, text, "<?", "?>");
        maskBetween(masked, text, "<!DOCTYPE", ">");
        return masked.toString();
    }

    private static void maskBetween(StringBuilder masked, String text, String open, String close) {
        int from = 0;
        while (true) {
            int start = masked.indexOf(open, from);
            if (start < 0) {
                return;
            }
            int end = masked.indexOf(close, start + open.length());
            end = end < 0 ? text.length() : end + close.length();
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                if (c != '\n' && c != '\r') {
                    masked.setCharAt(i, ' ');
                }
            }
            from = end;
        }
    }

    private static List<Element> scan(String masked) {
        List<Element> elements = new ArrayList<>();
        Deque<int[]> open = new ArrayDeque<>(); // {index into elements}
        Deque<String> names = new ArrayDeque<>();
        int i = 0;
        while ((i = masked.indexOf('<', i)) >= 0) {
            int end = masked.indexOf('>', i);
            if (end < 0) {
                break;
            }
            String tag = masked.substring(i + 1, end).trim();
            if (tag.startsWith("/")) {
                String name = localName(tag.substring(1).trim());
                // Close the nearest open element with this name.
                while (!names.isEmpty()) {
                    String top = names.pop();
                    int index = open.pop()[0];
                    Element e = elements.get(index);
                    if (top.equals(name)) {
                        elements.set(index, new Element(e.name(), e.depth(), e.openStart(), e.openEnd(), i, end + 1));
                        break;
                    }
                }
            } else if (!tag.isEmpty() && (Character.isLetter(tag.charAt(0)) || tag.charAt(0) == '_')) {
                boolean selfClosing = tag.endsWith("/");
                String name = localName(tag.split("[\\s/]", 2)[0]);
                Element element = new Element(name, names.size(), i, end + 1, -1, -1);
                elements.add(element);
                if (!selfClosing) {
                    names.push(name);
                    open.push(new int[] {elements.size() - 1});
                }
            }
            i = end + 1;
        }
        return elements;
    }

    private static String localName(String qualified) {
        int colon = qualified.indexOf(':');
        return colon >= 0 ? qualified.substring(colon + 1) : qualified;
    }
}
