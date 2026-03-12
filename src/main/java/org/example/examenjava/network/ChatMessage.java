package org.example.examenjava.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        LOGIN, LOGIN_SUCCESS, LOGIN_FAILURE,
        REGISTER, REGISTER_SUCCESS, REGISTER_FAILURE,
        LOGOUT,
        SEND_MESSAGE, RECEIVE_MESSAGE,
        USER_LIST_UPDATE,
        REQUEST_HISTORY, HISTORY_RESPONSE,
        STATUS_UPDATE,
        ERROR
    }

    private Type type;
    private String sender;
    private String receiver;
    private String content;
    private String timestamp;
    private String role;
    private String status;
    private String email;
    private String fullName;
    private String password;
    private List<UserInfo> users;
    private List<MessageInfo> messages;

    public ChatMessage(Type type) {
        this.type = type;
    }

    // Serializable inner classes for transporting user/message data
    public static class UserInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        public String username;
        public String fullName;
        public String role;
        public String status;

        public UserInfo(String username, String fullName, String role, String status) {
            this.username = username;
            this.fullName = fullName;
            this.role = role;
            this.status = status;
        }
    }

    public static class MessageInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        public String senderUsername;
        public String receiverUsername;
        public String content;
        public String timestamp;
        public String statut;

        public MessageInfo(String senderUsername, String receiverUsername, String content, String timestamp, String statut) {
            this.senderUsername = senderUsername;
            this.receiverUsername = receiverUsername;
            this.content = content;
            this.timestamp = timestamp;
            this.statut = statut;
        }
    }

    // Getters and Setters
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public List<UserInfo> getUsers() { return users; }
    public void setUsers(List<UserInfo> users) { this.users = users; }

    public List<MessageInfo> getMessages() { return messages; }
    public void setMessages(List<MessageInfo> messages) { this.messages = messages; }
}
