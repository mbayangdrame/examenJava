package org.example.examenjava.network;

import java.io.Serializable;
import java.util.List;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Type {
        // Auth
        LOGIN, LOGIN_SUCCESS, LOGIN_FAILURE,
        REGISTER, REGISTER_SUCCESS, REGISTER_FAILURE,
        LOGOUT,

        // Messages 1:1
        SEND_MESSAGE, RECEIVE_MESSAGE,
        REQUEST_HISTORY, HISTORY_RESPONSE,

        // Users
        USER_LIST_UPDATE,
        READ_RECEIPT,

        // Groupes
        CREATE_GROUP, CREATE_GROUP_SUCCESS, CREATE_GROUP_FAILURE,
        ADD_TO_GROUP, REMOVE_FROM_GROUP,
        GROUP_LIST_UPDATE,
        SEND_GROUP_MESSAGE, RECEIVE_GROUP_MESSAGE,
        REQUEST_GROUP_HISTORY, GROUP_HISTORY_RESPONSE,
        TOGGLE_GROUP_SEND,

        // Approbation
        PENDING_USERS_REQUEST, PENDING_USERS_RESPONSE,
        APPROVE_USER, APPROVE_USER_SUCCESS,
        REJECT_USER, REJECT_USER_SUCCESS,

        ERROR,

        // Keepalive
        PING, PONG
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
    private Long groupId;
    private String groupName;
    private boolean membersCanSend;
    private List<UserInfo> users;
    private List<MessageInfo> messages;
    private List<GroupInfo> groups;

    public ChatMessage(Type type) {
        this.type = type;
    }

    // ---- Inner classes ----

    public static class UserInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        public String username;
        public String fullName;
        public String role;
        public String status;
        public boolean approved;

        public UserInfo(String username, String fullName, String role, String status, boolean approved) {
            this.username = username;
            this.fullName = fullName;
            this.role = role;
            this.status = status;
            this.approved = approved;
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

    public static class GroupInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        public Long id;
        public String name;
        public String creatorUsername;
        public boolean membersCanSend;
        public List<String> memberUsernames;

        public GroupInfo(Long id, String name, String creatorUsername, boolean membersCanSend, List<String> memberUsernames) {
            this.id = id;
            this.name = name;
            this.creatorUsername = creatorUsername;
            this.membersCanSend = membersCanSend;
            this.memberUsernames = memberUsernames;
        }
    }

    // ---- Getters / Setters ----

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

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean isMembersCanSend() { return membersCanSend; }
    public void setMembersCanSend(boolean membersCanSend) { this.membersCanSend = membersCanSend; }

    public List<UserInfo> getUsers() { return users; }
    public void setUsers(List<UserInfo> users) { this.users = users; }

    public List<MessageInfo> getMessages() { return messages; }
    public void setMessages(List<MessageInfo> messages) { this.messages = messages; }

    public List<GroupInfo> getGroups() { return groups; }
    public void setGroups(List<GroupInfo> groups) { this.groups = groups; }
}
