package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticFilesTest {

    @TempDir
    Path sandbox;

    private Path publicDir;
    private StaticFiles staticFiles;
    private byte[] pngBytes;

    @BeforeEach
    void setUp() throws IOException {
        publicDir = Files.createDirectories(sandbox.resolve("public"));
        Files.writeString(publicDir.resolve("index.html"), "<html>home</html>", StandardCharsets.UTF_8);
        // A byte 0x89 is what a PNG starts with: it must survive untouched.
        pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, (byte) 0xFF, 0x00};
        Files.createDirectories(publicDir.resolve("img"));
        Files.write(publicDir.resolve("img/logo.png"), pngBytes);

        // A file that lives outside the public area and must never be served.
        Files.writeString(sandbox.resolve("secret.txt"), "do not serve me", StandardCharsets.UTF_8);

        staticFiles = new StaticFiles(publicDir);
    }

    @Test
    @DisplayName("the root path serves the home page")
    void rootServesIndex() throws Exception {
        HttpResponse response = staticFiles.read("/");

        assertEquals(200, response.getStatus());
        assertEquals("text/html; charset=utf-8", response.getContentType());
        assertEquals("<html>home</html>", new String(response.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("an image keeps its bytes and its length is the byte count")
    void imagesAreServedAsBytes() throws Exception {
        HttpResponse response = staticFiles.read("/img/logo.png");

        assertEquals("image/png", response.getContentType());
        assertArrayEquals(pngBytes, response.getBody());
        assertEquals(pngBytes.length, response.getContentLength());
    }

    @Test
    void missingFileIsNotFound() {
        assertEquals(404, assertThrows(HttpException.class, () -> staticFiles.read("/nope.html")).getStatus());
    }

    @Test
    void unsupportedTypeIsNotFound() {
        assertEquals(404, assertThrows(HttpException.class, () -> staticFiles.read("/data.exe")).getStatus());
    }

    @Test
    @DisplayName("a traversal attempt is rejected and discloses nothing")
    void traversalIsForbidden() {
        for (String attack : new String[]{"/../secret.txt", "/img/../../secret.txt",
                "/./../secret.txt", "/..%2fsecret.txt".replace("%2f", "/")}) {
            HttpException failure = assertThrows(HttpException.class, () -> staticFiles.read(attack),
                    "should have rejected " + attack);
            assertEquals(403, failure.getStatus(), attack);
        }
    }

    @Test
    void backslashAndHiddenFilesAreRejected() {
        assertEquals(403, assertThrows(HttpException.class,
                () -> staticFiles.read("/..\\secret.txt")).getStatus());
        assertEquals(403, assertThrows(HttpException.class,
                () -> staticFiles.read("/.env")).getStatus());
    }

    @Test
    void normalizeResolvesWelcomeFiles() throws Exception {
        assertEquals("index.html", StaticFiles.normalize("/"));
        assertEquals("index.html", StaticFiles.normalize(""));
        assertEquals("img/index.html", StaticFiles.normalize("/img/"));
        assertEquals("img/logo.png", StaticFiles.normalize("/img//logo.png"));
        assertEquals("img/logo.png", StaticFiles.normalize("/./img/logo.png"));
    }

    @Test
    void normalizeRejectsUnsafePaths() {
        assertEquals(403, assertThrows(HttpException.class, () -> StaticFiles.normalize("/../etc/passwd")).getStatus());
        assertEquals(403, assertThrows(HttpException.class, () -> StaticFiles.normalize("/C:/windows")).getStatus());
        assertEquals(400, assertThrows(HttpException.class, () -> StaticFiles.normalize("/a\u0000b.html")).getStatus());
    }
}
