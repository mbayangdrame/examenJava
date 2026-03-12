# Technologies Utilisees - Messagerie Interne

## 1. Java 17

**Role** : Langage principal du projet.

**Installation** :
```bash
# macOS
brew install openjdk@17

# Linux (Ubuntu/Debian)
sudo apt install openjdk-17-jdk

# Verifier
java -version
```

**Utilisation dans le projet** : Toutes les classes (entites, controleurs, serveur, client) sont ecrites en Java 17. On utilise les fonctionnalites modernes comme les `switch expressions`, les `records`, et les `text blocks`.

---

## 2. Maven

**Role** : Gestionnaire de build et de dependances.

**Fichier** : `pom.xml`

Le projet inclut un **Maven Wrapper** (`mvnw`) donc aucune installation n'est necessaire.

**Commandes principales** :
```bash
./mvnw compile              # Compiler le projet
./mvnw clean javafx:run     # Lancer le client JavaFX
./mvnw compile exec:java@server  # Lancer le serveur
```

Toutes les dependances sont declarees dans `pom.xml` et telechargees automatiquement par Maven.

---

## 3. JavaFX 17

**Role** : Interface graphique (GUI).

**Dependances Maven** :
```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>17.0.14</version>
</dependency>
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-fxml</artifactId>
    <version>17.0.14</version>
</dependency>
```

**Utilisation** :
- **FXML** : Les vues sont definies en XML (`login-view.fxml`, `register-view.fxml`, `messaging-view.fxml`)
- **CSS** : Le style est centralise dans `styles.css`
- **Controllers** : Chaque vue a un controleur Java (`LoginController`, `RegisterController`, `MessagingController`)
- **Pattern MVC** : Vue (FXML) + Controleur (Java) + Modele (Entity)

**Structure** :
```
resources/org/example/examenjava/
├── login-view.fxml         → Ecran de connexion
├── register-view.fxml      → Ecran d'inscription
├── messaging-view.fxml     → Ecran principal de chat
└── styles.css              → Styles CSS globaux
```

---

## 4. Sockets Java (ServerSocket / Socket)

**Role** : Communication client-serveur en temps reel.

**Aucune dependance externe** - fait partie de `java.net` (standard Java).

**Architecture** :

```
┌─────────────┐         Socket (TCP)         ┌─────────────┐
│  Client 1   │◄───────────────────────────►│             │
│  (JavaFX)   │   ObjectOutputStream/        │   Serveur   │
└─────────────┘   ObjectInputStream          │  (port 5555)│
                                              │             │
┌─────────────┐         Socket (TCP)         │  1 thread   │
│  Client 2   │◄───────────────────────────►│  par client │
│  (JavaFX)   │                              │             │
└─────────────┘                              └──────┬──────┘
                                                     │
                                                     ▼
                                              ┌─────────────┐
                                              │ PostgreSQL   │
                                              │ (Hibernate)  │
                                              └─────────────┘
```

**Fichiers concernes** :

| Fichier | Role |
|---|---|
| `server/ChatServer.java` | ServerSocket sur port 5555, accepte les connexions |
| `server/ClientHandler.java` | Thread dedie par client connecte |
| `network/ChatClient.java` | Cote client, se connecte au serveur |
| `network/ChatMessage.java` | Objet Serializable echange entre client et serveur |

**Protocole de communication** :

Les echanges se font via des objets `ChatMessage` serialises avec `ObjectOutputStream` / `ObjectInputStream`.

Types de messages :
```
LOGIN           → Client envoie username + password
LOGIN_SUCCESS   ← Serveur confirme la connexion
LOGIN_FAILURE   ← Serveur refuse (mauvais mdp, deja connecte...)
REGISTER        → Client envoie les infos d'inscription
SEND_MESSAGE    → Client envoie un message a un destinataire
RECEIVE_MESSAGE ← Serveur delivre un message au client
USER_LIST_UPDATE← Serveur diffuse la liste des utilisateurs a tous
REQUEST_HISTORY → Client demande l'historique avec un contact
HISTORY_RESPONSE← Serveur renvoie l'historique
LOGOUT          → Client signale sa deconnexion
```

**Flux d'un message** :
1. Client A ecrit un message et clique "Envoyer"
2. `ChatClient` envoie un `ChatMessage(SEND_MESSAGE)` au serveur via Socket
3. `ClientHandler` du serveur recoit le message
4. Le serveur sauvegarde en base via Hibernate
5. Si le destinataire (Client B) est connecte, le serveur lui transmet le message via son `ClientHandler`
6. Si Client B est hors ligne, le message est stocke et livre a sa prochaine connexion

---

## 5. Hibernate / JPA

**Role** : ORM (Object-Relational Mapping) - fait le lien entre les objets Java et les tables PostgreSQL.

**Dependances Maven** :
```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>6.4.0.Final</version>
</dependency>
<dependency>
    <groupId>jakarta.persistence</groupId>
    <artifactId>jakarta.persistence-api</artifactId>
    <version>3.1.0</version>
</dependency>
```

**Configuration** : `src/main/resources/META-INF/persistence.xml`
```xml
<persistence-unit name="default">
    <class>org.example.examenjava.Entity.User</class>
    <class>org.example.examenjava.Entity.Message</class>
    <properties>
        <property name="jakarta.persistence.jdbc.url"
                  value="jdbc:postgresql://localhost:5432/messagerie" />
        <property name="hibernate.hbm2ddl.auto" value="update" />
    </properties>
</persistence-unit>
```

`hibernate.hbm2ddl.auto = update` cree et met a jour les tables automatiquement au demarrage.

**Utilisation** :

Les entites Java sont annotees avec JPA :
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    private Role role;  // ORGANISATEUR, MEMBRE, BENEVOLE
}
```

Les operations CRUD se font via `EntityManager` dans `Database.java` :
```java
em.persist(user);           // INSERT
em.find(User.class, id);    // SELECT by ID
em.merge(user);             // UPDATE
em.remove(user);            // DELETE
em.createQuery("...");      // JPQL queries
```

---

## 6. PostgreSQL

**Role** : Base de donnees relationnelle.

**Installation** :
```bash
# macOS
brew install postgresql@15
brew services start postgresql@15

# Linux (Ubuntu/Debian)
sudo apt install postgresql
sudo systemctl start postgresql
```

**Creation de la base** :
```bash
psql -U postgres
CREATE DATABASE messagerie;
\q
```

Les tables sont creees automatiquement par Hibernate au premier lancement du serveur.

**Tables generees** :

```sql
-- Table users
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR UNIQUE NOT NULL,
    password    VARCHAR NOT NULL,
    email       VARCHAR NOT NULL,
    full_name   VARCHAR NOT NULL,
    is_online   BOOLEAN DEFAULT true,
    role        VARCHAR NOT NULL,    -- ORGANISATEUR, MEMBRE, BENEVOLE
    status      VARCHAR NOT NULL,    -- ONLINE, OFFLINE
    date_creation TIMESTAMP NOT NULL
);

-- Table messages
CREATE TABLE messages (
    id          BIGSERIAL PRIMARY KEY,
    sender_id   BIGINT REFERENCES users(id),
    receiver_id BIGINT REFERENCES users(id),
    contenu     VARCHAR(1000) NOT NULL,
    date_envoi  TIMESTAMP NOT NULL,
    statut      VARCHAR NOT NULL     -- ENVOYE, RECU, LU
);
```

---

## 7. Communication entre les technologies

```
┌──────────────────────────────────────────────────────────┐
│                     CLIENT (JavaFX)                      │
│                                                          │
│  ┌────────┐    ┌─────────────┐    ┌──────────────┐      │
│  │  FXML  │───►│ Controllers │───►│  ChatClient   │     │
│  │  +CSS  │    │  (JavaFX)   │    │  (Socket)     │     │
│  └────────┘    └─────────────┘    └──────┬───────┘      │
└──────────────────────────────────────────┼───────────────┘
                                           │ TCP Socket
                                           │ (ChatMessage)
                                           ▼
┌──────────────────────────────────────────────────────────┐
│                     SERVEUR                              │
│                                                          │
│  ┌────────────┐    ┌───────────────┐    ┌──────────┐    │
│  │ ChatServer │───►│ ClientHandler │───►│ Database │    │
│  │ (Socket)   │    │  (1 thread/   │    │ (JPA)    │    │
│  │            │    │   client)     │    └────┬─────┘    │
│  └────────────┘    └───────────────┘         │          │
└──────────────────────────────────────────────┼──────────┘
                                               │ JDBC
                                               ▼
                                        ┌──────────────┐
                                        │  PostgreSQL   │
                                        │  (messagerie) │
                                        └──────────────┘
```

**Resume du flux** :
1. L'utilisateur interagit avec l'**interface JavaFX** (FXML + CSS)
2. Le **Controller** traite l'action et appelle le **ChatClient**
3. Le ChatClient envoie un `ChatMessage` au serveur via **Socket TCP**
4. Le **ChatServer** recoit le message dans un **ClientHandler** (thread dedie)
5. Le ClientHandler utilise **Database.java** (Hibernate/JPA) pour lire/ecrire en base
6. Hibernate traduit les operations Java en requetes **SQL** vers **PostgreSQL**
7. La reponse remonte dans le sens inverse jusqu'a l'interface

---

## 8. Lancement du projet

**Prerequis** : Java 17+, PostgreSQL avec une base `messagerie` creee.

```bash
# Terminal 1 : Demarrer le serveur
./mvnw compile exec:java@server

# Terminal 2 : Demarrer un client
./mvnw clean javafx:run

# Terminal 3 : Demarrer un deuxieme client (pour tester le chat)
./mvnw javafx:run
```
