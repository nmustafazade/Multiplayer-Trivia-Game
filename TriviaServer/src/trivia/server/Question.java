package trivia.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Question {

    private final String text;
    private final String correctAnswer;
    private final List<String> options;

    public Question(String text, String correctAnswer,
                    List<String> incorrectAnswers) {

        this.text = text;
        this.correctAnswer = correctAnswer;

        options = new ArrayList<>(incorrectAnswers);
        options.add(correctAnswer);

        // Randomize the order of answers.
        Collections.shuffle(options);
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public boolean isCorrect(int optionIndex) {
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return false;
        }

        return options.get(optionIndex).equals(correctAnswer);
    }

    public String getCorrectLetter() {
        int index = options.indexOf(correctAnswer);
        return String.valueOf((char) ('A' + index));
    }

    public String getFormattedQuestion() {

        StringBuilder result = new StringBuilder();

        result.append(text).append("\n");

        for (int i = 0; i < options.size(); i++) {
            char letter = (char) ('A' + i);

            result.append(letter)
                  .append(". ")
                  .append(options.get(i))
                  .append("\n");
        }

        return result.toString();
    }
}
