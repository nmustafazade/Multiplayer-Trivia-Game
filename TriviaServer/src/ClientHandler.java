
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {

        try {
            socket.setSoTimeout(90000);

            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );

            PrintWriter out = new PrintWriter(
                socket.getOutputStream(), true
            );

            String message;

            while ((message = in.readLine()) != null) {

                System.out.println("Client: " + message);

                if (message.equals("/exit")) {
                    out.println("Goodbye!");
                    break;
                }

                out.println("Server received: " + message);
            }

        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
