#!/bin/bash

echo "Stopping databases..."
docker stop kmp-postgres kmp-redis 2>/dev/null || true
echo "✅ Databases stopped!"
echo ""
echo "To remove containers: docker rm kmp-postgres kmp-redis"
echo "To remove volumes: docker volume rm kmp-postgres-data kmp-redis-data"

