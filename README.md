# Multiplayer Trivia Game

A Java-based, two-player trivia game developed for **COMP 416** using TCP sockets, multithreading, a custom application-layer protocol, and the [Open Trivia Database (OpenTDB)](https://opentdb.com/) API.

Players can create and browse trivia challenges, join a waiting challenge, and compete by answering timed multiple-choice questions. The server manages game sessions, validates answers, tracks scores, and announces the winner.

## Requirements

- **Java JDK 17 or newer**
- **Eclipse IDE for Java Developers**
- **Gson** JAR in the server project's `lib/` directory and configured on the Java build path
- Internet connection for downloading trivia categories and questions

## Project Structure

```text
Multiplayer-Trivia-Game/
├── TriviaServer/
│   ├── src/trivia/server/
│   │   ├── Server.java
│   │   ├── ClientHandler.java
│   │   ├── Protocol.java
│   │   ├── OpenTDBService.java
│   │   ├── Question.java
│   │   ├── Challenge.java
│   │   └── ChallengeManager.java
│   └── lib/
│       └── gson-2.15.0.jar
└── TriviaClient/
    └── src/trivia/client/
        ├── Client.java
        ├── ServerListener.java
        └── Protocol.java
```

The source layout above assumes the Java package names `trivia.server` and `trivia.client`. Eclipse also maintains `.project` and `.classpath` configuration files in each project.

## How to Run

1. Open **Eclipse**.
2. Select **File → Import → General → Existing Projects into Workspace**.
3. Select the repository's root directory and import **TriviaServer** and **TriviaClient**.
4. In **TriviaServer**, verify the Gson JAR is on the build path (**Project → Properties → Java Build Path → Libraries**). If missing, add the JAR from `TriviaServer/lib/`.
5. Run `Server.java` using **Run As → Java Application**. The server listens on **TCP port 12000** by default. If desired, supply a different port as the server's first program argument.
6. Run `Client.java` using **Run As → Java Application**. The current client connects to **localhost:12000**; update its host/port settings if the server runs elsewhere or on another port.
7. Launch `Client.java` again to open a second player. Use Eclipse's Console selector to switch between running client consoles.

**Start the server before starting the clients.** The server needs internet access at startup to load the OpenTDB categories.

## Client Commands

| Command | Description |
| --- | --- |
| `/list` | List available (waiting) challenges. |
| `/list -difficulty "Medium"` | Filter challenges by difficulty. |
| `/list -category "Science: Computers"` | Filter challenges by category. |
| `/create -category "Science: Computers" -difficulty "Medium" -count 5` | Create a challenge with five questions. The creator joins automatically. |
| `/join -id 101` | Join an existing waiting challenge. |
| `/answer -id 101 -answer B` | Answer the current question with choice A, B, C, or D. |
| `/leave -id 101` | Leave a challenge. |
| `/exit` | Disconnect from the server. |

Use an actual challenge ID returned by `/create` or shown by `/list` instead of `101` when necessary. Categories must match an OpenTDB category name; difficulty can be `Easy`, `Medium`, or `Hard`.

## Example Game

**Player 1** creates a challenge:

```text
/create -category "Science: Computers" -difficulty "Medium" -count 3
```

**Player 2** lists and joins it:

```text
/list
/join -id 101
```

Once both players have joined, the game begins automatically. The server sends each question to both clients. They answer using commands such as:

```text
/answer -id 101 -answer C
```

Each question has a **30-second deadline**. Only each player's first valid submission is accepted. After the deadline, both players receive the correct answer and updated scores. After the last question, the server announces the winner or a tie.

## Implementation Overview

- **TCP server:** Accepts client connections and creates a separate `ClientHandler` thread for each player.
- **Challenge manager:** Maintains a shared, thread-safe challenge registry, coordinates challenge creation, and retrieves questions before a challenge is registered.
- **Challenge threads:** Manage individual game sessions, player participation, answer processing, deadlines, and scores. A `BlockingQueue` serializes challenge commands.
- **Client threads:** Separate keyboard input from server-message reception so questions and results can arrive asynchronously.
- **OpenTDB integration:** Retrieves categories and multiple-choice questions over HTTPS, parses JSON with Gson, and shuffles answer options.
- **Application-layer protocol:** Server messages have the format `<status-code> <message> <body-length>\r\n<body>`, where `body-length` is measured in UTF-8 bytes. Typical server-initiated statuses are `210 QUESTION`, `211 RESULT`, and `212 CHALLENGE_END`.

## Error Handling

The server validates commands, challenge IDs, categories, question counts, and answer choices. It rejects attempts to join full or active challenges, duplicate answers, and answers outside the question window. It also handles OpenTDB request failures, client disconnections, and the configured **90-second socket read timeout**.

## Notes

- A challenge begins when **exactly two players** have joined.
- The server retrieves questions at challenge creation time; gameplay does not require additional API calls.
- To test concurrent sessions, launch four clients and create two separate challenges.
- This README describes the project implementation developed in the COMP416 assignment. Verify the final code and test results before submission.
