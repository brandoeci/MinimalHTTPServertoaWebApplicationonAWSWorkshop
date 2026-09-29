package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MimeTypesTest {

    @Test
    void mapsTheTypesUsedByThePage() {
        assertEquals("text/html; charset=utf-8", MimeTypes.forPath("/index.html"));
        assertEquals("text/css; charset=utf-8", MimeTypes.forPath("/styles.css"));
        assertEquals("application/javascript; charset=utf-8", MimeTypes.forPath("/app.js"));
        assertEquals("image/png", MimeTypes.forPath("/img/logo.png"));
        assertEquals("image/jpeg", MimeTypes.forPath("/img/photo.jpg"));
        assertEquals("image/jpeg", MimeTypes.forPath("/img/photo.jpeg"));
    }

    @Test
    @DisplayName("the extension is matched ignoring case")
    void extensionIsCaseInsensitive() {
        assertEquals("image/png", MimeTypes.forPath("/IMG/LOGO.PNG"));
    }

    @Test
    void unsupportedOrMissingExtensionHasNoType() {
        assertNull(MimeTypes.forPath("/data.exe"));
        assertNull(MimeTypes.forPath("/README"));
        assertNull(MimeTypes.forPath("/weird."));
        assertFalse(MimeTypes.isSupported("/pom.xml"));
        assertTrue(MimeTypes.isSupported("/index.html"));
    }
}
