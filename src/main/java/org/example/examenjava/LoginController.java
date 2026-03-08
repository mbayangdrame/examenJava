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
import org.example.examenjava.Repository.Database;
import org.example.examenjava.Entity.User;

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
    private Button registerLinkButton;

    private User currentUser;

    @FXML
    protected void onLoginButtonClick() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Veuillez remplir tous les champs");
            return;
        }
        User user = Database.getInstance().findUserByUsername(username);
        if (user == null) {
            errorLabel.setText("Utilisateur inconnu");
            return;
        }
        String hashed = Integer.toHexString(password.hashCode());
        if (!user.getPassword().equals(hashed)) {
            errorLabel.setText("Mot de passe incorrect");
            return;
        }
        if (user.getStatus() == User.Status.ONLINE) {
            errorLabel.setText("Cet utilisateur est déjà connecté");
            return;
        }
        user.setStatus(User.Status.ONLINE);
        Database.getInstance().updateUser(user);
        this.currentUser = user;
        openMessagingApplication();
    }

    private void openMessagingApplication() {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("messaging-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 900, 700);
            scene.getStylesheets().add(HelloApplication.class.getResource("styles.css").toExternalForm());
            MessagingController controller = fxmlLoader.getController();
            controller.setCurrentUser(currentUser);
            currentStage.setTitle("Messagerie Interne");
            currentStage.setScene(scene);
        } catch (Exception e) {
            errorLabel.setText("Erreur lors de l'ouverture de la messagerie.");
        }
    }

    @FXML
    protected void onRegisterLinkClick() {
        try {

            Stage currentStage = (Stage) registerLinkButton.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(
                    HelloApplication.class.getResource("register-view.fxml")
            );

            Scene scene = new Scene(loader.load(), 600, 500);

            scene.getStylesheets().add(
                    HelloApplication.class.getResource("styles.css").toExternalForm()
            );

            currentStage.setTitle("Inscription utilisateur");
            currentStage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("Erreur lors de l'ouverture de la page d'inscription.");
        }
    }
}
