package edu.escuelaing.arep.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One parsed HTTP request head: the request line and its headers.
 *
 * <p>Only what this laboratory needs is parsed. There is no body handling
 * because only {@code GET} is accepted.</p>
 */
public class HttpRequest {

    private final String method;
    private final String target;
    private final String version;
    private final String path;
    private final String rawQuery;
    private final Map<String, String> queryParams;
    private final Map<String, String> headers;

    private HttpRequest(String method, String target, String version, String path,
                        String rawQuery, Map<String, String> queryParams, Map<String, String> headers) {
        this.method = method;
        this.target = target;
        this.version = version;
        this.path = path;
        this.rawQuery = rawQuery;
        this.queryParams = Collections.unmodifiableMap(queryParams);
        this.headers = Collections.unmodifiableMap(headers);
    }

    /**
     * Reads and parses a request head from an already connected client.
     *
     * @return the parsed request, or {@code null} when the peer closed the
     *         connection without sending anything (browsers pre-open sockets)
     * @throws HttpException 400 when the request line or the escaping is malformed
     */
    public static HttpRequest parse(BufferedReader in) throws IOException, HttpException {
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw HttpException.badRequest("Malformed request line");
        }
        String method = parts[0].toUpperCase(Locale.ROOT);
        String target = parts[1];
        String version = parts[2];
        if (!version.startsWith("HTTP/")) {
            throw HttpException.badRequest("Unsupported protocol in request line");
        }
        if (!target.startsWith("/")) {
            // Absolute-form targets (proxy style) are out of scope for this lab.
            throw HttpException.badRequest("Request target must be an absolute path");
        }

        int q = target.indexOf('?');
        String rawPath = q < 0 ? target : target.substring(0, q);
        String rawQuery = q < 0 ? "" : target.substring(q + 1);

        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }

        return new HttpRequest(method, target, version,
                Urls.decodePath(rawPath), rawQuery, Urls.parseQuery(rawQuery), headers);
    }

    public String getMethod() {
        return method;
    }

    /** The raw request target, query string included. */
    public String getTarget() {
        return target;
    }

    public String getVersion() {
        return version;
    }

    /** The percent-decoded path, without the query string. */
    public String getPath() {
        return path;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    /** @return the decoded value of a query parameter, or {@code null} when absent */
    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    /**
     * @return the trimmed value of a required query parameter
     * @throws HttpException 400 when the parameter is missing or blank
     */
    public String requireQueryParam(String name) throws HttpException {
        String value = queryParams.get(name);
        if (value == null || value.isBlank()) {
            throw HttpException.badRequest("Missing required query parameter '" + name + "'");
        }
        return value.trim();
    }

    /** @return a header value by case-insensitive name, or {@code null} */
    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        return method + " " + target + " " + version;
    }
}
