package org.example.examenjava.server;

import org.example.examenjava.Entity.Message;
import org.example.examenjava.Entity.User;
import org.example.examenjava.Repository.Database;
import org.example.examenjava.network.ChatMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private boolean running = true;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (running) {
                ChatMessage msg = (ChatMessage) in.readObject();
                handleMessage(msg);
            }
        } catch (IOException e) {
            LOGGER.info("Client deconnecte: " + (username != null ? username : "inconnu"));
        } catch (ClassNotFoundException e) {
            LOGGER.severe("Erreur de deserialization: " + e.getMessage());
        } finally {
            handleDisconnect();
        }
    }

    private void handleMessage(ChatMessage msg) {
        switch (msg.getType()) {
            case LOGIN -> handleLogin(msg);
            case REGISTER -> handleRegister(msg);
            case LOGOUT -> handleLogout();
            case SEND_MESSAGE -> handleSendMessage(msg);
            case REQUEST_HISTORY -> handleRequestHistory(msg);
            default -> LOGGER.warning("Type de message non gere: " + msg.getType());
        }
    }

    private static ChatMessage makeResponse(ChatMessage.Type type, String content) {
        ChatMessage m = new ChatMessage(type);
        m.setContent(content);
        return m;
    }

    private void handleLogin(ChatMessage msg) {
        Database db = Database.getInstance();
        User user = db.findUserByUsername(msg.getSender());

        if (user == null) {
            sendMessage(makeResponse(ChatMessage.Type.LOGIN_FAILURE, "Utilisateur inconnu"));
            return;
        }

        String hashed = hashPassword(msg.getPassword());
        if (!user.getPassword().equals(hashed)) {
            sendMessage(makeResponse(ChatMessage.Type.LOGIN_FAILURE, "Mot de passe incorrect"));
            return;
        }

        if (ChatServer.isUserOnline(msg.getSender())) {
            sendMessage(makeResponse(ChatMessage.Type.LOGIN_FAILURE, "Cet utilisateur est deja connecte"));
            return;
        }

        this.username = user.getUsername();
        user.setStatus(User.Status.ONLINE);
        db.updateUser(user);

        ChatServer.addClient(username, this);
        LOGGER.info("CONNEXION: " + username + " (role: " + user.getRole() + ")");

        ChatMessage response = new ChatMessage(ChatMessage.Type.LOGIN_SUCCESS);
        response.setSender(user.getUsername());
        response.setFullName(user.getFullName());
        response.setRole(user.getRole().name());
        response.setEmail(user.getEmail());
        sendMessage(response);

        deliverUnreadMessages(user);
        ChatServer.broadcastUserList();
    }

    private void handleRegister(ChatMessage msg) {
        Database db = Database.getInstance();

        if (db.findUserByUsername(msg.getSender()) != null) {
            sendMessage(makeResponse(ChatMessage.Type.REGISTER_FAILURE, "Nom d'utilisateur deja utilise"));
            return;
        }

        User newUser = new User();
        newUser.setUsername(msg.getSender());
        newUser.setPassword(hashPassword(msg.getPassword()));
        newUser.setEmail(msg.getEmail());
        newUser.setFullName(msg.getFullName());
        newUser.setRole(User.Role.valueOf(msg.getRole()));
        newUser.setStatus(User.Status.OFFLINE);
        newUser.setDateCreation(LocalDateTime.now());
        db.saveUser(newUser);

        LOGGER.info("INSCRIPTION: " + msg.getSender() + " (role: " + msg.getRole() + ")");
        sendMessage(makeResponse(ChatMessage.Type.REGISTER_SUCCESS, "Inscription reussie"));
    }

    private void handleSendMessage(ChatMessage msg) {
        if (username == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Vous devez etre connecte pour envoyer un message"));
            return;
        }

        if (msg.getContent() == null || msg.getContent().trim().isEmpty()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Le message ne peut pas etre vide"));
            return;
        }
        if (msg.getContent().length() > 1000) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Le message ne doit pas depasser 1000 caracteres"));
            return;
        }

        Database db = Database.getInstance();
        User sender = db.findUserByUsername(username);
        User receiver = db.findUserByUsername(msg.getReceiver());

        if (receiver == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Le destinataire n'existe pas"));
            return;
        }

        Message message = new Message(sender, receiver, msg.getContent());

        ClientHandler receiverHandler = ChatServer.getClient(msg.getReceiver());
        if (receiverHandler != null) {
            message.setStatut(Message.Statut.RECU);
        } else {
            message.setStatut(Message.Statut.ENVOYE);
        }
        db.saveMessage(message);

        LOGGER.info("MESSAGE: " + username + " -> " + msg.getReceiver() + " (" + msg.getContent().length() + " chars)");

        String timestamp = message.getDateEnvoi().format(DateTimeFormatter.ofPattern("HH:mm"));

        if (receiverHandler != null) {
            ChatMessage delivery = new ChatMessage(ChatMessage.Type.RECEIVE_MESSAGE);
            delivery.setSender(username);
            delivery.setReceiver(msg.getReceiver());
            delivery.setContent(msg.getContent());
            delivery.setTimestamp(timestamp);
            receiverHandler.sendMessage(delivery);
        }

        ChatMessage confirm = new ChatMessage(ChatMessage.Type.RECEIVE_MESSAGE);
        confirm.setSender(username);
        confirm.setReceiver(msg.getReceiver());
        confirm.setContent(msg.getContent());
        confirm.setTimestamp(timestamp);
        sendMessage(confirm);
    }

    private void handleRequestHistory(ChatMessage msg) {
        Database db = Database.getInstance();
        User currentUser = db.findUserByUsername(username);
        User otherUser = db.findUserByUsername(msg.getReceiver());

        if (currentUser == null || otherUser == null) return;

        List<Message> history = db.getMessagesBetweenUsers(currentUser.getId(), otherUser.getId());

        for (Message m : history) {
            if (m.getReceiver().getId().equals(currentUser.getId()) && m.getStatut() != Message.Statut.LU) {
                db.markMessageAsRead(m);
            }
        }

        ChatMessage response = new ChatMessage(ChatMessage.Type.HISTORY_RESPONSE);
        response.setReceiver(msg.getReceiver());
        List<ChatMessage.MessageInfo> messageInfos = new ArrayList<>();
        for (Message m : history) {
            messageInfos.add(new ChatMessage.MessageInfo(
                    m.getSender().getUsername(),
                    m.getReceiver().getUsername(),
                    m.getContenu(),
                    m.getDateEnvoi().format(DateTimeFormatter.ofPattern("HH:mm")),
                    m.getStatut().name()
            ));
        }
        response.setMessages(messageInfos);
        sendMessage(response);
    }

    private void handleLogout() {
        if (username != null) {
            Database db = Database.getInstance();
            User user = db.findUserByUsername(username);
            if (user != null) {
                user.setStatus(User.Status.OFFLINE);
                db.updateUser(user);
            }
            LOGGER.info("DECONNEXION: " + username);
            ChatServer.removeClient(username);
            ChatServer.broadcastUserList();
        }
        running = false;
        closeConnection();
    }

    private void handleDisconnect() {
        if (username != null) {
            Database db = Database.getInstance();
            User user = db.findUserByUsername(username);
            if (user != null) {
                user.setStatus(User.Status.OFFLINE);
                db.updateUser(user);
            }
            LOGGER.info("PERTE DE CONNEXION: " + username);
            ChatServer.removeClient(username);
            ChatServer.broadcastUserList();
        }
        closeConnection();
    }

    private void deliverUnreadMessages(User user) {
        Database db = Database.getInstance();
        List<Message> unread = db.getUnreadMessages(user.getId());
        for (Message m : unread) {
            ChatMessage delivery = new ChatMessage(ChatMessage.Type.RECEIVE_MESSAGE);
            delivery.setSender(m.getSender().getUsername());
            delivery.setReceiver(m.getReceiver().getUsername());
            delivery.setContent(m.getContenu());
            delivery.setTimestamp(m.getDateEnvoi().format(DateTimeFormatter.ofPattern("HH:mm")));
            sendMessage(delivery);
            db.markMessageAsRead(m);
        }
    }

    public synchronized void sendMessage(ChatMessage msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur d'envoi vers " + username + ": " + e.getMessage());
        }
    }

    private void closeConnection() {
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.warning("Erreur lors de la fermeture: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }
}
