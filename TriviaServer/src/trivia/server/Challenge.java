
package trivia.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

public class Challenge implements Runnable {

    public enum State {
        WAITING, RUNNING, FINISHED
    }

    private final int id;
    private final String category;
    private final String difficulty;
    private final List<Question> questions;
    private final ClientHandler creator;
    private final ChallengeManager manager;

    private final List<ClientHandler> players =
            new ArrayList<>();

    private volatile State state = State.WAITING;
    private volatile int playerCount = 1;

    private final BlockingQueue<Runnable> requestQueue =
            new LinkedBlockingQueue<>();

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

        // The creator joins automatically.
        players.add(creator);
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

    // Add a request to this challenge's queue.
    public void submitTask(Runnable task) {
        requestQueue.offer(task);
    }

    // Called by a client handler.
    public boolean join(ClientHandler player)
            throws InterruptedException, ExecutionException {

        CompletableFuture<Boolean> result =
                new CompletableFuture<>();

        submitTask(() -> {

            if (state != State.WAITING
                    || players.size() >= 2
                    || players.contains(player)) {

                result.complete(false);
                return;
            }

            players.add(player);
            playerCount = players.size();
            player.setCurrentChallenge(this);

            System.out.println(
                    "Player joined challenge " + id);

            if (players.size() == 2) {
                state = State.RUNNING;

                System.out.println(
                        "Challenge " + id + " started!");
            }

            result.complete(true);
        });

        return result.get();
    }

    // Called by a client handler.
    public boolean leave(ClientHandler player)
            throws InterruptedException, ExecutionException {

        CompletableFuture<Boolean> result =
                new CompletableFuture<>();

        submitTask(() -> {

            if (!players.contains(player)) {
                result.complete(false);
                return;
            }

            players.remove(player);
            playerCount = players.size();
            player.clearCurrentChallenge(this);

            System.out.println(
                    "Player left challenge " + id);

            // A challenge cannot continue without
            // both players after starting.
            // A creator leaving while waiting also ends it.
            finishChallenge("A player left the challenge.");

            result.complete(true);
        });

        return result.get();
    }

    private void finishChallenge(String reason) {

        state = State.FINISHED;

        for (ClientHandler player : players) {

            try {
                player.send(
                        212,
                        "CHALLENGE_END",
                        "Challenge " + id
                        + " terminated. " + reason);

            } catch (IOException e) {
                System.out.println(
                        "Could not notify a player: "
                        + e.getMessage());
            }

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

                Runnable task = requestQueue.take();
                task.run();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println(
                "Challenge " + id + " thread stopped.");
    }
}
