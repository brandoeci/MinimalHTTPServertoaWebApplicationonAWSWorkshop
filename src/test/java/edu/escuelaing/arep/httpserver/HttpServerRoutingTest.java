package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checks which of the hardcoded conditions in route() answers each path. */
class HttpServerRoutingTest {

    private final HttpServer server = new HttpServer(0,
            new StaticFiles(Paths.get("src/main/resources/public")), new Services());

    private HttpResponse route(String method, String target) throws Exception {
        return server.route(HttpRequestTest.parse(method + " " + target + " HTTP/1.1\r\n\r\n"));
    }

    @Test
    void specialUrlsReachTheirService() throws Exception {
        assertTrue(new String(route("GET", "/app/hello?name=Ana").getBody(), StandardCharsets.UTF_8)
                .contains("\"greeting\""));
        assertTrue(new String(route("GET", "/app/square?value=3").getBody(), StandardCharsets.UTF_8)
                .contains("\"square\":9"));
        assertTrue(new String(route("GET", "/app/time").getBody(), StandardCharsets.UTF_8)
                .contains("\"zone\""));
        assertTrue(new String(route("GET", "/health").getBody(), StandardCharsets.UTF_8)
                .contains("\"status\":\"UP\""));
    }

    @Test
    void everythingElseIsAStaticResource() throws Exception {
        assertEquals("text/html; charset=utf-8", route("GET", "/").getContentType());
        assertEquals("application/javascript; charset=utf-8", route("GET", "/app.js").getContentType());
        assertEquals("image/png", route("GET", "/img/logo.png").getContentType());
        assertEquals("image/jpeg", route("GET", "/img/photo.jpg").getContentType());
    }

    @Test
    @DisplayName("a path that only looks like a service is still a missing resource")
    void unknownServicePathIsNotFound() {
        assertEquals(404, assertThrows(HttpException.class, () -> route("GET", "/app/nope")).getStatus());
    }

    @Test
    void onlyGetIsAccepted() {
        for (String method : new String[]{"POST", "PUT", "DELETE", "HEAD"}) {
            assertEquals(405, assertThrows(HttpException.class, () -> route(method, "/")).getStatus(), method);
        }
    }
}
