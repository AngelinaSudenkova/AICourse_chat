#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const NOTION_API_TOKEN = process.env.NOTION_API_TOKEN || "";
const NOTION_API_TOKEN_STUDY = process.env.NOTION_API_TOKEN_STUDY || "";
const NOTION_FINANCE_DATABASE_ID = process.env.NOTION_FINANCE_DATABASE_ID || "1df789a3fe3681d7a156fb2e2d7bbef0";
const NOTION_STUDY_PARENT_PAGE_ID = process.env.NOTION_STUDY_PARENT_PAGE_ID || "";
const NOTION_VERSION = "2022-06-28";

/**
 * Converts a Notion page ID from URL format (no dashes) to API format (with dashes)
 * Example: "2b5789a3fe3680aa998ad2ff95072e9a" -> "2b5789a3-fe36-80aa-998a-d2ff95072e9a"
 */
function formatNotionPageId(pageId: string): string {
  // Remove any existing dashes
  const cleanId = pageId.replace(/-/g, "");
  // Format as UUID: 8-4-4-4-12
  if (cleanId.length === 32) {
    return `${cleanId.substring(0, 8)}-${cleanId.substring(8, 12)}-${cleanId.substring(12, 16)}-${cleanId.substring(16, 20)}-${cleanId.substring(20, 32)}`;
  }
  // If already formatted or invalid, return as-is
  return pageId;
}

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
        {
          name: "notion.create_study_note",
          description: "Create a structured study note page in Notion",
          inputSchema: {
            type: "object",
            properties: {
              title: {
                type: "string",
                description: "Title of the study note",
              },
              topic: {
                type: "string",
                description: "The topic being studied",
              },
              keyPoints: {
                type: "array",
                items: { type: "string" },
                description: "List of key points to remember",
              },
              explanation: {
                type: "string",
                description: "Detailed explanation of the topic",
              },
              resources: {
                type: "array",
                items: { type: "string" },
                description: "List of resource URLs (Wikipedia, YouTube, etc.)",
              },
              parentPageId: {
                type: "string",
                description: "Optional parent page ID (defaults to root)",
              },
            },
            required: ["title", "topic", "keyPoints", "explanation", "resources"],
          },
        },
      ],
    };
  });
  
async function createStudyNote(
  title: string,
  topic: string,
  keyPoints: string[],
  explanation: string,
  resources: string[],
  parentPageId?: string
): Promise<{ pageId: string; url: string }> {
  const url = "https://api.notion.com/v1/pages";
  
  // Build page content using Notion blocks
  const children: any[] = [
    {
      object: "block",
      type: "heading_1",
      heading_1: {
        rich_text: [{ type: "text", text: { content: title } }],
      },
    },
    {
      object: "block",
      type: "paragraph",
      paragraph: {
        rich_text: [{ type: "text", text: { content: `Topic: ${topic}` } }],
      },
    },
    {
      object: "block",
      type: "heading_2",
      heading_2: {
        rich_text: [{ type: "text", text: { content: "Key Points" } }],
      },
    },
  ];
  
  // Add key points as bullet list
  keyPoints.forEach((point) => {
    children.push({
      object: "block",
      type: "bulleted_list_item",
      bulleted_list_item: {
        rich_text: [{ type: "text", text: { content: point } }],
      },
    });
  });
  
  // Add explanation
  children.push({
    object: "block",
    type: "heading_2",
    heading_2: {
      rich_text: [{ type: "text", text: { content: "Explanation" } }],
    },
  });
  
  // Split explanation into paragraphs if it's long
  const explanationLines = explanation.split("\n\n");
  explanationLines.forEach((line) => {
    if (line.trim().length > 0) {
      children.push({
        object: "block",
        type: "paragraph",
        paragraph: {
          rich_text: [{ type: "text", text: { content: line.trim() } }],
        },
      });
    }
  });
  
  // Add resources
  if (resources.length > 0) {
    children.push({
      object: "block",
      type: "heading_2",
      heading_2: {
        rich_text: [{ type: "text", text: { content: "Resources" } }],
      },
    });
    
    resources.forEach((resource) => {
      children.push({
        object: "block",
        type: "paragraph",
        paragraph: {
          rich_text: [
            {
              type: "text",
              text: { content: resource },
              annotations: { underline: true },
              href: resource,
            },
          ],
        },
      });
    });
  }
  
  // Use provided parentPageId, or fall back to environment variable, or workspace root
  const finalParentPageId = parentPageId || NOTION_STUDY_PARENT_PAGE_ID;
  
  const body: any = {
    parent: finalParentPageId
      ? { page_id: formatNotionPageId(finalParentPageId) }
      : { type: "workspace", workspace: true },
    properties: {
      title: {
        title: [{ type: "text", text: { content: title } }],
      },
    },
    children: children,
  };
  
  // Use study token if available, otherwise fall back to regular token
  const token = NOTION_API_TOKEN_STUDY || NOTION_API_TOKEN;
  
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
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
  const pageId = data.id;
  const pageUrl = data.url || `https://www.notion.so/${pageId.replace(/-/g, "")}`;
  
  return { pageId, url: pageUrl };
}

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
    
    if (request.params.name === "notion.create_study_note") {
      const args = request.params.arguments as any;
      const title = args?.title;
      const topic = args?.topic;
      const keyPoints = args?.keyPoints || [];
      const explanation = args?.explanation || "";
      const resources = args?.resources || [];
      const parentPageId = args?.parentPageId;
      
      if (!title || !topic) {
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify({
                error: "Title and topic are required",
              }),
            },
          ],
          isError: true,
        };
      }
      
      try {
        const result = await createStudyNote(
          title,
          topic,
          keyPoints,
          explanation,
          resources,
          parentPageId
        );
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

