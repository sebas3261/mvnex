package com.sebas3261.ex.infrastructure.transport;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** URL building and encoding rules carried over from the C++ resolvers. */
public final class LookupUrls {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private LookupUrls() {
    }

    /** {@code <base>?q=<q>&rows=<n>&wt=json[&core=<core>]}. */
    public static URI solrSearch(String baseUrl, String query, int rows, String core) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?q=").append(encodeQueryValue(query))
                .append("&rows=").append(rows)
                .append("&wt=json");
        if (core != null && !core.isEmpty()) {
            url.append("&core=").append(encodeQueryValue(core));
        }
        return URI.create(url.toString());
    }

    /** {@code <base><encoded "groupId:artifactId">}. */
    public static URI depsDevPackage(String baseUrl, String groupId, String artifactId) {
        return URI.create(baseUrl + encodePathSegment(groupId + ":" + artifactId));
    }

    /** Keeps {@code A-Z a-z 0-9 - _ . ~}, turns space into {@code +}, percent-encodes other UTF-8 bytes. */
    public static String encodeQueryValue(String value) {
        return encode(value, true);
    }

    /** Like {@link #encodeQueryValue} but percent-encodes spaces. */
    public static String encodePathSegment(String value) {
        return encode(value, false);
    }

    private static String encode(String value, boolean spaceAsPlus) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                encoded.append((char) c);
            } else if (c == ' ' && spaceAsPlus) {
                encoded.append('+');
            } else {
                encoded.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return encoded.toString();
    }
}
