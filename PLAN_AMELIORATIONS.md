# Plan d'ameliorations - Messagerie ISI

## Etat actuel

Le projet fonctionne avec : authentification, messagerie 1:1 temps reel, statut en ligne, notifications sonores/visuelles, badges non lus, deploiement Docker sur VPS.

**Ce qui manque** par rapport aux nouvelles exigences :

---

## Phase 0 — Seed organisateur par defaut

### Objectif
Au demarrage du serveur, s'il n'existe aucun utilisateur, creer automatiquement un compte ORGANISATEUR :
- **Username** : `saliou`
- **Mot de passe** : `sall` (hashe en SHA-256)
- **Role** : ORGANISATEUR
- **Approved** : true
- **Status** : OFFLINE

Cet organisateur est le seul a pouvoir approuver les futures inscriptions.

**Fichiers** : `ChatServer.java`, `Database.java`

---

## Phase 1 — Visibilite par role + badges

### Probleme
Actuellement tout le monde voit tout le monde. Il faut :
- **ORGANISATEUR** : voit tout le monde, peut tout faire
- **MEMBRE** : voit les autres membres + les organisateurs
- **BENEVOLE** : ne voit **que** les groupes ou il a ete ajoute (pas de contacts individuels)

### Modifications

#### 1.1 Serveur — `ChatServer.broadcastUserList()`
Au lieu d'envoyer la meme liste a tous, filtrer selon le role du destinataire :

```
Pour chaque client connecte :
  si role == ORGANISATEUR → envoyer TOUS les users
  si role == MEMBRE → envoyer seulement MEMBRE + ORGANISATEUR
  si role == BENEVOLE → envoyer liste vide (pas de contacts individuels)
```

**Fichiers** : `ChatServer.java`, `ClientHandler.java`

#### 1.2 Badges de role dans le sidebar
Chaque contact affiche un badge visuel distinct :
- **ORGANISATEUR** : badge dore/violet avec icone etoile
- **MEMBRE** : badge bleu
- **BENEVOLE** : badge vert

**Fichiers** : `MessagingController.java` (ListCell factory), `styles.css`

---

## Phase 2 — Groupes de discussion

### Probleme
Aucun support groupe. Le BENEVOLE ne peut pas du tout communiquer sans groupes.

### Modifications

#### 2.1 Nouvelle entite `GroupChat`
```java
@Entity
@Table(name = "group_chats")
public class GroupChat {
    Long id;
    String name;
    User creator;               // ManyToOne → forcement un ORGANISATEUR
    boolean membersCanSend;     // true par defaut, ORGANISATEUR peut desactiver
    LocalDateTime dateCreation;
    List<User> members;         // ManyToMany
}
```

**Tables SQL** : `group_chats` + `group_chat_members` (table de jointure)

Le champ `membersCanSend` est le **bonus** : quand il est `false`, seuls les ORGANISATEURS du groupe peuvent envoyer des messages (mode "annonce").

#### 2.2 Modifier `Message` pour supporter les groupes
Ajouter un champ optionnel :
```java
@ManyToOne
GroupChat groupChat;  // null = message 1:1, non-null = message de groupe
```

Si `groupChat != null`, le champ `receiver` est ignore (le message va a tout le groupe).

#### 2.3 Nouveaux types dans `ChatMessage`
```
CREATE_GROUP, CREATE_GROUP_SUCCESS, CREATE_GROUP_FAILURE
ADD_TO_GROUP, REMOVE_FROM_GROUP
GROUP_LIST_UPDATE
SEND_GROUP_MESSAGE, RECEIVE_GROUP_MESSAGE
REQUEST_GROUP_HISTORY, GROUP_HISTORY_RESPONSE
TOGGLE_GROUP_SEND          // bonus : activer/desactiver envoi membres
```

#### 2.4 Serveur — nouveaux handlers dans `ClientHandler`
- `handleCreateGroup(msg)` → verifie ORGANISATEUR, cree le groupe en DB, ajoute les membres
- `handleAddToGroup(msg)` → verifie ORGANISATEUR, ajoute le user au groupe, notifie le user
- `handleRemoveFromGroup(msg)` → verifie ORGANISATEUR, retire le user
- `handleSendGroupMessage(msg)` → verifie que le sender est membre du groupe ET que `membersCanSend == true` (ou que le sender est ORGANISATEUR). Envoie a tous les membres connectes.
- `handleRequestGroupHistory(msg)` → retourne l'historique du groupe
- `handleToggleGroupSend(msg)` → verifie ORGANISATEUR, bascule `membersCanSend`

#### 2.5 Logique de reception groupe
Quand un message de groupe arrive au serveur :
1. Recuperer tous les membres du groupe
2. Pour chaque membre connecte (sauf l'expediteur) → envoyer `RECEIVE_GROUP_MESSAGE`
3. Pour les membres hors ligne → le message sera dans l'historique a leur prochaine connexion

#### 2.6 UI — onglet Groupes
Le bouton "Groupes" dans la navbar gauche affiche dans le sidebar :
- Pour **ORGANISATEUR** : liste de tous les groupes + bouton "Creer un groupe"
- Pour **MEMBRE** : groupes ou il est membre
- Pour **BENEVOLE** : groupes ou il est membre (seule vue possible, pas d'onglet chat individuel)

Cliquer sur un groupe ouvre la conversation de groupe dans la zone chat.
Si `membersCanSend == false` et le user n'est pas ORGANISATEUR → le champ message est desactive avec texte "Seuls les organisateurs peuvent envoyer des messages".

**Fichiers** : `GroupChat.java` (nouveau), `Message.java`, `ChatMessage.java`, `ClientHandler.java`, `ChatServer.java`, `Database.java`, `MessagingController.java`, `messaging-view.fxml`, `styles.css`, `module-info.java`, `persistence.xml`

---

## Phase 3 — Approbation des inscriptions par ORGANISATEUR

### Probleme
Actuellement l'inscription est immediate. Seul un ORGANISATEUR devrait valider.

### Modifications

#### 3.1 Inscription limitee a MEMBRE et BENEVOLE
Le formulaire d'inscription ne propose **que** deux roles :
- MEMBRE
- BENEVOLE

Le role ORGANISATEUR n'est **pas** selectionnable (seul le seed initial en cree un).

#### 3.2 Nouveau champ `User.approved`
```java
boolean approved = false;  // par defaut non approuve
```
Le user seede `saliou` a `approved = true`.

#### 3.3 Serveur — modifier `handleRegister`
- Sauvegarder le user avec `approved = false`
- Repondre `REGISTER_SUCCESS` avec message "Inscription soumise. En attente d'approbation par un organisateur."

#### 3.4 Serveur — modifier `handleLogin`
- Si `!user.isApproved()` → `LOGIN_FAILURE` "Votre compte est en attente d'approbation par un organisateur"

#### 3.5 Nouveaux types dans `ChatMessage`
```
PENDING_USERS_REQUEST, PENDING_USERS_RESPONSE
APPROVE_USER, APPROVE_USER_SUCCESS
REJECT_USER, REJECT_USER_SUCCESS
```

#### 3.6 UI — panneau d'approbation (ORGANISATEUR uniquement)
Icone dans la navbar (visible uniquement pour ORGANISATEUR) → affiche dans le sidebar la liste des inscriptions en attente.
Chaque inscription montre : nom complet, email, role demande, date.
Deux boutons par inscription : "Accepter" (vert) et "Refuser" (rouge).

**Fichiers** : `User.java`, `ChatMessage.java`, `ClientHandler.java`, `Database.java`, `MessagingController.java`, `RegisterController.java`, `register-view.fxml`, `messaging-view.fxml`

---

## Phase 4 — Settings en panneau integre + theme sombre/clair

### Probleme
Le bouton settings doit ouvrir un panneau dans le meme layout (pas de nouvelle fenetre). Il doit proposer la deconnexion et le toggle theme.

### Modifications

#### 4.1 Panneau settings dans le sidebar
Quand on clique sur l'icone settings :
- Le sidebar bascule vers un panneau "Parametres"
- Contenu : avatar, nom, role, bouton "Se deconnecter", toggle theme sombre/clair
- Bouton retour pour revenir aux conversations

#### 4.2 Theme sombre/clair
- Creer `styles-light.css` avec les couleurs inversees (fond blanc, texte sombre)
- Le toggle change le stylesheet de la scene dynamiquement
- Stocker le choix dans `java.util.prefs.Preferences` (persiste entre sessions)

**Fichiers** : `MessagingController.java`, `messaging-view.fxml`, `styles.css`, `styles-light.css` (nouveau)

---

## Phase 5 — Ameliorations UX

### 5.1 Icones plus grandes et lisibles
- Augmenter la taille des icones de la navbar (de ~20px a ~28px)
- Avatars contacts : de 38px a 45px
- Emojis remplacer par de vrais Label stylises plus gros et lisibles
- Espacement plus genereux (comme WhatsApp)

**Fichiers** : `messaging-view.fxml`, `styles.css`

### 5.2 Cache session des messages
Au lieu de recharger tout l'historique a chaque clic sur un contact :

```java
Map<String, List<MessageData>> chatCache = new HashMap<>();
```

- Premier clic → charger depuis serveur, stocker en cache
- Clics suivants → afficher le cache immediatement, demander seulement les nouveaux messages
- Nouveau message recu → ajouter au cache + afficher si conversation ouverte

**Fichiers** : `MessagingController.java`, `ChatMessage.java` (ajouter un champ `lastMessageId` pour le delta)

### 5.3 Indicateurs de lecture (style WhatsApp)
Afficher sous chaque bulle envoyee :
- **1 check gris** (✓) : message envoye (`ENVOYE`)
- **2 checks gris** (✓✓) : message recu (`RECU`)
- **2 checks bleus** (✓✓) : message lu (`LU`)

Ajouter un nouveau type `READ_RECEIPT` dans le protocole :
- Quand un user ouvre une conversation → le serveur notifie l'expediteur que ses messages sont lus
- L'expediteur met a jour les checkmarks en temps reel

**Fichiers** : `ChatMessage.java`, `ClientHandler.java`, `MessagingController.java`, `styles.css`

---

## Phase 6 — Page de telechargement (projet Vite separe)

### Objectif
Un petit site web (1 page) en dehors du projet Java, qui permet de telecharger le client `.jar` pour Mac ou PC.

### Implementation
```
/Users/satoshi/Developpement/ISI/messagerie-download/
├── index.html
├── package.json
├── vite.config.js
└── src/
    └── main.js
    └── style.css
```

Design simple :
- Logo + nom de l'app "Messagerie ISI"
- Description courte
- Bouton : "Telecharger le client (.jar)"
- Instructions d'installation (prerequis Java 17)
- Le JAR est heberge sur le VPS ou sur GitHub Releases

**Deploiement** : build statique (`npm run build`) → servir via Nginx sur le VPS

---

## Ordre d'implementation recommande

| Etape | Phase | Complexite | Dependances |
|-------|-------|------------|-------------|
| 1 | Phase 0 — Seed organisateur `saliou` | Faible | Aucune |
| 2 | Phase 1 — Visibilite par role + badges | Moyenne | Phase 0 |
| 3 | Phase 3 — Approbation inscriptions (MEMBRE/BENEVOLE uniquement) | Moyenne | Phase 0 + 1 |
| 4 | Phase 2 — Groupes de discussion + bonus toggle envoi | Haute | Phase 1 + 3 |
| 5 | Phase 5.1 — Icones + taille | Faible | Aucune |
| 6 | Phase 4 — Settings + theme | Moyenne | Aucune |
| 7 | Phase 5.2 — Cache session | Moyenne | Aucune |
| 8 | Phase 5.3 — Read receipts | Moyenne | Aucune |
| 9 | Phase 6 — Page download Vite | Faible | Aucune |

**Total** : 9 etapes, chacune testable independamment.

---

## Fichiers impactes (resume)

| Fichier | Phases |
|---------|--------|
| `User.java` | 0, 1, 3 |
| `Message.java` | 2 |
| `GroupChat.java` (nouveau) | 2 |
| `Database.java` | 0, 1, 2, 3 |
| `ChatMessage.java` | 1, 2, 3, 5.2, 5.3 |
| `ChatClient.java` | 2, 3, 5.3 |
| `ChatServer.java` | 0, 1, 2, 3 |
| `ClientHandler.java` | 1, 2, 3, 5.3 |
| `MessagingController.java` | 1, 2, 3, 4, 5.1, 5.2, 5.3 |
| `messaging-view.fxml` | 2, 3, 4, 5.1 |
| `styles.css` | 1, 2, 4, 5.1, 5.3 |
| `styles-light.css` (nouveau) | 4 |
| `persistence.xml` | 2 |
| `module-info.java` | 2 |
| `RegisterController.java` | 3 |
| `register-view.fxml` | 3 |
| `LoginController.java` | 3 |
| Projet Vite (nouveau) | 6 |
