# Stack Technique — Élan

## Résumé

| Couche | Technologie | Version |
|--------|-------------|---------|
| UI | JavaFX | 17.0.14 |
| Langage | Java | 17 |
| Build | Maven | 3.x |
| ORM | Hibernate | 6.4.0 |
| Persistance API | Jakarta Persistence | 3.1.0 |
| Base de données | PostgreSQL | 16 |
| Driver JDBC | PostgreSQL JDBC | 42.7.8 |
| Tests | JUnit Jupiter | 5.12.1 |
| Déploiement | Docker + Ansible | — |

---

## Dépendances Maven (pom.xml)

### JavaFX
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

### ORM / Base de données
```xml
<dependency>
  <groupId>org.hibernate.orm</groupId>
  <artifactId>hibernate-core</artifactId>
  <version>6.4.0.Final</version>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <version>42.7.8</version>
</dependency>
```

### Utilitaires UI
```xml
<!-- FormsFX : helpers de formulaires -->
<dependency>
  <groupId>com.dlsc.formsfx</groupId>
  <artifactId>formsfx-core</artifactId>
  <version>11.6.0</version>
</dependency>
<!-- BootstrapFX : styles supplémentaires -->
<dependency>
  <groupId>org.kordamp.bootstrapfx</groupId>
  <artifactId>bootstrapfx-core</artifactId>
  <version>0.4.0</version>
</dependency>
```

---

## Build & Packaging

Deux JARs produits par `mvn package` :

```
target/
├── elan-client.jar      Fat JAR exécutable (client JavaFX)
└── elan-server.jar      Fat JAR exécutable (serveur TCP)
```

### Plugin Shade (fat JAR)
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-shade-plugin</artifactId>
  <!-- Deux executions : client (Launcher) + serveur (ChatServer) -->
</plugin>
```

Le client utilise `Launcher.java` comme main class pour contourner les restrictions
de chargement des modules JavaFX depuis un fat JAR.

### Lancer sans IDE
```bash
mvn package -DskipTests
java -jar target/elan-server.jar    # Serveur
java -jar target/elan-client.jar    # Client
```

---

## Configuration base de données

### persistence.xml (développement local)
```xml
<property name="jakarta.persistence.jdbc.url"      value="jdbc:postgresql://localhost:5432/messagerie"/>
<property name="jakarta.persistence.jdbc.user"     value="postgres"/>
<property name="jakarta.persistence.jdbc.password" value="drame"/>
<property name="hibernate.hbm2ddl.auto"            value="update"/>
```

`hbm2ddl.auto = update` : Hibernate crée les tables si absentes et met à jour le schéma automatiquement.

### Variables d'environnement (production)
```bash
DB_URL=jdbc:postgresql://host:5432/messagerie
DB_USER=postgres
DB_PASSWORD=secret
```

Ces variables surchargent les valeurs du fichier `persistence.xml`.

---

## Déploiement

### Serveur cible
```
VPS : 213.199.51.197
OS  : Ubuntu 22.04
Port exposé : 5555 (TCP)
```

### Infrastructure
```
┌──────────────────────────────────┐
│            VPS                   │
│                                  │
│  ┌─────────────────────────────┐ │
│  │  Docker Compose              │ │
│  │                             │ │
│  │  ┌─────────┐  ┌──────────┐  │ │
│  │  │  app    │  │ postgres │  │ │
│  │  │ (Java)  │◄─►│  :5432   │  │ │
│  │  │  :5555  │  │          │  │ │
│  │  └─────────┘  └──────────┘  │ │
│  └─────────────────────────────┘ │
└──────────────────────────────────┘
```

### Docker Compose (docker-compose.yml)
```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: messagerie
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    image: elan-server:latest
    ports:
      - "5555:5555"
    environment:
      DB_URL: jdbc:postgresql://db:5432/messagerie
      DB_USER: postgres
      DB_PASSWORD: ${DB_PASSWORD}
    depends_on:
      - db
```

### Déploiement automatisé (Ansible)
Un playbook Ansible copie le JAR sur le VPS, reconstruit l'image Docker et redémarre les containers :
```bash
ansible-playbook -i inventory deploy.yml
```

---

## Module Java (module-info.java)

```java
module org.example.examenjava {
    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // UI helpers
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    // JPA / Hibernate
    requires jakarta.persistence;
    requires org.hibernate.orm.core;

    // Standard
    requires java.sql;
    requires java.logging;
    requires java.desktop;   // java.awt.Toolkit (beep)
    requires java.prefs;     // Preferences (thème persistant)

    // Ouverture nécessaire pour Hibernate et JavaFX
    opens org.example.examenjava to javafx.fxml;
    opens org.example.examenjava.Entity to org.hibernate.orm.core, javafx.base;
    opens org.example.examenjava.network to javafx.fxml;

    // Exports
    exports org.example.examenjava;
    exports org.example.examenjava.Entity;
    exports org.example.examenjava.Repository;
    exports org.example.examenjava.network;
    exports org.example.examenjava.server;
}
```

---

## Thème & UI

### Deux thèmes CSS inclus

| Fichier | Thème | Fond principal |
|---------|-------|---------------|
| `styles.css` | Sombre (WhatsApp dark) | `#0b141a` |
| `styles-light.css` | Clair | `#f0f2f5` |

Le thème choisi est persisté via `java.util.prefs.Preferences` (node `elan-app`).

### Palette de couleurs (dark)

```
#0b141a   Fond chat
#111b21   Fond app
#202c33   Navbar / header
#2a3942   Input / sélection
#00a884   Vert accent (boutons, badges, online)
#e9edef   Texte principal
#8696a0   Texte secondaire
#53bdeb   Bleu accusé de lecture (✓✓)
```

### Icônes rôles (SVGPath JavaFX)
```
★ (étoile)  →  ORGANISATEUR  (jaune #eab308)
◆ (diamant) →  MEMBRE        (bleu  #3b82f6)
● (cercle)  →  BENEVOLE      (vert  #22c55e)
```
