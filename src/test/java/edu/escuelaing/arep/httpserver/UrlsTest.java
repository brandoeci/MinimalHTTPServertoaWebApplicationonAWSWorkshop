package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlsTest {

    @Test
    @DisplayName("percent escapes are decoded as UTF-8")
    void decodesPercentEscapes() throws Exception {
        assertEquals("Ana María", Urls.decodeQueryToken("Ana%20Mar%C3%ADa"));
    }

    @Test
    @DisplayName("+ is a space in the query string but a plus sign in a path")
    void plusDependsOnWhereItAppears() throws Exception {
        assertEquals("Juan Perez", Urls.decodeQueryToken("Juan+Perez"));
        assertEquals("/a+b.png", Urls.decodePath("/a+b.png"));
    }

    @Test
    @DisplayName("a broken escape is a bad request")
    void rejectsBrokenEscapes() {
        assertEquals(400, assertThrows(HttpException.class, () -> Urls.decodeQueryToken("%ZZ")).getStatus());
        assertEquals(400, assertThrows(HttpException.class, () -> Urls.decodeQueryToken("abc%4")).getStatus());
    }

    @Test
    void parsesQueryString() throws Exception {
        Map<String, String> params = Urls.parseQuery("name=Ana&value=7&empty=");
        assertEquals("Ana", params.get("name"));
        assertEquals("7", params.get("value"));
        assertEquals("", params.get("empty"));
        assertEquals(3, params.size());
    }

    @Test
    void emptyQueryGivesEmptyMap() throws Exception {
        assertTrue(Urls.parseQuery("").isEmpty());
        assertTrue(Urls.parseQuery(null).isEmpty());
    }

    @Test
    @DisplayName("a repeated parameter keeps the first value")
    void repeatedParameterKeepsFirst() throws Exception {
        assertEquals("1", Urls.parseQuery("value=1&value=2").get("value"));
    }
}
