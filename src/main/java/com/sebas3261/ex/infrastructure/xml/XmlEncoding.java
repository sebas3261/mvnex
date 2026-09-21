package com.sebas3261.ex.infrastructure.xml;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects the character encoding of an XML file from its byte-order mark or XML declaration. */
public final class XmlEncoding {

    private static final Pattern DECLARED = Pattern.compile("<\\?xml[^>]*?encoding\\s*=\\s*[\"']([^\"']+)[\"']");

    private XmlEncoding() {
    }

    /** The declared or BOM-indicated encoding; UTF-8 when neither is present or recognized. */
    public static Charset detect(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.ISO_8859_1);
        Matcher declared = DECLARED.matcher(head);
        if (declared.find()) {
            try {
                return Charset.forName(declared.group(1).trim());
            } catch (IllegalArgumentException unsupported) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }
}
