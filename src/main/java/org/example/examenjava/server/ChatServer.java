package org.example.examenjava.server;

import org.example.examenjava.Entity.User;
import org.example.examenjava.Repository.Database;
import org.example.examenjava.network.ChatMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

/**
 * Serveur de chat - Architecture client-serveur avec Sockets Java.
 * RG11 : Chaque client est géré dans un thread séparé.
 * RG12 : Le serveur journalise les connexions, déconnexions et envois de messages.
 */
public class ChatServer {
    private static final int PORT = 5555;
    private static final Logger LOGGER = Logger.getLogger(ChatServer.class.getName());
    private static final ConcurrentHashMap<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;

    public void start() {
        setupLogger();
        LOGGER.info("=== Demarrage du serveur de messagerie sur le port " + PORT + " ===");

        // Initialiser la base de données
        Database.getInstance();
        LOGGER.info("Base de donnees connectee.");

        // Mettre tous les utilisateurs hors ligne au démarrage
        resetAllUsersOffline();

        try {
            // Ecouter sur 0.0.0.0 pour accepter les connexions depuis d'autres machines
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("0.0.0.0", PORT));
            LOGGER.info("Serveur en ecoute sur 0.0.0.0:" + PORT + " (toutes les interfaces reseau)");

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("Nouvelle connexion depuis: " + clientSocket.getInetAddress().getHostAddress());

                // RG11 : Thread séparé par client
                ClientHandler handler = new ClientHandler(clientSocket);
                Thread clientThread = new Thread(handler);
                clientThread.setDaemon(true);
                clientThread.start();
            }
        } catch (IOException e) {
            LOGGER.severe("Erreur du serveur: " + e.getMessage());
        }
    }

    private void resetAllUsersOffline() {
        Database db = Database.getInstance();
        List<User> allUsers = db.getAllUsers();
        for (User user : allUsers) {
            if (user.getStatus() == User.Status.ONLINE) {
                user.setStatus(User.Status.OFFLINE);
                db.updateUser(user);
            }
        }
        LOGGER.info("Tous les utilisateurs remis hors ligne.");
    }

    public static void addClient(String username, ClientHandler handler) {
        connectedClients.put(username, handler);
        LOGGER.info("Client ajoute: " + username + " | Total en ligne: " + connectedClients.size());
    }

    public static void removeClient(String username) {
        connectedClients.remove(username);
        LOGGER.info("Client retire: " + username + " | Total en ligne: " + connectedClients.size());
    }

    public static ClientHandler getClient(String username) {
        return connectedClients.get(username);
    }

    public static boolean isUserOnline(String username) {
        return connectedClients.containsKey(username);
    }

    /**
     * Diffuse la liste des utilisateurs mise à jour à tous les clients connectés.
     */
    public static void broadcastUserList() {
        Database db = Database.getInstance();
        List<User> allUsers = db.getAllUsers();

        List<ChatMessage.UserInfo> userInfos = new ArrayList<>();
        for (User u : allUsers) {
            userInfos.add(new ChatMessage.UserInfo(
                    u.getUsername(),
                    u.getFullName(),
                    u.getRole().name(),
                    connectedClients.containsKey(u.getUsername()) ? "ONLINE" : "OFFLINE"
            ));
        }

        ChatMessage userListMsg = new ChatMessage(ChatMessage.Type.USER_LIST_UPDATE);
        userListMsg.setUsers(userInfos);

        for (ClientHandler handler : connectedClients.values()) {
            handler.sendMessage(userListMsg);
        }
    }

    private void setupLogger() {
        try {
            // Console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%1$tF %1$tT] [%2$s] %3$s%n",
                            record.getMillis(), record.getLevel(), record.getMessage());
                }
            });

            // File handler for persistent logs
            FileHandler fileHandler = new FileHandler("server.log", true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%1$tF %1$tT] [%2$s] %3$s%n",
                            record.getMillis(), record.getLevel(), record.getMessage());
                }
            });

            LOGGER.setUseParentHandlers(false);
            LOGGER.addHandler(consoleHandler);
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Erreur configuration logger: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ChatServer().start();
    }
}
