#!/bin/bash

# Script to start the web frontend
# Usage: ./start-web.sh

set -e

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "Starting web frontend..."
./gradlew :web:jsBrowserDevelopmentRun

