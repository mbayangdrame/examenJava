#!/bin/bash

# ============================================================================
# QUICK START - Messagerie Interne
# ============================================================================
# Ce script démarre rapidement l'application JavaFX
# ============================================================================

echo "╔════════════════════════════════════════════════════════╗"
echo "║                                                        ║"
echo "║      🚀 DÉMARRAGE - MESSAGERIE INTERNE 🚀            ║"
echo "║                                                        ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# Vérifier que nous sommes dans le bon répertoire
if [ ! -f "pom.xml" ]; then
    echo "❌ Erreur: pom.xml non trouvé!"
    echo "Veuillez vous placer dans le répertoire examenJava"
    exit 1
fi

echo "✓ Répertoire correct détecté"
echo ""

# Afficher les options
echo "📋 Options disponibles:"
echo "  1) Lancer l'application (run)"
echo "  2) Compiler uniquement (compile)"
echo "  3) Nettoyer et compiler (clean compile)"
echo "  4) Générer le JAR (package)"
echo ""

read -p "Choisissez une option (1-4) [défaut: 1]: " choice
choice=${choice:-1}

echo ""

case $choice in
    1)
        echo "🚀 Lancement de l'application..."
        mvn clean javafx:run
        ;;
    2)
        echo "🔨 Compilation en cours..."
        mvn compile
        ;;
    3)
        echo "🔨 Nettoyage et compilation..."
        mvn clean compile
        ;;
    4)
        echo "📦 Génération du JAR..."
        mvn clean package -DskipTests
        echo ""
        echo "✅ JAR généré: target/examenJava-1.0-SNAPSHOT.jar"
        ;;
    *)
        echo "❌ Option invalide!"
        exit 1
        ;;
esac

echo ""
echo "✅ Terminé!"

