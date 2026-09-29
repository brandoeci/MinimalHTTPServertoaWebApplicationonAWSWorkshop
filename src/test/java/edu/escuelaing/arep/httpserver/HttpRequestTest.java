package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpRequestTest {

    /** Builds a request out of a raw request head, the way the socket would deliver it. */
    static HttpRequest parse(String raw) throws Exception {
        return HttpRequest.parse(new BufferedReader(new StringReader(raw)));
    }

    @Test
    void parsesRequestLineAndQuery() throws Exception {
        HttpRequest request = parse("GET /app/hello?name=Ana%20Mar%C3%ADa HTTP/1.1\r\nHost: localhost\r\n\r\n");

        assertEquals("GET", request.getMethod());
        assertEquals("/app/hello", request.getPath());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("Ana María", request.getQueryParam("name"));
        assertEquals("localhost", request.getHeader("host"));
    }

    @Test
    @DisplayName("header names are case insensitive")
    void headerLookupIgnoresCase() throws Exception {
        HttpRequest request = parse("GET / HTTP/1.1\r\nUser-Agent: JUnit\r\n\r\n");
        assertEquals("JUnit", request.getHeader("User-Agent"));
        assertEquals("JUnit", request.getHeader("user-agent"));
    }

    @Test
    void keepsTheMethodSoTheServerCanRejectIt() throws Exception {
        assertEquals("POST", parse("POST /app/hello HTTP/1.1\r\n\r\n").getMethod());
    }

    @Test
    void anEmptyConnectionIsNotARequest() throws Exception {
        assertNull(parse(""));
    }

    @Test
    void rejectsMalformedRequestLine() {
        assertEquals(400, assertThrows(HttpException.class, () -> parse("GET\r\n\r\n")).getStatus());
        assertEquals(400, assertThrows(HttpException.class, () -> parse("GET / FTP/1.0\r\n\r\n")).getStatus());
        assertEquals(400, assertThrows(HttpException.class,
                () -> parse("GET http://other/ HTTP/1.1\r\n\r\n")).getStatus());
    }

    @Test
    void missingParameterIsABadRequest() throws Exception {
        HttpRequest request = parse("GET /app/hello?name= HTTP/1.1\r\n\r\n");
        assertEquals(400, assertThrows(HttpException.class, () -> request.requireQueryParam("name")).getStatus());
    }
}
