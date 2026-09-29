package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Talks to the real server over a real socket.
 *
 * <p>The accept loop runs in a background thread so the test can be the client.
 * That thread belongs to the test only: the server still serves one connection
 * at a time and no concurrency was added to the application.</p>
 */
class HttpServerIntegrationTest {

    private static final Path PUBLIC_DIR = Paths.get("src/main/resources/public");

    private static HttpServer server;
    private static Thread serverThread;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = new HttpServer(0, new StaticFiles(PUBLIC_DIR), new Services());
        int port = server.bind();
        baseUrl = "http://localhost:" + port;
        serverThread = new Thread(server::acceptLoop, "test-accept-loop");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        server.stop();
        serverThread.join(2000);
    }

    /* ------------------------------------------------------------- helpers */

    private record Answer(int status, String contentType, byte[] body, HttpURLConnection connection) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private Answer request(String method, String path) throws IOException {
        URL url = URI.create(baseUrl + path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        int status = connection.getResponseCode();
        byte[] body;
        try (var stream = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
            body = stream == null ? new byte[0] : stream.readAllBytes();
        }
        return new Answer(status, connection.getContentType(), body, connection);
    }

    private Answer get(String path) throws IOException {
        return request("GET", path);
    }

    /**
     * Sends a request target exactly as written, without letting any client
     * library normalise it. Needed to test what a hostile client would send.
     *
     * @return the status line of the answer
     */
    private String rawStatusLine(String target) throws IOException {
        try (Socket socket = new Socket("localhost", URI.create(baseUrl).getPort())) {
            String head = "GET " + target + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String answer = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return answer.substring(0, answer.indexOf("\r\n"));
        }
    }

    /* --------------------------------------------------------------- tests */

    @Test
    @DisplayName("the home page and every resource it needs are served")
    void servesThePageAndItsResources() throws IOException {
        Answer page = get("/");
        assertEquals(200, page.status());
        assertEquals("text/html; charset=utf-8", page.contentType());
        assertTrue(page.text().contains("<title>minihttp-server</title>"));

        assertEquals("text/css; charset=utf-8", get("/styles.css").contentType());
        assertEquals("application/javascript; charset=utf-8", get("/app.js").contentType());
    }

    @Test
    @DisplayName("images travel as bytes and Content-Length matches the file on disk")
    void servesImagesByteForByte() throws IOException {
        Answer png = get("/img/logo.png");
        assertEquals(200, png.status());
        assertEquals("image/png", png.contentType());
        assertEquals(Files.size(PUBLIC_DIR.resolve("img/logo.png")), png.body().length);
        assertEquals(png.body().length, png.connection().getContentLength());

        Answer jpg = get("/img/photo.jpg");
        assertEquals("image/jpeg", jpg.contentType());
        assertEquals(Files.size(PUBLIC_DIR.resolve("img/photo.jpg")), jpg.body().length);
    }

    @Test
    void servicesAnswerJson() throws IOException {
        Answer greeting = get("/app/hello?name=Ana%20Mar%C3%ADa");
        assertEquals(200, greeting.status());
        assertEquals("application/json; charset=utf-8", greeting.contentType());
        assertTrue(greeting.text().contains("Hello, Ana María!"), greeting.text());

        assertTrue(get("/app/square?value=9").text().contains("\"square\":81"));
        assertTrue(get("/app/time").text().contains("\"epochMillis\""));
        assertTrue(get("/health").text().contains("\"status\":\"UP\""));
    }

    @Test
    void invalidInputGivesAClientError() throws IOException {
        Answer answer = get("/app/square?value=hola");
        assertEquals(400, answer.status());
        assertEquals("application/json; charset=utf-8", answer.contentType());
        assertTrue(answer.text().contains("is not a number"), answer.text());

        assertEquals(400, get("/app/hello").status());
    }

    @Test
    void missingResourceIsNotFound() throws IOException {
        assertEquals(404, get("/there-is-no-such-page.html").status());
    }

    @Test
    void unsupportedMethodIsRejectedWithAllow() throws IOException {
        Answer answer = request("POST", "/");
        assertEquals(405, answer.status());
        assertEquals("GET", answer.connection().getHeaderField("Allow"));
    }

    @Test
    void pathTraversalIsRejected() throws IOException {
        assertEquals("HTTP/1.1 403 Forbidden", rawStatusLine("/../pom.xml"));
        assertEquals("HTTP/1.1 403 Forbidden", rawStatusLine("/img/../../pom.xml"));
        assertEquals("HTTP/1.1 403 Forbidden", rawStatusLine("/img/%2e%2e/%2e%2e/pom.xml"));
        assertEquals("HTTP/1.1 403 Forbidden", rawStatusLine("/.gitignore"));
    }

    @Test
    @DisplayName("a malformed request does not stop the server")
    void malformedRequestDoesNotKillTheServer() throws IOException {
        try (Socket socket = new Socket("localhost", URI.create(baseUrl).getPort())) {
            socket.getOutputStream().write("NOT AN HTTP REQUEST\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String answer = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(answer.startsWith("HTTP/1.1 400"), answer);
        }
        assertEquals(200, get("/health").status());
    }

    @Test
    @DisplayName("ten consecutive requests in a single server run")
    void handlesTenConsecutiveRequests() throws IOException {
        for (int i = 1; i <= 10; i++) {
            assertEquals(200, get("/app/square?value=" + i).status());
        }
        assertEquals(200, get("/").status());
    }

    @Test
    @DisplayName("requests are served one after another, never at the same time")
    void requestsAreServedSequentially() throws Exception {
        long startedAt = System.currentTimeMillis();
        // A slow request is launched from another thread; the second one cannot be
        // served until the first finishes, so the total is about the sum of both.
        Thread slowClient = new Thread(() -> {
            try {
                get("/app/slow?millis=1500");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        slowClient.start();
        Thread.sleep(200);

        assertEquals(200, get("/app/slow?millis=1500").status());
        slowClient.join();

        long elapsed = System.currentTimeMillis() - startedAt;
        assertTrue(elapsed >= 3000, "expected the two slow requests to queue up, took " + elapsed + " ms");
    }
}
