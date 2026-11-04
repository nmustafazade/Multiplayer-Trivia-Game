
import java.io.*;
import java.net.Socket;

public class Client {

    public static void main(String[] args) throws IOException {

        Socket socket = new Socket("localhost", 12000);

        BufferedReader in = new BufferedReader(
            new InputStreamReader(socket.getInputStream())
        );

        PrintWriter out = new PrintWriter(
            socket.getOutputStream(), true
        );

        BufferedReader keyboard = new BufferedReader(
            new InputStreamReader(System.in)
        );

        System.out.println("Connected to server!");

        String message;

        while (true) {

            System.out.print("> ");
            message = keyboard.readLine();

            if (message == null) {
                break;
            }

            out.println(message);

            String response = in.readLine();

            if (response == null) {
                break;
            }

            System.out.println(response);

            if (message.equals("/exit")) {
                break;
            }
        }

        socket.close();
    }
}
