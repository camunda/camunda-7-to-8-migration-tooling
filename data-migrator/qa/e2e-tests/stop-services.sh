#!/bin/bash
set -e

echo "Stopping Docker Compose services..."

# Stop and remove containers, networks, and volumes
docker compose down -v

echo "✓ Docker Compose services stopped and cleaned up."

