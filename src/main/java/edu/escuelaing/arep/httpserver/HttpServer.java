package edu.escuelaing.arep.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Baseline of section 4.4 of the networking guide: accept ONE TCP connection,
 * read ONE HTTP request, answer with a small HTML document and stop.
 *
 * <p>This class is the starting point of the laboratory. It exists so the
 * protocol exchange (request line, headers, blank line, body) can be observed
 * before any extra behaviour is added.</p>
 */
public class HttpServer {

    private static final int PORT = 35000;

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Listening on http://localhost:" + PORT);

            Socket client = serverSocket.accept();
            System.out.println("Connection accepted from " + client.getRemoteSocketAddress());

            try (client;
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(client.getOutputStream(), false, StandardCharsets.UTF_8)) {

                // Echo the whole request head so the exchange can be inspected.
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    System.out.println("> " + line);
                }

                String body = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                        + "<title>Minimal server</title></head>"
                        + "<body><h1>It works</h1></body></html>";

                out.print("HTTP/1.1 200 OK\r\n");
                out.print("Content-Type: text/html; charset=utf-8\r\n");
                out.print("Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n");
                out.print("Connection: close\r\n");
                out.print("\r\n");
                out.print(body);
                out.flush();
            }
            System.out.println("One request served. Shutting down.");
        }
    }
}
