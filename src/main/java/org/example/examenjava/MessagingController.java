package org.example.examenjava;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.examenjava.Repository.Database;
import org.example.examenjava.Entity.User;
import org.example.examenjava.Entity.Message;
import java.util.List;

public class MessagingController {
    @FXML
    private ListView<String> userListView;

    @FXML
    private ScrollPane chatScrollPane;

    @FXML
    private VBox messagesContainer;

    @FXML
    private TextField messageField;

    @FXML
    private Label currentUserLabel;

    @FXML
    private Label selectedUserLabel;

    @FXML
    private Label userStatusLabel;

    @FXML
    private Button sendButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Button clearButton;

    private User currentUser;

    @FXML
    public void initialize() {
        setupUserList();
        setupMessagesContainer();
        setupMessageField();
        setupClearButton();
        currentUserLabel.setText("Utilisateur: Admin");
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        currentUserLabel.setText("Utilisateur: " + user.getUsername());
    }

    private void setupUserList() {
        ObservableList<String> users = FXCollections.observableArrayList(
            "Alice Johnson",
            "Bob Smith",
            "Charlie Brown",
            "Diana Prince",
            "Eve Wilson",
            "Frank Miller"
        );
        userListView.setItems(users);
        userListView.setStyle("-fx-font-size: 12px; -fx-padding: 10px;");

        userListView.setOnMouseClicked(event -> {
            String selectedUser = userListView.getSelectionModel().getSelectedItem();
            if (selectedUser != null) {
                selectedUserLabel.setText("👤 Chat avec: " + selectedUser);
                messagesContainer.getChildren().clear();
                // Mettre à jour le statut
                userStatusLabel.setText("● En ligne");
                // Charger les messages historiques
                loadChatHistory(selectedUser);
            }
        });
    }

    private void setupMessagesContainer() {
        // messagesContainer est un VBox placé dans un ScrollPane (chatScrollPane)
        // on s'assure que le ScrollPane suit la hauteur des messages
        messagesContainer.setSpacing(8);
        messagesContainer.setStyle("-fx-padding: 6;");
        // Scroll to bottom lorsque le contenu change
        messagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
        });
    }

    private void setupMessageField() {
        messageField.setStyle("-fx-font-size: 12px; -fx-padding: 8px;");
        messageField.setPromptText("Écrivez votre message...");
    }

    private void setupClearButton() {
        clearButton.setOnAction(event -> {
            messageField.clear();
            messageField.requestFocus();
        });
    }

    @FXML
    protected void onSendButtonClick() {
        String contenu = messageField.getText();
        String selectedUsername = userListView.getSelectionModel().getSelectedItem();

        if (currentUser == null || currentUser.getStatus() != User.Status.ONLINE) {
            showAlert("Erreur", "Vous devez être connecté pour envoyer un message.");
            return;
        }
        if (contenu == null || contenu.trim().isEmpty()) {
            showAlert("Erreur", "Veuillez écrire un message.");
            return;
        }
        if (contenu.length() > 1000) {
            showAlert("Erreur", "Le message ne doit pas dépasser 1000 caractères.");
            return;
        }
        if (selectedUsername == null) {
            showAlert("Erreur", "Veuillez sélectionner un utilisateur.");
            return;
        }
        User receiver = Database.getInstance().findUserByUsername(selectedUsername);
        if (receiver == null) {
            showAlert("Erreur", "Le destinataire n'existe pas.");
            return;
        }
        Message message = new Message(currentUser, receiver, contenu);
        if (receiver.getStatus() == User.Status.ONLINE) {
            message.setStatut(Message.Statut.RECU);
        } else {
            message.setStatut(Message.Statut.ENVOYE);
        }
        Database.getInstance().saveMessage(message);
        addMessageBubble("Vous", contenu, true);
        messageField.clear();
        messageField.requestFocus();
    }

    @FXML
    protected void onLogoutButtonClick() {
        try {
            // Logique de déconnexion
            System.out.println("Déconnexion...");
            javafx.stage.Stage currentStage = (javafx.stage.Stage) logoutButton.getScene().getWindow();
            javafx.fxml.FXMLLoader fxmlLoader = new javafx.fxml.FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(fxmlLoader.load(), 500, 600);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());
            currentStage.setTitle("Connexion");
            currentStage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onShowMembersButtonClick() {
        if (currentUser == null || currentUser.getRole() != User.Role.ORGANISATEUR) {
            showAlert("Accès refusé", "Seuls les organisateurs peuvent consulter la liste des membres.");
            return;
        }
        List<User> membres = Database.getInstance().getAllUsers();
        StringBuilder sb = new StringBuilder();
        for (User u : membres) {
            sb.append(u.getUsername()).append(" (role: ").append(u.getRole()).append(")\n");
        }
        showAlert("Liste des membres", sb.toString());
    }

    private void loadChatHistory(String username) {
        User receiver = Database.getInstance().findUserByUsername(username);
        if (currentUser == null || receiver == null) return;
        List<Message> messages = Database.getInstance().getMessagesBetweenUsers(currentUser.getId(), receiver.getId());
        messagesContainer.getChildren().clear();
        for (Message msg : messages) {
            boolean isOwn = msg.getSender().getId().equals(currentUser.getId());
            String text = "[" + msg.getDateEnvoi().toLocalTime() + "] " + msg.getContenu();
            addMessageBubble(isOwn ? "Vous" : receiver.getUsername(), text, isOwn);
        }
    }

    private void addMessageBubble(String sender, String text, boolean isOwn) {
        // Label pour le contenu du message
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.getStyleClass().add(isOwn ? "message-bubble-own" : "message-bubble-other");
        // Limiter la largeur de la bulle à 60% de la container
        bubble.maxWidthProperty().bind(messagesContainer.widthProperty().multiply(0.62));

        // Créer un spacer pour pousser la bulle à droite/à gauche
        Region spacer = new Region();
        HBox hbox;
        if (isOwn) {
            hbox = new HBox(spacer, bubble);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            hbox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            hbox = new HBox(bubble, spacer);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            hbox.setAlignment(Pos.CENTER_LEFT);
        }
        hbox.setStyle("-fx-padding: 2 8;");
        messagesContainer.getChildren().add(hbox);
        // scroll to bottom
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
