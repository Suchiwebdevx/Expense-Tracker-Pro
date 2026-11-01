package com.expensetracker.web;

import com.expensetracker.model.Expense;
import com.expensetracker.service.Summary;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A very small JSON reader and writer, written by hand so the project needs no libraries.
 * It reads only flat objects like {"title":"Tea","amount":200} and writes our own objects.
 */
public final class Json {

    private Json() {
    }

    // ---------- writing ----------

    public static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    public static String toJson(Expense e) {
        return "{\"id\":" + e.getId()
                + ",\"title\":" + quote(e.getTitle())
                + ",\"amount\":" + e.getAmount().toPlainString()
                + ",\"category\":" + quote(e.getCategory())
                + ",\"date\":" + quote(e.getDate().toString()) + "}";
    }

    public static String toJson(List<Expense> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(toJson(list.get(i)));
        }
        return sb.append(']').toString();
    }

    public static String toJson(Summary s) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"total\":").append(s.total().toPlainString());
        sb.append(",\"count\":").append(s.count());
        sb.append(",\"average\":").append(s.average().toPlainString());
        sb.append(",\"highest\":").append(s.highest() == null ? "null" : toJson(s.highest()));
        sb.append(",\"byCategory\":[");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> en : s.byCategory().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"category\":").append(quote(en.getKey()))
              .append(",\"amount\":").append(en.getValue().toPlainString()).append('}');
        }
        return sb.append("]}").toString();
    }

    public static String stringList(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(items.get(i)));
        }
        return sb.append(']').toString();
    }

    public static String error(String message, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{\"error\":").append(quote(message));
        if (fields != null && !fields.isEmpty()) {
            sb.append(",\"fields\":{");
            boolean first = true;
            for (Map.Entry<String, String> en : fields.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(en.getKey())).append(':').append(quote(en.getValue()));
            }
            sb.append('}');
        }
        return sb.append('}').toString();
    }

    // ---------- reading ----------

    /**
     * Reads a flat JSON object. Values become String, BigDecimal, Boolean or null.
     * Throws IllegalArgumentException if the text is not valid or too complex.
     */
    public static Map<String, Object> parseObject(String text) {
        return new Parser(text).parseTopLevel();
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        Map<String, Object> parseTopLevel() {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
            } else {
                while (true) {
                    skipWs();
                    String key = parseString();
                    skipWs();
                    expect(':');
                    skipWs();
                    map.put(key, parseValue());
                    skipWs();
                    char c = next();
                    if (c == ',') {
                        continue;
                    }
                    if (c == '}') {
                        break;
                    }
                    throw error("Expected ',' or '}'");
                }
            }
            skipWs();
            if (pos != s.length()) {
                throw error("Unexpected text after the JSON object");
            }
            return map;
        }

        private Object parseValue() {
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == '{' || c == '[') {
                throw error("Only flat objects with simple values are supported");
            }
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return parseNumber();
        }

        private BigDecimal parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            int digits = 0;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                pos++;
                digits++;
            }
            if (digits == 0) {
                throw error("Invalid value");
            }
            if (pos < s.length() && s.charAt(pos) == '.') {
                pos++;
                int frac = 0;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                    frac++;
                }
                if (frac == 0) {
                    throw error("Invalid number");
                }
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                throw error("Exponent numbers are not supported");
            }
            if (pos - start > 30) {
                throw error("Number is too long");
            }
            return new BigDecimal(s.substring(start, pos));
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw error("Control character inside a string");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw error("Bad \\u escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Bad \\u escape");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("Bad escape \\" + e);
                }
            }
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= s.length()) {
                throw error("Unexpected end of JSON");
            }
            return s.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                pos--;
                throw error("Expected '" + c + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " (at position " + pos + ")");
        }
    }
}
