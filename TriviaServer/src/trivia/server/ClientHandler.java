package trivia.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    // Synchronize all writes to this client's socket.
    public synchronized void send(
            int code, String message, String body) throws IOException {

        Protocol.sendResponse(
            socket.getOutputStream(), code, message, body);
    }

    @Override
    public void run() {

        try {
            socket.setSoTimeout(90000);

            BufferedReader in = new BufferedReader(
                new InputStreamReader(
                    socket.getInputStream(),
                    StandardCharsets.UTF_8)
            );

            String request;

            while ((request = in.readLine()) != null) {

                System.out.println("Client: " + request);

                switch (request) {

                    case "/list":
                        send(200, "OK", "No challenges found");
                        break;

                    case "/exit":
                        send(200, "OK", "Goodbye!");
                        return;

                    default:
                        send(400, "BAD_REQUEST",
                            "Unknown command");
                        break;
                }
            }

        } catch (IOException e) {
            System.out.println(
                "Client disconnected: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
