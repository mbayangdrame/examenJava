package org.example.examenjava.network;

import javafx.application.Platform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Client reseau qui se connecte au serveur de chat via Sockets.
 * L'adresse du serveur est configurable pour permettre la connexion entre machines.
 */
public class ChatClient {
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());
    private static final String SERVER_HOST = "213.199.51.197";
    private static final int SERVER_PORT = 5555;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean connected = false;
    private boolean listening = false;

    private Consumer<ChatMessage> onMessageReceived;
    private Consumer<ChatMessage> onUserListUpdated;
    private Consumer<ChatMessage> onHistoryReceived;
    private Consumer<String> onError;
    private Runnable onDisconnected;

    /**
     * Connecte au serveur. Retourne true si la connexion reussit.
     */
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

    /**
     * Envoie la requete de login et attend la reponse de maniere synchrone.
     */
    public ChatMessage sendLoginAndWait(String username, String password) {
        try {
            ChatMessage loginMsg = new ChatMessage(ChatMessage.Type.LOGIN);
            loginMsg.setSender(username);
            loginMsg.setPassword(password);
            out.writeObject(loginMsg);
            out.flush();
            return (ChatMessage) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.severe("Erreur login: " + e.getMessage());
            return null;
        }
    }

    /**
     * Envoie la requete d'inscription et attend la reponse de maniere synchrone.
     */
    public ChatMessage sendRegisterAndWait(String username, String password, String email, String fullName, String role) {
        try {
            ChatMessage registerMsg = new ChatMessage(ChatMessage.Type.REGISTER);
            registerMsg.setSender(username);
            registerMsg.setPassword(password);
            registerMsg.setEmail(email);
            registerMsg.setFullName(fullName);
            registerMsg.setRole(role);
            out.writeObject(registerMsg);
            out.flush();
            return (ChatMessage) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.severe("Erreur register: " + e.getMessage());
            return null;
        }
    }

    /**
     * Demarre le thread d'ecoute asynchrone pour recevoir les messages du serveur.
     * Appeler APRES le login reussi.
     */
    public void startListening() {
        if (listening) return;
        listening = true;
        Thread listenerThread = new Thread(() -> {
            try {
                while (connected) {
                    ChatMessage msg = (ChatMessage) in.readObject();
                    Platform.runLater(() -> handleServerMessage(msg));
                }
            } catch (IOException e) {
                if (connected) {
                    LOGGER.warning("Connexion perdue avec le serveur");
                    connected = false;
                    listening = false;
                    Platform.runLater(() -> {
                        if (onDisconnected != null) onDisconnected.run();
                    });
                }
            } catch (ClassNotFoundException e) {
                LOGGER.severe("Erreur de deserialization: " + e.getMessage());
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleServerMessage(ChatMessage msg) {
        switch (msg.getType()) {
            case RECEIVE_MESSAGE -> {
                if (onMessageReceived != null) onMessageReceived.accept(msg);
            }
            case USER_LIST_UPDATE -> {
                if (onUserListUpdated != null) onUserListUpdated.accept(msg);
            }
            case HISTORY_RESPONSE -> {
                if (onHistoryReceived != null) onHistoryReceived.accept(msg);
            }
            case ERROR -> {
                if (onError != null) onError.accept(msg.getContent());
            }
            default -> LOGGER.info("Message recu du serveur: " + msg.getType());
        }
    }

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
            LOGGER.warning("Erreur d'envoi du message: " + e.getMessage());
            if (onError != null) {
                Platform.runLater(() -> onError.accept("Erreur d'envoi du message"));
            }
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
            LOGGER.warning("Erreur de demande d'historique: " + e.getMessage());
        }
    }

    public void sendLogout() {
        if (!connected) return;
        try {
            ChatMessage msg = new ChatMessage(ChatMessage.Type.LOGOUT);
            out.writeObject(msg);
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

    public void setOnMessageReceived(Consumer<ChatMessage> handler) { this.onMessageReceived = handler; }
    public void setOnUserListUpdated(Consumer<ChatMessage> handler) { this.onUserListUpdated = handler; }
    public void setOnHistoryReceived(Consumer<ChatMessage> handler) { this.onHistoryReceived = handler; }
    public void setOnError(Consumer<String> handler) { this.onError = handler; }
    public void setOnDisconnected(Runnable handler) { this.onDisconnected = handler; }
}
