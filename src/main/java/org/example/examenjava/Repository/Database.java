package org.example.examenjava.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.example.examenjava.Entity.User;
import org.example.examenjava.Entity.Message;

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
            // Variables d'environnement pour surcharger la config (utile pour deploiement VPS)
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

    // --- Méthodes User ---

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
            return em.createQuery("SELECT u FROM User u", User.class).getResultList();
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

    // --- Méthodes Message ---

    public void saveMessage(Message message) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            // Re-attach les entités User au contexte de persistance
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

    public Message findMessageById(Long id) {
        EntityManager em = createEntityManager();
        try {
            return em.find(Message.class, id);
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
                            "ORDER BY m.dateEnvoi ASC",
                    Message.class)
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
            if (managed != null) {
                managed.setStatut(Message.Statut.LU);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur marquage message: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void deleteMessage(Message message) {
        EntityManager em = createEntityManager();
        try {
            em.getTransaction().begin();
            em.remove(em.merge(message));
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            System.err.println("Erreur suppression message: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
