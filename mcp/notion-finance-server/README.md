# Notion Finance MCP Server

This is a Model Context Protocol (MCP) server that provides access to Notion finance database entries.

## Setup

1. Install dependencies:
```bash
cd mcp/notion-finance-server
npm install
```

2. Build the TypeScript code:
```bash
npm run build
```

3. Set environment variables:
- `NOTION_API_TOKEN`: Your Notion integration token
- `NOTION_FINANCE_DATABASE_ID`: The database ID (defaults to `1df789a3fe3681d7a156fb2e2d7bbef0`)

## Running

The server communicates via stdio (standard input/output) using JSON-RPC 2.0 protocol.

To run manually:
```bash
NOTION_API_TOKEN=your_token node dist/index.js
```

The Ktor backend will automatically start this server as a subprocess when needed.

## Configuration

The backend expects the MCP server to be available at:
- Command: `node` (or set via `MCP_NOTION_CMD` environment variable)
- Args: `mcp/notion-finance-server/dist/index.js` (or set via `MCP_NOTION_ARGS` environment variable)

Make sure to set `NOTION_API_TOKEN` in your environment before running the backend.

## Tools

### `notion.finance_get_entries`

Retrieves finance entries from the Notion database.

**Parameters:**
- `databaseId` (optional): Database ID (defaults to env var)
- `fromDate` (optional): Start date in ISO format (e.g., "2025-09-01")
- `toDate` (optional): End date in ISO format (e.g., "2025-09-30")
- `limit` (optional): Maximum number of entries (default: 50)

**Returns:**
```json
{
  "entries": [
    {
      "id": "page-id",
      "title": "Expense name",
      "amount": 450.0,
      "date": "2025-09-26",
      "categoryIds": ["..."],
      "url": "https://www.notion.so/..."
    }
  ],
  "totalCount": 10,
  "databaseId": "1df789a3fe3681d7a156fb2e2d7bbef0"
}
```

