#!/bin/bash

# Start PostgreSQL
echo "Starting PostgreSQL..."
docker run -d \
  --name kmp-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=kmp \
  -p 5432:5432 \
  -v kmp-postgres-data:/var/lib/postgresql/data \
  postgres:16-alpine

# Start Redis
echo "Starting Redis..."
docker run -d \
  --name kmp-redis \
  -p 6379:6379 \
  -v kmp-redis-data:/data \
  redis:7-alpine

echo "✅ Databases started!"
echo "PostgreSQL: localhost:5432"
echo "Redis: localhost:6379"
echo ""
echo "To stop: ./stop-databases.sh"
echo "To view logs: docker logs kmp-postgres or docker logs kmp-redis"

