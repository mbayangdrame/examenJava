package org.example.examenjava;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.examenjava.network.ChatClient;
import org.example.examenjava.network.ChatMessage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;

public class MessagingController {

    // Navbar
    @FXML private Region currentUserAvatar;
    @FXML private Label  currentUserInitial;
    @FXML private Button navChatBtn;
    @FXML private Button navGroupBtn;
    @FXML private Button navPendingBtn;
    @FXML private Label  pendingBadge;
    @FXML private Button navSettingsBtn;

    // Sidebar panels
    @FXML private VBox panelContacts;
    @FXML private VBox panelGroups;
    @FXML private VBox panelPending;
    @FXML private VBox panelSettings;

    // Contacts panel
    @FXML private TextField searchField;
    @FXML private ListView<String> userListView;
    @FXML private Label onlineCountLabel;

    // Groups panel
    @FXML private ListView<ChatMessage.GroupInfo> groupListView;
    @FXML private Button createGroupBtn;

    // Pending panel
    @FXML private ListView<ChatMessage.UserInfo> pendingListView;
    @FXML private Label pendingEmptyLabel;

    // Settings panel
    @FXML private Region settingsAvatar;
    @FXML private Label  settingsInitial;
    @FXML private Label  settingsNameLabel;
    @FXML private Label  settingsRoleLabel;
    @FXML private ToggleButton themeToggle;
    @FXML private Label  themeLabel;

    // Chat area
    @FXML private VBox   welcomePane;
    @FXML private VBox   chatPane;
    @FXML private Region selectedUserAvatar;
    @FXML private Label  selectedUserInitial;
    @FXML private Label  selectedUserLabel;
    @FXML private Label  userStatusLabel;
    @FXML private Button toggleSendBtn;
    @FXML private Button addMemberBtn;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox   messagesContainer;
    @FXML private HBox   inputBar;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private HBox   sendDisabledBar;

    // State
    private ChatClient chatClient;
    private String currentUsername;
    private String currentFullName;
    private String currentRole;
    private String selectedContactUsername;
    private ChatMessage.GroupInfo selectedGroup;

    private List<ChatMessage.UserInfo> allUsers   = new ArrayList<>();
    private List<ChatMessage.GroupInfo> allGroups = new ArrayList<>();

    private final Map<String, List<CachedMsg>> messageCache = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();

    private boolean isDarkTheme = true;
    private static final Preferences PREFS = Preferences.userRoot().node("messagerie-isi");

    private static class CachedMsg {
        String sender, content, timestamp; boolean isOwn;
        CachedMsg(String s, String c, String t, boolean o) { sender=s; content=c; timestamp=t; isOwn=o; }
    }

    @FXML
    public void initialize() {
        messagesContainer.setSpacing(4);
        messagesContainer.heightProperty().addListener((obs, o, n) ->
                Platform.runLater(() -> chatScrollPane.setVvalue(1.0)));
        messageField.setOnAction(e -> onSendButtonClick());

        isDarkTheme = PREFS.getBoolean("darkTheme", true);
        themeToggle.setSelected(isDarkTheme);
        updateThemeToggleLabel();

        searchField.textProperty().addListener((obs, old, val) -> filterContacts(val));

        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); setStyle(""); return; }
                setGraphic(buildContactCell(item, findUserInfo(item), unreadCounts.getOrDefault(item, 0)));
                setText(null); setStyle("-fx-background-color: transparent; -fx-padding: 2;");
            }
        });
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) openContactChat(val);
        });

        groupListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ChatMessage.GroupInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); setStyle(""); return; }
                setGraphic(buildGroupCell(item, unreadCounts.getOrDefault("group:"+item.id, 0)));
                setText(null); setStyle("-fx-background-color: transparent; -fx-padding: 2;");
            }
        });
        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) openGroupChat(val);
        });

        pendingListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ChatMessage.UserInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); setStyle(""); return; }
                setGraphic(buildPendingCell(item));
                setText(null); setStyle("-fx-background-color: transparent; -fx-padding: 2;");
            }
        });
    }

    public void initWithClient(ChatClient client, ChatMessage loginResponse) {
        this.chatClient = client;
        this.currentUsername = loginResponse.getSender();
        this.currentFullName = loginResponse.getFullName();
        this.currentRole = loginResponse.getRole();

        String color = getAvatarColor(currentUsername);
        currentUserAvatar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 22;");
        currentUserInitial.setText(currentUsername.substring(0, 1).toUpperCase());
        settingsAvatar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 26;");
        settingsInitial.setText(currentUsername.substring(0, 1).toUpperCase());
        settingsNameLabel.setText(currentFullName);
        settingsRoleLabel.setText(getRoleBadgeText(currentRole));

        if ("ORGANISATEUR".equals(currentRole)) {
            navPendingBtn.setVisible(true);  navPendingBtn.setManaged(true);
            createGroupBtn.setVisible(true); createGroupBtn.setManaged(true);
        }

        chatClient.setOnMessageReceived(this::onMessageReceived);
        chatClient.setOnUserListUpdated(this::onUserListUpdated);
        chatClient.setOnHistoryReceived(this::onHistoryReceived);
        chatClient.setOnGroupListUpdated(this::onGroupListUpdated);
        chatClient.setOnGroupMessageReceived(this::onGroupMessageReceived);
        chatClient.setOnGroupHistoryReceived(this::onGroupHistoryReceived);
        chatClient.setOnPendingUsersReceived(this::onPendingUsersReceived);
        chatClient.setOnApprovalResult(this::onApprovalResult);
        chatClient.setOnError(this::onServerError);
        chatClient.setOnDisconnected(this::onDisconnected);

        chatClient.startListening();
        if ("ORGANISATEUR".equals(currentRole)) chatClient.requestPendingUsers();
    }

    // --- Navigation ---
    @FXML protected void onNavChat()     { showPanel(panelContacts); setNavActive(navChatBtn); }
    @FXML protected void onNavGroups()   { showPanel(panelGroups);   setNavActive(navGroupBtn); }
    @FXML protected void onNavPending()  { showPanel(panelPending);  setNavActive(navPendingBtn); chatClient.requestPendingUsers(); }
    @FXML protected void onNavSettings() { showPanel(panelSettings); setNavActive(navSettingsBtn); }

    private void showPanel(VBox panel) {
        for (VBox p : List.of(panelContacts, panelGroups, panelPending, panelSettings)) {
            p.setVisible(false); p.setManaged(false);
        }
        panel.setVisible(true); panel.setManaged(true);
    }
    private void setNavActive(Button active) {
        String on  = "-fx-background-color: #2a3942; -fx-text-fill: #00a884; -fx-font-size: 22; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10;";
        String off = "-fx-background-color: transparent; -fx-text-fill: #8696a0; -fx-font-size: 22; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10;";
        for (Button b : new Button[]{navChatBtn, navGroupBtn, navPendingBtn, navSettingsBtn}) {
            if (b != null) b.setStyle(b == active ? on : off);
        }
    }

    // --- Ouvrir chat 1:1 ---
    private void openContactChat(String username) {
        selectedContactUsername = username; selectedGroup = null;
        unreadCounts.remove(username); userListView.refresh();

        String color = getAvatarColor(username);
        selectedUserAvatar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 22;");
        selectedUserInitial.setText(username.substring(0, 1).toUpperCase());
        selectedUserLabel.setText(username);
        ChatMessage.UserInfo info = findUserInfo(username);
        if (info != null) {
            boolean online = "ONLINE".equals(info.status);
            userStatusLabel.setText(online ? "En ligne" : "Hors ligne");
            userStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + (online ? "#00a884" : "#667781") + ";");
        }
        toggleSendBtn.setVisible(false); toggleSendBtn.setManaged(false);
        addMemberBtn.setVisible(false);  addMemberBtn.setManaged(false);
        showInputBar(true); showChatPane();

        String key = username;
        if (messageCache.containsKey(key)) {
            messagesContainer.getChildren().clear();
            for (CachedMsg m : messageCache.get(key)) addMessageBubble(m.sender, m.content, m.timestamp, m.isOwn, false);
        } else {
            messagesContainer.getChildren().clear();
            chatClient.requestHistory(username);
        }
        messageField.requestFocus();
    }

    // --- Ouvrir chat groupe ---
    private void openGroupChat(ChatMessage.GroupInfo group) {
        selectedGroup = group; selectedContactUsername = null;
        unreadCounts.remove("group:" + group.id); groupListView.refresh();

        selectedUserAvatar.setStyle("-fx-background-color: #8b5cf6; -fx-background-radius: 22;");
        selectedUserInitial.setText(group.name.substring(0, 1).toUpperCase());
        selectedUserLabel.setText(group.name);
        userStatusLabel.setText(group.memberUsernames.size() + " membres");
        userStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #8696a0;");

        boolean isOrga = "ORGANISATEUR".equals(currentRole);
        if (isOrga) {
            toggleSendBtn.setVisible(true); toggleSendBtn.setManaged(true);
            addMemberBtn.setVisible(true);  addMemberBtn.setManaged(true);
            toggleSendBtn.setText(group.membersCanSend ? "Desactiver envoi" : "Activer envoi");
        } else {
            toggleSendBtn.setVisible(false); toggleSendBtn.setManaged(false);
            addMemberBtn.setVisible(false);  addMemberBtn.setManaged(false);
        }
        showInputBar(group.membersCanSend || isOrga);
        showChatPane();

        String key = "group:" + group.id;
        if (messageCache.containsKey(key)) {
            messagesContainer.getChildren().clear();
            for (CachedMsg m : messageCache.get(key)) addMessageBubble(m.sender, m.content, m.timestamp, m.isOwn, true);
        } else {
            messagesContainer.getChildren().clear();
            chatClient.requestGroupHistory(group.id);
        }
        messageField.requestFocus();
    }

    private void showChatPane() {
        welcomePane.setVisible(false); welcomePane.setManaged(false);
        chatPane.setVisible(true); chatPane.setManaged(true);
    }
    private void showInputBar(boolean canSend) {
        inputBar.setVisible(canSend); inputBar.setManaged(canSend);
        sendDisabledBar.setVisible(!canSend); sendDisabledBar.setManaged(!canSend);
    }

    // --- Callbacks reseau ---
    private void onMessageReceived(ChatMessage msg) {
        boolean isOwn = msg.getSender().equals(currentUsername);
        if (isOwn) {
            if (selectedContactUsername != null && msg.getReceiver().equals(selectedContactUsername)) {
                addAndCache(selectedContactUsername, msg.getSender(), msg.getContent(), msg.getTimestamp(), true, false);
            }
            return;
        }
        String sender = msg.getSender();
        if (selectedContactUsername != null && sender.equals(selectedContactUsername)) {
            addAndCache(sender, sender, msg.getContent(), msg.getTimestamp(), false, false);
        } else {
            unreadCounts.merge(sender, 1, Integer::sum); userListView.refresh();
        }
        playNotificationSound(); flashTitle("Nouveau message de " + sender);
    }

    private void onUserListUpdated(ChatMessage msg) {
        allUsers = msg.getUsers() != null ? msg.getUsers() : new ArrayList<>();
        filterContacts(searchField.getText());
        long online = allUsers.stream().filter(u -> "ONLINE".equals(u.status)).count();
        if (onlineCountLabel != null) onlineCountLabel.setText(online + " en ligne");
        if (selectedContactUsername != null) {
            ChatMessage.UserInfo info = findUserInfo(selectedContactUsername);
            if (info != null) {
                boolean o = "ONLINE".equals(info.status);
                userStatusLabel.setText(o ? "En ligne" : "Hors ligne");
                userStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + (o ? "#00a884" : "#667781") + ";");
            }
        }
    }

    private void onHistoryReceived(ChatMessage msg) {
        if (msg.getMessages() == null) return;
        String key = msg.getReceiver();
        messagesContainer.getChildren().clear();
        List<CachedMsg> cache = new ArrayList<>();
        for (ChatMessage.MessageInfo m : msg.getMessages()) {
            boolean isOwn = m.senderUsername.equals(currentUsername);
            addMessageBubble(m.senderUsername, m.content, m.timestamp, isOwn, false);
            cache.add(new CachedMsg(m.senderUsername, m.content, m.timestamp, isOwn));
        }
        messageCache.put(key, cache);
    }

    private void onGroupListUpdated(ChatMessage msg) {
        allGroups = msg.getGroups() != null ? msg.getGroups() : new ArrayList<>();
        groupListView.getItems().setAll(allGroups);
        if (selectedGroup != null) {
            allGroups.stream().filter(g -> g.id.equals(selectedGroup.id)).findFirst().ifPresent(g -> {
                selectedGroup = g;
                boolean isOrga = "ORGANISATEUR".equals(currentRole);
                showInputBar(g.membersCanSend || isOrga);
                if (isOrga) toggleSendBtn.setText(g.membersCanSend ? "Desactiver envoi" : "Activer envoi");
                userStatusLabel.setText(g.memberUsernames.size() + " membres");
            });
        }
    }

    private void onGroupMessageReceived(ChatMessage msg) {
        String key = "group:" + msg.getGroupId();
        boolean isOwn = msg.getSender().equals(currentUsername);
        if (selectedGroup != null && selectedGroup.id.equals(msg.getGroupId())) {
            addAndCache(key, msg.getSender(), msg.getContent(), msg.getTimestamp(), isOwn, true);
        } else if (!isOwn) {
            unreadCounts.merge(key, 1, Integer::sum); groupListView.refresh();
            playNotificationSound(); flashTitle("Nouveau message dans " + msg.getGroupName());
        }
    }

    private void onGroupHistoryReceived(ChatMessage msg) {
        if (msg.getMessages() == null) return;
        String key = "group:" + msg.getGroupId();
        messagesContainer.getChildren().clear();
        List<CachedMsg> cache = new ArrayList<>();
        for (ChatMessage.MessageInfo m : msg.getMessages()) {
            boolean isOwn = m.senderUsername.equals(currentUsername);
            addMessageBubble(m.senderUsername, m.content, m.timestamp, isOwn, true);
            cache.add(new CachedMsg(m.senderUsername, m.content, m.timestamp, isOwn));
        }
        messageCache.put(key, cache);
    }

    private void onPendingUsersReceived(ChatMessage msg) {
        List<ChatMessage.UserInfo> pending = msg.getUsers() != null ? msg.getUsers() : new ArrayList<>();
        pendingListView.getItems().setAll(pending);
        boolean empty = pending.isEmpty();
        pendingEmptyLabel.setVisible(empty); pendingEmptyLabel.setManaged(empty);
        if (!empty) {
            pendingBadge.setText(String.valueOf(pending.size()));
            pendingBadge.setVisible(true); pendingBadge.setManaged(true);
        } else {
            pendingBadge.setVisible(false); pendingBadge.setManaged(false);
        }
    }

    private void onApprovalResult(String message) { chatClient.requestPendingUsers(); }

    private void onServerError(String error) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Erreur"); a.setHeaderText(null); a.setContentText(error); a.showAndWait();
    }

    private void onDisconnected() {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Deconnexion"); a.setHeaderText("Connexion perdue");
        a.setContentText("La connexion avec le serveur a ete perdue."); a.showAndWait();
        navigateToLogin();
    }

    // --- Actions UI ---
    @FXML protected void onSendButtonClick() {
        String content = messageField.getText();
        if (content == null || content.trim().isEmpty()) return;
        if (content.length() > 1000) return;
        if (selectedContactUsername != null) chatClient.sendChatMessage(selectedContactUsername, content.trim());
        else if (selectedGroup != null)      chatClient.sendGroupMessage(selectedGroup.id, content.trim());
        else return;
        messageField.clear(); messageField.requestFocus();
    }

    @FXML protected void onCreateGroup() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Creer un groupe"); dlg.setHeaderText("Nouveau groupe");
        dlg.setContentText("Nom :"); dlg.getDialogPane().setStyle("-fx-background-color: #202c33;");
        dlg.showAndWait().ifPresent(n -> { if (!n.trim().isEmpty()) chatClient.createGroup(n.trim()); });
    }

    @FXML protected void onAddMemberToGroup() {
        if (selectedGroup == null) return;
        List<String> nonMembers = new ArrayList<>();
        for (ChatMessage.UserInfo u : allUsers) {
            if (!selectedGroup.memberUsernames.contains(u.username) && !u.username.equals(currentUsername))
                nonMembers.add(u.username + " (" + u.role + ")");
        }
        if (nonMembers.isEmpty()) { showInfo("Tous les utilisateurs sont deja membres."); return; }
        ChoiceDialog<String> dlg = new ChoiceDialog<>(nonMembers.get(0), nonMembers);
        dlg.setTitle("Ajouter un membre"); dlg.setHeaderText("Groupe : " + selectedGroup.name);
        dlg.setContentText("Utilisateur :"); dlg.getDialogPane().setStyle("-fx-background-color: #202c33;");
        dlg.showAndWait().ifPresent(c -> chatClient.addToGroup(selectedGroup.id, c.split(" \\(")[0]));
    }

    @FXML protected void onToggleGroupSend() {
        if (selectedGroup == null) return;
        chatClient.toggleGroupSend(selectedGroup.id, !selectedGroup.membersCanSend);
    }

    @FXML protected void onLogoutButtonClick() {
        if (chatClient != null) { chatClient.sendLogout(); chatClient.disconnect(); }
        navigateToLogin();
    }

    @FXML protected void onToggleTheme() {
        isDarkTheme = themeToggle.isSelected();
        PREFS.putBoolean("darkTheme", isDarkTheme);
        updateThemeToggleLabel();
        applyTheme();
    }

    void approveUser(String username) { chatClient.approveUser(username); }
    void rejectUser(String username)  { chatClient.rejectUser(username); }

    // --- Theme ---
    private void updateThemeToggleLabel() {
        if (themeToggle != null) {
            themeToggle.setText(isDarkTheme ? "ON" : "OFF");
            themeToggle.setStyle(isDarkTheme
                ? "-fx-background-color: #00a884; -fx-text-fill: white; -fx-font-size: 11; -fx-background-radius: 12; -fx-padding: 4 14; -fx-cursor: hand;"
                : "-fx-background-color: #374248; -fx-text-fill: #8696a0; -fx-font-size: 11; -fx-background-radius: 12; -fx-padding: 4 14; -fx-cursor: hand;");
        }
        if (themeLabel != null) themeLabel.setText(isDarkTheme ? "Theme sombre" : "Theme clair");
    }

    private void applyTheme() {
        try {
            Scene scene = messageField.getScene();
            if (scene == null) return;
            scene.getStylesheets().clear();
            scene.getStylesheets().add(HelloApplication.class.getResource(
                    isDarkTheme ? "styles.css" : "styles-light.css").toExternalForm());
        } catch (Exception ignored) {}
    }

    // --- Affichage messages ---
    private void addMessageBubble(String sender, String content, String timestamp, boolean isOwn, boolean isGroup) {
        VBox container = new VBox(2);
        if (isGroup && !isOwn) {
            Label sl = new Label(sender);
            sl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + getAvatarColor(sender) + ";");
            container.getChildren().add(sl);
        }
        Label bubble = new Label(content);
        bubble.setWrapText(true);
        bubble.getStyleClass().add(isOwn ? "message-bubble-own" : "message-bubble-other");
        bubble.maxWidthProperty().bind(messagesContainer.widthProperty().multiply(0.65));
        String ts = timestamp != null ? timestamp : LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        Label tl = new Label(ts);
        tl.setStyle("-fx-font-size: 9; -fx-text-fill: #667781;");
        container.getChildren().addAll(bubble, tl);

        Region spacer = new Region();
        HBox hbox;
        if (isOwn) {
            container.setAlignment(Pos.CENTER_RIGHT);
            hbox = new HBox(spacer, container); HBox.setHgrow(spacer, Priority.ALWAYS);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            hbox = new HBox(container, spacer); HBox.setHgrow(spacer, Priority.ALWAYS);
        }
        hbox.setPadding(new Insets(2, 12, 2, 12));
        messagesContainer.getChildren().add(hbox);
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void addAndCache(String key, String sender, String content, String timestamp, boolean isOwn, boolean isGroup) {
        addMessageBubble(sender, content, timestamp, isOwn, isGroup);
        messageCache.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new CachedMsg(sender, content, timestamp, isOwn));
    }

    // --- Cellules ---
    private HBox buildContactCell(String username, ChatMessage.UserInfo info, int unread) {
        HBox cell = new HBox(10); cell.setAlignment(Pos.CENTER_LEFT); cell.setPadding(new Insets(10, 14, 10, 14));
        cell.getChildren().addAll(buildAvatar(username, 44, 22), buildUserInfo(username, info));
        HBox.setHgrow(cell.getChildren().get(1), Priority.ALWAYS);
        if (unread > 0) cell.getChildren().add(buildBadge(unread));
        return cell;
    }

    private HBox buildGroupCell(ChatMessage.GroupInfo group, int unread) {
        HBox cell = new HBox(10); cell.setAlignment(Pos.CENTER_LEFT); cell.setPadding(new Insets(10, 14, 10, 14));
        StackPane avatar = buildAvatarRaw("#8b5cf6", group.name.substring(0, 1).toUpperCase(), 44, 22);
        VBox info = new VBox(3);
        Label name = new Label(group.name);
        name.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e9edef;");
        Label sub = new Label(group.memberUsernames.size() + " membres" + (group.membersCanSend ? "" : "  \uD83D\uDD07"));
        sub.setStyle("-fx-font-size: 11; -fx-text-fill: #8696a0;");
        info.getChildren().addAll(name, sub);
        HBox.setHgrow(info, Priority.ALWAYS);
        cell.getChildren().addAll(avatar, info);
        if (unread > 0) cell.getChildren().add(buildBadge(unread));
        return cell;
    }

    private HBox buildPendingCell(ChatMessage.UserInfo user) {
        HBox cell = new HBox(10); cell.setAlignment(Pos.CENTER_LEFT); cell.setPadding(new Insets(12, 14, 12, 14));
        StackPane avatar = buildAvatarRaw(getAvatarColor(user.username), user.username.substring(0,1).toUpperCase(), 40, 20);
        VBox info = new VBox(3);
        Label name = new Label(user.fullName);
        name.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #e9edef;");
        Label sub = new Label("@" + user.username + "  " + buildRoleBadgeText(user.role));
        sub.setStyle("-fx-font-size: 11; -fx-text-fill: #8696a0;");
        info.getChildren().addAll(name, sub);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button ok = new Button("Accepter");
        ok.setStyle("-fx-background-color: #00a884; -fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 5 10; -fx-cursor: hand;");
        ok.setOnAction(e -> approveUser(user.username));

        Button no = new Button("Refuser");
        no.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 5 10; -fx-cursor: hand;");
        no.setOnAction(e -> rejectUser(user.username));

        cell.getChildren().addAll(avatar, info, ok, no);
        return cell;
    }

    private StackPane buildAvatar(String username, int size, int radius) {
        return buildAvatarRaw(getAvatarColor(username), username.substring(0,1).toUpperCase(), size, radius);
    }
    private StackPane buildAvatarRaw(String color, String initial, int size, int radius) {
        StackPane sp = new StackPane();
        sp.setMinSize(size, size); sp.setMaxSize(size, size);
        Region bg = new Region(); bg.setMinSize(size, size); bg.setMaxSize(size, size);
        bg.setStyle("-fx-background-color: " + color + "; -fx-background-radius: " + radius + ";");
        Label lbl = new Label(initial);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: " + (size/2.5) + "; -fx-font-weight: bold;");
        sp.getChildren().addAll(bg, lbl);
        return sp;
    }
    private VBox buildUserInfo(String username, ChatMessage.UserInfo info) {
        VBox box = new VBox(3);
        Label name = new Label(username);
        name.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e9edef;");
        HBox row = new HBox(6); row.setAlignment(Pos.CENTER_LEFT);
        if (info != null) {
            row.getChildren().add(buildRoleBadge(info.role));
            Region dot = new Region(); dot.setMinSize(8,8); dot.setMaxSize(8,8);
            dot.setStyle("-fx-background-color: " + ("ONLINE".equals(info.status) ? "#00a884" : "#667781") + "; -fx-background-radius: 4;");
            row.getChildren().add(dot);
        }
        box.getChildren().addAll(name, row);
        return box;
    }
    private Label buildBadge(int count) {
        Label b = new Label(count > 99 ? "99+" : String.valueOf(count));
        b.setMinSize(22,22); b.setMaxSize(22,22); b.setAlignment(Pos.CENTER);
        b.setStyle("-fx-background-color: #00a884; -fx-background-radius: 11; -fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold;");
        return b;
    }
    private Label buildRoleBadge(String role) {
        Label b = new Label();
        switch (role) {
            case "ORGANISATEUR" -> b.setStyle("-fx-background-color: #7c3aed; -fx-text-fill: white; -fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 2 6;");
            case "MEMBRE"       -> b.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 2 6;");
            default             -> b.setStyle("-fx-background-color: #065f46; -fx-text-fill: white; -fx-font-size: 9; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 2 6;");
        }
        b.setText(buildRoleBadgeText(role));
        return b;
    }
    private String buildRoleBadgeText(String role) {
        return switch (role) {
            case "ORGANISATEUR" -> "\u2605 Organisateur";
            case "MEMBRE"       -> "\u25C6 Membre";
            default             -> "\u25CF Benevole";
        };
    }

    // --- Filtres ---
    private void filterContacts(String query) {
        List<String> filtered = new ArrayList<>();
        for (ChatMessage.UserInfo u : allUsers) {
            if (u.username.equals(currentUsername)) continue;
            if (query == null || query.trim().isEmpty()
                    || u.username.toLowerCase().contains(query.toLowerCase())
                    || (u.fullName != null && u.fullName.toLowerCase().contains(query.toLowerCase())))
                filtered.add(u.username);
        }
        String prev = userListView.getSelectionModel().getSelectedItem();
        userListView.getItems().setAll(filtered);
        if (prev != null && filtered.contains(prev)) userListView.getSelectionModel().select(prev);
    }

    // --- Notifications ---
    private void playNotificationSound() { try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {} }
    private void flashTitle(String msg) {
        try {
            Stage s = (Stage) messageField.getScene().getWindow();
            String orig = s.getTitle(); s.setTitle(msg);
            PauseTransition p = new PauseTransition(Duration.seconds(3));
            p.setOnFinished(e -> s.setTitle(orig)); p.play();
        } catch (Exception ignored) {}
    }

    // --- Navigation vers login ---
    private void navigateToLogin() {
        try {
            Stage stage = (Stage) navSettingsBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 600);
            scene.getStylesheets().add(HelloApplication.class.getResource(
                    PREFS.getBoolean("darkTheme", true) ? "styles.css" : "styles-light.css").toExternalForm());
            stage.setTitle("Messagerie ISI - Connexion"); stage.setScene(scene);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- Utilitaires ---
    private ChatMessage.UserInfo findUserInfo(String username) {
        return allUsers.stream().filter(u -> u.username.equals(username)).findFirst().orElse(null);
    }
    private String getAvatarColor(String name) {
        String[] colors = {"#e53e3e","#dd6b20","#d69e2e","#38a169","#319795","#3182ce","#6b46c1","#b83280","#00a884","#2d3748"};
        return colors[Math.abs(name.hashCode()) % colors.length];
    }
    private String getRoleBadgeText(String role) {
        return switch (role) { case "ORGANISATEUR" -> "\u2605 Organisateur"; case "MEMBRE" -> "\u25C6 Membre"; default -> "\u25CF Benevole"; };
    }
    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION); a.setTitle("Info"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
