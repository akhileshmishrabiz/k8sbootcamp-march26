#!/usr/bin/env bash

clear
echo "=========================================="
echo "   FAILURE MONITORING SYSTEM"
echo "=========================================="
echo ""
echo "Local AI + RAG on kind (Ollama, Qdrant, health checker)"
echo ""
echo "1) Deploy"
echo "2) Read documentation (README.md)"
echo "3) Exit"
echo ""
read -r -p "Enter your choice [1-3]: " choice

case $choice in
    1)
        echo ""
        echo "Starting deployment..."
        echo ""
        read -r -p "Press Enter to continue..."
        ./deploy.sh
        ;;
    2)
        echo ""
        less README.md
        echo ""
        exec "$0"
        ;;
    3)
        echo ""
        echo "Goodbye!"
        exit 0
        ;;
    *)
        echo ""
        echo "Invalid choice. Please try again."
        sleep 2
        exec "$0"
        ;;
esac
