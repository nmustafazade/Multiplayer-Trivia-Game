
package trivia.server;

import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) {

        int port = 12000;

        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        try {
            // Shared API service.
            OpenTDBService openTDB = new OpenTDBService();

            // Load category mappings once on startup.
            openTDB.loadCategories();

            // Shared challenge manager.
            ChallengeManager manager =
                    new ChallengeManager(openTDB);

            Thread managerThread = new Thread(
                    manager, "ChallengeManager");

            managerThread.start();

            try (ServerSocket serverSocket =
                         new ServerSocket(port)) {

                System.out.println(
                        "Server started on port " + port);

                while (true) {

                    Socket clientSocket =
                            serverSocket.accept();

                    System.out.println(
                            "New client connected!");

                    ClientHandler handler =
                            new ClientHandler(
                                    clientSocket, manager);

                    Thread thread = new Thread(handler);
                    thread.start();
                }
            }

        } catch (Exception e) {
            System.out.println(
                    "Server error: " + e.getMessage());
        }
    }
}
