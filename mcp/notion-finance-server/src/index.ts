#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const NOTION_API_TOKEN = process.env.NOTION_API_TOKEN || "";
const NOTION_FINANCE_DATABASE_ID = process.env.NOTION_FINANCE_DATABASE_ID || "1df789a3fe3681d7a156fb2e2d7bbef0";
const NOTION_VERSION = "2022-06-28";

interface FinanceEntry {
  id: string;
  title: string;
  amount: number;
  date: string;
  categoryIds: string[];
  url: string;
}

interface FinanceEntriesResult {
  entries: FinanceEntry[];
  totalCount: number;
  databaseId: string;
}

async function queryNotionDatabase(
  databaseId: string,
  fromDate?: string,
  toDate?: string,
  limit: number = 50
): Promise<FinanceEntriesResult> {
  const url = `https://api.notion.com/v1/databases/${databaseId}/query`;
  
  const filters: any = {};
  
  if (fromDate || toDate) {
    filters.and = [];
    if (fromDate) {
      filters.and.push({
        property: "Date",
        date: {
          on_or_after: fromDate,
        },
      });
    }
    if (toDate) {
      filters.and.push({
        property: "Date",
        date: {
          on_or_before: toDate,
        },
      });
    }
  }
  
  const body: any = {
    page_size: limit,
  };
  
  if (Object.keys(filters).length > 0) {
    body.filter = filters;
  }
  
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${NOTION_API_TOKEN}`,
      "Notion-Version": NOTION_VERSION,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Notion API error: ${response.status} ${errorText}`);
  }
  
  const data = await response.json();
  const results = data.results || [];
  
  const entries: FinanceEntry[] = results.map((page: any) => {
    const props = page.properties || {};
    
    // Extract title from "Expense" property
    const expenseProp = props.Expense || props.expense;
    let title = "";
    if (expenseProp?.title && expenseProp.title.length > 0) {
      title = expenseProp.title.map((t: any) => t.plain_text).join("");
    }
    
    // Extract amount from "Amount" property
    const amountProp = props.Amount || props.amount;
    const amount = amountProp?.number || 0;
    
    // Extract date from "Date" property
    const dateProp = props.Date || props.date;
    let date = "";
    if (dateProp?.date?.start) {
      date = dateProp.date.start;
    }
    
    // Extract category IDs from "Category" relation
    const categoryProp = props.Category || props.category;
    const categoryIds: string[] = [];
    if (categoryProp?.relation) {
      categoryIds.push(...categoryProp.relation.map((r: any) => r.id));
    }
    
    // Get page URL
    const url = page.url || `https://www.notion.so/${page.id.replace(/-/g, "")}`;
    
    return {
      id: page.id,
      title,
      amount,
      date,
      categoryIds,
      url,
    };
  });
  
  return {
    entries,
    totalCount: data.results?.length || 0,
    databaseId,
  };
}

async function main() {
  const server = new Server(
    {
      name: "notion-finance-server",
      version: "1.0.0",
    },
    {
      capabilities: {
        tools: {},
      },
    }
  );
  
  server.setRequestHandler(ListToolsRequestSchema, async () => {
    return {
      tools: [
        {
          name: "notion.finance_get_entries",
          description: "Get finance entries from Notion database",
          inputSchema: {
            type: "object",
            properties: {
              databaseId: {
                type: "string",
                description: "Database ID (optional, defaults to env var)",
              },
              fromDate: {
                type: "string",
                description: "Start date (ISO format, e.g. 2025-09-01)",
              },
              toDate: {
                type: "string",
                description: "End date (ISO format, e.g. 2025-09-30)",
              },
              limit: {
                type: "number",
                description: "Maximum number of entries to return",
                default: 50,
              },
            },
          },
        },
      ],
    };
  });
  
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    if (request.params.name === "notion.finance_get_entries") {
      const args = request.params.arguments as any;
      const databaseId = args?.databaseId || NOTION_FINANCE_DATABASE_ID;
      const fromDate = args?.fromDate;
      const toDate = args?.toDate;
      const limit = args?.limit || 50;
      
      try {
        const result = await queryNotionDatabase(databaseId, fromDate, toDate, limit);
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(result),
            },
          ],
        };
      } catch (error: any) {
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify({
                error: error.message || "Unknown error",
              }),
            },
          ],
          isError: true,
        };
      }
    }
    
    throw new Error(`Unknown tool: ${request.params.name}`);
  });
  
  const transport = new StdioServerTransport();
  await server.connect(transport);
  
  console.error("Notion Finance MCP server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});

