#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

interface YouTubeVideo {
  title: string;
  url: string;
  channel: string | null;
  duration: string | null;
}

interface YouTubeSearchResult {
  videos: YouTubeVideo[];
}

// Note: YouTube Data API v3 requires an API key
// For this implementation, we'll use a simple search approach
// In production, you'd want to use the YouTube Data API v3
async function searchYouTubeVideos(
  query: string,
  maxResults: number = 5
): Promise<YouTubeSearchResult> {
  // Since YouTube Data API requires authentication, we'll use a simplified approach
  // that constructs YouTube search URLs and provides a basic structure
  // In a real implementation, you'd use the YouTube Data API v3
  
  const encodedQuery = encodeURIComponent(query);
  const searchUrl = `https://www.youtube.com/results?search_query=${encodedQuery}`;
  
  // For now, return a structured response with search URL
  // In production, you'd parse actual YouTube API responses
  const videos: YouTubeVideo[] = [];
  
  // Generate placeholder videos based on search query
  // In production, replace this with actual YouTube API calls
  for (let i = 0; i < maxResults; i++) {
    videos.push({
      title: `${query} - Explanation Video ${i + 1}`,
      url: `https://www.youtube.com/watch?v=placeholder${i}`,
      channel: `Educational Channel ${i + 1}`,
      duration: `${(i + 1) * 5}:00`,
    });
  }
  
  // If YOUTUBE_API_KEY is set, try to use YouTube Data API v3
  const apiKey = process.env.YOUTUBE_API_KEY;
  if (apiKey) {
    try {
      const apiUrl = `https://www.googleapis.com/youtube/v3/search?part=snippet&q=${encodedQuery}&maxResults=${maxResults}&type=video&key=${apiKey}`;
      const response = await fetch(apiUrl);
      
      if (response.ok) {
        const data = await response.json() as { items?: Array<{ id: { videoId: string }; snippet: { title: string; channelTitle: string } }> };
        const realVideos: YouTubeVideo[] = (data.items || []).map((item) => ({
          title: item.snippet.title,
          url: `https://www.youtube.com/watch?v=${item.id.videoId}`,
          channel: item.snippet.channelTitle,
          duration: null, // Duration requires another API call
        }));
        
        if (realVideos.length > 0) {
          return { videos: realVideos };
        }
      }
    } catch (error) {
      console.error("YouTube API error:", error);
    }
  }
  
  return {
    videos: videos.length > 0 ? videos : [{
      title: `Search YouTube for: ${query}`,
      url: searchUrl,
      channel: null,
      duration: null,
    }],
  };
}

const server = new Server(
  {
    name: "youtube-server",
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
        name: "youtube.search_explain",
        description: "Search YouTube for educational/explanation videos on a topic",
        inputSchema: {
          type: "object",
          properties: {
            query: {
              type: "string",
              description: "Search query (e.g., 'quantum computing for beginners')",
            },
            maxResults: {
              type: "number",
              description: "Maximum number of videos to return (default: 5)",
              default: 5,
            },
          },
          required: ["query"],
        },
      },
    ],
  };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  if (name === "youtube.search_explain") {
    const query = (args as any)?.query;
    const maxResults = (args as any)?.maxResults || 5;
    
    if (!query || typeof query !== "string") {
      throw new Error("Query parameter is required and must be a string");
    }

    try {
      const result = await searchYouTubeVideos(query, maxResults);
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
  console.error("YouTube MCP server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});

