#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import * as fs from "fs";
import * as path from "path";

const NEWS_API_KEY = process.env.NEWS_API_KEY || "";
const NEWS_API_BASE_URL = "https://newsapi.org/v2";
const RESEARCH_DIR = process.env.RESEARCH_DIR || "server/data/research";

interface NewsArticle {
  source: string;
  title: string;
  description: string | null;
  url: string;
  publishedAt: string;
}

interface NewsSearchResult {
  query: string;
  totalResults: number;
  articles: NewsArticle[];
  fetchedAt: string;
}

interface SummarizeRequest {
  query: string;
  articles: NewsArticle[];
}

interface ResearchSummary {
  title: string;
  summary: string;
  keyPoints: string[];
  sources: string[];
}

interface SaveFileRequest {
  filename: string;
  content: string;
}

interface SaveFileResult {
  path: string;
  ok: boolean;
}

// Tool 1: news.search_docs
async function searchDocs(query: string, pageSize: number = 10): Promise<NewsSearchResult> {
  if (!NEWS_API_KEY) {
    throw new Error("NEWS_API_KEY environment variable is not set");
  }

  const params = new URLSearchParams();
  params.append("q", query);
  params.append("pageSize", pageSize.toString());
  params.append("sortBy", "publishedAt");

  const url = `${NEWS_API_BASE_URL}/everything?${params.toString()}`;

  const response = await fetch(url, {
    method: "GET",
    headers: {
      "X-Api-Key": NEWS_API_KEY,
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`NewsAPI error: ${response.status} ${errorText}`);
  }

  const data = (await response.json()) as {
    status: string;
    totalResults: number;
    articles: Array<{
      source?: { name?: string };
      title?: string;
      description?: string | null;
      url?: string;
      publishedAt?: string;
    }>;
  };

  const articles: NewsArticle[] = (data.articles || []).map((article) => ({
    source: article.source?.name || "Unknown",
    title: article.title || "",
    description: article.description || null,
    url: article.url || "",
    publishedAt: article.publishedAt || "",
  }));

  return {
    query,
    totalResults: data.totalResults || articles.length,
    articles,
    fetchedAt: new Date().toISOString(),
  };
}

// Tool 2: ai.summarize
// Note: This tool will be called from the server, which has access to GeminiClient
// For now, we'll return a placeholder structure. The actual AI call will happen in the Ktor client.
async function summarizeResearch(request: SummarizeRequest): Promise<ResearchSummary> {
  // This is a placeholder - the actual summarization will be done in the Ktor backend
  // using GeminiClient. This MCP tool just defines the interface.
  throw new Error("ai.summarize should be called from the Ktor backend, not the MCP server");
}

// Tool 3: fs.save_to_file
async function saveToFile(filename: string, content: string): Promise<SaveFileResult> {
  // Ensure directory exists
  if (!fs.existsSync(RESEARCH_DIR)) {
    fs.mkdirSync(RESEARCH_DIR, { recursive: true });
  }

  const filePath = path.join(RESEARCH_DIR, filename);
  
  try {
    fs.writeFileSync(filePath, content, "utf-8");
    return {
      path: filePath,
      ok: true,
    };
  } catch (error: any) {
    throw new Error(`Failed to save file: ${error.message}`);
  }
}

async function main() {
  const server = new Server(
    {
      name: "research-server",
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
          name: "news.search_docs",
          description: "Search news articles from NewsAPI.org",
          inputSchema: {
            type: "object",
            properties: {
              query: {
                type: "string",
                description: "Search query",
              },
              pageSize: {
                type: "number",
                description: "Number of articles to return",
                default: 10,
              },
            },
            required: ["query"],
          },
        },
        {
          name: "fs.save_to_file",
          description: "Save content to a file in the research directory",
          inputSchema: {
            type: "object",
            properties: {
              filename: {
                type: "string",
                description: "Filename (e.g., '2025-11-21-ai-regulation-europe.md')",
              },
              content: {
                type: "string",
                description: "File content to save",
              },
            },
            required: ["filename", "content"],
          },
        },
      ],
    };
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    if (request.params.name === "news.search_docs") {
      const args = request.params.arguments as any;
      const query = args?.query;
      const pageSize = args?.pageSize || 10;

      if (!query) {
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify({
                error: "Query parameter is required",
              }),
            },
          ],
          isError: true,
        };
      }

      try {
        const result = await searchDocs(query, pageSize);
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

    if (request.params.name === "fs.save_to_file") {
      const args = request.params.arguments as any;
      const filename = args?.filename;
      const content = args?.content;

      if (!filename || !content) {
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify({
                error: "Filename and content parameters are required",
              }),
            },
          ],
          isError: true,
        };
      }

      try {
        const result = await saveToFile(filename, content);
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

  console.error("Research MCP server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});

