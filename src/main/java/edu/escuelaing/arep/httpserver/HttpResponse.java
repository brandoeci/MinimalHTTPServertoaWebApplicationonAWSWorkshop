package edu.escuelaing.arep.httpserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One HTTP response: status, headers and a body that is always a byte array.
 *
 * <p>Keeping the body as bytes is what lets text and binary images travel the
 * same code path, and it is why {@code Content-Length} is always the real
 * number of bytes instead of a character count.</p>
 */
public class HttpResponse {

    public static final String TEXT_HTML = "text/html; charset=utf-8";
    public static final String TEXT_PLAIN = "text/plain; charset=utf-8";
    public static final String APPLICATION_JSON = "application/json; charset=utf-8";

    private final int status;
    private final String contentType;
    private final byte[] body;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public HttpResponse(int status, String contentType, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body;
    }

    public static HttpResponse of(int status, String contentType, byte[] body) {
        return new HttpResponse(status, contentType, body);
    }

    public static HttpResponse text(int status, String body) {
        return new HttpResponse(status, TEXT_PLAIN, body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse html(int status, String body) {
        return new HttpResponse(status, TEXT_HTML, body.getBytes(StandardCharsets.UTF_8));
    }

    /** JSON responses are never cached: the server time must be the real one. */
    public static HttpResponse json(int status, String body) {
        HttpResponse response = new HttpResponse(status, APPLICATION_JSON,
                body.getBytes(StandardCharsets.UTF_8));
        response.header("Cache-Control", "no-store");
        return response;
    }

    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    public int getContentLength() {
        return body.length;
    }

    /** Writes the status line, the headers, the mandatory blank line and the body. */
    public void writeTo(OutputStream out) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        head.append("Date: ").append(httpDate()).append("\r\n");
        head.append("Server: minihttp-server/1.0\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        // This server handles one connection at a time, so it never keeps one alive.
        head.append("Connection: close\r\n");
        head.append("\r\n");

        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String httpDate() {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

    static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 414 -> "URI Too Long";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}
