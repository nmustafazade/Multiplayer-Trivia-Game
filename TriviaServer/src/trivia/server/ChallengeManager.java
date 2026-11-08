
package trivia.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ChallengeManager implements Runnable {

    private final OpenTDBService openTDB;

    private final Map<Integer, Challenge> challenges =
            new ConcurrentHashMap<>();

    private final AtomicInteger nextId =
            new AtomicInteger(101);

    private final BlockingQueue<CreateRequest> requests =
            new LinkedBlockingQueue<>();

    private static class CreateRequest {

        String category;
        String difficulty;
        int count;
        ClientHandler creator;

        CompletableFuture<Challenge> result =
                new CompletableFuture<>();

        CreateRequest(String category, String difficulty,
                      int count, ClientHandler creator) {

            this.category = category;
            this.difficulty = difficulty;
            this.count = count;
            this.creator = creator;
        }
    }

    public ChallengeManager(OpenTDBService openTDB) {
        this.openTDB = openTDB;
    }

    public Challenge createChallenge(
            String category,
            String difficulty,
            int count,
            ClientHandler creator)
            throws InterruptedException, ExecutionException {

        CreateRequest request = new CreateRequest(
                category, difficulty, count, creator);

        requests.put(request);

        // Wait for the manager thread to complete creation.
        return request.result.get();
    }

    public List<Challenge> listChallenges(
            String category, String difficulty) {

        List<Challenge> matches = new ArrayList<>();

        for (Challenge challenge : challenges.values()) {

            if (challenge.getState() != Challenge.State.WAITING) {
                continue;
            }

            if (category != null
                    && !challenge.getCategory()
                            .equalsIgnoreCase(category)) {
                continue;
            }

            if (difficulty != null
                    && !challenge.getDifficulty()
                            .equalsIgnoreCase(difficulty)) {
                continue;
            }

            matches.add(challenge);
        }

        matches.sort(
                Comparator.comparingInt(Challenge::getId));

        return matches;
    }

    public Challenge getChallenge(int id) {
        return challenges.get(id);
    }
    
    public void removeChallenge(int id) {
        challenges.remove(id);
    }

    @Override
    public void run() {

        System.out.println("Challenge manager started.");

        try {
            while (!Thread.currentThread().isInterrupted()) {

                CreateRequest request = requests.take();

                try {
                    // Retrieve all questions before creation.
                    List<Question> questions =
                            openTDB.fetchQuestions(
                                    request.category,
                                    request.difficulty,
                                    request.count);

                    int id = nextId.getAndIncrement();

                    Challenge challenge = new Challenge(
                            id,
                            request.category,
                            request.difficulty,
                            questions,
                            request.creator,
                            this);

                    request.creator.setCurrentChallenge(challenge);

                    Thread thread = new Thread(
                            challenge, "Challenge-" + id);

                    // Register only after questions exist.
                    challenges.put(id, challenge);
                    thread.start();

                    request.result.complete(challenge);

                    System.out.println(
                            "Created challenge " + id);

                } catch (Exception e) {
                    request.result.completeExceptionally(e);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
