
package trivia.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Challenge implements Runnable {

    public enum State {
        WAITING, RUNNING, FINISHED
    }

    public enum Result {
        ACCEPTED, REJECTED, DUPLICATE, TOO_LATE
    }	

    private enum Type {
        JOIN, LEAVE, ANSWER
    }

    private static class Command {

        Type type;
        ClientHandler player;
        char answer;
        int round;
        long submittedAt;

        CompletableFuture<Result> result =
                new CompletableFuture<>();

        Command(Type type, ClientHandler player,
                char answer, int round) {

            this.type = type;
            this.player = player;
            this.answer = answer;
            this.round = round;
            this.submittedAt = System.nanoTime();
        }
    }

    private final int id;
    private final String category;
    private final String difficulty;
    private final List<Question> questions;
    private final ClientHandler creator;
    private final ChallengeManager manager;

    private final List<ClientHandler> players =
            new ArrayList<>();

    private final Map<ClientHandler, Integer> scores =
            new HashMap<>();

    private final Map<ClientHandler, Character> answers =
            new HashMap<>();

    private final BlockingQueue<Command> requestQueue =
            new LinkedBlockingQueue<>();

    private volatile State state = State.WAITING;
    private volatile int playerCount = 1;
    private volatile int currentRound = 0;

    private long deadline;

    private static final int QUESTION_SECONDS = 30;
    private static final int CORRECT_POINTS = 10;

    public Challenge(int id, String category,
                     String difficulty,
                     List<Question> questions,
                     ClientHandler creator,
                     ChallengeManager manager) {

        this.id = id;
        this.category = category;
        this.difficulty = difficulty;
        this.questions = List.copyOf(questions);
        this.creator = creator;
        this.manager = manager;

        players.add(creator);
        scores.put(creator, 0);
    }

    public int getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public State getState() {
        return state;
    }

    public int getQuestionCount() {
        return questions.size();
    }

    public ClientHandler getCreator() {
        return creator;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public String getSummary() {
        return "ID: " + id
                + " | " + category
                + " | " + difficulty
                + " | " + playerCount + "/2 players"
                + " | " + state;
    }

    // Safely submit requests to the challenge thread.
	
	private Result submit(Command command)
	        throws InterruptedException, ExecutionException {
	
	    synchronized (this) {
	
	        if (state == State.FINISHED) {
	            return Result.REJECTED;
	        }
	
	        requestQueue.offer(command);
	    }
	
	    // The lock has been released before waiting.
	    return command.result.get();
	}


    public boolean join(ClientHandler player)
            throws InterruptedException, ExecutionException {

        Command command = new Command(
                Type.JOIN, player, '\0', 0);

        return submit(command) == Result.ACCEPTED;
    }

    public boolean leave(ClientHandler player)
            throws InterruptedException, ExecutionException {

        Command command = new Command(
                Type.LEAVE, player, '\0', 0);

        return submit(command) == Result.ACCEPTED;
    }

    public Result answer(ClientHandler player, char choice)
            throws InterruptedException, ExecutionException {

        Command command = new Command(
                Type.ANSWER, player, choice, currentRound);

        return submit(command);
    }

    private void process(Command command) {

        switch (command.type) {

            case JOIN:
                if (state != State.WAITING
                        || players.size() >= 2
                        || players.contains(command.player)) {

                    command.result.complete(Result.REJECTED);
                    break;
                }

                players.add(command.player);
                scores.put(command.player, 0);
                playerCount = players.size();

                command.player.setCurrentChallenge(this);

                if (players.size() == 2) {
                    state = State.RUNNING;
                    System.out.println(
                            "Challenge " + id + " started!");
                }

                command.result.complete(Result.ACCEPTED);
                break;

            case LEAVE:
                if (!players.contains(command.player)) {
                    command.result.complete(Result.REJECTED);
                    break;
                }

                finishChallenge(
                        "A player left the challenge.");

                command.result.complete(Result.ACCEPTED);
                break;

            case ANSWER:
                if (state != State.RUNNING
                        || !players.contains(command.player)) {

                    command.result.complete(Result.REJECTED);
                    break;
                }

                if (command.round != currentRound
                        || currentRound == 0
                        || command.submittedAt >= deadline) {

                    command.result.complete(Result.TOO_LATE);
                    break;
                }

                if (answers.containsKey(command.player)) {
                    command.result.complete(Result.DUPLICATE);
                    break;
                }

                answers.put(command.player, command.answer);

                command.result.complete(Result.ACCEPTED);
                break;
        }
    }

    private boolean broadcast(
            int code, String message, String body) {

        boolean success = true;

        for (ClientHandler player : players) {

            try {
                player.send(code, message, body);
            } catch (IOException e) {
                success = false;
                System.out.println(
                        "Could not send to player: "
                        + e.getMessage());
            }
        }

        return success;
    }

    private void playGame() throws InterruptedException {

        for (int i = 0; i < questions.size(); i++) {

            if (state != State.RUNNING) {
                return;
            }

            Question question = questions.get(i);

            answers.clear();
            currentRound = i + 1;

            // Set the deadline before broadcasting.
            deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(
                            QUESTION_SECONDS);

            String questionBody =
                    "Challenge " + id
                    + "\nQuestion " + currentRound
                    + "/" + questions.size()
                    + "\nTime: 30 seconds\n\n"
                    + question.getFormattedQuestion();

            if (!broadcast(
                    210, "QUESTION", questionBody)) {

                finishChallenge(
                        "A player connection was lost.");
                return;
            }

            // Process requests until the deadline.
            while (state == State.RUNNING) {

                long remaining =
                        deadline - System.nanoTime();

                if (remaining <= 0) {
                    break;
                }

                Command command = requestQueue.poll(
                        remaining, TimeUnit.NANOSECONDS);

                if (command != null) {
                    process(command);
                }
            }

            if (state != State.RUNNING) {
                return;
            }

            int playerNumber = 1;

            for (ClientHandler player : players) {

                Character answer = answers.get(player);

                if (answer != null
                        && question.isCorrect(answer - 'A')) {

                    scores.put(player,
                            scores.get(player)
                            + CORRECT_POINTS);
                }

                playerNumber++;
            }

            String resultBody =
                    "Challenge " + id
                    + "\nQuestion " + currentRound
                    + " completed."
                    + "\nCorrect answer: "
                    + question.getCorrectLetter()
                    + "\nPlayer 1 score: "
                    + scores.get(players.get(0))
                    + "\nPlayer 2 score: "
                    + scores.get(players.get(1));

            if (!broadcast(
                    211, "RESULT", resultBody)) {

                finishChallenge(
                        "A player connection was lost.");
                return;
            }
        }

        if (state == State.RUNNING) {
            int score1 = scores.get(players.get(0));
            int score2 = scores.get(players.get(1));

            String winner;

            if (score1 > score2) {
                winner = "Player 1 wins!";
            } else if (score2 > score1) {
                winner = "Player 2 wins!";
            } else {
                winner = "It's a tie!";
            }

            finishChallenge(
                    "Game completed!"
                    + "\nPlayer 1: " + score1
                    + "\nPlayer 2: " + score2
                    + "\n" + winner);
        }
    }

    private void finishChallenge(String reason) {

        synchronized (this) {

            if (state == State.FINISHED) {
                return;
            }

            state = State.FINISHED;

            // Complete requests that were still waiting.
            Command pending;

            while ((pending = requestQueue.poll()) != null) {
                pending.result.complete(Result.REJECTED);
            }
        }

        broadcast(
                212,
                "CHALLENGE_END",
                "Challenge " + id + "\n" + reason);

        for (ClientHandler player : players) {
            player.clearCurrentChallenge(this);
        }

        players.clear();
        playerCount = 0;

        manager.removeChallenge(id);

        System.out.println(
                "Challenge " + id + " finished.");
    }

    @Override
    public void run() {

        System.out.println(
                "Challenge " + id + " thread started.");

        try {
            while (state != State.FINISHED) {

                if (state == State.WAITING) {

                    Command command = requestQueue.take();
                    process(command);

                } else if (state == State.RUNNING) {

                    playGame();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            finishChallenge("Challenge interrupted.");

        } finally {
            System.out.println(
                    "Challenge " + id + " thread stopped.");
        }
    }
}
