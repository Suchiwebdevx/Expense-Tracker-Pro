package com.expensetracker.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.expensetracker.model.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void quoteEscapesSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\"", Json.quote("a\"b\\c\nd\te"));
        assertEquals("\"\\u0001\"", Json.quote("\u0001"));
        assertEquals("null", Json.quote(null));
    }

    @Test
    void expenseIsWrittenAsJson() {
        Expense e = new Expense(7, "Tea \"special\"", new BigDecimal("200.00"), "Food", LocalDate.parse("2026-10-31"));
        assertEquals("{\"id\":7,\"title\":\"Tea \\\"special\\\"\",\"amount\":200.00,\"category\":\"Food\",\"date\":\"2026-10-31\"}",
                Json.toJson(e));
    }

    @Test
    void parsesAFlatObject() {
        Map<String, Object> m = Json.parseObject(" { \"title\" : \"Tea\", \"amount\": 20.50, \"ok\": true, \"none\": null } ");
        assertEquals("Tea", m.get("title"));
        assertEquals(new BigDecimal("20.50"), m.get("amount"));
        assertEquals(Boolean.TRUE, m.get("ok"));
        assertNull(m.get("none"));
        assertTrue(m.containsKey("none"));
    }

    @Test
    void parsesEscapesAndUnicode() {
        Map<String, Object> m = Json.parseObject("{\"t\":\"a\\n\\\"b\\\" \\u0041 \u20b9\"}");
        assertEquals("a\n\"b\" A \u20b9", m.get("t"));
    }

    @Test
    void parsesEmptyObject() {
        assertTrue(Json.parseObject("{}").isEmpty());
    }

    @Test
    void rejectsBrokenJson() {
        String[] bad = {"", "{", "[]", "{\"a\"}", "{\"a\":}", "{\"a\":1,}", "{\"a\":1} extra", "{\"a\":\"x}", "{a:1}",
            "{\"a\":\"\\q\"}", "{\"a\":\"\\u12\"}", "{\"a\":-}", "{\"a\":1.}"};
        for (String text : bad) {
            assertThrows(IllegalArgumentException.class, () -> Json.parseObject(text));
        }
    }

    @Test
    void rejectsNestedValuesAndExponents() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":{\"b\":1}}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":[1]}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":1e999999999}"));
    }

    @Test
    void errorJsonContainsFields() {
        assertEquals("{\"error\":\"Bad\",\"fields\":{\"title\":\"Required\"}}",
                Json.error("Bad", Map.of("title", "Required")));
        assertEquals("{\"error\":\"Bad\"}", Json.error("Bad", null));
    }
}
