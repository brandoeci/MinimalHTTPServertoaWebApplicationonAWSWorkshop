package edu.escuelaing.arep.httpserver;

/**
 * Signals a controlled HTTP error. Any layer can throw it and the request loop
 * turns it into a proper response instead of letting the server die.
 */
public class HttpException extends Exception {

    private final int status;

    public HttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static HttpException badRequest(String message) {
        return new HttpException(400, message);
    }

    public static HttpException notFound(String message) {
        return new HttpException(404, message);
    }

    public static HttpException methodNotAllowed(String method) {
        return new HttpException(405, "Method " + method + " is not supported by this server");
    }
}
