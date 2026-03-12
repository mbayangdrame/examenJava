package org.example.examenjava;

import org.example.examenjava.network.ChatClient;
import org.example.examenjava.network.ChatMessage;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;
    @FXML private TextField fullNameField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;
    @FXML private Button backToLoginButton;

    @FXML
    protected void onRegisterButtonClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String email = emailField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String roleStr = roleComboBox.getValue();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || fullName.isEmpty() || roleStr == null) {
            showError("Veuillez remplir tous les champs.");
            return;
        }

        if (password.length() < 4) {
            showError("Le mot de passe doit contenir au moins 4 caracteres.");
            return;
        }

        ChatClient client = new ChatClient();
        if (!client.connect()) {
            showError("Impossible de se connecter au serveur.");
            return;
        }

        ChatMessage response = client.sendRegisterAndWait(username, password, email, fullName, roleStr);
        client.disconnect();

        if (response == null) {
            showError("Erreur de communication avec le serveur.");
            return;
        }

        if (response.getType() == ChatMessage.Type.REGISTER_SUCCESS) {
            showSuccess("Inscription reussie ! Vous pouvez vous connecter.");
            usernameField.clear();
            passwordField.clear();
            emailField.clear();
            fullNameField.clear();
            roleComboBox.setValue(null);
        } else {
            showError(response.getContent());
        }
    }

    @FXML
    protected void onBackToLoginClick() {
        try {
            Stage stage = (Stage) backToLoginButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 600);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());
            stage.setTitle("Messagerie ISI - Connexion");
            stage.setScene(scene);
        } catch (Exception e) {
            showError("Erreur lors du retour a la connexion.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12; -fx-font-weight: bold;");
    }

    private void showSuccess(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #00a884; -fx-font-size: 12; -fx-font-weight: bold;");
    }
}
