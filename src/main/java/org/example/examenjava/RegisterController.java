package org.example.examenjava;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import org.example.examenjava.Repository.Database;
import org.example.examenjava.Entity.User;

public class RegisterController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button registerButton;
    @FXML
    private Button backToLoginButton;
    @FXML
    private TextField emailField;
    @FXML
    private TextField fullNameField;
    @FXML
    private ComboBox<String> roleComboBox;

    @FXML
    protected void onRegisterButtonClick(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String email = emailField.getText();
        String fullName = fullNameField.getText();
        String roleStr = roleComboBox.getValue();
        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || fullName.isEmpty() || roleStr == null) {
            errorLabel.setText("Veuillez remplir tous les champs.");
            return;
        }
        if (Database.getInstance().findUserByUsername(username) != null) {
            errorLabel.setText("Nom d'utilisateur déjà utilisé.");
            return;
        }
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(hashPassword(password));
        newUser.setEmail(email);
        newUser.setFullName(fullName);
        newUser.setRole(User.Role.valueOf(roleStr));
        newUser.setStatus(User.Status.OFFLINE);
        newUser.setDateCreation(java.time.LocalDateTime.now());
        Database.getInstance().saveUser(newUser);
        errorLabel.setText("Inscription réussie ! Vous pouvez vous connecter.");
    }

    private String hashPassword(String password) {
        // Simple hash pour démo, à remplacer par un vrai hash sécurisé
        return Integer.toHexString(password.hashCode());
    }

    @FXML
    protected void onBackToLoginClick(ActionEvent event) {
        try {
            Stage stage = (Stage) backToLoginButton.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/org/example/examenjava/login-view.fxml"));
            stage.setScene(new Scene(root));
        } catch (Exception e) {
            errorLabel.setText("Erreur lors du retour à la connexion.");
        }
    }
}
