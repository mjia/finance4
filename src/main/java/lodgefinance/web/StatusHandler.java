package lodgefinance.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lodgefinance.database.Database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class StatusHandler implements HttpHandler {
    private final Database database;
    private final String page;

    public StatusHandler(Database database) throws IOException {
        this.database = database;
        try (InputStream input = StatusHandler.class.getResourceAsStream("/pages/status.html")) {
            if (input == null) {
                throw new IOException("Missing pages/status.html resource.");
            }
            page = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            switch (path) {
                case "/" -> handleRoot(exchange);
                default -> send(exchange, 404, "Page not found.", "text/plain");
            }
        } finally {
            exchange.close();
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            send(exchange, 405, "Only GET is supported.", "text/plain");
            return;
        }

        String databaseStatus = "Connected";
        String userCount = "Unavailable";
        String transactionCount = "Unavailable";
        int statusCode = 200;
        String sql = "SELECT (SELECT COUNT(*) FROM USERS) AS users, "
                + "(SELECT COUNT(*) FROM TRANSACTIONS) AS transactions";
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            userCount = Integer.toString(rows.getInt("users"));
            transactionCount = Integer.toString(rows.getInt("transactions"));
        } catch (SQLException exception) {
            databaseStatus = "Unavailable";
            statusCode = 503;
            System.err.println("Status check failed: " + exception.getMessage());
        }

        String html = page.replace("{{databaseStatus}}", databaseStatus)
                .replace("{{userCount}}", userCount)
                .replace("{{transactionCount}}", transactionCount);
        send(exchange, statusCode, html, "text/html");
    }

    private void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
