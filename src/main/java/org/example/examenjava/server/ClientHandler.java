package org.example.examenjava.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.example.examenjava.Entity.GroupChat;
import org.example.examenjava.Entity.GroupMessage;
import org.example.examenjava.Entity.Message;
import org.example.examenjava.Entity.User;
import org.example.examenjava.Repository.Database;
import org.example.examenjava.network.ChatMessage;

public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

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
            case CREATE_GROUP -> handleCreateGroup(msg);
            case ADD_TO_GROUP -> handleAddToGroup(msg);
            case REMOVE_FROM_GROUP -> handleRemoveFromGroup(msg);
            case SEND_GROUP_MESSAGE -> handleSendGroupMessage(msg);
            case REQUEST_GROUP_HISTORY -> handleRequestGroupHistory(msg);
            case TOGGLE_GROUP_SEND -> handleToggleGroupSend(msg);
            case PENDING_USERS_REQUEST -> handlePendingUsersRequest();
            case APPROVE_USER -> handleApproveUser(msg);
            case REJECT_USER -> handleRejectUser(msg);
            case PING -> sendMessage(new ChatMessage(ChatMessage.Type.PONG));
            default -> LOGGER.warning("Type de message non gere: " + msg.getType());
        }
    }

    private static ChatMessage makeResponse(ChatMessage.Type type, String content) {
        ChatMessage m = new ChatMessage(type);
        m.setContent(content);
        return m;
    }

    // =====================================================================
    // AUTH
    // =====================================================================

    private void handleLogin(ChatMessage msg) {
        Database db = Database.getInstance();
        // Recherche insensible a la casse pour supporter toutes les variantes de saisie
        User user = db.findUserByUsernameIgnoreCase(msg.getSender());

        if (user == null) {
            sendMessage(makeResponse(ChatMessage.Type.LOGIN_FAILURE, "Utilisateur inconnu"));
            return;
        }
        if (!user.isApproved()) {
            sendMessage(makeResponse(ChatMessage.Type.LOGIN_FAILURE, "Votre compte est en attente d'approbation par un organisateur"));
            return;
        }
        String hashed = hashPassword(msg.getPassword());
        if (!user.getPassword().equals(hashed)) {
            sendMessage(makeResponse(ChatMessage.Type.LOGIN_FAILURE, "Mot de passe incorrect"));
            return;
        }
        // Si une ancienne session traîne (déconnexion brutale), on la force-ferme
        if (ChatServer.isUserOnline(user.getUsername())) {
            ClientHandler old = ChatServer.getClient(user.getUsername());
            if (old != null && old != this) {
                LOGGER.info("SESSION FANTOME detectee pour " + user.getUsername() + " — kick de l'ancienne session");
                old.forceDisconnect();
            }
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
        ChatServer.sendGroupListToUser(username);
    }

    private void handleRegister(ChatMessage msg) {
        Database db = Database.getInstance();

        // Vérification insensible à la casse : "Saliou" et "saliou" sont le même identifiant
        if (db.findUserByUsernameIgnoreCase(msg.getSender()) != null) {
            sendMessage(makeResponse(ChatMessage.Type.REGISTER_FAILURE, "Nom d'utilisateur deja utilise"));
            return;
        }

        // Seuls MEMBRE et BENEVOLE peuvent s'inscrire
        User.Role role;
        try {
            role = User.Role.valueOf(msg.getRole());
            if (role == User.Role.ORGANISATEUR) {
                sendMessage(makeResponse(ChatMessage.Type.REGISTER_FAILURE, "Impossible de creer un compte organisateur"));
                return;
            }
        } catch (Exception e) {
            sendMessage(makeResponse(ChatMessage.Type.REGISTER_FAILURE, "Role invalide"));
            return;
        }

        User newUser = new User();
        newUser.setUsername(msg.getSender());
        newUser.setPassword(hashPassword(msg.getPassword()));
        newUser.setEmail(msg.getEmail());
        newUser.setFullName(msg.getFullName());
        newUser.setRole(role);
        newUser.setStatus(User.Status.OFFLINE);
        newUser.setApproved(false);
        newUser.setDateCreation(LocalDateTime.now());
        db.saveUser(newUser);

        LOGGER.info("INSCRIPTION (en attente): " + msg.getSender() + " (role: " + role + ")");
        sendMessage(makeResponse(ChatMessage.Type.REGISTER_SUCCESS,
                "Inscription soumise. En attente d'approbation par un organisateur."));

        // Notifier les organisateurs connectes qu'il y a une nouvelle demande
        notifyOrganisateursPendingUpdate();
    }

    // =====================================================================
    // MESSAGES 1:1
    // =====================================================================

    private void handleSendMessage(ChatMessage msg) {
        if (username == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Vous devez etre connecte"));
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
        message.setStatut(receiverHandler != null ? Message.Statut.RECU : Message.Statut.ENVOYE);
        db.saveMessage(message);

        LOGGER.info("MESSAGE 1:1: " + username + " -> " + msg.getReceiver());
        String ts = message.getDateEnvoi().format(TIME_FMT);

        if (receiverHandler != null) {
            ChatMessage delivery = new ChatMessage(ChatMessage.Type.RECEIVE_MESSAGE);
            delivery.setSender(username);
            delivery.setReceiver(msg.getReceiver());
            delivery.setContent(msg.getContent());
            delivery.setTimestamp(ts);
            receiverHandler.sendMessage(delivery);
        }

        ChatMessage confirm = new ChatMessage(ChatMessage.Type.RECEIVE_MESSAGE);
        confirm.setSender(username);
        confirm.setReceiver(msg.getReceiver());
        confirm.setContent(msg.getContent());
        confirm.setTimestamp(ts);
        confirm.setStatus(message.getStatut().name());
        sendMessage(confirm);
    }

    private void handleRequestHistory(ChatMessage msg) {
        if (username == null) return;
        Database db = Database.getInstance();
        User currentUser = db.findUserByUsername(username);
        User otherUser = db.findUserByUsername(msg.getReceiver());
        if (currentUser == null || otherUser == null) return;

        List<Message> history = db.getMessagesBetweenUsers(currentUser.getId(), otherUser.getId());

        java.util.Set<String> sendersToNotify = new java.util.HashSet<>();
        for (Message m : history) {
            if (m.getReceiver().getId().equals(currentUser.getId()) && m.getStatut() != Message.Statut.LU) {
                sendersToNotify.add(m.getSender().getUsername());
                m.setStatut(Message.Statut.LU);
                db.markMessageAsRead(m);
            }
        }

        // Notify senders that their messages were read
        for (String senderName : sendersToNotify) {
            ClientHandler senderHandler = ChatServer.getClient(senderName);
            if (senderHandler != null) {
                ChatMessage receipt = new ChatMessage(ChatMessage.Type.READ_RECEIPT);
                receipt.setSender(username);   // who read
                receipt.setReceiver(senderName);
                senderHandler.sendMessage(receipt);
            }
        }

        ChatMessage response = new ChatMessage(ChatMessage.Type.HISTORY_RESPONSE);
        response.setReceiver(msg.getReceiver());
        List<ChatMessage.MessageInfo> infos = new ArrayList<>();
        for (Message m : history) {
            infos.add(new ChatMessage.MessageInfo(
                    m.getSender().getUsername(),
                    m.getReceiver().getUsername(),
                    m.getContenu(),
                    m.getDateEnvoi().format(TIME_FMT),
                    m.getStatut().name()
            ));
        }
        response.setMessages(infos);
        sendMessage(response);
    }

    // =====================================================================
    // GROUPES
    // =====================================================================

    private void handleCreateGroup(ChatMessage msg) {
        if (!isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.CREATE_GROUP_FAILURE, "Seul un organisateur peut creer un groupe"));
            return;
        }
        if (msg.getGroupName() == null || msg.getGroupName().trim().isEmpty()) {
            sendMessage(makeResponse(ChatMessage.Type.CREATE_GROUP_FAILURE, "Le nom du groupe ne peut pas etre vide"));
            return;
        }

        Database db = Database.getInstance();
        User creator = db.findUserByUsername(username);
        GroupChat group = new GroupChat(msg.getGroupName().trim(), creator);
        group.getMembers().add(creator); // l'organisateur est membre de son propre groupe
        GroupChat saved = db.saveGroup(group);

        if (saved != null) {
            LOGGER.info("GROUPE CREE: " + group.getName() + " par " + username);
            sendMessage(makeResponse(ChatMessage.Type.CREATE_GROUP_SUCCESS, "Groupe cree : " + group.getName()));
            ChatServer.broadcastGroupList();
        } else {
            sendMessage(makeResponse(ChatMessage.Type.CREATE_GROUP_FAILURE, "Erreur lors de la creation du groupe"));
        }
    }

    private void handleAddToGroup(ChatMessage msg) {
        if (!isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Seul un organisateur peut ajouter des membres"));
            return;
        }

        Database db = Database.getInstance();
        GroupChat group = db.findGroupById(msg.getGroupId());
        User target = db.findUserByUsername(msg.getReceiver());

        if (group == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Groupe introuvable"));
            return;
        }
        if (target == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Utilisateur introuvable"));
            return;
        }

        db.addUserToGroup(group.getId(), target.getId());
        LOGGER.info("AJOUT GROUPE: " + target.getUsername() + " -> " + group.getName());

        ChatMessage ok = makeResponse(ChatMessage.Type.ADD_TO_GROUP, target.getUsername() + " ajoute au groupe " + group.getName());
        ok.setGroupId(group.getId());
        sendMessage(ok);

        ChatServer.broadcastGroupList();
    }

    private void handleRemoveFromGroup(ChatMessage msg) {
        if (!isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Seul un organisateur peut retirer des membres"));
            return;
        }

        Database db = Database.getInstance();
        GroupChat group = db.findGroupById(msg.getGroupId());
        User target = db.findUserByUsername(msg.getReceiver());

        if (group == null || target == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Groupe ou utilisateur introuvable"));
            return;
        }

        db.removeUserFromGroup(group.getId(), target.getId());
        LOGGER.info("RETRAIT GROUPE: " + target.getUsername() + " <- " + group.getName());
        ChatServer.broadcastGroupList();
    }

    private void handleSendGroupMessage(ChatMessage msg) {
        if (username == null) return;

        Database db = Database.getInstance();
        GroupChat group = db.findGroupById(msg.getGroupId());
        if (group == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Groupe introuvable"));
            return;
        }

        // Verifier que le sender est membre du groupe
        boolean isMember = group.getMembers().stream().anyMatch(u -> u.getUsername().equals(username));
        if (!isMember) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Vous n'etes pas membre de ce groupe"));
            return;
        }

        // Bonus : verifier si les membres non-organisateurs peuvent envoyer
        if (!group.isMembersCanSend() && !isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "L'envoi de messages est desactive pour ce groupe"));
            return;
        }

        if (msg.getContent() == null || msg.getContent().trim().isEmpty()) return;
        if (msg.getContent().length() > 1000) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Message trop long (max 1000 caracteres)"));
            return;
        }

        User sender = db.findUserByUsername(username);
        GroupMessage gm = new GroupMessage(sender, group, msg.getContent());
        db.saveGroupMessage(gm);

        LOGGER.info("MESSAGE GROUPE: " + username + " -> [" + group.getName() + "]");
        String ts = gm.getDateEnvoi().format(TIME_FMT);

        // Envoyer a tous les membres connectes
        for (User member : group.getMembers()) {
            ClientHandler memberHandler = ChatServer.getClient(member.getUsername());
            if (memberHandler != null) {
                ChatMessage delivery = new ChatMessage(ChatMessage.Type.RECEIVE_GROUP_MESSAGE);
                delivery.setSender(username);
                delivery.setGroupId(group.getId());
                delivery.setGroupName(group.getName());
                delivery.setContent(msg.getContent());
                delivery.setTimestamp(ts);
                memberHandler.sendMessage(delivery);
            }
        }
    }

    private void handleRequestGroupHistory(ChatMessage msg) {
        if (username == null || msg.getGroupId() == null) return;

        Database db = Database.getInstance();
        GroupChat group = db.findGroupById(msg.getGroupId());
        if (group == null) return;

        // Verifier que l'utilisateur est membre
        boolean isMember = group.getMembers().stream().anyMatch(u -> u.getUsername().equals(username))
                || isOrganisateur();
        if (!isMember) return;

        List<GroupMessage> history = db.getGroupMessages(group.getId());
        List<ChatMessage.MessageInfo> infos = new ArrayList<>();
        for (GroupMessage m : history) {
            infos.add(new ChatMessage.MessageInfo(
                    m.getSender().getUsername(),
                    null,
                    m.getContenu(),
                    m.getDateEnvoi().format(TIME_FMT),
                    "LU"
            ));
        }

        ChatMessage response = new ChatMessage(ChatMessage.Type.GROUP_HISTORY_RESPONSE);
        response.setGroupId(group.getId());
        response.setGroupName(group.getName());
        response.setMembersCanSend(group.isMembersCanSend());
        response.setMessages(infos);
        sendMessage(response);
    }

    private void handleToggleGroupSend(ChatMessage msg) {
        if (!isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Seul un organisateur peut modifier ce parametre"));
            return;
        }

        Database db = Database.getInstance();
        GroupChat group = db.findGroupById(msg.getGroupId());
        if (group == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Groupe introuvable"));
            return;
        }

        group.setMembersCanSend(msg.isMembersCanSend());
        db.updateGroup(group);

        LOGGER.info("TOGGLE GROUPE SEND: " + group.getName() + " -> membersCanSend=" + msg.isMembersCanSend());
        ChatServer.broadcastGroupList();
    }

    // =====================================================================
    // APPROBATION
    // =====================================================================

    private void handlePendingUsersRequest() {
        if (!isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Acces reserve aux organisateurs"));
            return;
        }

        Database db = Database.getInstance();
        List<User> pending = db.getPendingUsers();
        List<ChatMessage.UserInfo> infos = new ArrayList<>();
        for (User u : pending) {
            infos.add(new ChatMessage.UserInfo(
                    u.getUsername(), u.getFullName(), u.getRole().name(), u.getStatus().name(), false
            ));
        }

        ChatMessage response = new ChatMessage(ChatMessage.Type.PENDING_USERS_RESPONSE);
        response.setUsers(infos);
        sendMessage(response);
    }

    private void handleApproveUser(ChatMessage msg) {
        if (!isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Acces reserve aux organisateurs"));
            return;
        }

        Database db = Database.getInstance();
        User target = db.findUserByUsername(msg.getReceiver());
        if (target == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Utilisateur introuvable"));
            return;
        }

        target.setApproved(true);
        db.updateUser(target);
        LOGGER.info("APPROBATION: " + target.getUsername() + " approuve par " + username);

        sendMessage(makeResponse(ChatMessage.Type.APPROVE_USER_SUCCESS,
                target.getUsername() + " approuve avec succes"));
        ChatServer.broadcastUserList();
    }

    private void handleRejectUser(ChatMessage msg) {
        if (!isOrganisateur()) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Acces reserve aux organisateurs"));
            return;
        }

        Database db = Database.getInstance();
        User target = db.findUserByUsername(msg.getReceiver());
        if (target == null) {
            sendMessage(makeResponse(ChatMessage.Type.ERROR, "Utilisateur introuvable"));
            return;
        }

        db.deleteUser(target);
        LOGGER.info("REJET: " + target.getUsername() + " rejete par " + username);
        sendMessage(makeResponse(ChatMessage.Type.REJECT_USER_SUCCESS, target.getUsername() + " rejete"));
    }

    // =====================================================================
    // DECONNEXION
    // =====================================================================

    private void handleLogout() {
        if (username != null) {
            try {
                Database db = Database.getInstance();
                User user = db.findUserByUsername(username);
                if (user != null) {
                    user.setStatus(User.Status.OFFLINE);
                    db.updateUser(user);
                }
                LOGGER.info("DECONNEXION: " + username);
            } catch (Exception e) {
                LOGGER.warning("Erreur lors de la déconnexion de " + username + ": " + e.getMessage());
            } finally {
                ChatServer.removeClient(username);
                ChatServer.broadcastUserList();
            }
        }
        running = false;
        closeConnection();
    }

    private void handleDisconnect() {
        if (username != null) {
            try {
                Database db = Database.getInstance();
                User user = db.findUserByUsername(username);
                if (user != null) {
                    user.setStatus(User.Status.OFFLINE);
                    db.updateUser(user);
                }
                LOGGER.info("PERTE DE CONNEXION: " + username);
            } catch (Exception e) {
                LOGGER.warning("Erreur lors du nettoyage de " + username + ": " + e.getMessage());
            } finally {
                ChatServer.removeClient(username);
                ChatServer.broadcastUserList();
            }
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
            delivery.setTimestamp(m.getDateEnvoi().format(TIME_FMT));
            sendMessage(delivery);
            db.markMessageAsRead(m);
        }
    }

    // =====================================================================
    // UTILITAIRES
    // =====================================================================

    private boolean isOrganisateur() {
        if (username == null) return false;
        User user = Database.getInstance().findUserByUsername(username);
        return user != null && user.getRole() == User.Role.ORGANISATEUR;
    }

    private void notifyOrganisateursPendingUpdate() {
        Database db = Database.getInstance();
        List<User> pending = db.getPendingUsers();
        List<ChatMessage.UserInfo> infos = new ArrayList<>();
        for (User u : pending) {
            infos.add(new ChatMessage.UserInfo(u.getUsername(), u.getFullName(), u.getRole().name(), u.getStatus().name(), false));
        }
        ChatMessage update = new ChatMessage(ChatMessage.Type.PENDING_USERS_RESPONSE);
        update.setUsers(infos);

        for (ClientHandler handler : ChatServer.connectedClients.values()) {
            if (handler.getUsername() == null) continue;
            User u = db.findUserByUsername(handler.getUsername());
            if (u != null && u.getRole() == User.Role.ORGANISATEUR) {
                handler.sendMessage(update);
            }
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

    /** Force la fermeture d'une session (ex : reconnexion d'un client déjà "connecté") */
    public void forceDisconnect() {
        running = false;
        if (username != null) {
            try {
                Database db = Database.getInstance();
                User user = db.findUserByUsername(username);
                if (user != null) {
                    user.setStatus(User.Status.OFFLINE);
                    db.updateUser(user);
                }
            } catch (Exception e) {
                LOGGER.warning("Erreur force disconnect " + username + ": " + e.getMessage());
            }
            ChatServer.removeClient(username);
        }
        closeConnection();
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

    public String getUsername() { return username; }

    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }
}
