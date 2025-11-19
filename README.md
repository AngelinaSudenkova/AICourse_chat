# KMP AI Chat Application

A production-ready full-stack Kotlin Multiplatform application featuring an AI-powered chat interface with Gemini integration, tool calling capabilities, and conversation management.

## 🎯 Project Overview

This is a modern chat application built with:
- **Kotlin Multiplatform (KMP)** for shared business logic
- **Compose for Web** for a beautiful, responsive UI
- **Ktor 3.x** for the backend API
- **Gemini 2.5 Flash** for AI-powered conversations
- **PostgreSQL** for persistent message storage
- **Redis** for caching and rate limiting (ready for implementation)

### Key Features

- 💬 **Multi-Conversation Chat**: Create, switch, and manage multiple chat conversations
- 🤖 **AI Integration**: Powered by Google's Gemini 2.5 Flash model
- 🔧 **Tool Calling**: Built-in calculator, time zone, and database search tools
- 📝 **Message Persistence**: All conversations saved to PostgreSQL
- 🎨 **Modern UI**: Beautiful Compose for Web interface with dark/light themes
- 📱 **Responsive Design**: Works seamlessly on desktop and mobile browsers
- ✨ **Real-time Updates**: Auto-scrolling, typing indicators, and smooth animations
- 💾 **Theme Persistence**: Dark mode preference saved across sessions
- 💰 **Notion Finance Integration**: Query your Notion expense database and ask AI about your spending via MCP

---

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **JDK 21+** (Java Development Kit)
- **Docker Desktop** (for running PostgreSQL and Redis)
- **Gradle 8.5+** (included via wrapper)
- **Node.js 20+** (for Notion Finance MCP server - optional)
- **Gemini API Key** (optional, for AI features - get one at [Google AI Studio](https://makersuite.google.com/app/apikey))
- **Notion API Token** (optional, for Notion Finance tab - get one at [Notion Integrations](https://www.notion.so/my-integrations))

### Verify Prerequisites

```bash
# Check Java version
java -version
# Should show: openjdk version "21" or higher

# Check Docker
docker --version
docker compose version
```

---

## 🚀 Step-by-Step Setup Guide

### Step 1: Start Database Services

**Open Terminal 1** and start PostgreSQL and Redis using Docker Compose:

```bash
cd /Users/anhelina.sudenkova/AICourse/week1
docker compose up -d
```

**Verify databases are running:**
```bash
docker compose ps
```

You should see two containers running:
- `week1-postgres-1` (PostgreSQL on port 5432)
- `week1-redis-1` (Redis on port 6379)

**Wait 5-10 seconds** for PostgreSQL to fully initialize.

---

### Step 2: Set Environment Variables

**Option A: Export in terminal (recommended for development)**

```bash
# Set Gemini API key (get from https://makersuite.google.com/app/apikey)
export GEMINI_API_KEY='your-api-key-here'

# (Optional) Hugging Face Inference Providers token – required for Model Comparison tab
export HF_API_TOKEN='your-hf-token-here'

# Database settings (defaults work if using docker-compose.yml)
export DATABASE_URL='jdbc:postgresql://localhost:5432/kmp'
export DB_USER='postgres'
export DB_PASSWORD='postgres'

# Server port
export PORT=8081
```

**Option B: Create .env file**

```bash
cat > .env << EOF
GEMINI_API_KEY=your-api-key-here
NOTION_API_TOKEN=secret_your-notion-token-here
HF_API_TOKEN=your-hf-token-here
DATABASE_URL=jdbc:postgresql://localhost:5432/kmp
DB_USER=postgres
DB_PASSWORD=postgres
PORT=8081
EOF
```

**⚠️ Important**: Java/Kotlin applications don't automatically read `.env` files. You need to either:

1. **Use the helper script** (recommended):
   ```bash
   ./start-server.sh  # Automatically loads .env and starts server
   ```

2. **Manually load .env before starting**:
   ```bash
   # Load .env file
   export $(cat .env | grep -v '^#' | xargs)
   # Then start server
   ./gradlew :server:run
   ```

**Notes:**
- `GEMINI_API_KEY` enables Gemini-powered chat and journaling.
- `HF_API_TOKEN` (Inference Providers token) is required for the Model Comparison tab. Generate one with "Make calls to Inference Providers" permission in the Hugging Face settings.

---

### Step 3: Run Database Migrations

This creates all necessary database tables and seed data:

```bash
./gradlew :server:flywayMigrate
```

**Expected output:**
```
> Task :server:flywayMigrate
Migrating schema "public" to version "1 - init"
Successfully applied 1 migration to schema "public"
BUILD SUCCESSFUL
```

**If you get connection errors:**
- Make sure Docker containers are running: `docker compose ps`
- Wait a few more seconds for PostgreSQL to be ready
- Check logs: `docker compose logs postgres`

---

### Step 4: Start the Backend Server

**Open Terminal 2** (keep Terminal 1 with Docker running) and start the Ktor server:

**If using .env file (recommended):**

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Use helper script that loads .env automatically
./start-server.sh
```

**If exporting variables manually:**

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Set environment variables
export PORT=8081
export GEMINI_API_KEY='your-api-key-here'  # Optional
export HF_API_TOKEN='your-hf-token-here'  # Optional, required for model comparison

# Start the server
./gradlew :server:run
```

**Wait for:**
```
Application started in 0.XXX seconds.
Application is responding at http://0.0.0.0:8081
```

The server is now running on **http://localhost:8081**

**Test the server:**
```bash
# In another terminal, test health endpoint
curl http://localhost:8081/health
# Should return: OK
```

---

### Step 5: Start the Web Client

**Open Terminal 3** (keep Terminal 1 and 2 running) and start the web development server:

```bash
cd /Users/anhelina.sudenkova/AICourse/week1
./gradlew :web:jsBrowserDevelopmentRun
```

**Wait for:**
```
> Task :web:jsBrowserDevelopmentRun
webpack 5.x.x compiled successfully
```

The web client will automatically open in your browser at **http://localhost:8080**

**If it doesn't open automatically:**
- Navigate manually to: `http://localhost:8080`
- The webpack dev server is running and will hot-reload on code changes

---

## 🎮 Using the Application

### First Launch

1. You'll see a **sidebar on the left** with a "New Chat" button
2. Click **"+ New Chat"** to create your first conversation
3. Start typing in the input field at the bottom

### Chat Features

**Send Regular Messages:**
- Type any message and press Enter
- The AI (Gemini) will respond (if API key is set)
- Messages are automatically saved

**Use Built-in Tools:**

- **Calculator**: `/calc 2+2*3` or `/calculate 10/2`
- **Current Time**: `/time` or `/now`
- **Database Search**: `/search kotlin` or `/db ktor`

**Example:**
```
You: /calc 2+2*3
AI: Result: 8
🔧 Used: calculator(2+2*3)
```

### Conversation Management

- **Create New Chat**: Click "+ New Chat" in the sidebar
- **Switch Conversations**: Click any conversation in the sidebar
- **Delete Conversation**: Click the "×" button next to a conversation
- **Export Messages**: Click the "📥" button in the top bar

### UI Features

- **Dark/Light Theme**: Toggle with the 🌙/☀️ button (preference is saved)
- **Auto-Scroll**: Chat automatically scrolls to show new messages
- **Typing Indicator**: Shows animated dots while waiting for responses
- **Markdown Support**: AI responses are beautifully formatted with headers, lists, code blocks, etc.

### Specialized AI Tabs

Use the mode toggle in the top bar to jump between focused workflows:

- **📔 Personal Journal** – guided Gemini journaling with follow-up questions and a structured reflection card.
- **🧪 Reasoning Lab** – run four prompting strategies (Direct, Step-by-step, Best Prompt, Expert Panel) plus on-demand comparisons.
- **🔥 Temperature Lab** – benchmark the same prompt at different sampling temperatures and review an aggregated summary card.
- **🧪 Model Comparison** – send provider-compatible prompts to multiple Hugging Face Inference Providers at once. Two tasks are available out of the box:
  - *Story Prompt*: “Tell a short 6 sentence story about a curious cat exploring a hidden room.”
  - *Fibonacci Code*: “Write idiomatic Kotlin code that prints the Fibonacci sequence up to 20 terms.”

  Expand any result row to reveal the full output in a scrollable panel; timing, token estimates, and optional cost calculations are displayed alongside each model run. Set `HF_API_TOKEN` for successful calls.

---

## 🏗️ Project Structure

```
kmp-ai-app/
├── shared/                    # Shared KMP module
│   ├── src/commonMain/kotlin/
│   │   ├── models/           # Data models (ChatMessage, Conversation, etc.)
│   │   ├── transport/        # HTTP transport layer
│   │   └── platform/         # Platform-specific code (time, etc.)
│   └── build.gradle.kts
│
├── web/                       # Compose for Web frontend
│   ├── src/jsMain/
│   │   ├── kotlin/Main.kt    # Main UI components
│   │   └── resources/
│   │       └── index.html    # HTML entry point
│   └── build.gradle.kts
│
├── server/                    # Ktor backend
│   ├── src/main/kotlin/
│   │   ├── Application.kt    # Server entry point
│   │   ├── routes/           # API routes
│   │   │   ├── AgentRoutes.kt        # Main chat endpoint
│   │   │   ├── ConversationRoutes.kt # Conversation management
│   │   │   ├── ChatRoutes.kt         # Legacy chat endpoint
│   │   │   └── ChatService.kt        # Database operations
│   │   ├── ai/
│   │   │   └── GeminiClient.kt       # Gemini API integration
│   │   ├── tools/
│   │   │   └── ToolRegistry.kt       # Tool execution
│   │   └── database/
│   │       ├── DatabaseFactory.kt    # Database setup
│   │       └── tables/Tables.kt      # Exposed table definitions
│   ├── src/main/resources/
│   │   └── db/migration/     # Flyway migrations
│   └── build.gradle.kts
│
├── docker-compose.yml         # Local development databases
├── Dockerfile                 # Production server image
├── build.gradle.kts           # Root build configuration
├── settings.gradle.kts        # Project settings
└── gradle/
    └── libs.versions.toml     # Dependency versions
```

---

## 🔌 API Endpoints

### Chat Endpoints

**`POST /api/agent`** - Send chat message (primary endpoint)
```bash
curl -X POST http://localhost:8081/api/agent \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "Hello!"}
    ],
    "conversationId": "123"
  }'
```

**Response:**
```json
{
  "message": {
    "role": "assistant",
    "content": "Hello! How can I help you?",
    "timestamp": 1762219173104
  },
  "toolCalls": []
}
```

### Conversation Management

**`GET /api/conversations`** - List all conversations
```bash
curl http://localhost:8081/api/conversations
```

**`GET /api/conversations/{id}`** - Get specific conversation with messages
```bash
curl http://localhost:8081/api/conversations/1
```

**`POST /api/conversations`** - Create new conversation
```bash
curl -X POST http://localhost:8081/api/conversations
```

**`DELETE /api/conversations/{id}`** - Delete conversation
```bash
curl -X DELETE http://localhost:8081/api/conversations/1
```

### Health Check

**`GET /health`** - Health check endpoint
```bash
curl http://localhost:8081/health
# Returns: OK
```

---

## 🛠️ Development Commands

### Building

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :server:build
./gradlew :web:build
./gradlew :shared:build

# Clean build
./gradlew clean build
```

### Testing

```bash
# Run all tests
./gradlew test

# Run server tests only
./gradlew :server:test

# Run with coverage
./gradlew test koverXml
```

### Code Quality

```bash
# Lint code
./gradlew ktlintCheck

# Static analysis
./gradlew detekt

# Format code
./gradlew ktlintFormat
```

### Database Operations

```bash
# Run migrations
./gradlew :server:flywayMigrate

# Clean database (development only - WARNING: deletes all data)
docker compose down -v
docker compose up -d
./gradlew :server:flywayMigrate
```

---

## 🐛 Troubleshooting

### Database Connection Issues

**Problem:** `Connection to localhost:5432 refused`

**Solution:**
```bash
# Check if containers are running
docker compose ps

# If not running, start them
docker compose up -d

# Check PostgreSQL logs
docker compose logs postgres

# Wait 10 seconds and try again
```

### Port Already in Use

**Problem:** `Address already in use: 8081`

**Solution:**
```bash
# Find process using port 8081
lsof -i :8081

# Kill the process
kill -9 <PID>

# Or use a different port
export PORT=8082
./gradlew :server:run
```

### Gemini API Not Working

**Problem:** `Error: GEMINI_API_KEY not configured` or timeout errors

**Solution:**
```bash
# Set the API key
export GEMINI_API_KEY='your-actual-api-key'

# Restart the server
./gradlew :server:run
```

**Get an API key:**
1. Visit https://makersuite.google.com/app/apikey
2. Sign in with your Google account
3. Click "Create API Key"
4. Copy the key and set it as an environment variable

### Web UI Not Loading

**Problem:** Blank screen or 404 errors

**Solution:**
```bash
# Rebuild the web module
./gradlew :web:clean :web:build

# Restart web dev server
./gradlew :web:jsBrowserDevelopmentRun

# Hard refresh browser (Cmd+Shift+R / Ctrl+Shift+R)
# Or clear browser cache
```

### Build Failures

**Problem:** Gradle build errors

**Solution:**
```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies

# Check Java version (need 21+)
java -version

# If wrong version, set JAVA_HOME
export JAVA_HOME=/path/to/java21
```

### Migration Errors

**Problem:** Flyway migration fails

**Solution:**
```bash
# Check database connection
docker exec -it week1-postgres-1 psql -U postgres -d kmp -c "SELECT 1;"

# Reset database (WARNING: deletes all data)
docker compose down -v
docker compose up -d
sleep 10
./gradlew :server:flywayMigrate
```

---

## 🌍 Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `PORT` | Server port | `8081` | No |
| `DATABASE_URL` | PostgreSQL connection string | `jdbc:postgresql://localhost:5432/kmp` | No |
| `DB_USER` | Database username | `postgres` | No |
| `DB_PASSWORD` | Database password | `postgres` | No |
| `REDIS_URL` | Redis connection string | `redis://localhost:6379` | No |
| `GEMINI_API_KEY` | Gemini API key | - | **Yes** (for AI features) |
| `HF_API_TOKEN` | Hugging Face Inference Providers token | - | **Yes** (for Model Comparison tab) |
| `NOTION_API_TOKEN` | Notion integration token | - | **Yes** (for Notion Finance tab) |
| `MCP_NOTION_CMD` | MCP server command | `node` | No |
| `MCP_NOTION_ARGS` | MCP server arguments | `mcp/notion-finance-server/dist/index.js` | No |
| `NOTION_FINANCE_DATABASE_ID` | Notion database ID | `ID` | No |

---

## 💰 Notion Finance MCP Integration

The application includes a Model Context Protocol (MCP) integration that allows you to query your Notion finance database and ask AI questions about your spending.

### Prerequisites for Notion Finance

1. **Node.js 20+** - Required for running the MCP server
   ```bash
   node --version  # Should show v20.x.x or higher
   npm --version   # Should show 9.x.x or higher
   ```

2. **Notion Integration Token** - Create a Notion integration:
   - Go to https://www.notion.so/my-integrations
   - Click "New integration"
   - Give it a name (e.g., "Finance MCP Integration")
   - Copy the "Internal Integration Token"
   - Share your finance database with this integration (click "..." on the database → "Connections" → Add your integration)

3. **Notion Database** - Your database should have these properties:
   - `Expense` (Title) - The expense name
   - `Amount` (Number) - The expense amount
   - `Date` (Date) - The expense date
   - `Category` (Relation) - Optional category relation

### Step-by-Step Notion Finance Setup

#### Step 1: Install and Build MCP Server

```bash
# Navigate to the MCP server directory
cd mcp/notion-finance-server

# Install dependencies
npm install

# Build TypeScript to JavaScript
npm run build

# Verify build succeeded
ls -la dist/index.js  # Should exist
```

#### Step 2: Configure Environment Variables

Add the Notion API token to your environment:

```bash
# Export in terminal (recommended for development)
export NOTION_API_TOKEN='secret_your_notion_integration_token_here'

# Optional: Customize MCP server path if needed
export MCP_NOTION_CMD='node'
export MCP_NOTION_ARGS='mcp/notion-finance-server/dist/index.js'

# Optional: Use a different database ID
export NOTION_FINANCE_DATABASE_ID='your-database-id-here'
```

**Or add to your `.env` file:**
```bash
echo "NOTION_API_TOKEN=secret_your_notion_integration_token_here" >> .env
```

#### Step 3: Verify MCP Server Works

Test the MCP server manually (optional):

```bash
cd mcp/notion-finance-server
NOTION_API_TOKEN='your-token' node dist/index.js
```

The server should start and wait for stdio input. Press Ctrl+C to exit.

#### Step 4: Start the Backend with Notion Support

Make sure you have all required environment variables set:

```bash
# Terminal 2 - Backend Server
export GEMINI_API_KEY='your-gemini-key'
export NOTION_API_TOKEN='your-notion-token'
./gradlew :server:run
```

The backend will automatically:
- Start the MCP server as a subprocess
- Connect to your Notion database
- Expose `/api/notion/finance/snapshot` and `/api/notion/finance/analyze` endpoints

#### Step 5: Access Notion Finance Tab

1. Start the web client (if not already running):
   ```bash
   # Terminal 3 - Web Client
   ./gradlew :web:jsBrowserDevelopmentRun
   ```

2. Open http://localhost:8080 in your browser

3. Click the **"💰 Notion Finance"** button in the top bar

4. The tab will:
   - Automatically load your finance entries (last 30 days)
   - Display them in a table with Date, Title, and Amount
   - Show total spending
   - Allow you to ask AI questions about your spending

### Using the Notion Finance Tab

#### Viewing Expenses

- Click **"Refresh Finance Data"** to reload entries
- The table shows all expenses with their dates, titles, and amounts
- Total amount is displayed above the table

#### Asking AI Questions

**Option 1: Default Question**
- Click **"Ask AI about my spending"** button
- AI will provide a summary and highlight unusual expenses

**Option 2: Custom Question**
- Type your question in the text input (e.g., "Why did I spend so much on transport?")
- Press **Enter** or click **"Send"**
- AI will analyze your expenses and answer your question

#### Example Questions

- "What were my biggest expenses this month?"
- "How much did I spend on food?"
- "Show me expenses over 100 PLN"
- "What's my average daily spending?"
- "Why did I spend so much on transport?"

### Troubleshooting Notion Finance

#### Problem: "Failed to start Notion MCP server process"

**Solution:**
```bash
# Verify Node.js is installed
node --version

# Verify MCP server is built
ls -la mcp/notion-finance-server/dist/index.js

# Rebuild if needed
cd mcp/notion-finance-server
npm run build
```

#### Problem: "Notion API error: 401 Unauthorized"

**Solution:**
- Verify `NOTION_API_TOKEN` is set correctly
- Make sure the token starts with `secret_`
- Ensure the integration is shared with your database:
  1. Open your Notion database
  2. Click "..." (three dots) → "Connections"
  3. Add your integration

#### Problem: "No expenses found"

**Solution:**
- Verify your database has entries
- Check that property names match: `Expense`, `Amount`, `Date`
- Try adjusting the date range in the code (default is last 30 days)

#### Problem: "MCP tools/call error"

**Solution:**
- Check server logs for detailed error messages
- Verify `NOTION_API_TOKEN` is set
- Ensure the database ID is correct
- Check that the database is accessible to the integration

### Notion Finance API Endpoints

The backend exposes two endpoints:

1. **GET `/api/notion/finance/snapshot`**
   - Returns finance entries (last 30 days by default)
   - Used by the UI to display the table

2. **POST `/api/notion/finance/analyze`**
   - Accepts: `{ "question": "optional question", "fromDate": "optional", "toDate": "optional" }`
   - Returns: `{ "entries": [...], "aiAnswer": "AI response" }`
   - Fetches entries, builds prompt, calls Gemini, returns analysis

### MCP Server Architecture

The MCP server (`mcp/notion-finance-server/`) is a Node.js/TypeScript application that:
- Communicates via stdio using JSON-RPC 2.0
- Queries Notion API using the official SDK
- Exposes the `notion.finance_get_entries` tool
- Returns simplified finance entry objects

The Kotlin backend (`NotionMcpClient`) spawns this server as a subprocess and communicates with it over stdin/stdout.

---

## 📦 Tech Stack Details

### Frontend
- **Compose for Web 1.7.0** - Modern declarative UI framework
- **Ktor Client 3.0.0** - HTTP client for API communication
- **Kotlinx Coroutines** - Asynchronous programming
- **Kotlinx Serialization** - JSON serialization

### Backend
- **Ktor 3.0.0** - Asynchronous web framework
- **Exposed 0.50.1** - Type-safe SQL framework
- **PostgreSQL 16** - Relational database
- **Flyway 10.17.0** - Database migrations
- **Koin 3.5.6** - Dependency injection
- **HikariCP 5.1.0** - Connection pooling

### AI Integration
- **Gemini 2.5 Flash** - Google's multimodal AI model
- Fallback support for multiple Gemini model versions

---

## 🚢 Deployment

### Docker Deployment

```bash
# Build Docker image
docker build -t kmp-ai-app:latest .

# Run container
docker run -d \
  -p 8081:8081 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/kmp \
  -e GEMINI_API_KEY=your_key \
  -e PORT=8081 \
  kmp-ai-app:latest
```

### Production Considerations

- Set `GEMINI_API_KEY` as a secure environment variable
- Use a managed PostgreSQL database (AWS RDS, Google Cloud SQL, etc.)
- Configure CORS for your production domain
- Set up proper logging and monitoring
- Use HTTPS in production
- Configure rate limiting with Redis

---

## 📝 License

MIT License - feel free to use this project for learning or production.

---

## 🎓 Learning Resources

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Compose for Web](https://compose-web.pages.dev/)
- [Ktor Documentation](https://ktor.io/)
- [Gemini API Documentation](https://ai.google.dev/docs)

---

## 💡 Tips & Best Practices

1. **Development Workflow**: Always run server and web client in separate terminals
2. **API Key Security**: Never commit your `GEMINI_API_KEY` to version control
3. **Database Backups**: Regularly backup your PostgreSQL database in production
4. **Error Handling**: Check server logs for detailed error messages
5. **Hot Reload**: The web dev server supports hot reload - edit code and see changes instantly

---

## 🎉 You're All Set!

Your KMP AI Chat application is now ready to use. Start chatting, explore the tools, and enjoy building with Kotlin Multiplatform!

**Quick Start Summary:**
1. ✅ `docker compose up -d` (Terminal 1)
2. ✅ `./gradlew :server:flywayMigrate`
3. ✅ `export GEMINI_API_KEY='your-key' && ./gradlew :server:run` (Terminal 2)
4. ✅ `./gradlew :web:jsBrowserDevelopmentRun` (Terminal 3)
5. ✅ Open http://localhost:8080 and start chatting!

**For Notion Finance:**
1. ✅ `cd mcp/notion-finance-server && npm install && npm run build`
2. ✅ `export NOTION_API_TOKEN='your-token'`
3. ✅ Restart backend server
4. ✅ Click "💰 Notion Finance" tab in the UI

**📖 For detailed step-by-step instructions, see [START_APP.md](START_APP.md)**
