package edu.escuelaing.arep.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Serves the public resources: the HTML page, its JavaScript, its stylesheet and
 * the images.
 *
 * <p>Two sources are supported, resolved once at startup:</p>
 * <ul>
 *   <li>a directory on disk, when {@code PUBLIC_DIR} (environment variable or
 *       {@code public.dir} system property) points to one, or when a
 *       {@code public} / {@code src/main/resources/public} directory exists next
 *       to the process;</li>
 *   <li>otherwise the {@code /public} folder packaged inside the jar, so a single
 *       artifact can be copied to EC2.</li>
 * </ul>
 *
 * <p>Every resource is read as bytes. No resource is ever read as text, so an
 * image is never corrupted by a charset conversion and the content length is
 * always the real number of bytes.</p>
 */
public class StaticFiles {

    private static final String CLASSPATH_ROOT = "/public/";
    private static final String WELCOME_FILE = "index.html";

    /** {@code null} means "read from the classpath". */
    private final Path root;

    public StaticFiles() {
        this(resolveRoot());
    }

    public StaticFiles(Path root) {
        this.root = root;
    }

    private static Path resolveRoot() {
        String configured = System.getProperty("public.dir", System.getenv("PUBLIC_DIR"));
        if (configured != null && !configured.isBlank()) {
            Path candidate = Paths.get(configured.trim());
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
            System.err.println("PUBLIC_DIR '" + configured + "' is not a directory; ignoring it");
        }
        for (String fallback : new String[]{"public", "src/main/resources/public"}) {
            Path candidate = Paths.get(fallback);
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    /** @return a human readable description of where the resources come from */
    public String describeSource() {
        return root == null ? "classpath:" + CLASSPATH_ROOT : root.toString();
    }

    /**
     * Reads one public resource.
     *
     * @param requestPath the decoded request path, for example {@code /app.js}
     * @return the response, with the content type derived from the extension
     * @throws HttpException 403 when the path tries to leave the public area,
     *                       404 when the resource does not exist or its type is
     *                       not supported
     */
    public HttpResponse read(String requestPath) throws HttpException {
        String relative = normalize(requestPath);

        String contentType = MimeTypes.forPath(relative);
        if (contentType == null) {
            throw HttpException.notFound("Resource type not supported: " + relative);
        }

        byte[] bytes = root == null ? readFromClasspath(relative) : readFromDirectory(relative);
        // The length is taken from the bytes themselves, never from a character count.
        return HttpResponse.of(200, contentType, bytes);
    }

    private byte[] readFromDirectory(String relative) throws HttpException {
        Path resolved = root.resolve(relative).normalize();
        // Second line of defence: whatever the normalisation produced, the file
        // must still live inside the public area.
        if (!resolved.startsWith(root)) {
            throw new HttpException(403, "Access outside the public resources area is forbidden");
        }
        if (!Files.isRegularFile(resolved)) {
            throw HttpException.notFound("No such resource: /" + relative);
        }
        try {
            // Resolve symbolic links before reading, so a link cannot point outside.
            if (!resolved.toRealPath().startsWith(root.toRealPath())) {
                throw new HttpException(403, "Access outside the public resources area is forbidden");
            }
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new HttpException(500, "Could not read the resource");
        }
    }

    private byte[] readFromClasspath(String relative) throws HttpException {
        try (InputStream in = StaticFiles.class.getResourceAsStream(CLASSPATH_ROOT + relative)) {
            if (in == null) {
                throw HttpException.notFound("No such resource: /" + relative);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new HttpException(500, "Could not read the resource");
        }
    }

    /**
     * Turns a request path into a safe relative resource name.
     *
     * <p>The root path and any directory become the welcome file. Empty and
     * {@code .} segments are dropped. A {@code ..} segment, a backslash, a NUL
     * byte, an absolute Windows path or a hidden file are rejected instead of
     * being resolved: this server never walks outside its public area.</p>
     */
    static String normalize(String requestPath) throws HttpException {
        if (requestPath == null || requestPath.isEmpty()) {
            return WELCOME_FILE;
        }
        if (requestPath.indexOf('\0') >= 0) {
            throw HttpException.badRequest("Illegal character in path");
        }
        if (requestPath.indexOf('\\') >= 0) {
            throw new HttpException(403, "Illegal path separator");
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : requestPath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new HttpException(403, "Path traversal is not allowed");
            }
            if (segment.startsWith(".")) {
                throw new HttpException(403, "Hidden resources are not served");
            }
            if (segment.contains(":")) {
                throw new HttpException(403, "Illegal path segment");
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            return WELCOME_FILE;
        }
        String joined = String.join("/", segments);
        return requestPath.endsWith("/") ? joined + "/" + WELCOME_FILE : joined;
    }
}
