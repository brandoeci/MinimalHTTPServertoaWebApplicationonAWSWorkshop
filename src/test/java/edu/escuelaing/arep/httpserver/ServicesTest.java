package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServicesTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-29T15:30:00Z"), ZoneId.of("America/Bogota"));

    private final Services services = new Services(FIXED_CLOCK);

    private static String bodyOf(HttpResponse response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private static HttpRequest get(String target) throws Exception {
        return HttpRequestTest.parse("GET " + target + " HTTP/1.1\r\n\r\n");
    }

    @Test
    void greetingUsesTheSuppliedName() throws Exception {
        HttpResponse response = services.greeting(get("/app/hello?name=Ana"));

        assertEquals(200, response.getStatus());
        assertEquals("application/json; charset=utf-8", response.getContentType());
        assertEquals("{\"service\":\"greeting\",\"name\":\"Ana\",\"greeting\":\"Hello, Ana!\"}",
                bodyOf(response));
    }

    @Test
    @DisplayName("a name with quotes does not break the JSON")
    void greetingEscapesTheName() throws Exception {
        String body = bodyOf(services.greeting(get("/app/hello?name=%22%3Cb%3E")));
        assertTrue(body.contains("\\\""), body);
        assertTrue(body.contains("\\u003c"), body);
    }

    @Test
    void greetingNeedsAName() throws Exception {
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.greeting(get("/app/hello"))).getStatus());
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.greeting(get("/app/hello?name=%20"))).getStatus());
    }

    @Test
    void greetingRejectsAVeryLongName() throws Exception {
        String longName = "a".repeat(61);
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.greeting(get("/app/hello?name=" + longName))).getStatus());
    }

    @Test
    void squareComputesTheSquare() throws Exception {
        assertEquals("{\"service\":\"square\",\"value\":7,\"square\":49}",
                bodyOf(services.square(get("/app/square?value=7"))));
        assertEquals("{\"service\":\"square\",\"value\":-2.5,\"square\":6.25}",
                bodyOf(services.square(get("/app/square?value=-2.5"))));
    }

    @Test
    void squareRejectsSomethingThatIsNotANumber() throws Exception {
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.square(get("/app/square?value=abc"))).getStatus());
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.square(get("/app/square"))).getStatus());
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.square(get("/app/square?value=NaN"))).getStatus());
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.square(get("/app/square?value=1e200"))).getStatus());
    }

    @Test
    @DisplayName("the time comes from the server clock, not from the browser")
    void serverTimeUsesTheServerClock() {
        String body = bodyOf(services.serverTime());

        assertTrue(body.contains("\"iso\":\"2026-09-29T10:30:00-05:00\""), body);
        assertTrue(body.contains("\"zone\":\"America/Bogota\""), body);
        assertTrue(body.contains("\"epochMillis\":1790695800000"), body);
    }

    @Test
    void healthIsASmallSuccessfulResponse() {
        HttpResponse response = services.health();

        assertEquals(200, response.getStatus());
        assertEquals("{\"status\":\"UP\",\"uptimeMillis\":0}", bodyOf(response));
    }

    @Test
    void slowServiceValidatesItsDelay() throws Exception {
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.slow(get("/app/slow?millis=abc"))).getStatus());
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.slow(get("/app/slow?millis=-1"))).getStatus());
        assertEquals(400, assertThrows(HttpException.class,
                () -> services.slow(get("/app/slow?millis=999999"))).getStatus());
    }

    @Test
    void slowServiceAnswersWhenItFinishes() throws Exception {
        HttpResponse response = services.slow(get("/app/slow?millis=0"));

        assertEquals(200, response.getStatus());
        assertTrue(bodyOf(response).contains("\"requestedMillis\":0"), bodyOf(response));
    }
}
