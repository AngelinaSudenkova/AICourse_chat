#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

interface WikipediaSummary {
  title: string;
  url: string;
  summary: string;
}

interface WikipediaApiResponse {
  title?: string;
  extract?: string;
  content_urls?: {
    desktop?: {
      page?: string;
    };
  };
}

async function getWikipediaSummary(topic: string): Promise<WikipediaSummary> {
  // Use Wikipedia API to get summary
  // API: https://en.wikipedia.org/api/rest_v1/page/summary/{title}
  const encodedTopic = encodeURIComponent(topic);
  const url = `https://en.wikipedia.org/api/rest_v1/page/summary/${encodedTopic}`;
  
  try {
    const response = await fetch(url);
    
    if (!response.ok) {
      // Try searching for the topic if direct page doesn't exist
      const searchUrl = `https://en.wikipedia.org/api/rest_v1/page/summary/${encodedTopic}?redirect=true`;
      const searchResponse = await fetch(searchUrl);
      
      if (!searchResponse.ok) {
        throw new Error(`Wikipedia API error: ${response.status} ${response.statusText}`);
      }
      
      const data = await searchResponse.json() as WikipediaApiResponse;
      return {
        title: data.title || topic,
        url: data.content_urls?.desktop?.page || `https://en.wikipedia.org/wiki/${encodedTopic}`,
        summary: data.extract || `No summary available for "${topic}".`
      };
    }
    
    const data = await response.json() as WikipediaApiResponse;
    return {
      title: data.title || topic,
      url: data.content_urls?.desktop?.page || `https://en.wikipedia.org/wiki/${encodedTopic}`,
      summary: data.extract || `No summary available for "${topic}".`
    };
  } catch (error: any) {
    throw new Error(`Failed to fetch Wikipedia summary: ${error.message}`);
  }
}

const server = new Server(
  {
    name: "wikipedia-server",
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
        name: "wikipedia.summary",
        description: "Get a Wikipedia summary for a given topic",
        inputSchema: {
          type: "object",
          properties: {
            topic: {
              type: "string",
              description: "The topic to search for (e.g., 'quantum computing')",
            },
          },
          required: ["topic"],
        },
      },
    ],
  };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  if (name === "wikipedia.summary") {
    const topic = (args as any)?.topic;
    if (!topic || typeof topic !== "string") {
      throw new Error("Topic parameter is required and must be a string");
    }

    try {
      const summary = await getWikipediaSummary(topic);
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(summary),
          },
        ],
      };
    } catch (error: any) {
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify({
              error: error.message,
            }),
          },
        ],
        isError: true,
      };
    }
  }

  throw new Error(`Unknown tool: ${name}`);
});

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("Wikipedia MCP server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});

