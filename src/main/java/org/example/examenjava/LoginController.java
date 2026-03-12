package org.example.examenjava;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.examenjava.network.ChatClient;
import org.example.examenjava.network.ChatMessage;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Button registerLinkButton;

    @FXML
    protected void onLoginButtonClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Veuillez remplir tous les champs");
            return;
        }

        ChatClient client = new ChatClient();
        if (!client.connect()) {
            showError("Impossible de se connecter au serveur.");
            return;
        }

        ChatMessage response = client.sendLoginAndWait(username, password);
        if (response == null) {
            showError("Erreur de communication avec le serveur");
            client.disconnect();
            return;
        }

        if (response.getType() == ChatMessage.Type.LOGIN_SUCCESS) {
            openMessagingApplication(client, response);
        } else {
            showError(response.getContent());
            client.disconnect();
        }
    }

    private void openMessagingApplication(ChatClient client, ChatMessage loginResponse) {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("messaging-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());

            MessagingController controller = fxmlLoader.getController();
            controller.initWithClient(client, loginResponse);

            currentStage.setTitle("Messagerie Interne - " + loginResponse.getSender());
            currentStage.setScene(scene);
            currentStage.setMinWidth(900);
            currentStage.setMinHeight(600);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Erreur lors de l'ouverture de la messagerie.");
        }
    }

    @FXML
    protected void onRegisterLinkClick() {
        try {
            Stage currentStage = (Stage) registerLinkButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("register-view.fxml"));
            Scene scene = new Scene(loader.load(), 550, 650);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());
            currentStage.setTitle("Inscription - Messagerie Interne");
            currentStage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Erreur lors de l'ouverture de la page d'inscription.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
    }
}
