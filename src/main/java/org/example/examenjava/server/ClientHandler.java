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

/**
 * RG11 : Chaque client est géré dans un thread séparé côté serveur.
 */
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

    private void handleLogin(ChatMessage msg) {
        Database db = Database.getInstance();
        User user = db.findUserByUsername(msg.getSender());

        if (user == null) {
            sendMessage(new ChatMessage(ChatMessage.Type.LOGIN_FAILURE) {{
                setContent("Utilisateur inconnu");
            }});
            return;
        }

        String hashed = hashPassword(msg.getPassword());
        if (!user.getPassword().equals(hashed)) {
            sendMessage(new ChatMessage(ChatMessage.Type.LOGIN_FAILURE) {{
                setContent("Mot de passe incorrect");
            }});
            return;
        }

        // RG3 : Un utilisateur ne peut être connecté qu'une seule fois
        if (ChatServer.isUserOnline(msg.getSender())) {
            sendMessage(new ChatMessage(ChatMessage.Type.LOGIN_FAILURE) {{
                setContent("Cet utilisateur est deja connecte");
            }});
            return;
        }

        // RG4 : À la connexion le statut devient ONLINE
        this.username = user.getUsername();
        user.setStatus(User.Status.ONLINE);
        db.updateUser(user);

        ChatServer.addClient(username, this);

        // RG12 : Le serveur doit journaliser les connexions
        LOGGER.info("CONNEXION: " + username + " (role: " + user.getRole() + ")");

        ChatMessage response = new ChatMessage(ChatMessage.Type.LOGIN_SUCCESS);
        response.setSender(user.getUsername());
        response.setFullName(user.getFullName());
        response.setRole(user.getRole().name());
        response.setEmail(user.getEmail());
        sendMessage(response);

        // Envoyer les messages non lus (RG6)
        deliverUnreadMessages(user);

        // Diffuser la liste des utilisateurs mise à jour à tous les clients
        ChatServer.broadcastUserList();
    }

    private void handleRegister(ChatMessage msg) {
        Database db = Database.getInstance();

        // RG1 : Le username doit être unique
        if (db.findUserByUsername(msg.getSender()) != null) {
            sendMessage(new ChatMessage(ChatMessage.Type.REGISTER_FAILURE) {{
                setContent("Nom d'utilisateur deja utilise");
            }});
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

        sendMessage(new ChatMessage(ChatMessage.Type.REGISTER_SUCCESS) {{
            setContent("Inscription reussie");
        }});
    }

    private void handleSendMessage(ChatMessage msg) {
        // RG5 : L'expéditeur doit être connecté
        if (username == null) {
            sendMessage(new ChatMessage(ChatMessage.Type.ERROR) {{
                setContent("Vous devez etre connecte pour envoyer un message");
            }});
            return;
        }

        // RG7 : Le contenu ne doit pas être vide et ne doit pas dépasser 1000 caractères
        if (msg.getContent() == null || msg.getContent().trim().isEmpty()) {
            sendMessage(new ChatMessage(ChatMessage.Type.ERROR) {{
                setContent("Le message ne peut pas etre vide");
            }});
            return;
        }
        if (msg.getContent().length() > 1000) {
            sendMessage(new ChatMessage(ChatMessage.Type.ERROR) {{
                setContent("Le message ne doit pas depasser 1000 caracteres");
            }});
            return;
        }

        Database db = Database.getInstance();
        User sender = db.findUserByUsername(username);
        User receiver = db.findUserByUsername(msg.getReceiver());

        // RG5 : Le destinataire doit exister
        if (receiver == null) {
            sendMessage(new ChatMessage(ChatMessage.Type.ERROR) {{
                setContent("Le destinataire n'existe pas");
            }});
            return;
        }

        // Créer et sauvegarder le message
        Message message = new Message(sender, receiver, msg.getContent());

        // RG6 : Si le destinataire est hors ligne, le message est enregistré
        ClientHandler receiverHandler = ChatServer.getClient(msg.getReceiver());
        if (receiverHandler != null) {
            message.setStatut(Message.Statut.RECU);
        } else {
            message.setStatut(Message.Statut.ENVOYE);
        }
        db.saveMessage(message);

        // RG12 : Journaliser les envois de messages
        LOGGER.info("MESSAGE: " + username + " -> " + msg.getReceiver() + " (" + msg.getContent().length() + " chars)");

        String timestamp = message.getDateEnvoi().format(DateTimeFormatter.ofPattern("HH:mm"));

        // Envoyer au destinataire s'il est en ligne
        if (receiverHandler != null) {
            ChatMessage delivery = new ChatMessage(ChatMessage.Type.RECEIVE_MESSAGE);
            delivery.setSender(username);
            delivery.setReceiver(msg.getReceiver());
            delivery.setContent(msg.getContent());
            delivery.setTimestamp(timestamp);
            receiverHandler.sendMessage(delivery);
        }

        // Confirmer l'envoi à l'expéditeur
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

        // RG8 : L'historique affiché par ordre chronologique (ORDER BY dateEnvoi ASC dans la requête)
        List<Message> history = db.getMessagesBetweenUsers(currentUser.getId(), otherUser.getId());

        // Marquer les messages reçus comme lus
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
        // RG4 : À la déconnexion le statut devient OFFLINE
        if (username != null) {
            Database db = Database.getInstance();
            User user = db.findUserByUsername(username);
            if (user != null) {
                user.setStatus(User.Status.OFFLINE);
                db.updateUser(user);
            }
            // RG12 : Journaliser les déconnexions
            LOGGER.info("DECONNEXION: " + username);
            ChatServer.removeClient(username);
            ChatServer.broadcastUserList();
        }
        running = false;
        closeConnection();
    }

    private void handleDisconnect() {
        // RG10 : En cas de perte de connexion, passer hors ligne
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

    // RG9 : Les mots de passe doivent être stockés sous forme hachée
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
