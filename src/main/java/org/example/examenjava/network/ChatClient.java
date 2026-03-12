package org.example.examenjava.network;

import javafx.application.Platform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
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
            case RECEIVE_MESSAGE -> { if (onMessageReceived != null) onMessageReceived.accept(msg); }
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
            default -> LOGGER.info("Message recu: " + msg.getType());
        }
    }

    // ---- Envoi messages 1:1 ----
    public void sendChatMessage(String receiver, String content) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.SEND_MESSAGE);
            msg.setReceiver(receiver);
            msg.setContent(content);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur envoi message: " + e.getMessage());
        }
    }

    public void requestHistory(String otherUsername) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.REQUEST_HISTORY);
            msg.setReceiver(otherUsername);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur demande historique: " + e.getMessage());
        }
    }

    // ---- Groupes ----
    public void createGroup(String groupName) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.CREATE_GROUP);
            msg.setGroupName(groupName);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur creation groupe: " + e.getMessage());
        }
    }

    public void addToGroup(Long groupId, String username) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.ADD_TO_GROUP);
            msg.setGroupId(groupId);
            msg.setReceiver(username);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur ajout groupe: " + e.getMessage());
        }
    }

    public void removeFromGroup(Long groupId, String username) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.REMOVE_FROM_GROUP);
            msg.setGroupId(groupId);
            msg.setReceiver(username);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur retrait groupe: " + e.getMessage());
        }
    }

    public void sendGroupMessage(Long groupId, String content) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.SEND_GROUP_MESSAGE);
            msg.setGroupId(groupId);
            msg.setContent(content);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur envoi message groupe: " + e.getMessage());
        }
    }

    public void requestGroupHistory(Long groupId) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.REQUEST_GROUP_HISTORY);
            msg.setGroupId(groupId);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur historique groupe: " + e.getMessage());
        }
    }

    public void toggleGroupSend(Long groupId, boolean membersCanSend) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.TOGGLE_GROUP_SEND);
            msg.setGroupId(groupId);
            msg.setMembersCanSend(membersCanSend);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur toggle groupe: " + e.getMessage());
        }
    }

    // ---- Approbation ----
    public void requestPendingUsers() {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.PENDING_USERS_REQUEST);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur demande users en attente: " + e.getMessage());
        }
    }

    public void approveUser(String username) {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.APPROVE_USER);
            msg.setReceiver(username);
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            LOGGER.warning("Erreur approbation: " + e.getMessage());
        }
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
