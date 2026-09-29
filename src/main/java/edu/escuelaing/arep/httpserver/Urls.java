package edu.escuelaing.arep.httpserver;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Percent-decoding and query-string parsing helpers. */
public final class Urls {

    private Urls() {
    }

    /**
     * Decodes a query-string token: {@code %XX} escapes plus {@code '+'} as a space.
     *
     * @throws HttpException 400 when the escaping is malformed
     */
    public static String decodeQueryToken(String value) throws HttpException {
        return decode(value, true);
    }

    /**
     * Decodes a path segment string. Unlike query tokens, {@code '+'} is a literal
     * plus sign inside a path, so it is preserved.
     *
     * @throws HttpException 400 when the escaping is malformed
     */
    public static String decodePath(String value) throws HttpException {
        return decode(value, false);
    }

    private static String decode(String value, boolean plusIsSpace) throws HttpException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) {
                    throw HttpException.badRequest("Truncated percent-escape in URL");
                }
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    throw HttpException.badRequest("Invalid percent-escape in URL");
                }
                bytes.write((hi << 4) + lo);
                i += 2;
            } else if (c == '+' && plusIsSpace) {
                bytes.write(' ');
            } else {
                byte[] raw = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(raw, 0, raw.length);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Parses {@code a=1&b=hello%20world} into a map. Empty input yields an empty map
     * and repeated names keep the first value.
     */
    public static Map<String, String> parseQuery(String query) throws HttpException {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawName = eq < 0 ? pair : pair.substring(0, eq);
            String rawValue = eq < 0 ? "" : pair.substring(eq + 1);
            params.putIfAbsent(decodeQueryToken(rawName), decodeQueryToken(rawValue));
        }
        return params;
    }
}
