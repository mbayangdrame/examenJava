package org.example.examenjava.network;

import javafx.application.Platform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ChatClient {
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());
    private static final String SERVER_HOST = "213.199.51.197";
    private static final int SERVER_PORT = 5555;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean connected = false;
    private boolean listening = false;
    private ScheduledExecutorService pingScheduler;

    // Callbacks
    private Consumer<ChatMessage> onMessageReceived;
    private Consumer<ChatMessage> onUserListUpdated;
    private Consumer<ChatMessage> onHistoryReceived;
    private Consumer<ChatMessage> onGroupListUpdated;
    private Consumer<ChatMessage> onGroupMessageReceived;
    private Consumer<ChatMessage> onGroupHistoryReceived;
    private Consumer<ChatMessage> onPendingUsersReceived;
    private Consumer<String> onApprovalResult;
    private Consumer<String> onError;
    private Runnable onDisconnected;

    public boolean connect() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            socket.setTcpNoDelay(true); // envoi immédiat, pas de bufferisation Nagle
            socket.setKeepAlive(true);  // keepalive OS en backup
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            LOGGER.info("Connecte au serveur " + SERVER_HOST + ":" + SERVER_PORT);
            return true;
        } catch (IOException e) {
            LOGGER.warning("Impossible de se connecter au serveur: " + e.getMessage());
            return false;
        }
    }

    public ChatMessage sendLoginAndWait(String username, String password) {
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.LOGIN);
            msg.setSender(username);
            msg.setPassword(password);
            out.writeObject(msg);
            out.flush();
            return (ChatMessage) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.severe("Erreur login: " + e.getMessage());
            return null;
        }
    }

    public ChatMessage sendRegisterAndWait(String username, String password, String email, String fullName, String role) {
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.REGISTER);
            msg.setSender(username);
            msg.setPassword(password);
            msg.setEmail(email);
            msg.setFullName(fullName);
            msg.setRole(role);
            out.writeObject(msg);
            out.flush();
            return (ChatMessage) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.severe("Erreur register: " + e.getMessage());
            return null;
        }
    }

    public void startListening() {
        if (listening) return;
        listening = true;

        // Heartbeat : PING toutes les 30s pour maintenir la connexion à travers les NAT/firewalls
        pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ping-scheduler");
            t.setDaemon(true);
            return t;
        });
        pingScheduler.scheduleAtFixedRate(
            () -> send(new ChatMessage(ChatMessage.Type.PING)),
            30, 30, TimeUnit.SECONDS
        );

        Thread t = new Thread(() -> {
            try {
                while (connected) {
                    ChatMessage msg = (ChatMessage) in.readObject();
                    Platform.runLater(() -> handleServerMessage(msg));
                }
            } catch (IOException e) {
                if (connected) {
                    connected = false;
                    listening = false;
                    Platform.runLater(() -> { if (onDisconnected != null) onDisconnected.run(); });
                }
            } catch (ClassNotFoundException e) {
                LOGGER.severe("Erreur de deserialization: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void handleServerMessage(ChatMessage msg) {
        switch (msg.getType()) {
            case RECEIVE_MESSAGE, READ_RECEIPT -> { if (onMessageReceived != null) onMessageReceived.accept(msg); }
            case USER_LIST_UPDATE -> { if (onUserListUpdated != null) onUserListUpdated.accept(msg); }
            case HISTORY_RESPONSE -> { if (onHistoryReceived != null) onHistoryReceived.accept(msg); }
            case GROUP_LIST_UPDATE -> { if (onGroupListUpdated != null) onGroupListUpdated.accept(msg); }
            case RECEIVE_GROUP_MESSAGE -> { if (onGroupMessageReceived != null) onGroupMessageReceived.accept(msg); }
            case GROUP_HISTORY_RESPONSE -> { if (onGroupHistoryReceived != null) onGroupHistoryReceived.accept(msg); }
            case PENDING_USERS_RESPONSE -> { if (onPendingUsersReceived != null) onPendingUsersReceived.accept(msg); }
            case APPROVE_USER_SUCCESS, REJECT_USER_SUCCESS -> { if (onApprovalResult != null) onApprovalResult.accept(msg.getContent()); }
            case CREATE_GROUP_SUCCESS, ADD_TO_GROUP -> { if (onApprovalResult != null) onApprovalResult.accept(msg.getContent()); }
            case CREATE_GROUP_FAILURE -> { if (onError != null) onError.accept(msg.getContent()); }
            case ERROR -> { if (onError != null) onError.accept(msg.getContent()); }
            case PONG -> { /* keepalive — rien à faire */ }
            default -> LOGGER.info("Message recu: " + msg.getType());
        }
    }

    /** Envoi thread-safe (partagé avec le ping scheduler) */
    private void send(ChatMessage msg) {
        if (!connected) return;
        try {
            synchronized (out) { out.writeObject(msg); out.flush(); out.reset(); }
        } catch (IOException e) {
            LOGGER.warning("Erreur envoi " + msg.getType() + ": " + e.getMessage());
        }
    }

    // ---- Envoi messages 1:1 ----
    public void sendChatMessage(String receiver, String content) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.SEND_MESSAGE);
        msg.setReceiver(receiver); msg.setContent(content);
        send(msg);
    }

    public void requestHistory(String otherUsername) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.REQUEST_HISTORY);
        msg.setReceiver(otherUsername);
        send(msg);
    }

    // ---- Groupes ----
    public void createGroup(String groupName) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.CREATE_GROUP);
        msg.setGroupName(groupName);
        send(msg);
    }

    public void addToGroup(Long groupId, String username) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.ADD_TO_GROUP);
        msg.setGroupId(groupId); msg.setReceiver(username);
        send(msg);
    }

    public void removeFromGroup(Long groupId, String username) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.REMOVE_FROM_GROUP);
        msg.setGroupId(groupId); msg.setReceiver(username);
        send(msg);
    }

    public void sendGroupMessage(Long groupId, String content) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.SEND_GROUP_MESSAGE);
        msg.setGroupId(groupId); msg.setContent(content);
        send(msg);
    }

    public void requestGroupHistory(Long groupId) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.REQUEST_GROUP_HISTORY);
        msg.setGroupId(groupId);
        send(msg);
    }

    public void toggleGroupSend(Long groupId, boolean membersCanSend) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.TOGGLE_GROUP_SEND);
        msg.setGroupId(groupId); msg.setMembersCanSend(membersCanSend);
        send(msg);
    }

    // ---- Approbation ----
    public void requestPendingUsers() {
        send(new ChatMessage(ChatMessage.Type.PENDING_USERS_REQUEST));
    }

    public void approveUser(String username) {
        ChatMessage msg = new ChatMessage(ChatMessage.Type.APPROVE_USER);
        msg.setReceiver(username);
        send(msg);
    }

    public void rejectUser(String username) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.REJECT_USER);
            msg.setReceiver(username);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur rejet: " + e.getMessage());
        }
    }

    // ---- Logout / disconnect ----
    public void sendLogout() {
        if (!connected) return;
        try {
            out.writeObject(new ChatMessage(ChatMessage.Type.LOGOUT));
            out.flush();
        } catch (IOException e) {
            LOGGER.warning("Erreur logout: " + e.getMessage());
        }
    }

    public void disconnect() {
        connected = false;
        listening = false;
        if (pingScheduler != null) pingScheduler.shutdownNow();
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            LOGGER.warning("Erreur fermeture: " + e.getMessage());
        }
    }

    public boolean isConnected() { return connected; }

    // ---- Setters callbacks ----
    public void setOnMessageReceived(Consumer<ChatMessage> h) { this.onMessageReceived = h; }
    public void setOnUserListUpdated(Consumer<ChatMessage> h) { this.onUserListUpdated = h; }
    public void setOnHistoryReceived(Consumer<ChatMessage> h) { this.onHistoryReceived = h; }
    public void setOnGroupListUpdated(Consumer<ChatMessage> h) { this.onGroupListUpdated = h; }
    public void setOnGroupMessageReceived(Consumer<ChatMessage> h) { this.onGroupMessageReceived = h; }
    public void setOnGroupHistoryReceived(Consumer<ChatMessage> h) { this.onGroupHistoryReceived = h; }
    public void setOnPendingUsersReceived(Consumer<ChatMessage> h) { this.onPendingUsersReceived = h; }
    public void setOnApprovalResult(Consumer<String> h) { this.onApprovalResult = h; }
    public void setOnError(Consumer<String> h) { this.onError = h; }
    public void setOnDisconnected(Runnable h) { this.onDisconnected = h; }
}
