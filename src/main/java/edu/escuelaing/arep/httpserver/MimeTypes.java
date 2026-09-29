package edu.escuelaing.arep.httpserver;

import java.util.Locale;
import java.util.Map;

/**
 * Extension to content-type table.
 *
 * <p>The content type is the only thing that tells the browser how to interpret
 * the bytes it received: the same byte stream becomes a page, a script or an
 * image depending on this header.</p>
 */
public final class MimeTypes {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("mjs", "application/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"));

    private MimeTypes() {
    }

    /**
     * @return the content type for a resource name, or {@code null} when the
     *         extension is missing or not supported by this laboratory
     */
    public static String forPath(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return BY_EXTENSION.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** @return true when the extension is one this server knows how to serve */
    public static boolean isSupported(String path) {
        return forPath(path) != null;
    }
}
