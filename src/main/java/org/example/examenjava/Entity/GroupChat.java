package org.example.examenjava.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "group_chats")
public class GroupChat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    // Bonus : si false, seuls les ORGANISATEURS peuvent envoyer des messages dans ce groupe
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean membersCanSend = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "group_members",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> members = new ArrayList<>();

    public GroupChat() {}

    public GroupChat(String name, User creator) {
        this.name = name;
        this.creator = creator;
        this.membersCanSend = true;
        this.dateCreation = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }

    public boolean isMembersCanSend() { return membersCanSend; }
    public void setMembersCanSend(boolean membersCanSend) { this.membersCanSend = membersCanSend; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public List<User> getMembers() { return members; }
    public void setMembers(List<User> members) { this.members = members; }
}
