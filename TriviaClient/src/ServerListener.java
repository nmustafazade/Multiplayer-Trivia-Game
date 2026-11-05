
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class ServerListener implements Runnable {

    private final InputStream in;

    public ServerListener(InputStream in) {
        this.in = in;
    }

    @Override
    public void run() {

        try {
            while (true) {

                String response = Protocol.readResponse(in);

                System.out.println();
                System.out.println(response);
                System.out.print("> ");
            }

        } catch (EOFException e) {
            System.out.println("\nServer disconnected.");

        } catch (IOException e) {
            System.out.println("\nConnection error: "
                    + e.getMessage());
        }
    }
}
