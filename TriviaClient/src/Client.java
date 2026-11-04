
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
                    StandardCharsets.UTF_8), true
            );

            InputStream in = socket.getInputStream();

            System.out.println("Connected to server!");

            while (true) {

                System.out.print("> ");

                String request = keyboard.readLine();

                if (request == null) {
                    break;
                }

                out.println(request);

                String response = Protocol.readResponse(in);

                System.out.println(response);

                if (request.equals("/exit")) {
                    break;
                }
            }
        }
    }
}
