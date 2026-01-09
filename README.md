🚢 Multiplayer Battleship (Java Sockets & Multithreading)

A robust, CLI-based implementation of the classic Battleship strategy game, built from scratch using Java Sockets and Multithreading.

This project demonstrates core Network Computing concepts, including TCP/IP communication, Client-Server architecture, Thread Synchronization, and custom Application Layer Protocols.
📷 Preview

🚀 Key Features

    Client-Server Architecture: Centralized server manages game state, preventing P2P cheating.

    Multithreaded Concurrency: Server handles multiple simultaneous connections using the Runnable interface and Thread pooling.

    Real-Time Gameplay: Instant updates for moves, hits, and misses using a custom text-based protocol over TCP.

    Lobby System:

        User Registration & Authentication (Persistent storage in users.txt).

        Live Player List.

        Challenge / Accept / Decline mechanism.

    Robust Error Handling: Handles client disconnections, invalid moves, and synchronization issues gracefully.

    Rich CLI Interface: Visualizes the game board using ANSI Color Codes for ships, hits, and misses.

🛠️ Tech Stack

    Language: Java (JDK 8+)

    Networking: java.net.Socket, java.net.ServerSocket (TCP)

    Concurrency: java.util.concurrent, Thread, synchronized blocks

    I/O: java.io (BufferedReader, PrintWriter)

🧩 System Architecture

The system avoids Peer-to-Peer communication to ensure security and state consistency.

    The Server: Listens on Port 8888. Spawns a dedicated ClientHandler thread for every connected user.

    The Protocol: Uses a custom string-based protocol (e.g., FIRE:A,5, PLACE_SHIP:B,2,V) for lightweight, human-readable debugging.

    State Management: The server maintains the "Source of Truth" for the game board (char[][]), ensuring validation of every move before updating the clients.

🎮 How to Run

1. Start the Server

Firstly run javac BattleShipServer.java
Secondly run java BattleShipServer
# Output: --- BattleShip Server Started on Port 8888 ---


2. Start Client A (Player 1)

Firstly run javac BattleShipClient.java
Secondly java BattleShipClient
# Follow the menu to REGISTER or LOGIN


3. Start Client B (Player 2) Open a new terminal window and run the client again.

Firsttly run java BattleShipClient
# Login with a different account

📝 License

This project was created for the CCS3320 Network Computing coursework module on my University.
