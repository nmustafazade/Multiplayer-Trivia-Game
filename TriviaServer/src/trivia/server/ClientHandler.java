
package trivia.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChallengeManager manager;
    private volatile Challenge currentChallenge;

    public ClientHandler(Socket socket,
                         ChallengeManager manager) {
        this.socket = socket;
        this.manager = manager;
    }

    public synchronized void send(
            int code, String message, String body)
            throws IOException {

        Protocol.sendResponse(
                socket.getOutputStream(),
                code, message, body);
    }
    

	public void setCurrentChallenge(Challenge challenge) {
	    this.currentChallenge = challenge;
	}
	
	public void clearCurrentChallenge(Challenge challenge) {
	
	    if (this.currentChallenge == challenge) {
	        this.currentChallenge = null;
	    }
	}


    private List<String> tokenize(String input) {

        List<String> tokens = new ArrayList<>();

        Pattern pattern = Pattern.compile(
                "\"([^\"]*)\"|([^\\s\"]+)");

        Matcher matcher = pattern.matcher(input);

        int position = 0;

        while (matcher.find()) {

            if (!input.substring(position, matcher.start())
                    .trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid quotation marks");
            }

            tokens.add(matcher.group(1) != null
                    ? matcher.group(1)
                    : matcher.group(2));

            position = matcher.end();
        }

        if (!input.substring(position).trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid quotation marks");
        }

        return tokens;
    }

    private Map<String, String> parseOptions(
            List<String> tokens,
            Set<String> allowed) {

        Map<String, String> options = new HashMap<>();

        if (tokens.size() % 2 == 0) {
            throw new IllegalArgumentException(
                    "Missing option or value");
        }

        for (int i = 1; i < tokens.size(); i += 2) {

            String key = tokens.get(i);
            String value = tokens.get(i + 1);

            if (!allowed.contains(key)
                    || value.isBlank()
                    || options.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Invalid or duplicate option: " + key);
            }

            options.put(key, value);
        }

        return options;
    }

    private void handleCreate(List<String> tokens)
            throws IOException, InterruptedException,
                   ExecutionException {
    	
    	if (currentChallenge != null) {
    	    throw new IllegalArgumentException(
    	            "Leave your current challenge first");
    	}

        Map<String, String> options = parseOptions(
                tokens,
                Set.of("-category", "-difficulty", "-count"));

        if (options.size() != 3) {
            throw new IllegalArgumentException(
                    "Create requires category, difficulty and count");
        }

        String category = options.get("-category");
        String difficulty = options.get("-difficulty");

        int count;

        try {
            count = Integer.parseInt(options.get("-count"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Count must be an integer");
        }

        Challenge challenge = manager.createChallenge(
                category, difficulty, count, this);

        send(200, "OK",
                "Challenge created successfully. ID: "
                + challenge.getId()
                + "\nWaiting for another player...");
    }

    private void handleList(List<String> tokens)
            throws IOException {

        Map<String, String> options = parseOptions(
                tokens,
                Set.of("-category", "-difficulty"));

        String category = options.get("-category");
        String difficulty = options.get("-difficulty");

        if (difficulty != null
                && !difficulty.equalsIgnoreCase("Easy")
                && !difficulty.equalsIgnoreCase("Medium")
                && !difficulty.equalsIgnoreCase("Hard")) {

            throw new IllegalArgumentException(
                    "Invalid difficulty");
        }

        List<Challenge> challenges =
                manager.listChallenges(category, difficulty);

        if (challenges.isEmpty()) {
            send(200, "OK", "No challenges found");
            return;
        }

        StringBuilder body = new StringBuilder();

        for (Challenge challenge : challenges) {
            body.append(challenge.getSummary())
                    .append("\n");
        }

        send(200, "OK", body.toString().trim());
    }

    private void handleRequest(String request)
            throws IOException, InterruptedException,
                   ExecutionException {

        List<String> tokens = tokenize(request.trim());

        if (tokens.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty command");
        }

        String command = tokens.get(0);


		switch (command) {
		
		    case "/create":
		        handleCreate(tokens);
		        break;
		
		    case "/list":
		        handleList(tokens);
		        break;
		
		    case "/join":
		        handleJoin(tokens);
		        break;
		
		    case "/leave":
		        handleLeave(tokens);
		        break;
		
		    case "/exit":
		        if (tokens.size() != 1) {
		            throw new IllegalArgumentException(
		                    "Exit takes no arguments");
		        }
		
		        send(200, "OK", "Goodbye!");
		        break;
		
		    default:
		        throw new IllegalArgumentException(
		                "Unknown command");
		}

    }
    

	private void handleJoin(List<String> tokens)
	        throws IOException, InterruptedException,
	               ExecutionException {
	
	    Map<String, String> options = parseOptions(
	            tokens, Set.of("-id"));
	
	    if (options.size() != 1) {
	        throw new IllegalArgumentException(
	                "Join requires a challenge ID");
	    }
	
	    if (currentChallenge != null) {
	        throw new IllegalArgumentException(
	                "You are already in a challenge");
	    }
	
	    int id;
	
	    try {
	        id = Integer.parseInt(options.get("-id"));
	    } catch (NumberFormatException e) {
	        throw new IllegalArgumentException(
	                "Invalid challenge ID");
	    }
	
	    Challenge challenge = manager.getChallenge(id);
	
	    if (challenge == null) {
	        send(404, "NOT_FOUND", "Challenge not found");
	        return;
	    }
	
	    boolean joined = challenge.join(this);
	
	    if (!joined) {
	        send(400, "BAD_REQUEST",
	                "Challenge already started or is full");
	        return;
	    }
	
	    send(200, "OK",
	            "Successfully joined challenge " + id);
	}
	

	private void handleLeave(List<String> tokens)
	        throws IOException, InterruptedException,
	               ExecutionException {
	
	    Map<String, String> options = parseOptions(
	            tokens, Set.of("-id"));
	
	    if (options.size() != 1) {
	        throw new IllegalArgumentException(
	                "Leave requires a challenge ID");
	    }
	
	    int id;
	
	    try {
	        id = Integer.parseInt(options.get("-id"));
	    } catch (NumberFormatException e) {
	        throw new IllegalArgumentException(
	                "Invalid challenge ID");
	    }
	
	    Challenge challenge = manager.getChallenge(id);
	
	    if (challenge == null) {
	        send(404, "NOT_FOUND", "Challenge not found");
	        return;
	    }
	
	    if (currentChallenge != challenge) {
	        send(400, "BAD_REQUEST",
	                "You are not in this challenge");
	        return;
	    }
	
	    boolean left = challenge.leave(this);
	
	    if (left) {
	        send(200, "OK",
	                "Successfully left challenge " + id);
	    } else {
	        send(400, "BAD_REQUEST",
	                "Could not leave challenge");
	    }
	}



    @Override
    public void run() {

        try {
            socket.setSoTimeout(90000);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8));

            String request;

            while ((request = in.readLine()) != null) {

                System.out.println("Client: " + request);

                try {
                    handleRequest(request);

                    if (request.trim().equals("/exit")) {
                        break;
                    }

                } catch (IllegalArgumentException e) {
                    send(400, "BAD_REQUEST", e.getMessage());

                } catch (ExecutionException e) {

                    Throwable cause = e.getCause();

                    if (cause instanceof IllegalArgumentException) {
                        send(400, "BAD_REQUEST",
                                cause.getMessage());
                    } else if (cause instanceof IOException
                            || cause instanceof InterruptedException) {
                        send(503, "SERVICE_UNAVAILABLE",
                                "Could not retrieve trivia questions");
                    } else {
                        send(500, "INTERNAL_SERVER_ERROR",
                                "Challenge creation failed");
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (IOException e) {
            System.out.println(
                    "Client disconnected: " + e.getMessage());

        } finally {

            // Leave the challenge if the client disconnects.
            Challenge joined = currentChallenge;

            if (joined != null) {
                try {
                    joined.leave(this);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    System.out.println(
                            "Could not clean up challenge: "
                            + e.getMessage());
                }
            }

            try {
                socket.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
