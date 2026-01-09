/**
 * BattleShipServer.java
 * --------------------------------------------------------------------------------------
 * Assessment: CCS3320 Network Computing
 * Student IDs: [CSY23077], [CSY23107]
 * --------------------------------------------------------------------------------------
 * Description:
 * This class implements a multi-threaded TCP server for the Battleship game.
 * It manages client connections, persistent user authentication, and game sessions.
 * Architecture: Client-Server with a dedicated thread per client (ClientHandler).
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class BattleShipServer {
    private static final int PORT = 8888;

    // Thread-safe map to store active sessions: "Username" -> ClientHandler Object
    // We use ConcurrentHashMap to prevent concurrency issues when multiple threads access the list.
    public static ConcurrentHashMap<String, ClientHandler> onlinePlayers = new ConcurrentHashMap<>();


    //     Continuously listens for new client connections on port 8888.
    public static void main(String[] args) {
        System.out.println("--- BattleShip Server Started on Port " + PORT + " ---");

        // Load registered users from file into memory
        UserDatabase.loadUsers();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Block until a client connects
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection: " + clientSocket.getInetAddress());

                // Spawn a new thread to handle this specific client
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // DATABASE -> Manages persistent storage (users.txt) for authentication.
    static class UserDatabase {
        private static final String FILE_NAME = "users.txt";
        private static ArrayList<String> users = new ArrayList<>();


        // Loads the user into an arrayList
        public static synchronized void loadUsers() {
            File file = new File(FILE_NAME);
            if (!file.exists()) {
                try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
            }
            try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) users.add(line);
                }
                System.out.println("Database loaded: " + users.size() + " users.");
            } catch (IOException e) {
                System.out.println("Error loading DB: " + e.getMessage());
            }
        }

        // register the user if the user is unique
        public static synchronized boolean register(String user, String pass) {
            for (String record : users) {
                if (record.split(":")[0].equals(user)) return false;
            }
            String record = user + ":" + pass;
            users.add(record);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
                bw.write(record);
                bw.newLine();
            } catch (IOException e) { return false; }
            return true;
        }

        // User verification, checks if user and password match in the database
        public static synchronized boolean checkLogin(String user, String pass) {
            return users.contains(user + ":" + pass);
        }
    }

    // INNER CLASS: CLIENT HANDLER -> Acts as the "Worker" thread for a single connected client.
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username = null;
        private GameSession currentSession = null;

        public ClientHandler(Socket socket) { this.socket = socket; }

        public void sendMessage(String msg) { out.println(msg); }
        public String getUsername() { return username; }

        @Override
        public void run() {
            try {
                // Initialize Streams
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("CMD from " + (username != null ? username : "Guest") + ": " + inputLine);

                    String[] parts = inputLine.split(":");
                    String command = parts[0];
                    String data = parts.length > 1 ? parts[1] : "";

                    // Authentication section
                    if (command.equals("LOGIN")) {
                        String[] creds = data.split("@");
                        if (creds.length == 2 && UserDatabase.checkLogin(creds[0], creds[1])) {
                            this.username = creds[0];
                            onlinePlayers.put(username, this);
                            sendMessage("LOGIN_SUCCESS");
                        } else {
                            sendMessage("LOGIN_FAILED:Invalid Credentials");
                        }
                    }
                    else if (command.equals("REGISTER")) {
                        String[] creds = data.split("@");
                        if (creds.length == 2 && UserDatabase.register(creds[0], creds[1])) {
                            sendMessage("REGISTER_SUCCESS");
                        } else {
                            sendMessage("REGISTER_FAILED:Username taken");
                        }
                    }
                    // --- Matchmaking section ---
                    else if (command.equals("PLAYER_LIST")) {
                        sendMessage("PLAYER_LIST:" + String.join(",", onlinePlayers.keySet()));
                    }
                    else if (command.equals("CHALLENGE")) {
                        ClientHandler opponent = onlinePlayers.get(data);
                        if (opponent != null && !opponent.getUsername().equals(username)) {
                            opponent.sendMessage("CHALLENGE_FROM:" + this.username);
                        } else {
                            sendMessage("ERROR:Player not found");
                        }
                    }
                    else if (command.equals("CHALLENGE_ACCEPTED")) {
                        ClientHandler challenger = onlinePlayers.get(data);
                        if (challenger != null) {
                            // Create the game session linking both players
                            GameSession session = new GameSession(challenger, this);
                            challenger.currentSession = session;
                            this.currentSession = session;

                            challenger.sendMessage("GAME_START:You go first");
                            this.sendMessage("GAME_START:Opponent goes first");
                        }
                    }
                    else if (command.equals("CHALLENGE_DECLINED")) {
                        ClientHandler challenger = onlinePlayers.get(data);
                        if (challenger != null) {
                            challenger.sendMessage("CHALLENGE_REJECTED:" + this.username + " declined.");
                        }
                    }
                    // --- Gameplay section ---
                    else if (command.equals("PLACE_SHIP")) {
                        if (currentSession != null) currentSession.placeShip(this, data);
                    }
                    else if (command.equals("FIRE")) {
                        if (currentSession != null) currentSession.processMove(this, data);
                    }
                    else if (command.equals("LOGOUT")) break;
                }
            } catch (IOException e) {
                System.out.println("Connection dropped: " + username);
            } finally {
                // Cleanup: Remove user from online list on disconnect
                if (username != null) onlinePlayers.remove(username);
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }


    // GAME SESSION -> Encapsulates the state and logic of a single match between two players.
    static class GameSession {
        private ClientHandler player1, player2;
        private char[][] p1Board = new char[10][10];
        private char[][] p2Board = new char[10][10];

        // Configurations
        private final int[] SHIP_SIZES = {5, 4, 3, 2, 1};
        private int p1ShipIndex = 0, p2ShipIndex = 0;

        // Turn State
        private boolean isPlayer1Turn = true;
        private int p1HitsTaken = 0, p2HitsTaken = 0; // Win Condition: 17 hits

        public GameSession(ClientHandler p1, ClientHandler p2) {
            this.player1 = p1;
            this.player2 = p2;
            for(char[] row : p1Board) Arrays.fill(row, '~');
            for(char[] row : p2Board) Arrays.fill(row, '~');
        }


        // Handles ship placement and validates bounds and any overlap before
        public synchronized void placeShip(ClientHandler player, String data) {
            boolean isP1 = (player == player1);
            int currentIdx = isP1 ? p1ShipIndex : p2ShipIndex;
            char[][] board = isP1 ? p1Board : p2Board;

            if (currentIdx >= SHIP_SIZES.length) {
                player.sendMessage("ERROR:All ships placed");
                return;
            }

            // Parse placement data: Row,Col,Direction
            String[] parts = data.split(",");
            int row = parts[0].charAt(0) - 'A';
            int col = Integer.parseInt(parts[1]);
            char dir = parts[2].toUpperCase().charAt(0);
            int size = SHIP_SIZES[currentIdx];

            // Boundary Check
            if (dir == 'H' && col + size > 10) { player.sendMessage("ERROR:Ship sticks out (Horizontal)"); return; }
            if (dir == 'V' && row + size > 10) { player.sendMessage("ERROR:Ship sticks out (Vertical)"); return; }

            // Overlap Check
            for (int i = 0; i < size; i++) {
                int r = row + (dir == 'V' ? i : 0);
                int c = col + (dir == 'H' ? i : 0);
                if (board[r][c] == 'S') { player.sendMessage("ERROR:Overlap detected"); return; }
            }

            // Commit to Board
            for (int i = 0; i < size; i++) {
                int r = row + (dir == 'V' ? i : 0);
                int c = col + (dir == 'H' ? i : 0);
                board[r][c] = 'S';
            }

            player.sendMessage("SHIP_PLACED");
            if (isP1) p1ShipIndex++; else p2ShipIndex++;

            // 4. Check if both players are ready to start
            if (p1ShipIndex == 5 && p2ShipIndex == 5) {
                player1.sendMessage("GAME_STARTED:Your turn");
                player2.sendMessage("GAME_STARTED:Enemy turn");
            }
        }


        // Process the attack (FIRE) Command and checks turns, coordinates and Hit or Miss
        public synchronized void processMove(ClientHandler player, String coords) {
            // Setup Check
            if (p1ShipIndex < 5 || p2ShipIndex < 5) { player.sendMessage("ERROR:Game not started"); return; }

            // Turn Check
            if (player == player1 && !isPlayer1Turn) { player.sendMessage("ERROR:Wait for turn"); return; }
            if (player == player2 && isPlayer1Turn) { player.sendMessage("ERROR:Wait for turn"); return; }

            String[] parts = coords.split(",");
            int row = parts[0].charAt(0) - 'A';
            int col = Integer.parseInt(parts[1]);

            char[][] targetBoard = (player == player1) ? p2Board : p1Board;
            ClientHandler opponent = (player == player1) ? player2 : player1;

            // Prevent duplicate shots
            if (targetBoard[row][col] == 'X' || targetBoard[row][col] == 'O') {
                player.sendMessage("ERROR:Already fired there");
                return;
            }

            // Hit Logic
            if (targetBoard[row][col] == 'S') {
                targetBoard[row][col] = 'X';
                if (player == player1) p2HitsTaken++; else p1HitsTaken++;
                player.sendMessage("HIT:" + coords);
                opponent.sendMessage("ENEMY_HIT:" + coords);
                checkWin(player, opponent);
            } else { // Miss Logic
                targetBoard[row][col] = 'O';
                player.sendMessage("MISS:" + coords);
                opponent.sendMessage("ENEMY_MISSED:" + coords);
            }

            // Toggle Turn
            isPlayer1Turn = !isPlayer1Turn;
        }

        // Obvious function :P
        private void checkWin(ClientHandler winner, ClientHandler loser) {
            if (p1HitsTaken == 17 || p2HitsTaken == 17) {
                winner.sendMessage("GAME_OVER:YOU_WON");
                loser.sendMessage("GAME_OVER:YOU_LOST");
            }
        }
    }
}