import { Server, Socket } from 'socket.io';
import { v4 as uuidv4 } from 'uuid';
import { getProject } from '../services/projects.js';
import { executeCommand, startInteractiveSession, ClaudeExecution } from '../services/claude.js';
import type {
  SendCommandPayload,
  SendCommandAck,
  CancelCommandPayload,
  CancelCommandAck,
  OutputChunk,
  CommandComplete,
  CommandError,
  ExecutionContext,
  PtyInputPayload,
  PtyResizePayload,
  PtyInputAck,
  PtyResizeAck,
  StartPtyPayload,
  StartPtyAck,
} from '../types/index.js';

// Configuration
const MAX_COMMAND_LENGTH = 10000;
const MAX_CONCURRENT_EXECUTIONS = 20;
const CANCELLED_EXECUTION_TTL_MS = 60000; // 1 minute

// Track active executions
const activeExecutions = new Map<string, { execution: ClaudeExecution; context: ExecutionContext }>();

// Track cancelled executions with timestamp for cleanup (F14 fix)
const cancelledExecutions = new Map<string, number>();

// Validate command input (F1 fix)
function validateCommand(command: string): { valid: boolean; error?: string } {
  if (!command || typeof command !== 'string') {
    return { valid: false, error: 'Command is required' };
  }

  if (command.length > MAX_COMMAND_LENGTH) {
    return { valid: false, error: `Command exceeds maximum length of ${MAX_COMMAND_LENGTH} characters` };
  }

  // Basic validation - command should be printable text
  if (!/^[\x20-\x7E\n\r\t]+$/.test(command)) {
    return { valid: false, error: 'Command contains invalid characters' };
  }

  return { valid: true };
}

// Cleanup old cancelled executions (F14 fix)
function cleanupCancelledExecutions(): void {
  const now = Date.now();
  for (const [id, timestamp] of cancelledExecutions) {
    if (now - timestamp > CANCELLED_EXECUTION_TTL_MS) {
      cancelledExecutions.delete(id);
    }
  }
}

// Run cleanup periodically
setInterval(cleanupCancelledExecutions, CANCELLED_EXECUTION_TTL_MS);

export function registerSocketHandlers(io: Server): void {
  io.on('connection', (socket: Socket) => {

    // Send connection status
    socket.emit('connection-status', {
      connected: true,
      serverTime: Date.now(),
    });

    // Handle send-command
    socket.on(
      'send-command',
      (payload: SendCommandPayload, ack: (response: SendCommandAck) => void) => {
        const { projectId, sessionId, command, correlationId } = payload;
        const executionId = uuidv4();

        // Rate limiting (F5 fix)
        if (activeExecutions.size >= MAX_CONCURRENT_EXECUTIONS) {
          ack({
            success: false,
            executionId,
            correlationId,
            error: `Too many concurrent executions (max: ${MAX_CONCURRENT_EXECUTIONS})`,
          });
          return;
        }

        // Validate command (F1 fix)
        const validation = validateCommand(command);
        if (!validation.valid) {
          ack({
            success: false,
            executionId,
            correlationId,
            error: validation.error,
          });
          return;
        }

        // Check if already cancelled (race condition handling)
        if (cancelledExecutions.has(correlationId)) {
          cancelledExecutions.delete(correlationId);
          ack({
            success: false,
            executionId,
            correlationId,
            error: 'Command was cancelled before execution',
          });
          return;
        }

        // Validate project
        const project = getProject(projectId);
        if (!project) {
          ack({
            success: false,
            executionId,
            correlationId,
            error: 'Project not found',
          });
          return;
        }

        // Create execution context
        const context: ExecutionContext = {
          executionId,
          correlationId,
          projectId,
          projectPath: project.path,
          sessionId,
          command,
          startTime: Date.now(),
          cancelled: false,
        };

        // Start execution
        const execution = executeCommand(project.path, sessionId, command);

        // Store in active executions
        activeExecutions.set(executionId, { execution, context });

        // Acknowledge command received
        ack({
          success: true,
          executionId,
          correlationId,
        });

        // Handle output data
        execution.on('data', (chunk: string, type: 'stdout' | 'stderr') => {
          const outputChunk: OutputChunk = {
            executionId,
            correlationId,
            type,
            content: chunk,
            timestamp: Date.now(),
          };
          socket.emit('output-chunk', outputChunk);
        });

        // Handle errors
        execution.on('error', (message: string) => {
          const cmdError: CommandError = {
            executionId,
            correlationId,
            error: message,
            code: context.cancelled ? 'CANCELLED' : message.includes('timeout') ? 'TIMEOUT' : 'PROCESS_FAILED',
          };
          socket.emit('command-error', cmdError);
          activeExecutions.delete(executionId);
        });

        // Handle completion
        execution.on('complete', (exitCode: number, newSessionId?: string) => {
          const cmdComplete: CommandComplete = {
            executionId,
            correlationId,
            exitCode,
            sessionId: sessionId || newSessionId || '',
            duration: Date.now() - context.startTime,
          };
          socket.emit('command-complete', cmdComplete);
          activeExecutions.delete(executionId);
        });
      }
    );

    // Handle cancel-command
    socket.on(
      'cancel-command',
      (payload: CancelCommandPayload, ack: (response: CancelCommandAck) => void) => {
        const { executionId } = payload;

        const active = activeExecutions.get(executionId);
        if (!active) {
          // Might be already completed or not started yet
          // Mark as cancelled for race condition (with timestamp for cleanup)
          cancelledExecutions.set(executionId, Date.now());
          ack({ success: true }); // Already done or will be cancelled
          return;
        }

        // Mark context as cancelled
        active.context.cancelled = true;

        // Kill the process
        active.execution.kill();

        // Emit cancellation error
        const cmdError: CommandError = {
          executionId,
          correlationId: active.context.correlationId,
          error: 'Command cancelled by user',
          code: 'CANCELLED',
        };
        socket.emit('command-error', cmdError);

        // Cleanup
        activeExecutions.delete(executionId);

        ack({ success: true });
      }
    );

    // Handle pty-input - forward keystrokes to PTY
    socket.on(
      'pty-input',
      (payload: PtyInputPayload, ack: (response: PtyInputAck) => void) => {
        const { executionId, data } = payload;

        const active = activeExecutions.get(executionId);
        if (!active) {
          ack({ success: false, error: 'Execution not found' });
          return;
        }

        try {
          active.execution.write(data);
          ack({ success: true });
        } catch (e) {
          ack({ success: false, error: (e as Error).message });
        }
      }
    );

    // Handle pty-resize - resize terminal
    socket.on(
      'pty-resize',
      (payload: PtyResizePayload, ack: (response: PtyResizeAck) => void) => {
        const { executionId, cols, rows } = payload;

        const active = activeExecutions.get(executionId);
        if (!active) {
          ack({ success: false, error: 'Execution not found' });
          return;
        }

        try {
          active.execution.resize(cols, rows);
          ack({ success: true });
        } catch (e) {
          ack({ success: false, error: (e as Error).message });
        }
      }
    );

    // Handle start-pty - start interactive Claude CLI session
    socket.on(
      'start-pty',
      (payload: StartPtyPayload, ack: (response: StartPtyAck) => void) => {
        const { projectId, sessionId, cols, rows } = payload;
        const executionId = uuidv4();

        // Validate project
        const project = getProject(projectId);
        if (!project) {
          ack({
            success: false,
            executionId: '',
            error: 'Project not found',
          });
          return;
        }

        // Check concurrent limit
        if (activeExecutions.size >= MAX_CONCURRENT_EXECUTIONS) {
          ack({
            success: false,
            executionId: '',
            error: 'Too many concurrent executions',
          });
          return;
        }

        // Start interactive session
        const execution = startInteractiveSession(project.path, sessionId, cols, rows);

        // Create execution context
        const context: ExecutionContext = {
          executionId,
          correlationId: executionId, // Use same ID for interactive sessions
          projectId,
          projectPath: project.path,
          sessionId,
          command: '', // No command for interactive mode
          startTime: Date.now(),
          cancelled: false,
        };

        // Store in active executions
        activeExecutions.set(executionId, { execution, context });

        // Acknowledge
        ack({
          success: true,
          executionId,
        });

        // Handle output data
        execution.on('data', (chunk: string, type: 'stdout' | 'stderr') => {
          const outputChunk: OutputChunk = {
            executionId,
            correlationId: executionId,
            type,
            content: chunk,
            timestamp: Date.now(),
          };
          socket.emit('output-chunk', outputChunk);
        });

        // Handle errors
        execution.on('error', (message: string) => {
          const cmdError: CommandError = {
            executionId,
            correlationId: executionId,
            error: message,
            code: 'PROCESS_FAILED',
          };
          socket.emit('command-error', cmdError);
          activeExecutions.delete(executionId);
        });

        // Handle completion (user exits Claude CLI)
        execution.on('complete', (exitCode: number) => {
          const cmdComplete: CommandComplete = {
            executionId,
            correlationId: executionId,
            exitCode,
            sessionId: sessionId || '',
            duration: Date.now() - context.startTime,
          };
          socket.emit('command-complete', cmdComplete);
          activeExecutions.delete(executionId);
        });
      }
    );

    // Handle disconnect - cleanup all executions for this socket
    socket.on('disconnect', () => {
      // Kill all active executions that belong to this socket
      // We track this by checking socket.id on each execution
      const toDelete: string[] = [];
      for (const [executionId, { execution }] of activeExecutions) {
        // Kill execution and mark for deletion
        execution.kill();
        toDelete.push(executionId);
      }
      // Note: In a multi-user scenario, we'd need to track socket ownership
      // For now, we just kill all since it's single-user
      for (const id of toDelete) {
        activeExecutions.delete(id);
      }
    });
  });
}

// Cleanup function for graceful shutdown
export function cleanupAllExecutions(): void {
  for (const [executionId, { execution }] of activeExecutions) {
    execution.kill();
  }
  activeExecutions.clear();
  cancelledExecutions.clear();
}
