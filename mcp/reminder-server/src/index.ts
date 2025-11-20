#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import * as fs from "fs";
import * as path from "path";

const REMINDERS_FILE = process.env.REMINDERS_FILE || "server/data/reminders.json";

interface Reminder {
  id: string;
  text: string;
  createdAt: number;
  dueDate?: number | null;
  completed: boolean;
  completedAt?: number | null;
}

function ensureFileExists(filePath: string): void {
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  if (!fs.existsSync(filePath)) {
    fs.writeFileSync(filePath, "[]", "utf-8");
  }
}

function loadReminders(): Reminder[] {
  ensureFileExists(REMINDERS_FILE);
  try {
    const content = fs.readFileSync(REMINDERS_FILE, "utf-8");
    if (content.trim() === "") {
      return [];
    }
    return JSON.parse(content);
  } catch (e: any) {
    console.error(`Error loading reminders: ${e.message}`);
    return [];
  }
}

function saveReminders(reminders: Reminder[]): void {
  ensureFileExists(REMINDERS_FILE);
  try {
    fs.writeFileSync(REMINDERS_FILE, JSON.stringify(reminders, null, 2), "utf-8");
  } catch (e: any) {
    console.error(`Error saving reminders: ${e.message}`);
    throw new Error(`Failed to save reminders: ${e.message}`);
  }
}

async function main() {
  const server = new Server(
    {
      name: "reminder-server",
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
          name: "reminder.add",
          description: "Add a new reminder",
          inputSchema: {
            type: "object",
            properties: {
              text: {
                type: "string",
                description: "Reminder text",
              },
              dueDate: {
                type: "number",
                description: "Due date timestamp (Unix milliseconds, optional)",
              },
            },
            required: ["text"],
          },
        },
        {
          name: "reminder.list",
          description: "List all reminders (optionally filter by pending)",
          inputSchema: {
            type: "object",
            properties: {
              onlyPending: {
                type: "boolean",
                description: "If true, only return pending (not completed) reminders",
                default: false,
              },
            },
          },
        },
        {
          name: "reminder.summary_now",
          description: "Get a summary of current reminders (pending count, overdue count, etc.)",
          inputSchema: {
            type: "object",
            properties: {},
          },
        },
      ],
    };
  });

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    if (request.params.name === "reminder.add") {
      const args = request.params.arguments as any;
      const text = args?.text;
      const dueDate = args?.dueDate;

      if (!text) {
        return {
          content: [
            {
              type: "text",
              text: JSON.stringify({
                error: "Text parameter is required",
              }),
            },
          ],
          isError: true,
        };
      }

      try {
        const reminders = loadReminders();
        const newReminder: Reminder = {
          id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
          text,
          createdAt: Date.now(),
          dueDate: dueDate || null,
          completed: false,
          completedAt: null,
        };
        reminders.push(newReminder);
        saveReminders(reminders);

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(newReminder),
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

    if (request.params.name === "reminder.list") {
      const args = request.params.arguments as any;
      const onlyPending = args?.onlyPending || false;

      try {
        let reminders = loadReminders();
        if (onlyPending) {
          reminders = reminders.filter((r) => !r.completed);
        }

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify({
                reminders,
                totalCount: reminders.length,
              }),
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

    if (request.params.name === "reminder.summary_now") {
      try {
        const reminders = loadReminders();
        const pending = reminders.filter((r) => !r.completed);
        const now = Date.now();
        const overdue = pending.filter(
          (r) => r.dueDate != null && r.dueDate < now
        );

        const summary = {
          pendingCount: pending.length,
          overdueCount: overdue.length,
          reminders: pending,
          aiSummary: null,
          generatedAt: now,
        };

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

  console.error("Reminder MCP server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});

