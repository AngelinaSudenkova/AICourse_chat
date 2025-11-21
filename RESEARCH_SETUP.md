# Research Feature Setup Guide

This guide explains how to set up and use the Research & Save feature.

## Prerequisites

1. **NewsAPI Key**: Get a free API key from https://newsapi.org/
2. **Node.js**: Required to build and run the MCP research server
3. **Backend Server**: Must be running on port 8081

## Step 1: Build the Research MCP Server

The research MCP server needs to be compiled from TypeScript:

```bash
cd mcp/research-server
npm install
npm run build
cd ../..
```

This creates the `dist/index.js` file that the backend will use.

## Step 2: Set Environment Variables

Add these to your `.env` file or export them:

```bash
# Required: NewsAPI key for searching news articles
export NEWS_API_KEY='your-newsapi-key-here'

# Optional: Custom research directory (defaults to server/data/research)
export RESEARCH_DIR='server/data/research'

# Optional: Custom MCP command (defaults to "node")
export MCP_RESEARCH_CMD='node'

# Optional: Custom MCP args (defaults to "mcp/research-server/dist/index.js")
export MCP_RESEARCH_ARGS='mcp/research-server/dist/index.js'
```

## Step 3: Create Research Directory

The research directory will be created automatically when you save your first research, but you can create it manually:

```bash
mkdir -p server/data/research
```

## Step 4: Start the Backend Server

Make sure your backend server is running:

```bash
./start-server.sh
```

The server will automatically:
- Start the Research MCP client
- Create the research directory if needed
- Make the research endpoints available

## Step 5: Using the Research Feature

### Option 1: Via Chat Command (Recommended)

1. Open the chat interface in your web app
2. Type: `/research <your query>`
   
   Example:
   ```
   /research AI regulation in Europe
   ```

3. The system will:
   - Search NewsAPI for relevant articles
   - Generate an AI summary using Gemini
   - Save the summary to `server/data/research/YYYY-MM-DD-query.md`
   - Display the summary in chat

### Option 2: Via API Endpoint

```bash
curl -X POST http://localhost:8081/api/research \
  -H "Content-Type: application/json" \
  -d '{"query": "AI regulation in Europe"}'
```

Response:
```json
{
  "query": "AI regulation in Europe",
  "summary": {
    "title": "AI Regulation in Europe: Key Developments",
    "summary": "Recent developments in AI regulation...",
    "keyPoints": ["Point 1", "Point 2", "Point 3"],
    "sources": ["url1", "url2"]
  },
  "savedPath": "server/data/research/2025-01-21-ai-regulation-in-europe.md"
}
```

## Step 6: View Research Log

### Via Web UI

1. Click the **"📚 Research Log"** tab in the navigation bar
2. You'll see a list of all saved research entries
3. Click any entry to view its full content

### Via API

```bash
# List all research entries
curl http://localhost:8081/api/research/log

# Get specific file content
curl http://localhost:8081/api/research/file/2025-01-21-ai-regulation-in-europe.md
```

## Troubleshooting

### "Research failed: MCP server error"
- Make sure the MCP server is built: `cd mcp/research-server && npm run build`
- Check that `NEWS_API_KEY` is set correctly
- Verify the MCP server path in `MCP_RESEARCH_ARGS`

### "No research entries yet"
- This is normal if you haven't created any research yet
- Try running `/research <query>` in chat first

### "Cannot connect to server"
- Make sure the backend server is running: `./start-server.sh`
- Check that port 8081 is not blocked

### Research directory not found
- The directory is created automatically on first save
- Or create it manually: `mkdir -p server/data/research`

## File Format

Research files are saved as Markdown with this structure:

```markdown
# Research Title

**Research Query:** your query

## Summary
Detailed summary paragraph...

## Key Points
- Point 1
- Point 2
- Point 3

## Sources
- [Source URL 1](url1)
- [Source URL 2](url2)

---
*Generated on [timestamp]*
```

## Architecture

The research pipeline chains three operations:

1. **`news.search_docs`** (MCP tool) → Searches NewsAPI.org
2. **AI Summarize** (GeminiClient) → Generates summary from articles
3. **`fs.save_to_file`** (MCP tool) → Saves to disk

All three are orchestrated by `ResearchPipeline` in the backend.

