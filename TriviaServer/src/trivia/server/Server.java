package trivia.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) throws IOException {

        int port = 12000;

        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Server started on port " + port);

        while (true) {

            Socket clientSocket = serverSocket.accept();

            System.out.println("New client connected!");

            ClientHandler handler = new ClientHandler(clientSocket);

            Thread thread = new Thread(handler);
            thread.start();
        }
    }
}
