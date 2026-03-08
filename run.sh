#!/bin/bash

# Script de lancement de l'application Messagerie Interne

echo "🚀 Lancement de l'application Messagerie Interne..."

cd "$(dirname "$0")"

# Compiler et exécuter avec Maven
mvn clean javafx:run

echo "✅ Application fermée"

