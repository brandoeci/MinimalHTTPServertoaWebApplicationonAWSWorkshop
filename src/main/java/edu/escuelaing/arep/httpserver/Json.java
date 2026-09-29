package edu.escuelaing.arep.httpserver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON writer. No library is used, so escaping is explicit and visible:
 * a value coming from the query string is never concatenated into a document
 * without being escaped first.
 */
public final class Json {

    private Json() {
    }

    /** Starts a JSON object. */
    public static Builder object() {
        return new Builder();
    }

    /** Escapes a string and wraps it in double quotes. */
    public static String string(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                // '<', '>' and '&' are escaped too, so a value can never be read
                // as markup if the JSON ends up inside a page.
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * Renders a number without a trailing {@code .0} when it is a whole value,
     * so the square of 5 reads as {@code 25} and not {@code 25.0}.
     */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "null";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** Accumulates pairs and renders them in insertion order. */
    public static final class Builder {

        private final Map<String, String> fields = new LinkedHashMap<>();

        /** Adds a string field, escaped. */
        public Builder put(String name, String value) {
            fields.put(name, string(value));
            return this;
        }

        /** Adds a numeric field. */
        public Builder put(String name, double value) {
            fields.put(name, number(value));
            return this;
        }

        /** Adds a numeric field. */
        public Builder put(String name, long value) {
            fields.put(name, Long.toString(value));
            return this;
        }

        /** Adds a boolean field. */
        public Builder put(String name, boolean value) {
            fields.put(name, Boolean.toString(value));
            return this;
        }

        public String build() {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                out.append(string(field.getKey())).append(':').append(field.getValue());
                first = false;
            }
            return out.append('}').toString();
        }

        @Override
        public String toString() {
            return build();
        }
    }
}
