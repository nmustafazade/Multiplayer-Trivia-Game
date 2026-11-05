package trivia.client;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Protocol {

    public static String readResponse(InputStream in)
            throws IOException {

        ByteArrayOutputStream headerBytes =
                new ByteArrayOutputStream();

        int b;

        // Read the header until CRLF.
        while (true) {

            b = in.read();

            if (b == -1) {
                throw new EOFException("Server disconnected");
            }

            headerBytes.write(b);

            byte[] data = headerBytes.toByteArray();
            int n = data.length;

            if (n >= 2 && data[n - 2] == '\r'
                    && data[n - 1] == '\n') {
                break;
            }

            if (n > 4096) {
                throw new IOException("Header too large");
            }
        }

        String header = headerBytes.toString(
                StandardCharsets.UTF_8);

        String[] parts = header.trim().split(" ");

        if (parts.length != 3) {
            throw new IOException("Invalid response header");
        }

        int bodyLength;
        try {
            bodyLength = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid body length", e);
        }

        if (bodyLength < 0 || bodyLength > 1_000_000) {
            throw new IOException("Invalid body length");
        }

        byte[] bodyBytes = in.readNBytes(bodyLength);

        if (bodyBytes.length != bodyLength) {
            throw new EOFException("Incomplete response body");
        }

        String body = new String(
                bodyBytes, StandardCharsets.UTF_8);

        return parts[0] + " " + parts[1] + "\n" + body;
    }
}
