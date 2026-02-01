// Project Configuration
export interface Project {
  id: string;
  name: string;
  path: string;
  devServerPort?: number;
}

// Session from Claude's sessions-index.json
export interface SessionEntry {
  sessionId: string;
  fullPath: string;
  fileMtime: number;
  firstPrompt: string;
  summary: string;
  messageCount: number;
  created: string;
  modified: string;
  gitBranch: string;
  projectPath: string;
  isSidechain: boolean;
}

export interface SessionsIndex {
  version: number;
  originalPath: string;
  entries: SessionEntry[];
}

// API Response types
export interface Session {
  sessionId: string;
  summary: string;
  firstPrompt: string;
  messageCount: number;
  created: string;
  modified: string;
  gitBranch: string;
}

// Socket.io Event Payloads - Client → Server

export interface SendCommandPayload {
  projectId: string;
  sessionId: string | null; // null for new session
  command: string;
  correlationId: string;
}

export interface SendCommandAck {
  success: boolean;
  executionId: string;
  correlationId: string;
  error?: string;
}

export interface CancelCommandPayload {
  executionId: string;
}

export interface CancelCommandAck {
  success: boolean;
  error?: string;
}

// Socket.io Event Payloads - Server → Client

export interface OutputChunk {
  executionId: string;
  correlationId: string;
  type: 'stdout' | 'stderr' | 'system';
  content: string;
  timestamp: number;
}

export interface CommandComplete {
  executionId: string;
  correlationId: string;
  exitCode: number;
  sessionId: string;
  duration: number;
}

export type CommandErrorCode = 'PROCESS_FAILED' | 'TIMEOUT' | 'CANCELLED' | 'SESSION_NOT_FOUND';

export interface CommandError {
  executionId: string;
  correlationId: string;
  error: string;
  code: CommandErrorCode;
}

export interface ConnectionStatus {
  connected: boolean;
  serverTime: number;
}

// Health check response
export interface HealthResponse {
  status: 'ok';
  uptime: number;
  claudeCliInstalled: boolean;
}

// Internal types
export interface ExecutionContext {
  executionId: string;
  correlationId: string;
  projectId: string;
  projectPath: string;
  sessionId: string | null;
  command: string;
  startTime: number;
  cancelled: boolean;
}

// PTY Input/Output payloads
export interface PtyInputPayload {
  executionId: string;
  data: string;
}

export interface PtyResizePayload {
  executionId: string;
  cols: number;
  rows: number;
}

export interface PtyInputAck {
  success: boolean;
  error?: string;
}

export interface PtyResizeAck {
  success: boolean;
  error?: string;
}

// Start interactive PTY session
export interface StartPtyPayload {
  projectId: string;
  sessionId: string | null;
  cols: number;
  rows: number;
}

export interface StartPtyAck {
  success: boolean;
  executionId: string;
  error?: string;
}
