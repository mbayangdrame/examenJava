#!/bin/bash
# =============================================================
#  build-client.sh — Génère l'installeur natif du client
#  Résultat : target/installer/Messagerie-ISI.dmg  (Mac)
#             target/installer/Messagerie-ISI.exe  (Windows)
#             target/installer/Messagerie-ISI.deb  (Linux)
#
#  Prérequis :
#    - JDK 17+ avec jpackage (inclus dans JDK 14+)
#    - Maven wrapper (./mvnw)
#    - macOS : Xcode Command Line Tools  (xcode-select --install)
#    - Windows : WiX Toolset v3 (pour .msi) ou aucun (pour .exe)
# =============================================================

set -e

echo "======================================================"
echo "  Build installeur client — Messagerie ISI"
echo "======================================================"

# 1. Build du fat JAR client (toutes dépendances incluses)
echo ""
echo "→ Étape 1 : Compilation et création du fat JAR client..."
./mvnw clean package -DskipTests -q
echo "   ✅ target/chat-client.jar créé"

# 2. Préparer le dossier d'entrée pour jpackage
INPUT_DIR="target/jpackage-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp target/chat-client.jar "$INPUT_DIR/"
echo "→ Étape 2 : Dossier d'entrée préparé ($INPUT_DIR)"

# 3. Créer l'installeur avec jpackage
echo ""
echo "→ Étape 3 : Génération de l'installeur avec jpackage..."
rm -rf target/installer
mkdir -p target/installer

# Options Windows spécifiques
WIN_OPTS=""
if [[ "$(uname -s)" == MINGW* ]] || [[ "$(uname -s)" == CYGWIN* ]] || [[ "$(uname -s)" == MSYS* ]]; then
  WIN_OPTS="--type exe --win-per-user-install --win-dir-chooser --win-shortcut --win-menu --win-menu-group Messagerie-ISI"
fi

jpackage \
  --name "Messagerie-ISI" \
  --app-version "1.0" \
  --vendor "ISI" \
  --description "Application de messagerie instantanee pour associations ISI" \
  --input "$INPUT_DIR" \
  --main-jar chat-client.jar \
  --main-class org.example.examenjava.Launcher \
  --dest target/installer \
  --java-options "--add-opens java.base/java.lang=ALL-UNNAMED" \
  --java-options "--add-opens java.base/java.util=ALL-UNNAMED" \
  --java-options "--add-opens java.base/java.lang.reflect=ALL-UNNAMED" \
  $WIN_OPTS

echo ""
echo "======================================================"
echo "  ✅ Installeur généré dans : target/installer/"
ls -lh target/installer/
echo "======================================================"
echo ""
echo "  → Copiez ce fichier sur n'importe quel Mac/PC et"
echo "    installez-le normalement (double-clic)."
echo "  → Aucun Java requis sur la machine cible !"
echo "======================================================"
