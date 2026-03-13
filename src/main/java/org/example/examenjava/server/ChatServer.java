package org.example.examenjava.server;

import org.example.examenjava.Entity.GroupChat;
import org.example.examenjava.Entity.User;
import org.example.examenjava.Repository.Database;
import org.example.examenjava.network.ChatMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class ChatServer {
    private static final int PORT = 5555;
    private static final Logger LOGGER = Logger.getLogger(ChatServer.class.getName());
    static final ConcurrentHashMap<String, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;

    public void start() {
        setupLogger();
        LOGGER.info("=== Demarrage du serveur de messagerie sur le port " + PORT + " ===");

        Database db = Database.getInstance();
        LOGGER.info("Base de donnees connectee.");

        // Seed organisateur par defaut si aucun user
        db.seedDefaultOrganisateur();

        // Mettre tous les utilisateurs hors ligne au demarrage
        resetAllUsersOffline();

        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("0.0.0.0", PORT));
            LOGGER.info("Serveur en ecoute sur 0.0.0.0:" + PORT + " (toutes les interfaces reseau)");

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);  // envoi immédiat, pas de bufferisation Nagle
                clientSocket.setKeepAlive(true);   // keepalive OS en backup
                LOGGER.info("Nouvelle connexion depuis: " + clientSocket.getInetAddress().getHostAddress());
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
     * Diffuse la liste des utilisateurs filtree selon le role du destinataire.
     * - ORGANISATEUR : voit tous les users approuves
     * - MEMBRE : voit seulement MEMBRE + ORGANISATEUR approuves
     * - BENEVOLE : liste vide (n'a pas de contacts individuels, passe par les groupes)
     */
    public static void broadcastUserList() {
        Database db = Database.getInstance();
        List<User> approvedUsers = db.getApprovedUsers();

        for (ClientHandler handler : connectedClients.values()) {
            String username = handler.getUsername();
            if (username == null) continue;

            User recipient = db.findUserByUsername(username);
            if (recipient == null) continue;

            List<ChatMessage.UserInfo> filtered = new ArrayList<>();
            for (User u : approvedUsers) {
                boolean include = false;
                switch (recipient.getRole()) {
                    case ORGANISATEUR -> include = true;
                    case MEMBRE -> include = (u.getRole() == User.Role.MEMBRE || u.getRole() == User.Role.ORGANISATEUR);
                    case BENEVOLE -> include = false;
                }
                if (include) {
                    filtered.add(new ChatMessage.UserInfo(
                            u.getUsername(),
                            u.getFullName(),
                            u.getRole().name(),
                            connectedClients.containsKey(u.getUsername()) ? "ONLINE" : "OFFLINE",
                            u.isApproved()
                    ));
                }
            }

            ChatMessage userListMsg = new ChatMessage(ChatMessage.Type.USER_LIST_UPDATE);
            userListMsg.setUsers(filtered);
            handler.sendMessage(userListMsg);
        }
    }

    /**
     * Diffuse la liste des groupes d'un utilisateur specifique.
     */
    public static void sendGroupListToUser(String username) {
        ClientHandler handler = connectedClients.get(username);
        if (handler == null) return;

        Database db = Database.getInstance();
        User user = db.findUserByUsername(username);
        if (user == null) return;

        List<GroupChat> groups;
        if (user.getRole() == User.Role.ORGANISATEUR) {
            groups = db.getAllGroups();
        } else {
            groups = db.getGroupsForUser(user.getId());
        }

        List<ChatMessage.GroupInfo> groupInfos = buildGroupInfoList(groups);
        ChatMessage msg = new ChatMessage(ChatMessage.Type.GROUP_LIST_UPDATE);
        msg.setGroups(groupInfos);
        handler.sendMessage(msg);
    }

    /**
     * Diffuse la liste des groupes a tous les membres connectes concernes.
     */
    public static void broadcastGroupList() {
        Database db = Database.getInstance();
        List<GroupChat> allGroups = db.getAllGroups();

        for (ClientHandler handler : connectedClients.values()) {
            String username = handler.getUsername();
            if (username == null) continue;

            User user = db.findUserByUsername(username);
            if (user == null) continue;

            List<GroupChat> userGroups;
            if (user.getRole() == User.Role.ORGANISATEUR) {
                userGroups = allGroups;
            } else {
                userGroups = db.getGroupsForUser(user.getId());
            }

            List<ChatMessage.GroupInfo> groupInfos = buildGroupInfoList(userGroups);
            ChatMessage msg = new ChatMessage(ChatMessage.Type.GROUP_LIST_UPDATE);
            msg.setGroups(groupInfos);
            handler.sendMessage(msg);
        }
    }

    public static List<ChatMessage.GroupInfo> buildGroupInfoList(List<GroupChat> groups) {
        List<ChatMessage.GroupInfo> infos = new ArrayList<>();
        for (GroupChat g : groups) {
            List<String> memberNames = new ArrayList<>();
            for (org.example.examenjava.Entity.User m : g.getMembers()) {
                memberNames.add(m.getUsername());
            }
            infos.add(new ChatMessage.GroupInfo(
                    g.getId(),
                    g.getName(),
                    g.getCreator().getUsername(),
                    g.isMembersCanSend(),
                    memberNames
            ));
        }
        return infos;
    }

    private void setupLogger() {
        try {
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%1$tF %1$tT] [%2$s] %3$s%n",
                            record.getMillis(), record.getLevel(), record.getMessage());
                }
            });

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
