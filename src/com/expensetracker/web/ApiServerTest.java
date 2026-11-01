package com.expensetracker.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.expensetracker.repository.InMemoryExpenseRepository;
import com.expensetracker.service.ExpenseService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Starts the real server on a free port and talks to it over HTTP, like a browser would. */
class ApiServerTest {

    private ApiServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void setUp() throws IOException {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC);
        ExpenseService service = new ExpenseService(new InMemoryExpenseRepository(), fixed);
        server = new ApiServer("127.0.0.1", 0, service); // port 0 = any free port
        server.start();
        base = "http://127.0.0.1:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send("GET", path, null, null);
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        return send("POST", path, json, "application/json");
    }

    private static final String TEA = "{\"title\":\"Tea\",\"amount\":\"200\",\"category\":\"Food\",\"date\":\"2026-10-31\"}";

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> r = get("/api/health");
        assertEquals(200, r.statusCode());
        assertEquals("{\"status\":\"UP\"}", r.body());
    }

    @Test
    void createReadUpdateDeleteWorkTogether() throws Exception {
        HttpResponse<String> created = postJson("/api/expenses", TEA);
        assertEquals(201, created.statusCode());
        assertEquals("/api/expenses/1", created.headers().firstValue("Location").orElse(""));
        assertTrue(created.body().contains("\"amount\":200.00"));

        assertTrue(get("/api/expenses").body().contains("\"title\":\"Tea\""));
        assertEquals(200, get("/api/expenses/1").statusCode());

        HttpResponse<String> updated = send("PUT", "/api/expenses/1",
                "{\"title\":\"Coffee\",\"amount\":25.5,\"category\":\"Food\",\"date\":\"2026-10-30\"}", "application/json");
        assertEquals(200, updated.statusCode());
        assertTrue(updated.body().contains("\"title\":\"Coffee\""));
        assertTrue(updated.body().contains("\"amount\":25.50"));

        assertEquals(204, send("DELETE", "/api/expenses/1", null, null).statusCode());
        assertEquals(404, get("/api/expenses/1").statusCode());
        assertEquals(404, send("DELETE", "/api/expenses/1", null, null).statusCode());
        assertEquals("[]", get("/api/expenses").body());
    }

    @Test
    void invalidInputReturns400WithFieldMessages() throws Exception {
        HttpResponse<String> r = postJson("/api/expenses", "{\"title\":\"\",\"amount\":\"-5\",\"category\":\"Food\",\"date\":\"2026-10-31\"}");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("\"fields\""));
        assertTrue(r.body().contains("\"title\":\"Title is required\""));
        assertTrue(r.body().contains("\"amount\":\"Amount must be greater than 0\""));
        assertEquals("[]", get("/api/expenses").body()); // nothing was saved
    }

    @Test
    void brokenJsonReturns400() throws Exception {
        assertEquals(400, postJson("/api/expenses", "{not json").statusCode());
        assertEquals(400, postJson("/api/expenses", "[1,2]").statusCode());
    }

    @Test
    void wrongContentTypeReturns415() throws Exception {
        assertEquals(415, send("POST", "/api/expenses", TEA, "text/plain").statusCode());
        assertEquals(415, send("POST", "/api/expenses", TEA, null).statusCode());
    }

    @Test
    void tooLargeBodyReturns413() throws Exception {
        String big = "{\"title\":\"" + "a".repeat(20000) + "\"}";
        assertEquals(413, postJson("/api/expenses", big).statusCode());
    }

    @Test
    void unknownAddressesAndMethods() throws Exception {
        assertEquals(404, get("/api/nothing").statusCode());
        assertEquals(404, get("/api/expenses/abc").statusCode());
        assertEquals(404, get("/secret.txt").statusCode());
        assertEquals(404, get("/../etc/passwd").statusCode());
        HttpResponse<String> r = send("DELETE", "/api/expenses", null, null);
        assertEquals(405, r.statusCode());
        assertEquals("GET, POST", r.headers().firstValue("Allow").orElse(""));
        assertEquals(405, send("POST", "/api/summary", "{}", "application/json").statusCode());
        assertEquals(405, send("POST", "/", "{}", "application/json").statusCode());
    }

    @Test
    void filtersAndSummaryWorkOverHttp() throws Exception {
        postJson("/api/expenses", TEA);
        postJson("/api/expenses", "{\"title\":\"Bus\",\"amount\":\"20\",\"category\":\"Travel\",\"date\":\"2026-09-30\"}");

        String food = get("/api/expenses?category=Food").body();
        assertTrue(food.contains("Tea"));
        assertTrue(!food.contains("Bus"));
        assertTrue(get("/api/expenses?month=2026-09").body().contains("Bus"));
        assertEquals(400, get("/api/expenses?month=nope").statusCode());

        String summary = get("/api/summary").body();
        assertTrue(summary.contains("\"total\":220.00"));
        assertTrue(summary.contains("\"count\":2"));
        assertTrue(get("/api/summary?month=2026-09").body().contains("\"total\":20.00"));
    }

    @Test
    void categoriesAreListed() throws Exception {
        String body = get("/api/categories").body();
        assertTrue(body.startsWith("[\"Food\""));
        assertTrue(body.contains("\"Other\""));
    }

    @Test
    void csvExportIsADownload() throws Exception {
        postJson("/api/expenses", TEA);
        HttpResponse<String> r = get("/api/export.csv");
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
        assertTrue(r.headers().firstValue("Content-Disposition").orElse("").contains("expenses.csv"));
        assertTrue(r.body().contains("1,Tea,200.00,Food,2026-10-31"));
    }

    @Test
    void webPageAndScriptsAreServedWithSecurityHeaders() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Expense Tracker"));
        assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("DENY", page.headers().firstValue("X-Frame-Options").orElse(""));
        assertNotNull(page.headers().firstValue("Content-Security-Policy").orElse(null));
        assertEquals(200, get("/app.js").statusCode());
        assertEquals(200, get("/style.css").statusCode());
    }

    @Test
    void scriptInTitleIsStoredAsPlainTextAndEscapedInJson() throws Exception {
        HttpResponse<String> r = postJson("/api/expenses",
                "{\"title\":\"<script>alert(1)</script>\",\"amount\":\"1\",\"category\":\"Food\",\"date\":\"2026-10-31\"}");
        assertEquals(201, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(r.body().contains("<script>alert(1)</script>")); // data is kept; the page shows it with textContent
    }
}
