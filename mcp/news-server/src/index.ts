#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const NEWS_API_KEY = process.env.NEWS_API_KEY || "";
const NEWS_API_BASE_URL = "https://newsapi.org/v2";

interface NewsArticle {
  source: string;
  author: string | null;
  title: string;
  description: string | null;
  url: string;
  publishedAt: string;
}

interface TopHeadlinesResult {
  articles: NewsArticle[];
  totalResults: number;
  country?: string;
  category?: string | null;
  fetchedAt: string;
}

interface SearchNewsResult {
  articles: NewsArticle[];
  totalResults: number;
  query: string;
  fetchedAt: string;
}

interface NewsApiResponse {
  status: string;
  totalResults: number;
  articles: Array<{
    source?: { name?: string };
    author?: string | null;
    title?: string;
    description?: string | null;
    url?: string;
    publishedAt?: string;
  }>;
}

async function getTopHeadlines(
  country?: string,
  category?: string,
  pageSize: number = 20
): Promise<TopHeadlinesResult> {
  if (!NEWS_API_KEY) {
    throw new Error("NEWS_API_KEY environment variable is not set");
  }

  const params = new URLSearchParams();
  if (country) {
    params.append("country", country);
  }
  if (category) {
    params.append("category", category);
  }
  params.append("pageSize", pageSize.toString());

  const url = `${NEWS_API_BASE_URL}/top-headlines?${params.toString()}`;

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

  const data = (await response.json()) as NewsApiResponse;

  const articles: NewsArticle[] = (data.articles || []).map((article) => ({
    source: article.source?.name || "Unknown",
    author: article.author || null,
    title: article.title || "",
    description: article.description || null,
    url: article.url || "",
    publishedAt: article.publishedAt || "",
  }));

  return {
    articles,
    totalResults: data.totalResults || articles.length,
    country: country || undefined,
    category: category || null,
    fetchedAt: new Date().toISOString(),
  };
}

async function searchNews(
  query: string,
  pageSize: number = 20,
  sortBy: string = "publishedAt"
): Promise<SearchNewsResult> {
  if (!NEWS_API_KEY) {
    throw new Error("NEWS_API_KEY environment variable is not set");
  }

  const params = new URLSearchParams();
  params.append("q", query);
  params.append("pageSize", pageSize.toString());
  params.append("sortBy", sortBy);

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

  const data = (await response.json()) as NewsApiResponse;

  const articles: NewsArticle[] = (data.articles || []).map((article) => ({
    source: article.source?.name || "Unknown",
    author: article.author || null,
    title: article.title || "",
    description: article.description || null,
    url: article.url || "",
    publishedAt: article.publishedAt || "",
  }));

  return {
    articles,
    totalResults: data.totalResults || articles.length,
    query,
    fetchedAt: new Date().toISOString(),
  };
}

async function main() {
  const server = new Server(
    {
      name: "news-server",
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
          name: "news.get_top_headlines",
          description: "Get top headlines from NewsAPI.org",
          inputSchema: {
            type: "object",
            properties: {
              country: {
                type: "string",
                description: "ISO 3166-1 alpha-2 country code (e.g., 'us', 'gb', 'pl')",
              },
              category: {
                type: "string",
                description: "Category (e.g., 'business', 'entertainment', 'general', 'health', 'science', 'sports', 'technology')",
              },
              pageSize: {
                type: "number",
                description: "Number of articles to return",
                default: 20,
              },
            },
          },
        },
        {
          name: "news.search",
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
                default: 20,
              },
              sortBy: {
                type: "string",
                description: "Sort order: 'publishedAt' or 'relevancy'",
                default: "publishedAt",
              },
            },
            required: ["query"],
          },
        },
      ],
    };
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    if (request.params.name === "news.get_top_headlines") {
      const args = request.params.arguments as any;
      const country = args?.country;
      const category = args?.category;
      const pageSize = args?.pageSize || 20;

      try {
        const result = await getTopHeadlines(country, category, pageSize);
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

    if (request.params.name === "news.search") {
      const args = request.params.arguments as any;
      const query = args?.query;
      const pageSize = args?.pageSize || 20;
      const sortBy = args?.sortBy || "publishedAt";

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
        const result = await searchNews(query, pageSize, sortBy);
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

  console.error("News MCP server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});

