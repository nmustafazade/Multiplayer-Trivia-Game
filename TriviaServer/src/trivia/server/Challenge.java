
package trivia.server;

import java.util.List;
import java.util.concurrent.BlockingQueue;
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

    private volatile State state = State.WAITING;

    private final BlockingQueue<Runnable> requestQueue =
            new LinkedBlockingQueue<>();

    public Challenge(int id, String category,
                     String difficulty,
                     List<Question> questions,
                     ClientHandler creator) {

        this.id = id;
        this.category = category;
        this.difficulty = difficulty;
        this.questions = List.copyOf(questions);
        this.creator = creator;
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
        // The creator is automatically joined.
        // We will expand this in Step 10.
        return 1;
    }

    public String getSummary() {
        return "ID: " + id
                + " | " + category
                + " | " + difficulty
                + " | " + getPlayerCount() + "/2 players"
                + " | " + state;
    }

    public void submitTask(Runnable task) {
        requestQueue.offer(task);
    }

    @Override
    public void run() {

        System.out.println(
                "Challenge " + id + " thread started.");

        try {
            while (!Thread.currentThread().isInterrupted()) {

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
