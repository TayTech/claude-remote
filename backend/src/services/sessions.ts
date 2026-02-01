import { readFileSync, existsSync } from 'fs';
import { join } from 'path';
import { config } from '../config.js';
import type { SessionsIndex, Session, SessionEntry } from '../types/index.js';

function getPathHash(projectPath: string): string {
  return projectPath.replace(/\//g, '-');
}

function sessionEntryToSession(entry: SessionEntry): Session {
  return {
    sessionId: entry.sessionId,
    summary: entry.summary,
    firstPrompt: entry.firstPrompt,
    messageCount: entry.messageCount,
    created: entry.created,
    modified: entry.modified,
    gitBranch: entry.gitBranch,
  };
}

export function getSessionsForProject(projectPath: string): Session[] {
  const pathHash = getPathHash(projectPath);
  const indexPath = join(config.claudeProjectsPath, pathHash, 'sessions-index.json');

  if (!existsSync(indexPath)) {
    console.warn(`Sessions index not found: ${indexPath}`);
    return [];
  }

  try {
    const data = readFileSync(indexPath, 'utf-8');
    const index: SessionsIndex = JSON.parse(data);

    // Sort by modified descending (newest first)
    const sessions = index.entries
      .map(sessionEntryToSession)
      .sort((a, b) => new Date(b.modified).getTime() - new Date(a.modified).getTime());

    return sessions;
  } catch (error) {
    console.error(`Failed to read sessions index: ${indexPath}`, error);
    return [];
  }
}

export function refreshSessionsIndex(projectPath: string): Session[] {
  // Simply re-read the file - Claude CLI maintains this automatically
  return getSessionsForProject(projectPath);
}

export function getSession(projectPath: string, sessionId: string): Session | undefined {
  const sessions = getSessionsForProject(projectPath);
  return sessions.find((s) => s.sessionId === sessionId);
}

export interface HistoryMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

export function getSessionHistory(projectPath: string, sessionId: string): HistoryMessage[] {
  const pathHash = getPathHash(projectPath);
  const sessionFile = join(config.claudeProjectsPath, pathHash, `${sessionId}.jsonl`);

  if (!existsSync(sessionFile)) {
    console.warn(`Session file not found: ${sessionFile}`);
    return [];
  }

  try {
    const data = readFileSync(sessionFile, 'utf-8');
    const lines = data.split('\n').filter(line => line.trim());
    const messages: HistoryMessage[] = [];

    for (const line of lines) {
      try {
        const entry = JSON.parse(line);

        if (entry.type === 'user' && entry.message?.content) {
          messages.push({
            role: 'user',
            content: typeof entry.message.content === 'string'
              ? entry.message.content
              : JSON.stringify(entry.message.content),
            timestamp: entry.timestamp || ''
          });
        } else if (entry.type === 'assistant' && entry.message?.content) {
          // Assistant content is an array of content blocks
          const textContent = entry.message.content
            .filter((c: any) => c.type === 'text' && c.text)
            .map((c: any) => c.text)
            .join('\n');

          if (textContent) {
            messages.push({
              role: 'assistant',
              content: textContent,
              timestamp: entry.timestamp || ''
            });
          }
        }
      } catch (e) {
        // Skip invalid JSON lines
      }
    }

    return messages;
  } catch (error) {
    console.error(`Failed to read session file: ${sessionFile}`, error);
    return [];
  }
}
