package trivia.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Protocol {

    public static void sendResponse(
            OutputStream out,
            int statusCode,
            String message,
            String body) throws IOException {

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        String header = statusCode + " " + message + " "
                + bodyBytes.length + "\r\n";

        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }
}
