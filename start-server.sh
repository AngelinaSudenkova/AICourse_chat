#!/bin/bash

# Script to start the backend server with .env file support
# Usage: ./start-server.sh

set -e

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Load .env file if it exists
if [ -f .env ]; then
    echo "Loading environment variables from .env file..."
    # Export variables from .env file, ignoring comments and empty lines
    # This handles values with spaces and special characters correctly
    set -a  # Automatically export all variables
    source .env
    set +a  # Stop automatically exporting
    echo "Environment variables loaded from .env file."
    
    # Show which keys were loaded (without showing values for security)
    echo "Loaded keys: $(grep -v '^#' .env | grep -v '^$' | cut -d'=' -f1 | tr '\n' ' ')"
else
    echo "Warning: .env file not found. Using environment variables from shell."
fi

# Verify required variables (optional check)
if [ -z "$GEMINI_API_KEY" ]; then
    echo "Warning: GEMINI_API_KEY is not set. AI features may not work."
fi

if [ -z "$NOTION_API_TOKEN" ]; then
    echo "Info: NOTION_API_TOKEN is not set. Notion Finance tab will not work."
else
    echo "Info: NOTION_API_TOKEN is set (starts with: ${NOTION_API_TOKEN:0:10}...)"
fi

# Check if port is already in use
PORT=${PORT:-8081}
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "ERROR: Port $PORT is already in use!"
    echo ""
    echo "To fix this, you can:"
    echo "  1. Kill the process using port $PORT:"
    echo "     lsof -ti:$PORT | xargs kill -9"
    echo ""
    echo "  2. Or use a different port:"
    echo "     export PORT=8082"
    echo "     ./start-server.sh"
    echo ""
    echo "Finding process using port $PORT..."
    lsof -i:$PORT
    exit 1
fi

# Start the server
echo "Starting backend server on port $PORT..."
./gradlew :server:run

