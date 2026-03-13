# Architecture — Élan

## Vue d'ensemble

Élan est une application client/serveur TCP. Le serveur est un processus Java indépendant qui gère la persistance et le routage des messages. Chaque client JavaFX ouvre une connexion socket persistante au démarrage.

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT (JavaFX)                            │
│                                                                     │
│   LoginController ──► MessagingController ◄── RegisterController   │
│                               │                                     │
│                          ChatClient                                 │
│                    (callbacks + envoi)                              │
└──────────────────────────┬────────────────────────────────────────-─┘
                           │  TCP Socket  :5555
                           │  ObjectOutputStream / ObjectInputStream
                           │  (ChatMessage sérialisé)
┌──────────────────────────▼──────────────────────────────────────────┐
│                          SERVEUR                                    │
│                                                                     │
│   ChatServer                                                        │
│   (ServerSocket, connectedClients Map)                              │
│          │                                                          │
│          ├─► ClientHandler thread #1  (alice)                       │
│          ├─► ClientHandler thread #2  (bob)                         │
│          └─► ClientHandler thread #N  (...)                         │
│                        │                                            │
│                   Database (singleton)                              │
│                   EntityManagerFactory                              │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  JDBC / Hibernate ORM
┌──────────────────────────▼──────────────────────────────────────────┐
│                       PostgreSQL                                    │
│   users  │  messages  │  group_chats  │  group_members  │  group_messages │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Couches applicatives

```
┌───────────────────────────────────────┐
│  PRÉSENTATION (JavaFX + FXML + CSS)   │
│  LoginController                      │
│  RegisterController                   │
│  MessagingController                  │
├───────────────────────────────────────┤
│  RÉSEAU CLIENT                        │
│  ChatClient  ──  ChatMessage          │
├───────────────────────────────────────┤
│  RÉSEAU SERVEUR                       │
│  ChatServer  ──  ClientHandler        │
├───────────────────────────────────────┤
│  PERSISTANCE                          │
│  Database (Repository)                │
│  Hibernate ORM + Jakarta Persistence  │
├───────────────────────────────────────┤
│  DOMAINE (Entités JPA)                │
│  User / Message / GroupChat / GroupMessage │
└───────────────────────────────────────┘
```

---

## Schéma base de données

```
┌──────────────────┐        ┌──────────────────────┐
│      users       │        │       messages        │
├──────────────────┤        ├──────────────────────┤
│ id          PK   │◄───┐   │ id             PK    │
│ username         │    ├───│ sender_id      FK    │
│ password (SHA256)│    └───│ receiver_id    FK    │
│ email            │        │ contenu              │
│ full_name        │        │ date_envoi           │
│ role  (enum)     │        │ statut (ENVOYE/RECU/LU) │
│ status (enum)    │        └──────────────────────┘
│ approved (bool)  │
│ date_creation    │        ┌──────────────────────┐
└──────────────────┘        │     group_chats       │
          │                 ├──────────────────────┤
          │                 │ id             PK    │
          │           ┌─────│ creator_id     FK    │
          │           │     │ name                 │
          │           │     │ members_can_send (bool) │
          │           │     │ date_creation        │
          │           │     └──────────────────────┘
          │           │              │
          │     ┌─────┘              │  ManyToMany
          │     ▼                    ▼
          │  ┌─────────────────────────────┐
          │  │       group_members         │
          │  ├─────────────────────────────┤
          │  │ group_id    FK              │
          │  │ user_id     FK              │
          │  └─────────────────────────────┘
          │
          │           ┌──────────────────────┐
          │           │    group_messages     │
          │           ├──────────────────────┤
          └──────────►│ sender_id      FK    │
                      │ group_id       FK    │
                      │ contenu              │
                      │ date_envoi           │
                      └──────────────────────┘
```

---

## Flux de connexion

```
CLIENT                              SERVEUR
  │                                    │
  │──── REGISTER(username, pwd, role) ─►│
  │◄─── REGISTER_SUCCESS ──────────────│
  │        (approved = false)          │  ──► notifie ORGANISATEURS connectés
  │                                    │
  │──── LOGIN(username, password) ─────►│
  │                                    │  1. recherche username (insensible à la casse)
  │                                    │  2. vérifie approved = true
  │                                    │  3. vérifie SHA-256(pwd) == stocké
  │                                    │  4. si session fantôme → forceDisconnect() ancienne
  │◄─── LOGIN_SUCCESS(role, fullName) ──│
  │◄─── RECEIVE_MESSAGE* (unread msgs) ─│  messages non-lus livrés
  │◄─── USER_LIST_UPDATE ───────────────│  liste contacts filtrée par rôle
  │◄─── GROUP_LIST_UPDATE ──────────────│  groupes dont user est membre
  │                                    │
  │  [startListening() thread daemon]  │
  │  [ping scheduler 30s]             │
```

---

## Flux d'un message 1:1

```
ALICE (sender)                SERVEUR                BOB (receiver)
    │                            │                        │
    │── SEND_MESSAGE(bob, txt) ──►│                        │
    │                            │  1. crée Message en DB  │
    │                            │  2. statut = RECU       │  (si bob online)
    │                            │     ou ENVOYE           │  (si bob offline)
    │                            │──── RECEIVE_MESSAGE ────►│
    │◄── RECEIVE_MESSAGE(✓✓) ────│     (livraison directe)  │
    │    (confirm avec statut)   │                        │
    │                            │                        │
    │   [plus tard, bob ouvre    │                        │
    │    la conversation]        │                        │
    │                            │◄── REQUEST_HISTORY ────│
    │                            │  marque msgs en LU      │
    │◄── READ_RECEIPT(bob) ──────│──── HISTORY_RESPONSE ──►│
    │  (update ✓✓ → bleu)        │                        │
```

---

## Flux d'un message de groupe

```
ALICE (organisateur)           SERVEUR          BOB        CAROL
    │                             │               │           │
    │── SEND_GROUP_MESSAGE(g1) ──►│               │           │
    │                             │  vérifie:      │           │
    │                             │  - alice est membre       │
    │                             │  - membersCanSend=true     │
    │                             │  crée GroupMessage en DB   │
    │◄── RECEIVE_GROUP_MESSAGE ───│──► (bob online)           │
    │    (même message renvoyé    │               ✓           │
    │     à l'envoyeur aussi)     │──────────────────────────►│
    │                             │                        (carol online) ✓
```

---

## Filtrage des contacts par rôle

```
broadcastUserList() côté serveur :

Pour chaque client connecté :
  si destinataire == ORGANISATEUR  →  envoie TOUS les utilisateurs approuvés
  si destinataire == MEMBRE        →  envoie MEMBRES + ORGANISATEURS seulement
  si destinataire == BENEVOLE      →  envoie liste VIDE (pas de contacts 1:1)
```

---

## Cache client (session)

```
MessagingController
│
├── messageCache : Map<String, List<CachedMsg>>
│   ├── "bob"        → [CachedMsg, CachedMsg, ...]   (1:1 avec bob)
│   ├── "alice"      → [CachedMsg, ...]               (1:1 avec alice)
│   └── "group:42"   → [CachedMsg, ...]               (groupe id=42)
│
├── unreadCounts : Map<String, Integer>
│   ├── "bob"        → 3    (3 msgs non-lus de bob)
│   └── "group:42"   → 1
│
├── sentCheckmarks : Map<String, List<Label>>
│   └── "bob"        → [Label "✓", Label "✓✓", ...]
│                       (refs vers les labels affichés dans la UI)
│                       mis à jour en bleu sur READ_RECEIPT
│
└── lastContactActivity : Map<String, Long>
    └── "bob"        → 1741823145000   (System.currentTimeMillis() dernier msg)
                        utilisé pour trier la liste contacts (plus récent en haut)
```

**Règle :** Premier clic sur un contact → requête serveur + mise en cache.
Clics suivants → lecture depuis cache (zéro requête réseau).

**Tri des contacts :** La liste est triée par `lastContactActivity` décroissant.
Mis à jour à chaque message envoyé/reçu et à chaque chargement d'historique.

---

## Modèle de threads

```
Thread principal (JavaFX Application Thread)
├── Toutes les manipulations UI
├── Tous les callbacks (via Platform.runLater)
└── Login/Register (sendXxxAndWait → bloquant, mais avant startListening)

Thread daemon "ChatClient-listener"
├── Boucle infinie : in.readObject()
├── Platform.runLater( handleServerMessage(msg) )
└── Si IOException → callback onDisconnected

Thread daemon "ping-scheduler"  (ScheduledExecutorService, 1 thread)
├── Toutes les 30 secondes : envoie ChatMessage(PING)
├── synchronized(out) pour éviter collision avec le thread listener
└── Maintient la connexion vivante à travers les NAT/firewalls

Thread par client (serveur)
├── ClientHandler.run() boucle infinie
├── sendMessage() est synchronized
└── PING reçu → répond PONG immédiatement (sans log)
```

### Sécurité du stream `out` côté client

Le `ObjectOutputStream out` est partagé entre :
- Le thread JavaFX (envoi de messages utilisateur)
- Le thread ping-scheduler (envoi des PING)

Tous les accès passent par `synchronized(out)` via la méthode privée `send(ChatMessage)`.
