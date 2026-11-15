package lodgefinance;

import com.sun.net.httpserver.HttpServer;
import lodgefinance.database.Database;
import lodgefinance.web.StatusHandler;

import java.net.InetSocketAddress;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        Path databasePath = Path.of(System.getProperty("lodge.database", "data/lodge_finance.db"));
        int port = Integer.parseInt(System.getProperty("lodge.port", "8080"));

        try {
            Database database = new Database(databasePath);
            database.initialize();

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/", new StatusHandler(database));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            server.start();

            System.out.println("Database: " + databasePath.toAbsolutePath().normalize());
            System.out.println("Open http://localhost:" + server.getAddress().getPort() + "/");
            System.out.println("Stop the program in IntelliJ, or press Ctrl+C in the terminal.");
        } catch (Exception exception) {
            System.err.println("Startup failed: " + exception.getMessage());
            exception.printStackTrace();
            System.exit(1);
        }
    }
}
