package org.example.examenjava;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

public class LoginController {
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    @FXML
    protected void onLoginButtonClick() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Veuillez remplir tous les champs");
            return;
        }

        // Simulation d'authentification (à remplacer par une vraie logique)
        if (username.equals("admin") && password.equals("123456")) {
            openMessagingApplication();
        } else {
            errorLabel.setText("Identifiants invalides");
        }
    }

    private void openMessagingApplication() {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("messaging-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 900, 700);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());
            currentStage.setTitle("Messagerie Interne");
            currentStage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

