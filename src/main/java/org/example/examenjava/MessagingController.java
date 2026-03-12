package org.example.examenjava;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import org.example.examenjava.network.ChatClient;
import org.example.examenjava.network.ChatMessage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MessagingController {
    @FXML private ListView<String> userListView;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesContainer;
    @FXML private TextField messageField;
    @FXML private Label currentUserLabel;
    @FXML private Label selectedUserLabel;
    @FXML private Label userStatusLabel;
    @FXML private Label onlineCountLabel;
    @FXML private Button sendButton;
    @FXML private Button logoutButton;
    @FXML private Button membersButton;

    private ChatClient chatClient;
    private String currentUsername;
    private String currentRole;
    private String selectedUsername;
    private List<ChatMessage.UserInfo> currentUsers;

    @FXML
    public void initialize() {
        messagesContainer.setSpacing(6);
        messagesContainer.heightProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> chatScrollPane.setVvalue(1.0)));

        messageField.setOnAction(e -> onSendButtonClick());

        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    // Trouver le UserInfo correspondant
                    ChatMessage.UserInfo userInfo = findUserInfo(item);
                    HBox cellBox = new HBox(10);
                    cellBox.setAlignment(Pos.CENTER_LEFT);
                    cellBox.setPadding(new Insets(8, 12, 8, 12));

                    // Avatar cercle avec initiale
                    StackPane avatar = new StackPane();
                    avatar.setMinSize(38, 38);
                    avatar.setMaxSize(38, 38);
                    String avatarColor = getAvatarColor(item);
                    avatar.setStyle("-fx-background-color: " + avatarColor + "; -fx-background-radius: 19;");
                    Label initial = new Label(item.substring(0, 1).toUpperCase());
                    initial.setStyle("-fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold;");
                    avatar.getChildren().add(initial);

                    // Info utilisateur
                    VBox info = new VBox(2);
                    Label nameLabel = new Label(item);
                    nameLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

                    String roleText = userInfo != null ? userInfo.role : "";
                    boolean isOnline = userInfo != null && "ONLINE".equals(userInfo.status);
                    Label detailLabel = new Label(roleText);
                    detailLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #94a3b8;");
                    info.getChildren().addAll(nameLabel, detailLabel);
                    HBox.setHgrow(info, Priority.ALWAYS);

                    // Indicateur en ligne
                    Region statusDot = new Region();
                    statusDot.setMinSize(10, 10);
                    statusDot.setMaxSize(10, 10);
                    statusDot.setStyle("-fx-background-color: " + (isOnline ? "#10b981" : "#cbd5e1") + "; -fx-background-radius: 5;");

                    cellBox.getChildren().addAll(avatar, info, statusDot);
                    setGraphic(cellBox);
                    setText(null);
                    setStyle("-fx-background-color: transparent; -fx-padding: 2;");
                }
            }
        });

        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedUsername = newVal;
                selectedUserLabel.setText(newVal);

                ChatMessage.UserInfo info = findUserInfo(newVal);
                if (info != null) {
                    boolean isOnline = "ONLINE".equals(info.status);
                    userStatusLabel.setText(isOnline ? "En ligne" : "Hors ligne");
                    userStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + (isOnline ? "#10b981" : "#94a3b8") + ";");
                }

                messagesContainer.getChildren().clear();
                chatClient.requestHistory(newVal);
                messageField.requestFocus();
            }
        });
    }

    public void initWithClient(ChatClient client, ChatMessage loginResponse) {
        this.chatClient = client;
        this.currentUsername = loginResponse.getSender();
        this.currentRole = loginResponse.getRole();

        currentUserLabel.setText(loginResponse.getFullName());

        // RG13 : ORGANISATEUR peut voir la liste des membres
        if (membersButton != null) {
            membersButton.setVisible("ORGANISATEUR".equals(currentRole));
            membersButton.setManaged("ORGANISATEUR".equals(currentRole));
        }

        // Configurer les callbacks du client
        chatClient.setOnMessageReceived(this::onMessageReceived);
        chatClient.setOnUserListUpdated(this::onUserListUpdated);
        chatClient.setOnHistoryReceived(this::onHistoryReceived);
        chatClient.setOnError(this::onServerError);
        chatClient.setOnDisconnected(this::onDisconnected);

        // Démarrer le listener asynchrone pour recevoir les messages en temps réel
        chatClient.startListening();
    }

    private void onMessageReceived(ChatMessage msg) {
        if (selectedUsername != null && (msg.getSender().equals(selectedUsername) || msg.getSender().equals(currentUsername))) {
            boolean isOwn = msg.getSender().equals(currentUsername);
            addMessageBubble(msg.getContent(), msg.getTimestamp(), isOwn);
        }
    }

    private void onUserListUpdated(ChatMessage msg) {
        this.currentUsers = msg.getUsers();
        userListView.getItems().clear();
        int onlineCount = 0;
        for (ChatMessage.UserInfo user : msg.getUsers()) {
            if (!user.username.equals(currentUsername)) {
                userListView.getItems().add(user.username);
            }
            if ("ONLINE".equals(user.status)) onlineCount++;
        }
        if (onlineCountLabel != null) {
            onlineCountLabel.setText(onlineCount + " en ligne");
        }

        // Rafraîchir le statut de l'utilisateur sélectionné
        if (selectedUsername != null) {
            ChatMessage.UserInfo info = findUserInfo(selectedUsername);
            if (info != null) {
                boolean isOnline = "ONLINE".equals(info.status);
                userStatusLabel.setText(isOnline ? "En ligne" : "Hors ligne");
                userStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + (isOnline ? "#10b981" : "#94a3b8") + ";");
            }
        }

        // Forcer le refresh des cellules
        userListView.refresh();
    }

    private void onHistoryReceived(ChatMessage msg) {
        messagesContainer.getChildren().clear();
        if (msg.getMessages() != null) {
            for (ChatMessage.MessageInfo m : msg.getMessages()) {
                boolean isOwn = m.senderUsername.equals(currentUsername);
                addMessageBubble(m.content, m.timestamp, isOwn);
            }
        }
    }

    private void onServerError(String error) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(error);
        alert.showAndWait();
    }

    private void onDisconnected() {
        // RG10 : En cas de perte de connexion, afficher une erreur et passer hors ligne
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Deconnexion");
        alert.setHeaderText("Connexion perdue");
        alert.setContentText("La connexion avec le serveur a ete perdue. Vous allez etre redirige vers la page de connexion.");
        alert.showAndWait();
        navigateToLogin();
    }

    @FXML
    protected void onSendButtonClick() {
        if (selectedUsername == null) {
            showNotification("Selectionnez un contact d'abord");
            return;
        }

        String content = messageField.getText();
        if (content == null || content.trim().isEmpty()) return;
        if (content.length() > 1000) {
            showNotification("Message trop long (max 1000 caracteres)");
            return;
        }

        chatClient.sendChatMessage(selectedUsername, content.trim());
        messageField.clear();
        messageField.requestFocus();
    }

    @FXML
    protected void onLogoutButtonClick() {
        // RG4 : À la déconnexion le statut devient OFFLINE
        if (chatClient != null) {
            chatClient.sendLogout();
            chatClient.disconnect();
        }
        navigateToLogin();
    }

    @FXML
    protected void onShowMembersButtonClick() {
        // RG13 : Un ORGANISATEUR peut consulter la liste complète des membres inscrits
        if (!"ORGANISATEUR".equals(currentRole)) {
            showNotification("Acces reserve aux organisateurs");
            return;
        }

        if (currentUsers == null) return;

        StringBuilder sb = new StringBuilder();
        for (ChatMessage.UserInfo u : currentUsers) {
            String status = "ONLINE".equals(u.status) ? "En ligne" : "Hors ligne";
            sb.append(u.username)
                    .append(" - ").append(u.fullName)
                    .append(" (").append(u.role).append(") - ")
                    .append(status).append("\n");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Liste des membres");
        alert.setHeaderText("Membres inscrits (" + currentUsers.size() + ")");
        alert.setContentText(sb.toString());
        alert.getDialogPane().setMinWidth(400);
        alert.showAndWait();
    }

    private void addMessageBubble(String text, String timestamp, boolean isOwn) {
        VBox bubbleContainer = new VBox(2);
        bubbleContainer.setMaxWidth(Double.MAX_VALUE);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.getStyleClass().add(isOwn ? "message-bubble-own" : "message-bubble-other");
        bubble.maxWidthProperty().bind(messagesContainer.widthProperty().multiply(0.65));

        Label timeLabel = new Label(timestamp != null ? timestamp : LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.setStyle("-fx-font-size: 9; -fx-text-fill: " + (isOwn ? "#93c5fd" : "#94a3b8") + ";");

        Region spacer = new Region();
        HBox hbox;
        if (isOwn) {
            bubbleContainer.setAlignment(Pos.CENTER_RIGHT);
            bubbleContainer.getChildren().addAll(bubble, timeLabel);
            hbox = new HBox(spacer, bubbleContainer);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            hbox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bubbleContainer.setAlignment(Pos.CENTER_LEFT);
            bubbleContainer.getChildren().addAll(bubble, timeLabel);
            hbox = new HBox(bubbleContainer, spacer);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            hbox.setAlignment(Pos.CENTER_LEFT);
        }
        hbox.setPadding(new Insets(2, 12, 2, 12));

        messagesContainer.getChildren().add(hbox);
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void showNotification(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void navigateToLogin() {
        try {
            Stage currentStage = (Stage) logoutButton.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 500, 600);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());
            currentStage.setTitle("Messagerie Interne - Connexion");
            currentStage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ChatMessage.UserInfo findUserInfo(String username) {
        if (currentUsers == null) return null;
        for (ChatMessage.UserInfo u : currentUsers) {
            if (u.username.equals(username)) return u;
        }
        return null;
    }

    private String getAvatarColor(String name) {
        String[] colors = {"#6366f1", "#8b5cf6", "#ec4899", "#f43f5e", "#f97316", "#eab308", "#22c55e", "#14b8a6", "#0ea5e9", "#3b82f6"};
        return colors[Math.abs(name.hashCode()) % colors.length];
    }
}
