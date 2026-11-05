
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {

    public static void main(String[] args) throws IOException {

        try (Socket socket = new Socket("localhost", 12000)) {

            BufferedReader keyboard = new BufferedReader(
                new InputStreamReader(System.in)
            );

            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(
                    socket.getOutputStream(),
                    StandardCharsets.UTF_8),
                true
            );

            // Thread 1: Read user input and send commands.
            Thread inputThread = new Thread(() -> {

                try {
                    String command;

                    while ((command = keyboard.readLine()) != null) {

                        out.println(command);

                        if (out.checkError()) {
                            System.out.println("Failed to send command.");
                            break;
                        }

                        if (command.equals("/exit")) {
                            break;
                        }
                    }

                } catch (IOException e) {
                    System.out.println("Input error: "
                            + e.getMessage());
                }

            });

            // Thread 2: Receive server messages.
            ServerListener listener = new ServerListener(
                socket.getInputStream()
            );

            Thread listenerThread = new Thread(listener);

            // Keyboard input should not prevent shutdown.
            inputThread.setDaemon(true);

            inputThread.start();
            listenerThread.start();

            System.out.println("Connected to server!");
            System.out.print("> ");

            // Wait until the server connection ends.
            listenerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
