package org.example.examenjava.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.example.examenjava.Entity.GroupChat;
import org.example.examenjava.Entity.GroupMessage;
import org.example.examenjava.Entity.Message;
import org.example.examenjava.Entity.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {
    private final EntityManagerFactory emf;
    private static Database instance;

    public static synchronized Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    private Database() {
        try {
            Map<String, String> overrides = new HashMap<>();
            String dbUrl = System.getenv("DB_URL");
            String dbUser = System.getenv("DB_USER");
            String dbPassword = System.getenv("DB_PASSWORD");
            if (dbUrl != null) overrides.put("jakarta.persistence.jdbc.url", dbUrl);
            if (dbUser != null) overrides.put("jakarta.persistence.jdbc.user", dbUser);
            if (dbPassword != null) overrides.put("jakarta.persistence.jdbc.password", dbPassword);

            emf = Persistence.createEntityManagerFactory("default", overrides);
        } catch (Exception e) {
            throw new RuntimeException("Erreur de connexion a la base de donnees: " + e.getMessage(), e);
        }
    }

    private EntityManager createEntityManager() {
        return emf.createEntityManager();
    }

    // =====================================================================
    // SEED : organisateur par defaut
    // =====================================================================

    /**
     * Cree l'organisateur par defaut saliou/sall s'il n'existe aucun user en base.
     */
    public void seedDefaultOrganisateur() {
        EntityManager em = createEntityManager();
        try {
            // Creer saliou seulement s'il n'existe pas encore
            long exists = em.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE u.username = 'saliou'", Long.class)
                    .getSingleResult();
            if (exists == 0) {
                User admin = new User();
                admin.setUsername("saliou");
                admin.setPassword(hashPassword("sall"));
                admin.setEmail("saliou@elan.asso");
                admin.setFullName("Saliou Admin");
                admin.setRole(User.Role.ORGANISATEUR);
                admin.setStatus(User.Status.OFFLINE);
                admin.setApproved(true);
                admin.setDateCreation(LocalDateTime.now());

                em.getTransaction().begin();
                em.persist(admin);
                em.getTransaction().commit();
                System.out.println("[SEED] Organisateur par defaut cree : saliou / sall");
            }
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur seed: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // =====================================================================
    // USER METHODS
    // =====================================================================

    public void saveUser(User user) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(user);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur sauvegarde utilisateur: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public User findUserById(Long id) {
        EntityManager em = createEntityManager();
        try {
            return em.find(User.class, id);
        } finally {
            em.close();
        }
    }

    public List<User> getAllUsers() {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u ORDER BY u.role, u.username", User.class).getResultList();
        } finally {
            em.close();
        }
    }

    /** Tous les users approuves (pour la liste de contacts) */
    public List<User> getApprovedUsers() {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.approved = true ORDER BY u.role, u.username", User.class).getResultList();
        } finally {
            em.close();
        }
    }

    /** Users en attente d'approbation */
    public List<User> getPendingUsers() {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.approved = false ORDER BY u.dateCreation ASC", User.class).getResultList();
        } finally {
            em.close();
        }
    }

    public User findUserByUsername(String username) {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        } finally {
            em.close();
        }
    }

    /** Recherche insensible a la casse — pour eviter les doublons maj/min */
    public User findUserByUsernameIgnoreCase(String username) {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username)", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        } finally {
            em.close();
        }
    }

    public void updateUser(User user) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(user);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur mise a jour utilisateur: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void deleteUser(User user) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            em.remove(em.merge(user));
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur suppression utilisateur: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // =====================================================================
    // MESSAGE METHODS (1:1)
    // =====================================================================

    public void saveMessage(Message message) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            User sender = em.find(User.class, message.getSender().getId());
            User receiver = em.find(User.class, message.getReceiver().getId());
            message.setSender(sender);
            message.setReceiver(receiver);
            em.persist(message);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur sauvegarde message: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public List<Message> getMessagesBetweenUsers(Long userId1, Long userId2) {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Message m WHERE " +
                    "(m.sender.id = :user1 AND m.receiver.id = :user2) OR " +
                    "(m.sender.id = :user2 AND m.receiver.id = :user1) " +
                    "ORDER BY m.dateEnvoi ASC", Message.class)
                    .setParameter("user1", userId1)
                    .setParameter("user2", userId2)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Message> getUnreadMessages(Long userId) {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM Message m WHERE m.receiver.id = :userId AND m.statut <> :statutLu ORDER BY m.dateEnvoi ASC",
                    Message.class)
                    .setParameter("userId", userId)
                    .setParameter("statutLu", Message.Statut.LU)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public void markMessageAsRead(Message message) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            Message managed = em.find(Message.class, message.getId());
            if (managed != null) managed.setStatut(Message.Statut.LU);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur marquage message: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // =====================================================================
    // GROUP METHODS
    // =====================================================================

    public GroupChat saveGroup(GroupChat group) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            // Re-attach creator
            User creator = em.find(User.class, group.getCreator().getId());
            group.setCreator(creator);
            // Re-attach members
            List<User> attached = new java.util.ArrayList<>();
            for (User m : group.getMembers()) {
                attached.add(em.find(User.class, m.getId()));
            }
            group.setMembers(attached);
            em.persist(group);
            em.getTransaction().commit();
            return group;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur sauvegarde groupe: " + e.getMessage());
            return null;
        } finally {
            em.close();
        }
    }

    public GroupChat findGroupById(Long id) {
        EntityManager em = createEntityManager();
        try {
            return em.find(GroupChat.class, id);
        } finally {
            em.close();
        }
    }

    public List<GroupChat> getAllGroups() {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery("SELECT g FROM GroupChat g ORDER BY g.name", GroupChat.class).getResultList();
        } finally {
            em.close();
        }
    }

    /** Groupes dont l'utilisateur est membre */
    public List<GroupChat> getGroupsForUser(Long userId) {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery(
                    "SELECT g FROM GroupChat g JOIN g.members m WHERE m.id = :userId ORDER BY g.name",
                    GroupChat.class)
                    .setParameter("userId", userId)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public void addUserToGroup(Long groupId, Long userId) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            GroupChat group = em.find(GroupChat.class, groupId);
            User user = em.find(User.class, userId);
            if (group != null && user != null && !group.getMembers().contains(user)) {
                group.getMembers().add(user);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur ajout membre groupe: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void removeUserFromGroup(Long groupId, Long userId) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            GroupChat group = em.find(GroupChat.class, groupId);
            if (group != null) {
                group.getMembers().removeIf(u -> u.getId().equals(userId));
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur retrait membre groupe: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void updateGroup(GroupChat group) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(group);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur mise a jour groupe: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void saveGroupMessage(GroupMessage msg) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            User sender = em.find(User.class, msg.getSender().getId());
            GroupChat group = em.find(GroupChat.class, msg.getGroup().getId());
            msg.setSender(sender);
            msg.setGroup(group);
            em.persist(msg);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur sauvegarde message groupe: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public List<GroupMessage> getGroupMessages(Long groupId) {
        EntityManager em = createEntityManager();
        try {
            return em.createQuery(
                    "SELECT m FROM GroupMessage m WHERE m.group.id = :groupId ORDER BY m.dateEnvoi ASC",
                    GroupMessage.class)
                    .setParameter("groupId", groupId)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // =====================================================================
    // UTILITAIRES
    // =====================================================================

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }

    public void close() {
        if (emf != null && emf.isOpen()) emf.close();
    }
}
