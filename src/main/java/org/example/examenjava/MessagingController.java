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

    @FXML
    public void initialize() {
        setupUserList();
        setupMessagesContainer();
        setupMessageField();
        setupClearButton();
        currentUserLabel.setText("Utilisateur: Admin");
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
        String message = messageField.getText();
        String selectedUser = userListView.getSelectionModel().getSelectedItem();

        if (message == null || message.trim().isEmpty()) {
            showAlert("Erreur", "Veuillez écrire un message");
            return;
        }

        if (selectedUser == null) {
            showAlert("Erreur", "Veuillez sélectionner un utilisateur");
            return;
        }

        // Ajouter la bulle pour le message envoyé (alignée à droite)
        addMessageBubble("Vous", message, true);
        // Ici on pourrait envoyer le message au backend / save en base
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

    private void loadChatHistory(String username) {
        // Exemple de chargement d'historique (messages alternés)
        addMessageBubble("Alice", "[14:30] Bonjour, comment ça va?", false);
        addMessageBubble("Vous", "[14:31] Bien, et toi?", true);
        addMessageBubble("Alice", "[14:32] Super! On se voit ce soir?", false);
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
