package edu.escuelaing.arep.httpserver;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The four hardcoded services of the laboratory, plus a deliberately slow one
 * used to observe the sequential limitation.
 *
 * <p>Each method is a plain function of the request: nothing is stored between
 * calls, so the server holds no user state at all.</p>
 */
public class Services {

    /** Longest accepted name, so a request cannot grow the response without bound. */
    private static final int MAX_NAME_LENGTH = 60;

    /** Upper bound for the slow service, so the single-threaded loop is never blocked forever. */
    static final long MAX_SLOW_MILLIS = 20_000L;

    private static final long DEFAULT_SLOW_MILLIS = 5_000L;

    private final Clock clock;
    private final long startedAtMillis;

    public Services() {
        this(Clock.systemDefaultZone());
    }

    public Services(Clock clock) {
        this.clock = clock;
        this.startedAtMillis = clock.millis();
    }

    /**
     * {@code GET /app/hello?name=Ana} - a greeting built with the supplied name.
     *
     * @throws HttpException 400 when the name is missing, blank or too long
     */
    public HttpResponse greeting(HttpRequest request) throws HttpException {
        String name = request.requireQueryParam("name");
        if (name.length() > MAX_NAME_LENGTH) {
            throw HttpException.badRequest(
                    "The name must be at most " + MAX_NAME_LENGTH + " characters long");
        }
        // Json.string() escapes the value: an untrusted name never breaks the document.
        String body = Json.object()
                .put("service", "greeting")
                .put("name", name)
                .put("greeting", "Hello, " + name + "!")
                .build();
        return HttpResponse.json(200, body);
    }

    /**
     * {@code GET /app/square?value=7} - the square of the supplied number.
     *
     * @throws HttpException 400 when the value is missing or is not a finite number
     */
    public HttpResponse square(HttpRequest request) throws HttpException {
        String raw = request.requireQueryParam("value");
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw HttpException.badRequest("'" + raw + "' is not a number");
        }
        if (!Double.isFinite(value)) {
            throw HttpException.badRequest("The value must be a finite number");
        }
        double square = value * value;
        if (!Double.isFinite(square)) {
            throw HttpException.badRequest("The value is too large to be squared");
        }
        String body = Json.object()
                .put("service", "square")
                .put("value", value)
                .put("square", square)
                .build();
        return HttpResponse.json(200, body);
    }

    /**
     * {@code GET /app/time} - the current time as seen by the server, which is
     * what makes a remote deployment observable from the browser.
     */
    public HttpResponse serverTime() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        String body = Json.object()
                .put("service", "time")
                .put("iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .put("zone", now.getZone().getId())
                .put("epochMillis", now.toInstant().toEpochMilli())
                .build();
        return HttpResponse.json(200, body);
    }

    /** {@code GET /health} - a small successful response proving the process can serve requests. */
    public HttpResponse health() {
        String body = Json.object()
                .put("status", "UP")
                .put("uptimeMillis", clock.millis() - startedAtMillis)
                .build();
        return HttpResponse.json(200, body);
    }

    /**
     * {@code GET /app/slow?millis=5000} - sleeps before answering.
     *
     * <p>Used in section 6.2: while this request is being served, the accept loop
     * is not accepting anything else. Sleeping is not concurrency; it is the
     * absence of it made visible.</p>
     *
     * @throws HttpException 400 when the delay is not a number or is out of range
     */
    public HttpResponse slow(HttpRequest request) throws HttpException {
        long millis = DEFAULT_SLOW_MILLIS;
        String raw = request.getQueryParam("millis");
        if (raw != null && !raw.isBlank()) {
            try {
                millis = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                throw HttpException.badRequest("'" + raw + "' is not a whole number of milliseconds");
            }
            if (millis < 0 || millis > MAX_SLOW_MILLIS) {
                throw HttpException.badRequest(
                        "The delay must be between 0 and " + MAX_SLOW_MILLIS + " milliseconds");
            }
        }

        long startedAt = clock.millis();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpException(500, "The slow service was interrupted");
        }
        String body = Json.object()
                .put("service", "slow")
                .put("requestedMillis", millis)
                .put("elapsedMillis", clock.millis() - startedAt)
                .put("note", "While this request was being served the server accepted nothing else")
                .build();
        return HttpResponse.json(200, body);
    }
}
