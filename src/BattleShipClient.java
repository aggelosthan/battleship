/**
 * BattleShipServer.java
 * --------------------------------------------------------------------------------------
 * Assessment: CCS3320 Network Computing
 * Student IDs: [CSY23077], [CSY23107]
 * --------------------------------------------------------------------------------------
 * Description:
 * The Client application for Battleship. It handles the Command Line Interface (CLI),
 * reads user input, sends commands to the server, and uses a background thread
 * to listen for asynchronous updates (like incoming challenges or hits).
 */

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.Arrays;

public class BattleShipClient {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 8888;

    // boards for visualization
    private static char[][] myBoard = new char[10][10];
    private static char[][] enemyBoard = new char[10][10];

    // Configurations
    private static final int[] SHIP_SIZES = {5, 4, 3, 2, 1};
    private static final char[] SHIP_SYMBOLS = {'C', 'B', 'K', 'S', 'D'};
    private static int currentShipIndex = 0;

    // Stores the command momentarily to allow for "Wait-for-Ack" validation
    private static String lastAttemptedPlacement = "";

    // terminal board colors for the ships, sea , hit & miss
    public static final String RESET = "\u001B[0m";
    public static final String BLUE = "\u001B[34m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String WHITE = "\u001B[37m";
    public static final String YELLOW = "\u001B[33m";
    public static final String CYAN = "\u001B[36m";


    public static void main(String[] args) {
        printUsageInstructions();
        initializeGrid(myBoard);
        initializeGrid(enemyBoard);

        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            System.out.println(GREEN + ">> Connected to BattleShip Server!" + RESET);

            // Start the background listener thread
            ServerListener listener = new ServerListener(socket);
            new Thread(listener).start();

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);


            while (true) {
                String command = scanner.nextLine();
                if (command.startsWith("PLACE_SHIP")) lastAttemptedPlacement = command;
                out.println(command);
                if (command.equals("LOGOUT")) break;
            }
            socket.close(); scanner.close();
        } catch (IOException e) {
            System.out.println(RED + "Could not connect to server." + RESET);
        }
    }

    // --- HELPER METHODS ---

    private static void printUsageInstructions() {
        System.out.println(CYAN + "=================================================" + RESET);
        System.out.println(YELLOW + "          WELCOME TO JACK SPARROW BATTLESHIP GAME 'ARGGGH' " + RESET);
        System.out.println(CYAN + "=================================================" + RESET);
        System.out.println("1. " + GREEN + "LOGIN:user@pass" + RESET);
        System.out.println("2. " + GREEN + "PLAYER_LIST" + RESET);
        System.out.println("3. " + GREEN + "CHALLENGE:opponent" + RESET);
        System.out.println("4. " + GREEN + "CHALLENGE_ACCEPTED:opponent" + RESET);
        System.out.println("5. " + GREEN + "CHALLENGE_DECLINED:opponent" + RESET);
        System.out.println("6. " + GREEN + "PLACE_SHIP:Row,Col,Dir" + RESET + " (e.g. PLACE_SHIP:A,0,V)");
        System.out.println("7. " + GREEN + "FIRE:Row,Col" + RESET + "           (e.g. FIRE:B,5)");
        System.out.println(CYAN + "=================================================" + RESET);
    }

    private static void initializeGrid(char[][] grid) {
        for (char[] row : grid) Arrays.fill(row, '~');
    }


    // Updates the local board with ship letter ONLY when server confirms it
    private static void commitLastShip() {
        if (currentShipIndex >= SHIP_SIZES.length) return;
        try {
            String data = lastAttemptedPlacement.split(":")[1];
            String[] parts = data.split(",");
            int row = parts[0].charAt(0) - 'A';
            int col = Integer.parseInt(parts[1]);
            String dir = parts[2].toUpperCase();

            int size = SHIP_SIZES[currentShipIndex];
            char symbol = SHIP_SYMBOLS[currentShipIndex];

            for (int i = 0; i < size; i++) {
                int r = row + (dir.equals("V") ? i : 0);
                int c = col + (dir.equals("H") ? i : 0);
                if(r >= 0 && r < 10 && c >= 0 && c < 10) myBoard[r][c] = symbol;
            }
            currentShipIndex++;
        } catch (Exception e) { }
    }

    private static void updateGrid(char[][] grid, String coords, char mark) {
        try {
            String[] parts = coords.split(",");
            int row = parts[0].charAt(0) - 'A';
            int col = Integer.parseInt(parts[1]);
            grid[row][col] = mark;
        } catch (Exception e) { }
    }

    // Display the board with the colors we initiated above
    public static void displayBoards() {
        // Clear screen (ANSI)
        System.out.print("\033[H\033[2J"); System.out.flush();

        System.out.println(YELLOW + "\n      --- MY FLEET ---                  --- TARGET GRID ---" + RESET);
        System.out.println("   0 1 2 3 4 5 6 7 8 9              0 1 2 3 4 5 6 7 8 9");

        for (int i = 0; i < 10; i++) {
            char rowLabel = (char) ('A' + i);
            System.out.print(rowLabel + "  ");
            for (int j = 0; j < 10; j++) printCell(myBoard[i][j]);
            System.out.print("           ");
            System.out.print(rowLabel + "  ");
            for (int j = 0; j < 10; j++) printCell(enemyBoard[i][j]);
            System.out.println();
        }


        System.out.println(CYAN + "\n-----------------------------------------------------------" + RESET);
        if (currentShipIndex < SHIP_SIZES.length) {
            System.out.println("STATUS: Placing Ships. Next: " + GREEN + getShipName(currentShipIndex) +
                    " (Size " + SHIP_SIZES[currentShipIndex] + ")" + RESET);
        } else {
            System.out.println("STATUS: Battle Mode! " + YELLOW + "FIRE AT WILL!" + RESET);
        }
        System.out.println(CYAN + "-----------------------------------------------------------" + RESET);
        System.out.print("Your Action > ");
    }

    private static String getShipName(int index) {
        switch(index) {
            case 0: return "Carrier [C]";
            case 1: return "Battleship [B]";
            case 2: return "Cruiser [K]";
            case 3: return "Submarine [S]";
            case 4: return "Destroyer [D]";
            default: return "Unknown";
        }
    }

    private static void printCell(char c) {
        if (c == '~') System.out.print(BLUE + "~ " + RESET);
        else if (c == 'X') System.out.print(RED + "X " + RESET);
        else if (c == 'O') System.out.print(WHITE + "O " + RESET);
        else if (Character.isLetter(c)) System.out.print(GREEN + c + " " + RESET);
        else System.out.print(c + " ");
    }

    // SERVER LISTENER -> Purpose: Listens for messages from the server on a separate thread

    static class ServerListener implements Runnable {
        private Socket socket;
        public ServerListener(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("SHIP_PLACED")) {
                        commitLastShip(); displayBoards();
                    }
                    else if (msg.startsWith("GAME_STARTED")) {
                        displayBoards();
                        System.out.println(YELLOW + ">> BATTLE BEGINS! " + msg.split(":")[1] + RESET);
                    }
                    else if (msg.startsWith("GAME_START")) {
                        currentShipIndex = 0;
                        initializeGrid(myBoard); initializeGrid(enemyBoard);
                        displayBoards();
                        System.out.println(YELLOW + ">> SETUP PHASE! " + msg.split(":")[1] + RESET);
                    }
                    else if (msg.startsWith("HIT:")) {
                        updateGrid(enemyBoard, msg.split(":")[1], 'X'); displayBoards();
                        System.out.println(GREEN + ">> KABOOM! Direct Hit!" + RESET);
                    }
                    else if (msg.startsWith("MISS:")) {
                        updateGrid(enemyBoard, msg.split(":")[1], 'O'); displayBoards();
                        System.out.println(WHITE + ">> Splash... missed." + RESET);
                    }
                    else if (msg.startsWith("ENEMY_HIT:")) {
                        updateGrid(myBoard, msg.split(":")[1], 'X'); displayBoards();
                        System.out.println(RED + ">> WARNING! We took a hit!" + RESET);
                    }
                    else if (msg.startsWith("ENEMY_MISSED:")) {
                        updateGrid(myBoard, msg.split(":")[1], 'O'); displayBoards();
                    }
                    else if (msg.startsWith("GAME_OVER")) {
                        String res = msg.split(":")[1];
                        System.out.println(CYAN + "\n=================================================" + RESET);
                        if (res.equals("YOU_WON")) {
                            System.out.println(GREEN + "   VICTORY! You sank the enemy fleet!" + RESET);
                        } else {
                            System.out.println(RED + "   DEFEAT... Your fleet is gone." + RESET);
                        }
                        System.out.println(CYAN + "=================================================" + RESET);
                    }
                    else if (msg.startsWith("CHALLENGE_REJECTED")) {
                        System.out.println(RED + ">> " + msg.split(":")[1] + RESET);
                    }
                    else if (msg.startsWith("ERROR")) {
                        System.out.println(RED + ">> " + msg + RESET);
                        System.out.print("Your Action > ");
                    }
                    else {
                        System.out.println("\n[SERVER]: " + msg);
                        System.out.print("Your Action > ");
                    }
                }
            } catch (IOException e) {
                System.out.println("Disconnected.");
                System.exit(0);
            }
        }
    }
}