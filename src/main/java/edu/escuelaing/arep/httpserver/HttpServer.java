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
 * <p>There is no thread, executor or queue anywhere in this class. That is the
 * point of the laboratory: the limit must stay visible.</p>
 */
public class HttpServer {

    /** Default port; override with the PORT environment variable or the first argument. */
    public static final int DEFAULT_PORT = 35000;

    /** A client that opens a socket and never finishes its request must not block the loop forever. */
    private static final int CLIENT_READ_TIMEOUT_MS = 15_000;

    private final int port;
    private final StaticFiles staticFiles;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public HttpServer(int port) {
        this(port, new StaticFiles());
    }

    public HttpServer(int port, StaticFiles staticFiles) {
        this.port = port;
        this.staticFiles = staticFiles;
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

    /** Binds the listening socket and serves connections until {@link #stop()} is called. */
    public void start() throws IOException {
        // Binding without an address means every interface, so the instance is
        // reachable from outside the machine once the firewall allows the port.
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("minihttp-server listening on http://0.0.0.0:" + getBoundPort());
        System.out.println("Sequential mode: one connection at a time.");
        System.out.println("Public resources: " + staticFiles.describeSource());

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
     * Routing: only GET is accepted, and every path refers to a public resource.
     * The hardcoded services are added in the next step.
     */
    private HttpResponse route(HttpRequest request) throws HttpException {
        if (!"GET".equals(request.getMethod())) {
            throw HttpException.methodNotAllowed(request.getMethod());
        }
        return staticFiles.read(request.getPath());
    }

    private HttpResponse errorResponse(int status, String message, HttpRequest request) {
        String reason = HttpResponse.reason(status);
        HttpResponse response = HttpResponse.html(status,
                "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + status + " " + reason
                        + "</title></head><body><h1>" + status + " " + reason + "</h1><p>"
                        + escapeHtml(message) + "</p></body></html>");
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
