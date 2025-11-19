# 🚀 Step-by-Step Guide: Starting the KMP AI Chat App

This guide will walk you through starting the application from scratch.

## Prerequisites Check

Before starting, verify you have everything installed:

```bash
# Check Java (should be 21+)
java -version

# Check Docker
docker --version
docker compose version

# Check Node.js (for Notion Finance - optional)
node --version
npm --version

# Check Gradle (wrapper is included)
./gradlew --version
```

---

## Step 1: Start Database Services

**Open Terminal 1** and start PostgreSQL and Redis:

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Start databases
docker compose up -d

# Verify they're running (wait 5-10 seconds for PostgreSQL to initialize)
docker compose ps
```

You should see:
- `week1-postgres-1` (PostgreSQL on port 5432)
- `week1-redis-1` (Redis on port 6379)

**✅ Keep this terminal open** - databases will keep running.

---

## Step 2: Run Database Migrations

**In the same Terminal 1** (or a new terminal):

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Run Flyway migrations to set up database schema
./gradlew :server:flywayMigrate
```

You should see: `BUILD SUCCESSFUL`

---

## Step 3: Set Up Environment Variables

**Open Terminal 2** (keep Terminal 1 running):

**Option A: Export in terminal (recommended for development)**

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Set required environment variables
export GEMINI_API_KEY='your-gemini-api-key-here'

# Optional: For Notion Finance tab
export NOTION_API_TOKEN='secret_your-notion-token-here'

# Optional: For Model Comparison tab
export HF_API_TOKEN='your-huggingface-token-here'

# Database settings (defaults work with docker-compose)
export DATABASE_URL='jdbc:postgresql://localhost:5432/kmp'
export DB_USER='postgres'
export DB_PASSWORD='postgres'

# Server port (optional, defaults to 8081)
export PORT=8081
```

**Option B: Use .env file**

Create a `.env` file in the project root:

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

cat > .env << EOF
GEMINI_API_KEY=your-gemini-api-key-here
NOTION_API_TOKEN=secret_your-notion-token-here
HF_API_TOKEN=your-huggingface-token-here
DATABASE_URL=jdbc:postgresql://localhost:5432/kmp
DB_USER=postgres
DB_PASSWORD=postgres
PORT=8081
EOF
```

**⚠️ Important**: If you use a `.env` file, you have two options:

**Option 1: Use the helper script (Recommended)**

```bash
# Use the provided script that automatically loads .env
./start-server.sh
```

**Option 2: Manually load .env before starting**

```bash
# Load .env file (if it exists)
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Verify the token is loaded (optional)
echo "NOTION_API_TOKEN is set: ${NOTION_API_TOKEN:0:10}..."

# Then start the server
./gradlew :server:run
```

**💡 The helper script (`start-server.sh`) automatically:**
- Loads variables from `.env` file
- Exports them to the environment
- Starts the server with those variables

**💡 Tip**: You can also add these to your shell profile (`~/.zshrc` or `~/.bashrc`) to avoid setting them every time.

---

## Step 4: Set Up Notion Finance MCP Server (Optional)

**Only if you want to use the Notion Finance tab:**

**In Terminal 2** (or a new terminal):

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Navigate to MCP server directory
cd mcp/notion-finance-server

# Install dependencies
npm install

# Build TypeScript to JavaScript
npm run build

# Verify build succeeded
ls -la dist/index.js

# Go back to project root
cd ../..
```

**✅ This only needs to be done once** (or when you update the MCP server code).

---

## Step 5: Start the Backend Server

**In Terminal 2**:

**If you're using a `.env` file (recommended):**

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Use the helper script that automatically loads .env
./start-server.sh
```

**If you exported variables manually:**

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Start the Ktor backend server
./gradlew :server:run
```

You should see:
```
> Task :server:run
2025-XX-XX XX:XX:XX - Application started in X.XXX seconds.
2025-XX-XX XX:XX:XX - Responding at http://0.0.0.0:8081
```

**✅ Keep this terminal open** - the server will keep running.

**🔍 Watch for:**
- If using Notion Finance, you should see: `Starting MCP server: node ...` and `Resolved path (project root): ...`
- `Passing NOTION_API_TOKEN to MCP server process` (confirms token is being passed)
- Any error messages (check the troubleshooting section below)

**⚠️ Important**: If you get a 404 error for `/api/notion/finance/snapshot`, make sure you:
1. Restarted the server after adding the routes
2. The server compiled successfully (check for BUILD SUCCESSFUL)
3. The route is registered in `Application.kt` (should see `notionFinanceRoutes()` in the routing block)

---

## Step 6: Start the Frontend (Web Client)

**Open Terminal 3**:

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Option 1: Use the helper script
./start-web.sh

# Option 2: Or run directly


```

You should see:
```
> Task :web:jsBrowserDevelopmentRun
Starting webpack dev server...
webpack compiled successfully
```

The browser should automatically open to `http://localhost:8080`

**✅ Keep this terminal open** - the dev server will keep running and auto-reload on code changes.

---

## Step 7: Access the Application

1. **Open your browser** to: http://localhost:8080

2. **You should see:**
   - The KMP AI Chat interface
   - Top bar with mode buttons (Chat, Journal, Temperature Lab, etc.)
   - If Notion Finance is set up: "💰 Notion Finance" button

3. **Try it out:**
   - Click "New Chat" to start a conversation
   - Type a message and press Enter
   - Explore different tabs (Journal, Temperature Lab, etc.)

---

## Quick Reference: All Commands

Here's a quick checklist of what to run:

```bash
# Terminal 1: Databases
cd /Users/anhelina.sudenkova/AICourse/week1
docker compose up -d
./gradlew :server:flywayMigrate

# Terminal 2: Backend (with .env file)
cd /Users/anhelina.sudenkova/AICourse/week1
./start-server.sh

# OR if using manual exports:
export GEMINI_API_KEY='your-key'
export NOTION_API_TOKEN='your-token'  # Optional
./gradlew :server:run

# Terminal 3: Frontend
cd /Users/anhelina.sudenkova/AICourse/week1
./start-web.sh
# OR: ./gradlew :web:jsBrowserDevelopmentRun
```

---

## Troubleshooting

### Problem: "Cannot connect to database"

**Solution:**
```bash
# Check if databases are running
docker compose ps

# If not running, start them
docker compose up -d

# Wait 10 seconds, then try again
```

### Problem: "GEMINI_API_KEY not configured"

**Solution:**
```bash
# Make sure you set the environment variable in the same terminal
export GEMINI_API_KEY='your-actual-key-here'

# Then restart the server
./gradlew :server:run
```

### Problem: "Failed to start Notion MCP server process"

**Solution:**
```bash
# Check if Node.js is installed
node --version

# Rebuild the MCP server
cd mcp/notion-finance-server
npm install
npm run build
cd ../..

# Check if the file exists
ls -la mcp/notion-finance-server/dist/index.js

# Make sure NOTION_API_TOKEN is set
export NOTION_API_TOKEN='secret_your-token'
```

### Problem: "Port 8081 already in use" or "Address already in use"

**Solution:**
```bash
# Option 1: Kill the process using port 8081
lsof -ti:8081 | xargs kill -9

# Then try starting again
./start-server.sh

# Option 2: Use a different port
export PORT=8082
./start-server.sh
```

**Note**: The `start-server.sh` script now automatically checks if the port is in use and provides helpful error messages.

### Problem: "404 Not Found" or "Cannot GET /api/notion/finance/snapshot"

**Solution:**
```bash
# 1. Make sure the server was restarted after adding routes
# Stop the server (Ctrl+C) and restart:
./start-server.sh

# 2. Verify the route is registered
# Check that Application.kt includes: notionFinanceRoutes()

# 3. Test the endpoint directly
curl http://localhost:8081/api/notion/finance/snapshot

# 4. Check server logs for route registration
# You should see the server starting without errors
```

### Problem: Frontend shows "Connection refused"

**Solution:**
- Make sure the backend server is running (Terminal 2)
- Check that it's listening on port 8081
- Verify CORS is configured (should work by default for localhost:8080)

### Problem: "Module not found" for MCP server

**Solution:**
- The path resolution should now work automatically
- Check the server logs for: `Resolved path (project root): ...`
- If it still fails, verify the file exists: `ls -la mcp/notion-finance-server/dist/index.js`

---

## Stopping the Application

To stop everything:

1. **Stop Frontend** (Terminal 3): Press `Ctrl+C`

2. **Stop Backend** (Terminal 2): Press `Ctrl+C`

3. **Stop Databases** (Terminal 1):
   ```bash
   docker compose down
   ```

---

## Development Workflow

Once everything is running:

1. **Backend changes**: Restart Terminal 2 (`./gradlew :server:run`)
2. **Frontend changes**: The webpack dev server auto-reloads (Terminal 3)
3. **Database changes**: Run migrations (`./gradlew :server:flywayMigrate`)
4. **MCP server changes**: Rebuild (`cd mcp/notion-finance-server && npm run build`)

---

## Next Steps

- ✅ Start chatting with the AI
- ✅ Explore the Journal tab
- ✅ Try the Temperature Lab
- ✅ Test the Notion Finance tab (if configured)
- ✅ Check out the Model Comparison tab

**Happy coding! 🎉**

