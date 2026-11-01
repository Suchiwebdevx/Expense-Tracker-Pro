package com.expensetracker.web;

import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.NotFoundException;
import com.expensetracker.service.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The web layer. It turns HTTP requests into calls to ExpenseService and turns the
 * results back into JSON. It also serves the web page (index.html, style.css, app.js).
 *
 * REST API:
 *   GET    /api/health
 *   GET    /api/categories
 *   GET    /api/expenses?category=Food&month=2026-10
 *   POST   /api/expenses            body: {"title":"Tea","amount":"20","category":"Food","date":"2026-10-31"}
 *   GET    /api/expenses/{id}
 *   PUT    /api/expenses/{id}
 *   DELETE /api/expenses/{id}
 *   GET    /api/summary?month=2026-10
 *   GET    /api/export.csv?category=Food&month=2026-10
 */
public final class ApiServer {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Pattern EXPENSE_ID = Pattern.compile("/api/expenses/(\\d{1,18})");

    /** Only these files can be downloaded from the web folder. Nothing else is reachable. */
    private static final Map<String, String[]> STATIC_FILES = Map.of(
            "/", new String[] {"/web/index.html", "text/html; charset=utf-8"},
            "/index.html", new String[] {"/web/index.html", "text/html; charset=utf-8"},
            "/style.css", new String[] {"/web/style.css", "text/css; charset=utf-8"},
            "/app.js", new String[] {"/web/app.js", "text/javascript; charset=utf-8"});

    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ExpenseService service;

    public ApiServer(String host, int port, ExpenseService service) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdown();
    }

    /** The real port. Useful when the server was started with port 0 (any free port). */
    public int getPort() {
        return server.getAddress().getPort();
    }

    // ---------- request handling ----------

    private void handle(HttpExchange ex) {
        long start = System.nanoTime();
        int status = 500;
        try {
            status = route(ex);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error", e);
            try {
                status = sendJson(ex, 500, Json.error("Internal server error", null));
            } catch (IOException ignored) {
                // The client has probably gone away.
            }
        } finally {
            ex.close();
            LOG.info(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " -> " + status
                    + " (" + (System.nanoTime() - start) / 1_000_000 + " ms)");
        }
    }

    private int route(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.startsWith("/api/")) {
            try {
                return api(ex, ex.getRequestMethod(), path);
            } catch (ValidationException e) {
                return sendJson(ex, 400, Json.error("Please correct the highlighted fields", e.getFields()));
            } catch (NotFoundException e) {
                return sendJson(ex, 404, Json.error(e.getMessage(), null));
            } catch (BodyTooLargeException e) {
                return sendJson(ex, 413, Json.error("Request body is too large", null));
            } catch (UnsupportedTypeException e) {
                return sendJson(ex, 415, Json.error("Content-Type must be application/json", null));
            } catch (IllegalArgumentException e) {
                return sendJson(ex, 400, Json.error("Invalid JSON: " + e.getMessage(), null));
            }
        }
        return staticFile(ex, path);
    }

    private int api(HttpExchange ex, String method, String path) throws IOException {
        Map<String, String> q = query(ex.getRequestURI());

        switch (path) {
            case "/api/health":
                return allow(ex, method, "GET") ? sendJson(ex, 200, "{\"status\":\"UP\"}") : notAllowed(ex, "GET");
            case "/api/categories":
                return allow(ex, method, "GET")
                        ? sendJson(ex, 200, Json.stringList(ExpenseService.CATEGORIES)) : notAllowed(ex, "GET");
            case "/api/summary":
                return allow(ex, method, "GET")
                        ? sendJson(ex, 200, Json.toJson(service.summary(q.get("month")))) : notAllowed(ex, "GET");
            case "/api/export.csv":
                if (!allow(ex, method, "GET")) {
                    return notAllowed(ex, "GET");
                }
                ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"expenses.csv\"");
                return send(ex, 200, "text/csv; charset=utf-8",
                        service.exportCsv(q.get("category"), q.get("month")).getBytes(StandardCharsets.UTF_8));
            case "/api/expenses":
                if ("GET".equals(method)) {
                    return sendJson(ex, 200, Json.toJson(service.list(q.get("category"), q.get("month"))));
                }
                if ("POST".equals(method)) {
                    Map<String, Object> body = readJsonBody(ex);
                    Expense saved = service.add(text(body, "title"), text(body, "amount"),
                            text(body, "category"), text(body, "date"));
                    ex.getResponseHeaders().set("Location", "/api/expenses/" + saved.getId());
                    return sendJson(ex, 201, Json.toJson(saved));
                }
                return notAllowed(ex, "GET, POST");
            default:
                break;
        }

        Matcher m = EXPENSE_ID.matcher(path);
        if (m.matches()) {
            long id = Long.parseLong(m.group(1));
            switch (method) {
                case "GET":
                    return sendJson(ex, 200, Json.toJson(service.get(id)));
                case "PUT":
                    Map<String, Object> body = readJsonBody(ex);
                    Expense updated = service.update(id, text(body, "title"), text(body, "amount"),
                            text(body, "category"), text(body, "date"));
                    return sendJson(ex, 200, Json.toJson(updated));
                case "DELETE":
                    service.delete(id);
                    addSecurityHeaders(ex);
                    ex.sendResponseHeaders(204, -1);
                    return 204;
                default:
                    return notAllowed(ex, "GET, PUT, DELETE");
            }
        }
        return sendJson(ex, 404, Json.error("Unknown API address", null));
    }

    private int staticFile(HttpExchange ex, String path) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            return notAllowed(ex, "GET");
        }
        String[] file = STATIC_FILES.get(path);
        if (file == null) {
            return send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = ApiServer.class.getResourceAsStream(file[0])) {
            if (in == null) {
                return send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            }
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            return send(ex, 200, file[1], in.readAllBytes());
        }
    }

    // ---------- helpers ----------

    private static boolean allow(HttpExchange ex, String method, String expected) {
        return expected.equals(method);
    }

    private int notAllowed(HttpExchange ex, String allowed) throws IOException {
        ex.getResponseHeaders().set("Allow", allowed);
        return sendJson(ex, 405, Json.error("Method not allowed", null));
    }

    private Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json")) {
            throw new UnsupportedTypeException();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        try (InputStream in = ex.getRequestBody()) {
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY_BYTES) {
                    throw new BodyTooLargeException();
                }
            }
        }
        return Json.parseObject(out.toString(StandardCharsets.UTF_8));
    }

    /** Reads a field as text, whatever type it was in the JSON. Missing or null gives null. */
    private static String text(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).toPlainString();
        }
        return v.toString();
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            result.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return result;
    }

    private int sendJson(HttpExchange ex, int status, String json) throws IOException {
        return send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private int send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        addSecurityHeaders(ex);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
        return status;
    }

    private static void addSecurityHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'");
    }

    private static final class BodyTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class UnsupportedTypeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
