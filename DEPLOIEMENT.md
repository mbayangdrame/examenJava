# Deploiement - Messagerie Interne

## 1. Architecture de deploiement

```
    Machine A (Mac/PC)              Machine B (Mac/PC)
    ┌──────────────┐               ┌──────────────┐
    │ Client JavaFX│               │ Client JavaFX│
    │ (./mvnw      │               │ (./mvnw      │
    │  javafx:run) │               │  javafx:run) │
    └──────┬───────┘               └──────┬───────┘
           │ TCP :5555                     │ TCP :5555
           │                               │
           ▼                               ▼
    ┌─────────────────────────────────────────────┐
    │              VPS (213.199.51.197)            │
    │                                             │
    │  ┌─────────────────┐   ┌────────────────┐  │
    │  │ chat-messagerie  │──►│   PostgreSQL   │  │
    │  │ (Docker)         │   │   (Docker)     │  │
    │  │ port 5555        │   │   port 5432    │  │
    │  └─────────────────┘   └────────────────┘  │
    │         reseau Docker : nginx-proxy         │
    └─────────────────────────────────────────────┘
```

Le serveur de chat tourne dans un **container Docker** sur le VPS.
Les clients JavaFX se connectent directement au VPS via **TCP sur le port 5555**.
La base de donnees PostgreSQL est un container Docker partage sur le meme VPS.

---

## 2. Ce qui tourne sur le VPS

| Container | Image | Role | Port |
|---|---|---|---|
| `chat-messagerie` | `chat-messagerie:latest` | Serveur de chat (Java 17) | 5555 (expose) |
| `postgres` | `postgres:16-alpine` | Base de donnees | 5432 (interne) |

Les deux containers communiquent via le reseau Docker `nginx-proxy`.
PostgreSQL n'est **pas expose** sur Internet, seul le serveur de chat y accede en interne.

---

## 3. Fichiers de deploiement

```
examenjava/
├── Dockerfile                          ← Image Docker du serveur
├── target/chat-server.jar              ← JAR genere par Maven
│
vps-ansible/
├── files/projects/chat-messagerie/
│   └── docker-compose.yml              ← Config du container
├── playbooks/
│   └── deploy-chat-messagerie.yml      ← Playbook Ansible
```

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/chat-server.jar /app/chat-server.jar
EXPOSE 5555
CMD ["java", "-jar", "chat-server.jar"]
```

- Base : `eclipse-temurin:17-jre-alpine` (JRE 17, image legere ~80MB)
- Copie le fat JAR (serveur + toutes les dependances)
- Expose le port 5555 pour les connexions Socket

### docker-compose.yml

```yaml
services:
  chat-server:
    image: chat-messagerie:latest
    container_name: chat-messagerie
    restart: always
    ports:
      - "5555:5555"
    environment:
      DB_URL: "jdbc:postgresql://postgres:5432/messagerie"
      DB_USER: "fitsen"
      DB_PASSWORD: "${POSTGRES_PASSWORD}"
    mem_limit: 384m
    networks:
      - nginx-proxy

networks:
  nginx-proxy:
    external: true
```

- Le container rejoint le reseau `nginx-proxy` ou se trouve deja PostgreSQL
- `postgres:5432` est le nom DNS interne du container PostgreSQL
- Les identifiants DB sont passes en variables d'environnement
- `restart: always` relance automatiquement le serveur en cas de crash

---

## 4. Comment deployer

### Prerequis

- Java 17 installe sur ta machine (pour builder le JAR)
- Acces SSH au VPS (`ssh satoshi@213.199.51.197`)
- Ansible installe (`pip install ansible`)
- PostgreSQL et Docker deja en place sur le VPS (via `vps-ansible`)

### Etape 1 — Builder le JAR

```bash
cd /Users/satoshi/Developpement/ISI/examenjava
./mvnw clean package -DskipTests
```

Genere `target/chat-server.jar` (29MB, contient le serveur + Hibernate + PostgreSQL driver).

### Etape 2 — Deployer sur le VPS

```bash
cd /Users/satoshi/Developpement/my-projects/vps-ansible
ansible-playbook playbooks/deploy-chat-messagerie.yml
```

Ce playbook fait automatiquement :

1. Verifie que le JAR existe
2. Verifie que PostgreSQL tourne sur le VPS
3. Cree la base de donnees `messagerie` si elle n'existe pas
4. Copie le JAR + Dockerfile + docker-compose.yml sur le VPS dans `/srv/projects/chat-messagerie/`
5. Build l'image Docker sur le VPS
6. Arrete l'ancien container s'il existe
7. Lance le nouveau container
8. Ouvre le port 5555 dans le firewall

### Etape 3 — Verifier

```bash
ssh satoshi@213.199.51.197 "docker ps --filter name=chat-messagerie"
ssh satoshi@213.199.51.197 "docker logs chat-messagerie --tail 10"
```

On doit voir :
```
[INFO] Serveur en ecoute sur 0.0.0.0:5555 (toutes les interfaces reseau)
```

---

## 5. Comment utiliser (cote client)

Chaque utilisateur lance le client JavaFX sur sa machine :

```bash
cd /Users/satoshi/Developpement/ISI/examenjava
./mvnw javafx:run
```

L'adresse du VPS (`213.199.51.197`) est deja configuree dans le code.
L'utilisateur voit l'ecran de login, s'inscrit ou se connecte, et commence a chatter.

---

## 6. Mettre a jour le serveur

Apres une modification du code serveur :

```bash
# 1. Rebuild le JAR
cd /Users/satoshi/Developpement/ISI/examenjava
./mvnw clean package -DskipTests

# 2. Redeployer
cd /Users/satoshi/Developpement/my-projects/vps-ansible
ansible-playbook playbooks/deploy-chat-messagerie.yml
```

Le playbook arrete l'ancien container, rebuild l'image, et relance. Zero downtime de configuration.

---

## 7. Commandes utiles

| Action | Commande |
|---|---|
| Voir les logs du serveur | `ssh satoshi@213.199.51.197 "docker logs chat-messagerie -f"` |
| Redemarrer le serveur | `ssh satoshi@213.199.51.197 "cd /srv/projects/chat-messagerie && docker compose restart"` |
| Arreter le serveur | `ssh satoshi@213.199.51.197 "cd /srv/projects/chat-messagerie && docker compose down"` |
| Voir la base de donnees | `ssh satoshi@213.199.51.197 "docker exec postgres psql -U fitsen -d messagerie -c '\dt'"` |
| Voir les utilisateurs | `ssh satoshi@213.199.51.197 "docker exec postgres psql -U fitsen -d messagerie -c 'SELECT username, role, status FROM users;'"` |
| Voir les messages | `ssh satoshi@213.199.51.197 "docker exec postgres psql -U fitsen -d messagerie -c 'SELECT * FROM messages ORDER BY dateenvoi DESC LIMIT 10;'"` |
| Creer la base (playbook) | `ansible-playbook playbooks/create-database.yml -e "db_name=messagerie"` |

---

## 8. Securite

| Element | Mesure |
|---|---|
| SSH | Cle Ed25519 uniquement, pas de mot de passe |
| Firewall | UFW : seuls les ports 22, 80, 443, 5555 sont ouverts |
| PostgreSQL | Non expose sur Internet, accessible uniquement via reseau Docker interne |
| Mots de passe | Haches en SHA-256 avant stockage en base |
| Fail2ban | Actif sur SSH (ban apres 5 tentatives) |
