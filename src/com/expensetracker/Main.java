package com.expensetracker;

import com.expensetracker.repository.FileExpenseRepository;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.web.ApiServer;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Starts the application. Run this class, then open http://localhost:8080
 *
 * Optional settings (environment variables):
 *   PORT              default 8080
 *   HOST              default 127.0.0.1 (only this computer). Use 0.0.0.0 on a server.
 *   EXPENSE_DATA_DIR  default "data" (the folder where expenses.txt is saved)
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String host = env("HOST", "127.0.0.1");
        int port;
        try {
            port = Integer.parseInt(env("PORT", "8080"));
        } catch (NumberFormatException e) {
            System.err.println("PORT must be a number, for example 8080");
            return;
        }
        Path dataFile = Paths.get(env("EXPENSE_DATA_DIR", "data"), "expenses.txt");

        ExpenseService service = new ExpenseService(new FileExpenseRepository(dataFile));
        ApiServer server = new ApiServer(host, port, service);
        server.start();

        System.out.println("Expense Tracker Pro is running.");
        System.out.println("Open this address in your browser: http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + server.getPort());
        System.out.println("Data file: " + dataFile.toAbsolutePath());
        System.out.println("Stop the server with the red square button in Eclipse.");
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
