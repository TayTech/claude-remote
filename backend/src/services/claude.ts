import * as pty from 'node-pty';
import { EventEmitter } from 'events';
import { realpathSync } from 'fs';
import { config } from '../config.js';

export interface ClaudeExecutionEvents {
  data: (chunk: string, type: 'stdout' | 'stderr') => void;
  error: (message: string) => void;
  complete: (exitCode: number, newSessionId?: string) => void;
}

export interface ClaudeExecution extends EventEmitter {
  on<K extends keyof ClaudeExecutionEvents>(event: K, listener: ClaudeExecutionEvents[K]): this;
  emit<K extends keyof ClaudeExecutionEvents>(event: K, ...args: Parameters<ClaudeExecutionEvents[K]>): boolean;
  ptyProcess: pty.IPty | null;
  kill(): void;
  write(data: string): void;
  resize(cols: number, rows: number): void;
}

export function executeCommand(
  projectPath: string,
  sessionId: string | null,
  command: string,
  cols: number = 80,
  rows: number = 24
): ClaudeExecution {
  const emitter = new EventEmitter() as ClaudeExecution;
  emitter.ptyProcess = null;

  // Check if this is a slash command (like /status, /help)
  const isSlashCommand = command.startsWith('/');

  // Build Claude command arguments
  const claudeArgs: string[] = ['--verbose'];

  if (sessionId) {
    claudeArgs.push('-r', sessionId);
  }

  // For slash commands, use interactive mode (no -p)
  // For regular prompts, use -p for single prompt mode
  if (!isSlashCommand) {
    claudeArgs.push('-p', command);
  }

  // Get full paths and resolve symlinks
  let claudePath = process.env.CLAUDE_PATH || '/opt/homebrew/bin/claude';
  try {
    claudePath = realpathSync(claudePath);
  } catch {
    // Keep original path if realpath fails
  }


  let ptyProc: pty.IPty;

  try {
    // Use node-pty to create a real PTY - this makes Claude CLI think it's
    // connected to a real terminal with full ANSI support
    ptyProc = pty.spawn(claudePath, claudeArgs, {
      name: 'xterm-256color',
      cols,
      rows,
      cwd: projectPath,
      env: { ...process.env, TERM: 'xterm-256color', COLORTERM: 'truecolor' },
    });
  } catch (err) {
    console.error(`[Claude] Failed to spawn PTY:`, err);
    // Emit error asynchronously to allow caller to attach listeners
    setTimeout(() => {
      emitter.emit('error', `Failed to spawn PTY: ${(err as Error).message}`);
    }, 0);
    return emitter;
  }


  emitter.ptyProcess = ptyProc;

  let killed = false;
  let exited = false;
  let initialPromptSent = false;

  // Handle PTY data (combined stdout/stderr in PTY)
  ptyProc.onData((data: string) => {
    emitter.emit('data', data, 'stdout');

    // For slash commands in interactive mode, send the command once we see the prompt
    if (isSlashCommand && !initialPromptSent) {
      // Look for Claude's interactive prompt (usually ends with > or similar)
      if (data.includes('>') || data.includes('claude')) {
        initialPromptSent = true;
        setTimeout(() => {
          ptyProc.write(command + '\r');
        }, 100);
      }
    }
  });

  // Handle process exit
  ptyProc.onExit(({ exitCode, signal }) => {
    exited = true;
    if (!killed) {
      emitter.emit('complete', exitCode);
    }
  });

  // Command timeout
  const timeout = setTimeout(() => {
    if (!exited) {
      console.warn(`[Claude] Command timeout after ${config.commandTimeout}ms`);
      killed = true;
      ptyProc.kill();
      emitter.emit('error', 'Command timeout');
    }
  }, config.commandTimeout);

  ptyProc.onExit(() => {
    clearTimeout(timeout);
  });

  // Kill method for cancellation
  emitter.kill = () => {
    if (!exited) {
      killed = true;
      ptyProc.kill();
    }
  };

  // Write method for sending input to PTY
  emitter.write = (data: string) => {
    if (!exited && ptyProc) {
      ptyProc.write(data);
    }
  };

  // Resize method for terminal resize
  emitter.resize = (newCols: number, newRows: number) => {
    if (!exited && ptyProc) {
      ptyProc.resize(newCols, newRows);
    }
  };

  return emitter;
}

/**
 * Start an interactive Claude CLI session (no command, pure interactive mode).
 * The user sends keystrokes directly via write() and sees real-time output.
 */
export function startInteractiveSession(
  projectPath: string,
  sessionId: string | null,
  cols: number = 80,
  rows: number = 24
): ClaudeExecution {
  const emitter = new EventEmitter() as ClaudeExecution;
  emitter.ptyProcess = null;

  // Build Claude command arguments - interactive mode only
  const claudeArgs: string[] = ['--verbose'];

  if (sessionId) {
    claudeArgs.push('-r', sessionId);
  }
  // No -p flag = interactive mode

  // Get full paths and resolve symlinks
  let claudePath = process.env.CLAUDE_PATH || '/opt/homebrew/bin/claude';
  try {
    claudePath = realpathSync(claudePath);
  } catch {
    // Keep original path if realpath fails
  }


  let ptyProc: pty.IPty;

  try {
    ptyProc = pty.spawn(claudePath, claudeArgs, {
      name: 'xterm-256color',
      cols,
      rows,
      cwd: projectPath,
      env: { ...process.env, TERM: 'xterm-256color', COLORTERM: 'truecolor' },
    });
  } catch (err) {
    console.error(`[Claude] Failed to spawn interactive PTY:`, err);
    setTimeout(() => {
      emitter.emit('error', `Failed to spawn PTY: ${(err as Error).message}`);
    }, 0);
    return emitter;
  }


  emitter.ptyProcess = ptyProc;

  let killed = false;
  let exited = false;

  // Handle PTY data
  ptyProc.onData((data: string) => {
    emitter.emit('data', data, 'stdout');
  });

  // Handle process exit
  ptyProc.onExit(({ exitCode, signal }) => {
    exited = true;
    if (!killed) {
      emitter.emit('complete', exitCode);
    }
  });

  // No timeout for interactive sessions - user controls the session

  // Kill method
  emitter.kill = () => {
    if (!exited) {
      killed = true;
      ptyProc.kill();
    }
  };

  // Write method for sending input
  emitter.write = (data: string) => {
    if (!exited && ptyProc) {
      ptyProc.write(data);
    }
  };

  // Resize method
  emitter.resize = (newCols: number, newRows: number) => {
    if (!exited && ptyProc) {
      ptyProc.resize(newCols, newRows);
    }
  };

  return emitter;
}
