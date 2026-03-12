# Élan — Messagerie Interne d'Association

Application de messagerie temps réel pour associations, réalisée en Java 17 + JavaFX 17.
Projet académique ISI — Architecture client/serveur TCP avec gestion des rôles, groupes et accusés de lecture.

---

## Démarrage rapide

### Prérequis
- Java 17+
- PostgreSQL 16

### Lancer le serveur
```bash
# Variables d'environnement optionnelles (sinon valeurs de persistence.xml)
export DB_URL=jdbc:postgresql://localhost:5432/messagerie
export DB_USER=postgres
export DB_PASSWORD=yourpassword

java -jar elan-server.jar
```

### Lancer le client
```bash
java -jar elan-client.jar
```

**Compte par défaut :** `saliou` / `sall` (ORGANISATEUR)

---

## Documentation

| Fichier | Contenu |
|---------|---------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Vue d'ensemble, couches, diagrammes de flux |
| [PROTOCOL.md](./PROTOCOL.md) | Protocole réseau TCP, tous les types de messages |
| [STACK.md](./STACK.md) | Stack technique, dépendances, déploiement |

---

## Structure du projet

```
src/main/java/org/example/examenjava/
├── Entity/                 Entités JPA (User, Message, GroupChat, GroupMessage)
├── Repository/             Accès base de données (singleton Database)
├── network/                Protocole réseau (ChatMessage, ChatClient)
├── server/                 Serveur TCP (ChatServer, ClientHandler)
├── HelloApplication.java   Point d'entrée JavaFX
├── Launcher.java           Entry point fat JAR
├── LoginController.java    Contrôleur login
├── RegisterController.java Contrôleur inscription
└── MessagingController.java Contrôleur principal UI

src/main/resources/
├── META-INF/persistence.xml   Configuration Hibernate/JPA
└── org/example/examenjava/
    ├── login-view.fxml         Interface de connexion
    ├── register-view.fxml      Interface d'inscription
    ├── messaging-view.fxml     Interface de messagerie principale
    ├── styles.css              Thème sombre (WhatsApp dark)
    └── styles-light.css        Thème clair
```

---

## Rôles

| Rôle | Contacts visibles | Chat 1:1 | Groupes | Administration |
|------|-------------------|----------|---------|---------------|
| **ORGANISATEUR** | Tous les membres approuvés | Oui | Créer, gérer | Approuver inscriptions |
| **MEMBRE** | ORGANISATEURS + MEMBRES | Oui | Participer | — |
| **BENEVOLE** | — | Non | Participer seulement | — |
