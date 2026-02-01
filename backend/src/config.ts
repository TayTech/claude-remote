import { config as loadEnv } from 'dotenv';
import { execSync } from 'child_process';
import { existsSync } from 'fs';
import { homedir } from 'os';
import { join } from 'path';

loadEnv();

function expandPath(p: string): string {
  if (p.startsWith('~')) {
    return join(homedir(), p.slice(1));
  }
  return p;
}

function checkClaudeCli(): boolean {
  try {
    execSync('which claude', { encoding: 'utf-8', stdio: 'pipe' });
    return true;
  } catch {
    return false;
  }
}

export const config = {
  port: parseInt(process.env.PORT || '3000', 10),
  claudeProjectsPath: expandPath(process.env.CLAUDE_PROJECTS_PATH || '~/.claude/projects'),
  commandTimeout: parseInt(process.env.COMMAND_TIMEOUT || '300000', 10),
  claudeCliInstalled: false,
};

export function validateConfig(): void {
  config.claudeCliInstalled = checkClaudeCli();

  if (!config.claudeCliInstalled) {
    console.error('ERROR: Claude CLI is not installed or not in PATH.');
    console.error('Please install Claude CLI and ensure it is accessible via the "claude" command.');
    process.exit(1);
  }

  if (!existsSync(config.claudeProjectsPath)) {
    console.warn(`WARNING: Claude projects path does not exist: ${config.claudeProjectsPath}`);
    console.warn('Sessions discovery may not work until Claude CLI is used at least once.');
  }

}
