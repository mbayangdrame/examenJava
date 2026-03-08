-- ==========================================
-- Script d'initialisation de la base de données
-- Messagerie Interne
-- ==========================================

-- Créer la base de données (si elle n'existe pas)
CREATE DATABASE IF NOT EXISTS messagerie;

-- Utiliser la base de données
\c messagerie;

-- ==========================================
-- Table USERS
-- ==========================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    is_online BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- Table MESSAGES
-- ==========================================
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN DEFAULT false
);

-- ==========================================
-- Insertion de données de test
-- ==========================================

-- Insérer des utilisateurs (Note: Les mots de passe sont en clair pour la démo)
INSERT INTO users (username, password, email, full_name, is_online) VALUES
('admin', '123456', 'admin@association.fr', 'Administrateur', true),
('alice', 'alice123', 'alice@association.fr', 'Alice Johnson', true),
('bob', 'bob123', 'bob@association.fr', 'Bob Smith', true),
('charlie', 'charlie123', 'charlie@association.fr', 'Charlie Brown', false),
('diana', 'diana123', 'diana@association.fr', 'Diana Prince', true),
('eve', 'eve123', 'eve@association.fr', 'Eve Wilson', true),
('frank', 'frank123', 'frank@association.fr', 'Frank Miller', false)
ON CONFLICT (username) DO NOTHING;

-- Insérer des messages de démonstration
INSERT INTO messages (sender_id, recipient_id, content, is_read) VALUES
(2, 1, 'Bonjour Admin, comment ça va?', true),
(1, 2, 'Bien Alice, et toi?', true),
(2, 1, 'Super! On se voit ce soir?', false),
(3, 2, 'Salut Alice, tu as fini le projet?', true),
(2, 3, 'Presque, on en reparle demain?', true),
(4, 3, 'Coucou Charlie, comment vas-tu?', false),
(5, 1, 'Admin, j''ai besoin d''aide avec mon compte', false),
(1, 5, 'Bien sûr Diana, dis-moi ce qui ne va pas', false),
(6, 4, 'Eve à Charlie: T''as vu le dernier message?', true),
(3, 6, 'Non pas encore, merci de me prévenir!', true);

-- ==========================================
-- Créer des index pour les performances
-- ==========================================
CREATE INDEX idx_messages_sender ON messages(sender_id);
CREATE INDEX idx_messages_recipient ON messages(recipient_id);
CREATE INDEX idx_messages_timestamp ON messages(timestamp);
CREATE INDEX idx_users_username ON users(username);

-- ==========================================
-- Afficher les données insérées
-- ==========================================
SELECT 'Users' AS table_name, COUNT(*) AS count FROM users
UNION ALL
SELECT 'Messages', COUNT(*) FROM messages;

SELECT '✅ Base de données initialisée avec succès!' AS status;

-- ==========================================
-- Requêtes utiles pour tester
-- ==========================================

-- Récupérer tous les utilisateurs
-- SELECT * FROM users;

-- Récupérer les messages entre deux utilisateurs
-- SELECT * FROM messages
-- WHERE (sender_id = 1 AND recipient_id = 2)
--    OR (sender_id = 2 AND recipient_id = 1)
-- ORDER BY timestamp;

-- Récupérer les messages non lus d'un utilisateur
-- SELECT * FROM messages WHERE recipient_id = 1 AND is_read = false;

-- Compter les messages non lus par utilisateur
-- SELECT recipient_id, COUNT(*) as unread_count
-- FROM messages WHERE is_read = false
-- GROUP BY recipient_id;

-- Renommer une colonne (si besoin)
-- ALTER TABLE users RENAME COLUMN full_name TO fullName;
-- ALTER TABLE messages RENAME COLUMN is_read TO isRead;

