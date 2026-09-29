package edu.escuelaing.arep.httpserver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Sequential HTTP server: the listening socket stays open and connections are
 * accepted one after another. Each client socket is fully served and closed
 * before the next connection is accepted.
 *
 * <p>No thread, executor or queue is used anywhere in this class: the lab asks
 * for that limit to stay visible.</p>
 */
public class HttpServer {

    /** Default port; override with the PORT environment variable or the first argument. */
    public static final int DEFAULT_PORT = 35000;

    /** A client that opens a socket and never finishes its request must not block the loop forever. */
    private static final int CLIENT_READ_TIMEOUT_MS = 15_000;

    private final int port;
    private final StaticFiles staticFiles;
    private final Services services;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public HttpServer(int port) {
        this(port, new StaticFiles(), new Services());
    }

    public HttpServer(int port, StaticFiles staticFiles, Services services) {
        this.port = port;
        this.staticFiles = staticFiles;
        this.services = services;
    }

    public static void main(String[] args) throws IOException {
        new HttpServer(resolvePort(args)).start();
    }

    /**
     * Resolves the listening port from the first command-line argument, then the
     * {@code PORT} environment variable, then the default.
     */
    static int resolvePort(String[] args) {
        String candidate = (args != null && args.length > 0) ? args[0] : System.getenv("PORT");
        if (candidate == null || candidate.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int value = Integer.parseInt(candidate.trim());
            if (value < 1 || value > 65535) {
                throw new NumberFormatException("out of range");
            }
            return value;
        } catch (NumberFormatException e) {
            System.err.println("Invalid port '" + candidate + "', using " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /** @return the port actually bound, useful when the server was started on port 0 */
    public int getBoundPort() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    /**
     * Opens the listening socket.
     *
     * <p>Binding without an address means every interface, so the instance is
     * reachable from outside the machine once the firewall allows the port.</p>
     *
     * @return the port that was actually bound
     */
    public int bind() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("minihttp-server listening on http://0.0.0.0:" + getBoundPort());
        System.out.println("Sequential mode: one connection at a time.");
        System.out.println("Public resources: " + staticFiles.describeSource());
        return getBoundPort();
    }

    /** Binds if needed and serves connections until {@link #stop()} is called. */
    public void start() throws IOException {
        if (serverSocket == null) {
            bind();
        }
        acceptLoop();
    }

    /** The whole server: accept one connection, serve it, close it, accept the next. */
    public void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                serve(client);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    /** Closes the listening socket so {@link #start()} returns. */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Could not close the listening socket: " + e.getMessage());
        }
    }

    /**
     * Serves exactly one connection and closes it. A malformed or failing
     * request produces an error response; it never stops the server.
     */
    private void serve(Socket client) {
        long startedAt = System.currentTimeMillis();
        String summary = "-";
        try (Socket socket = client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);

            HttpRequest request = null;
            HttpResponse response;
            try {
                request = HttpRequest.parse(in);
                if (request == null) {
                    return; // The peer opened a socket and closed it without asking anything.
                }
                summary = request.toString();
                response = route(request);
            } catch (HttpException e) {
                response = errorResponse(e.getStatus(), e.getMessage(), request);
            } catch (RuntimeException e) {
                System.err.println("Unhandled failure while serving " + summary + ": " + e);
                response = errorResponse(500, "The server could not complete the request", request);
            }

            response.writeTo(out);
            log(summary, response, startedAt);
        } catch (SocketTimeoutException e) {
            System.err.println("Client timed out before finishing its request");
        } catch (IOException e) {
            System.err.println("I/O failure while serving " + summary + ": " + e.getMessage());
        }
    }

    /**
     * Routing, deliberately hardcoded: one explicit condition per special URL and
     * everything else is a public resource.
     *
     * <p>No framework, no reflection, no router table. A framework would end up
     * generalising exactly these five conditions, so they are written out here.</p>
     */
    HttpResponse route(HttpRequest request) throws HttpException {
        if (!"GET".equals(request.getMethod())) {
            throw HttpException.methodNotAllowed(request.getMethod());
        }

        String path = request.getPath();
        if (path.equals("/app/hello")) {
            return services.greeting(request);
        }
        if (path.equals("/app/square")) {
            return services.square(request);
        }
        if (path.equals("/app/time")) {
            return services.serverTime();
        }
        if (path.equals("/app/slow")) {
            return services.slow(request);
        }
        if (path.equals("/health")) {
            return services.health();
        }
        return staticFiles.read(path);
    }

    /**
     * Builds the error response. A failing service answers JSON, because its
     * caller is the JavaScript client; anything else answers a small HTML page,
     * because its caller is the browser address bar.
     */
    private HttpResponse errorResponse(int status, String message, HttpRequest request) {
        String reason = HttpResponse.reason(status);
        String path = request == null ? "" : request.getPath();
        boolean serviceCall = path.startsWith("/app/") || path.equals("/health");

        HttpResponse response;
        if (serviceCall) {
            response = HttpResponse.json(status, Json.object()
                    .put("error", reason)
                    .put("status", status)
                    .put("message", message)
                    .build());
        } else {
            response = HttpResponse.html(status,
                    "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                            + "<title>" + status + " " + reason + "</title>"
                            + "<style>body{margin:0;min-height:100vh;display:flex;align-items:center;"
                            + "justify-content:center;background:#0f172a;color:#e5ecff;"
                            + "font-family:'Segoe UI',system-ui,sans-serif}"
                            + "div{max-width:34rem;padding:28px 32px;border:1px solid #27324f;"
                            + "border-radius:12px;background:#16203a}"
                            + "h1{margin:0 0 8px;font-size:1.6rem}p{color:#94a3b8}"
                            + "a{color:#38bdf8}</style></head><body><div><h1>" + status + " " + reason
                            + "</h1><p>" + escapeHtml(message) + "</p>"
                            + "<p><a href=\"/\">Back to the home page</a></p></div></body></html>");
        }
        if (status == 405) {
            response.header("Allow", "GET");
        }
        return response;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void log(String summary, HttpResponse response, long startedAt) {
        System.out.printf("%s -> %d %s (%d bytes, %d ms)%n", summary, response.getStatus(),
                response.getContentType(), response.getContentLength(),
                System.currentTimeMillis() - startedAt);
    }
}
