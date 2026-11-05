package trivia.server;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OpenTDBService {

    private final HttpClient client;
    private final Map<String, Integer> categories;

    private long lastRequestTime = 0;

    public OpenTDBService() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        categories = new HashMap<>();
    }

    // Make an HTTP request and return its JSON response.
    private synchronized JsonObject request(String url)
            throws IOException, InterruptedException {

        // OpenTDB permits one request per 5 seconds.
        long elapsed = System.currentTimeMillis()
                - lastRequestTime;

        if (lastRequestTime != 0 && elapsed < 5500) {
            Thread.sleep(5500 - elapsed);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        // Record the request time before sending.
        lastRequestTime = System.currentTimeMillis();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8)
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "OpenTDB HTTP error: "
                    + response.statusCode());
        }

        try {
            return JsonParser.parseString(response.body())
                    .getAsJsonObject();

        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON response", e);
        }
    }

    // Retrieve the available trivia categories.
    public void loadCategories()
            throws IOException, InterruptedException {

        JsonObject data = request(
                "https://opentdb.com/api_category.php");

        JsonArray array =
                data.getAsJsonArray("trivia_categories");

        if (array == null) {
            throw new IOException("Missing categories");
        }

        Map<String, Integer> loaded = new HashMap<>();

        for (int i = 0; i < array.size(); i++) {

            JsonObject category =
                    array.get(i).getAsJsonObject();

            String name = category.get("name").getAsString();
            int id = category.get("id").getAsInt();

            loaded.put(name.toLowerCase(), id);
        }

        synchronized (categories) {
            categories.clear();
            categories.putAll(loaded);
        }

        System.out.println(
                "Loaded " + loaded.size() + " categories.");
    }

    // Download multiple-choice trivia questions.
    public List<Question> fetchQuestions(
            String categoryName,
            String difficulty,
            int count)
            throws IOException, InterruptedException {

        Integer categoryId;

        synchronized (categories) {
            categoryId = categories.get(
                    categoryName.trim().toLowerCase());
        }

        if (categoryId == null) {
            throw new IllegalArgumentException(
                    "Unknown category: " + categoryName);
        }

        difficulty = difficulty.trim().toLowerCase();

        if (!difficulty.equals("easy")
                && !difficulty.equals("medium")
                && !difficulty.equals("hard")) {

            throw new IllegalArgumentException(
                    "Invalid difficulty");
        }

        if (count < 1 || count > 50) {
            throw new IllegalArgumentException(
                    "Question count must be 1-50");
        }

        String url = "https://opentdb.com/api.php"
                + "?amount=" + count
                + "&category=" + categoryId
                + "&difficulty=" + difficulty
                + "&type=multiple"
                + "&encode=url3986";

        JsonObject data = request(url);

        int responseCode =
                data.get("response_code").getAsInt();

        if (responseCode != 0) {
            throw new IOException(
                    "OpenTDB response code: "
                    + responseCode);
        }

        JsonArray results = data.getAsJsonArray("results");

        if (results == null || results.size() != count) {
            throw new IOException(
                    "OpenTDB returned an unexpected question count");
        }

        List<Question> questions = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {

            JsonObject item =
                    results.get(i).getAsJsonObject();

            String text = decode(
                    item.get("question").getAsString());

            String correct = decode(
                    item.get("correct_answer").getAsString());

            JsonArray wrongAnswers =
                    item.getAsJsonArray("incorrect_answers");

            if (wrongAnswers == null
                    || wrongAnswers.size() != 3) {
                throw new IOException(
                        "Invalid answer options from OpenTDB");
            }

            List<String> incorrect = new ArrayList<>();

            for (int j = 0; j < wrongAnswers.size(); j++) {
                incorrect.add(
                        decode(wrongAnswers.get(j).getAsString()));
            }

            questions.add(
                    new Question(text, correct, incorrect));
        }

        return questions;
    }

    private String decode(String value) {
        return URLDecoder.decode(
                value, StandardCharsets.UTF_8);
    }
}
