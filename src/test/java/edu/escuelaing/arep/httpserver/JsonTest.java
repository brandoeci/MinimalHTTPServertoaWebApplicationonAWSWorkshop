package edu.escuelaing.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void escapesQuotesAndBackslashes() {
        assertEquals("\"say \\\"hi\\\"\"", Json.string("say \"hi\""));
        assertEquals("\"C:\\\\temp\"", Json.string("C:\\temp"));
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("\"a\\nb\\tc\"", Json.string("a\nb\tc"));
        assertEquals("\"\\u0001\"", Json.string("\u0001"));
    }

    @Test
    @DisplayName("a value that looks like markup cannot close a tag")
    void escapesMarkup() {
        String json = Json.object().put("name", "<script>alert(1)</script>").build();
        assertFalse(json.contains("<"));
        assertEquals("{\"name\":\"\\u003cscript\\u003ealert(1)\\u003c/script\\u003e\"}", json);
    }

    @Test
    void wholeNumbersHaveNoDecimalPart() {
        assertEquals("25", Json.number(25.0));
        assertEquals("-3", Json.number(-3.0));
        assertEquals("6.25", Json.number(6.25));
        assertEquals("null", Json.number(Double.NaN));
    }

    @Test
    void buildsObjectsInInsertionOrder() {
        String json = Json.object()
                .put("service", "square")
                .put("value", 5.0)
                .put("ok", true)
                .put("count", 2L)
                .build();
        assertEquals("{\"service\":\"square\",\"value\":5,\"ok\":true,\"count\":2}", json);
    }
}
