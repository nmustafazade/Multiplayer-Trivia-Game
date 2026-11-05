package trivia.server;

import java.util.List;

public class ApiTest {

    public static void main(String[] args) {

        OpenTDBService service = new OpenTDBService();

        try {
            // Load available categories.
            service.loadCategories();

            // Fetch 3 computer science questions.
            List<Question> questions =
                    service.fetchQuestions(
                            "Science: Computers",
                            "Medium",
                            3);

            System.out.println("\nQuestions received: "
                    + questions.size());

            for (Question question : questions) {

                System.out.println();
                System.out.println(
                        question.getFormattedQuestion());

                // Only for testing; do not reveal
                // correct answers during a real game.
                System.out.println(
                        "Correct answer: "
                        + question.getCorrectLetter());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
