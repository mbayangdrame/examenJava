# Protocole Réseau — Élan

## Transport

- **Protocole :** TCP, port **5555**
- **Sérialisation :** Java Object Serialization (`ObjectOutputStream` / `ObjectInputStream`)
- **Classe échangée :** `ChatMessage` (implémente `Serializable`, `serialVersionUID = 2L`)
- **Connexion :** Persistante (une socket par client, ouverte à la connexion, fermée à la déconnexion)

---

## Structure de ChatMessage

```java
class ChatMessage implements Serializable {
  Type   type          // Obligatoire — détermine l'action
  String sender        // Username expéditeur
  String receiver      // Username destinataire
  String content       // Corps du message
  String timestamp     // "HH:mm"
  String role          // Rôle (auth / registre)
  String status        // Statut message (ENVOYE/RECU/LU)
  String email         // Email (registre)
  String fullName      // Nom complet
  String password      // Mot de passe (auth uniquement)
  Long   groupId       // ID du groupe
  String groupName     // Nom du groupe
  boolean membersCanSend // Permission envoi groupe

  List<UserInfo>    users    // Listes d'utilisateurs
  List<MessageInfo> messages // Historiques
  List<GroupInfo>   groups   // Listes de groupes
}
```

### Classes internes sérialisables

```java
UserInfo    { username, fullName, role, status, approved }
MessageInfo { senderUsername, receiverUsername, content, timestamp, statut }
GroupInfo   { id, name, creatorUsername, membersCanSend, memberUsernames[] }
```

---

## Tous les types de messages

### Authentification

| Type | Sens | Champs utilisés | Description |
|------|------|-----------------|-------------|
| `LOGIN` | C→S | sender, password | Tentative de connexion |
| `LOGIN_SUCCESS` | S→C | sender, fullName, role | Connexion réussie |
| `LOGIN_FAILURE` | S→C | content | Message d'erreur |
| `REGISTER` | C→S | sender, password, email, fullName, role | Inscription |
| `REGISTER_SUCCESS` | S→C | content | Inscription soumise (en attente) |
| `REGISTER_FAILURE` | S→C | content | Erreur inscription |
| `LOGOUT` | C→S | — | Déconnexion propre |

### Messages 1:1

| Type | Sens | Champs utilisés | Description |
|------|------|-----------------|-------------|
| `SEND_MESSAGE` | C→S | receiver, content | Envoyer un message |
| `RECEIVE_MESSAGE` | S→C | sender, receiver, content, timestamp, status | Livraison message (au destinataire ET à l'expéditeur comme confirm) |
| `REQUEST_HISTORY` | C→S | receiver | Demander l'historique avec un contact |
| `HISTORY_RESPONSE` | S→C | receiver, messages[] | Historique complet (marque les non-lus en LU) |
| `READ_RECEIPT` | S→C | sender (= celui qui a lu) | Accusé de lecture — met ✓✓ bleu |

### Utilisateurs

| Type | Sens | Champs utilisés | Description |
|------|------|-----------------|-------------|
| `USER_LIST_UPDATE` | S→C | users[] | Liste contacts (filtrée par rôle du destinataire) |

### Groupes

| Type | Sens | Champs utilisés | Description |
|------|------|-----------------|-------------|
| `CREATE_GROUP` | C→S | groupName | Créer un groupe (ORGANISATEUR only) |
| `CREATE_GROUP_SUCCESS` | S→C | content | Confirmation création |
| `CREATE_GROUP_FAILURE` | S→C | content | Erreur création |
| `ADD_TO_GROUP` | C→S | groupId, receiver | Ajouter un membre (ORGANISATEUR only) |
| `REMOVE_FROM_GROUP` | C→S | groupId, receiver | Retirer un membre (ORGANISATEUR only) |
| `GROUP_LIST_UPDATE` | S→C | groups[] | Liste des groupes de l'utilisateur |
| `SEND_GROUP_MESSAGE` | C→S | groupId, content | Envoyer dans un groupe |
| `RECEIVE_GROUP_MESSAGE` | S→C | sender, groupId, groupName, content, timestamp | Réception message groupe |
| `REQUEST_GROUP_HISTORY` | C→S | groupId | Demander l'historique d'un groupe |
| `GROUP_HISTORY_RESPONSE` | S→C | groupId, groupName, messages[], membersCanSend | Historique groupe |
| `TOGGLE_GROUP_SEND` | C→S | groupId, membersCanSend | Activer/Désactiver l'envoi des membres |

### Approbation (ORGANISATEUR)

| Type | Sens | Champs utilisés | Description |
|------|------|-----------------|-------------|
| `PENDING_USERS_REQUEST` | C→S | — | Demander la liste des inscriptions en attente |
| `PENDING_USERS_RESPONSE` | S→C | users[] | Liste des utilisateurs approved=false |
| `APPROVE_USER` | C→S | receiver | Approuver une inscription |
| `APPROVE_USER_SUCCESS` | S→C | content | Confirmation approbation |
| `REJECT_USER` | C→S | receiver | Rejeter/supprimer une inscription |
| `REJECT_USER_SUCCESS` | S→C | content | Confirmation rejet |

### Erreur

| Type | Sens | Champs utilisés | Description |
|------|------|-----------------|-------------|
| `ERROR` | S→C | content | Erreur générique serveur |

---

## Diagrammes de séquence

### Connexion complète

```
CLIENT                                    SERVEUR
  │                                          │
  │──── LOGIN(sender="alice", pwd="***") ───►│
  │                                          │
  │                              [vérifie DB]│
  │                              [SHA-256 hash]
  │                              [approved?] │
  │                                          │
  │◄─── LOGIN_SUCCESS(role, fullName) ───────│
  │◄─── RECEIVE_MESSAGE(msg1) ───────────────│  (messages non-lus livrés)
  │◄─── RECEIVE_MESSAGE(msg2) ───────────────│
  │◄─── USER_LIST_UPDATE(users[]) ───────────│  (filtrée selon rôle alice)
  │◄─── GROUP_LIST_UPDATE(groups[]) ─────────│
```

### Accusés de lecture (Read Receipts)

```
ALICE                    SERVEUR                    BOB
  │                         │                        │
  │── SEND_MESSAGE(bob) ───►│                        │
  │                         │── RECEIVE_MESSAGE ─────►│  (bob online)
  │◄── RECEIVE_MESSAGE ─────│    statut = RECU        │
  │    (status=RECU)        │                        │
  │    → affiche ✓✓ gris    │                        │
  │                         │                        │
  │                         │◄── REQUEST_HISTORY(alice)│  (bob ouvre le chat)
  │                         │    [marque msgs LU]     │
  │◄── READ_RECEIPT(bob) ───│──── HISTORY_RESPONSE ──►│
  │    (sender=bob)         │                        │
  │    → met ✓✓ bleu        │                        │
```

### Création et usage d'un groupe

```
ALICE (orga)              SERVEUR              BOB         CAROL
    │                        │                   │            │
    │── CREATE_GROUP("ISI") ─►│                   │            │
    │◄── CREATE_GROUP_SUCCESS │                   │            │
    │◄── GROUP_LIST_UPDATE ───│──────────────────►│            │
    │                        │                   │            │
    │── ADD_TO_GROUP(id,bob) ─►│                   │            │
    │◄── GROUP_LIST_UPDATE ───│──────────────────►│            │
    │                        │                   │            │
    │── ADD_TO_GROUP(id,carol)►│                   │            │
    │◄── GROUP_LIST_UPDATE ───│─────────────────────────────►  │
    │                        │                   │            │
    │── SEND_GROUP_MESSAGE ──►│                   │            │
    │◄── RECEIVE_GROUP_MESSAGE│──────────────────►│            │
    │                        │─────────────────────────────►  │
```

### Inscription et approbation

```
NOUVEAU USER              SERVEUR              ALICE (orga)
    │                        │                     │
    │── REGISTER(role=MEMBRE)►│                     │
    │◄── REGISTER_SUCCESS ───│                     │
    │    (en attente)        │── PENDING_USERS_RESPONSE ──►│
    │                        │   (pousse la MAJ aux orgas) │
    │                        │                     │
    │                        │◄── APPROVE_USER ────│
    │                        │  [user.approved=true]│
    │◄── (peut maintenant se connecter)            │
    │                        │── APPROVE_USER_SUCCESS ─────►│
    │                        │── USER_LIST_UPDATE ──────────►│
```

---

## Sécurité

### Hachage des mots de passe

Aucun mot de passe n'est stocké en clair. À l'inscription et à la connexion :

```
password  ──► SHA-256 ──► 64 hex chars ──► stocké/comparé en base
```

Implémentation identique côté client (envoyé haché) et côté serveur (stocké haché).

> Note : dans une version production, un sel par utilisateur (bcrypt/argon2) serait préférable.

### Contrôle d'accès côté serveur

Chaque `ClientHandler` vérifie le rôle avant toute action sensible :

```java
// Exemple : isOrganisateur()
User user = db.findUserByUsername(username);
return user != null && user.getRole() == User.Role.ORGANISATEUR;
```

Les actions `CREATE_GROUP`, `ADD_TO_GROUP`, `APPROVE_USER`, `REJECT_USER`, `TOGGLE_GROUP_SEND` échouent avec `ERROR` si le client n'est pas ORGANISATEUR.

### Double vérification des permissions de groupe

```
SEND_GROUP_MESSAGE
  1. user est bien membre du groupe (vérifié en DB)
  2. si membersCanSend == false ET user != ORGANISATEUR → ERROR
```
