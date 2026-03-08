package org.example.examenjava.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.example.examenjava.Entity.User;
import org.example.examenjava.Entity.Message;
import java.util.List;

public class Database {
    private EntityManagerFactory emf;
    private EntityManager em;

    public Database() {
        try {
            emf = Persistence.createEntityManagerFactory("default");
            em = emf.createEntityManager();
        } catch (Exception e) {
            System.err.println("Erreur de connexion à la base de données: " + e.getMessage());
        }
    }

    // Méthodes pour User
    public void saveUser(User user) {
        try {
            em.getTransaction().begin();
            em.persist(user);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.err.println("Erreur lors de la sauvegarde de l'utilisateur: " + e.getMessage());
        }
    }

    public User findUserById(Long id) {
        return em.find(User.class, id);
    }

    public List<User> getAllUsers() {
        return em.createQuery("SELECT u FROM User u", User.class).getResultList();
    }

    public User findUserByUsername(String username) {
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public void updateUser(User user) {
        try {
            em.getTransaction().begin();
            em.merge(user);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.err.println("Erreur lors de la mise à jour de l'utilisateur: " + e.getMessage());
        }
    }

    public void deleteUser(User user) {
        try {
            em.getTransaction().begin();
            em.remove(em.merge(user));
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.err.println("Erreur lors de la suppression de l'utilisateur: " + e.getMessage());
        }
    }

    // Méthodes pour Message
    public void saveMessage(Message message) {
        try {
            em.getTransaction().begin();
            em.persist(message);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.err.println("Erreur lors de la sauvegarde du message: " + e.getMessage());
        }
    }

    public Message findMessageById(Long id) {
        return em.find(Message.class, id);
    }

    public List<Message> getMessagesBetweenUsers(Long userId1, Long userId2) {
        return em.createQuery(
                "SELECT m FROM Message m WHERE " +
                "(m.sender.id = :user1 AND m.recipient.id = :user2) OR " +
                "(m.sender.id = :user2 AND m.recipient.id = :user1) " +
                "ORDER BY m.timestamp ASC",
                Message.class)
                .setParameter("user1", userId1)
                .setParameter("user2", userId2)
                .getResultList();
    }

    public List<Message> getUnreadMessages(Long userId) {
        return em.createQuery(
                "SELECT m FROM Message m WHERE m.recipient.id = :userId AND m.isRead = false",
                Message.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public void markMessageAsRead(Message message) {
        try {
            em.getTransaction().begin();
            message.setIsRead(Boolean.TRUE);
            em.merge(message);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.err.println("Erreur lors du marquage du message: " + e.getMessage());
        }
    }

    public void deleteMessage(Message message) {
        try {
            em.getTransaction().begin();
            em.remove(em.merge(message));
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.err.println("Erreur lors de la suppression du message: " + e.getMessage());
        }
    }

    // Méthode de fermeture
    public void close() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
