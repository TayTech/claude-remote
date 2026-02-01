import { readdirSync, readFileSync, existsSync, statSync } from 'fs';
import { join, basename } from 'path';
import { config } from '../config.js';
import type { Project, SessionsIndex } from '../types/index.js';

let projectsCache: Project[] | null = null;
let lastScanTime: number = 0;
const CACHE_TTL_MS = 30000; // 30 seconds cache

/**
 * Scan ~/.claude/projects/ directory and discover all Claude projects.
 * Projects are identified by folders containing sessions-index.json.
 */
export function loadProjects(): Project[] {
  const claudeProjectsPath = config.claudeProjectsPath;

  if (!existsSync(claudeProjectsPath)) {
    console.warn(`Claude projects path not found: ${claudeProjectsPath}`);
    return [];
  }

  const projects: Project[] = [];

  try {
    const entries = readdirSync(claudeProjectsPath);

    for (const entry of entries) {
      // Skip hidden files and special entries
      if (entry.startsWith('.')) continue;

      const projectDir = join(claudeProjectsPath, entry);

      // Check if it's a directory
      try {
        if (!statSync(projectDir).isDirectory()) continue;
      } catch {
        continue;
      }

      // Check for sessions-index.json
      const sessionsIndexPath = join(projectDir, 'sessions-index.json');
      if (!existsSync(sessionsIndexPath)) continue;

      try {
        const indexData = readFileSync(sessionsIndexPath, 'utf-8');
        const sessionsIndex: SessionsIndex = JSON.parse(indexData);

        // Get project path from the first session entry or decode from folder name
        let projectPath: string;
        if (sessionsIndex.entries && sessionsIndex.entries.length > 0) {
          projectPath = sessionsIndex.entries[0].projectPath;
        } else {
          // Decode from folder name: -Users-foo-bar -> /Users/foo/bar
          projectPath = '/' + entry.replace(/^-/, '').replace(/-/g, '/');
        }

        // Verify the project path exists
        if (!existsSync(projectPath)) {
          console.warn(`Project path does not exist: ${projectPath}`);
          continue;
        }

        // Extract project name from path
        const projectName = basename(projectPath);

        // Try to detect dev server port from package.json
        let devServerPort: number | undefined;
        const packageJsonPath = join(projectPath, 'package.json');
        if (existsSync(packageJsonPath)) {
          try {
            const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf-8'));
            // Common patterns for dev server ports
            if (packageJson.config?.port) {
              devServerPort = parseInt(packageJson.config.port, 10);
            }
          } catch {
            // Ignore package.json parsing errors
          }
        }

        const project: Project = {
          id: entry, // Use folder name as ID
          name: projectName,
          path: projectPath,
          devServerPort,
        };

        projects.push(project);
      } catch (error) {
        console.warn(`Failed to read sessions-index.json for ${entry}:`, error);
      }
    }

    // Sort by name
    projects.sort((a, b) => a.name.localeCompare(b.name));

    projectsCache = projects;
    lastScanTime = Date.now();

    return projects;
  } catch (error) {
    console.error('Failed to scan projects directory:', error);
    return [];
  }
}

/**
 * List all discovered projects.
 * Uses cache if available and not expired.
 */
export function listProjects(): Project[] {
  const now = Date.now();
  if (projectsCache !== null && now - lastScanTime < CACHE_TTL_MS) {
    return projectsCache;
  }
  return loadProjects();
}

/**
 * Get a specific project by ID.
 */
export function getProject(id: string): Project | undefined {
  const projects = listProjects();
  return projects.find((p) => p.id === id);
}

/**
 * Force reload projects from disk.
 */
export function reloadProjects(): Project[] {
  projectsCache = null;
  lastScanTime = 0;
  return loadProjects();
}

/**
 * Add a custom project (not from Claude's directory).
 * Useful for adding projects that haven't been used with Claude CLI yet.
 */
export function addCustomProject(project: Project): void {
  if (projectsCache === null) {
    loadProjects();
  }

  // Check if already exists
  if (projectsCache!.find(p => p.id === project.id)) {
    return;
  }

  projectsCache!.push(project);
  projectsCache!.sort((a, b) => a.name.localeCompare(b.name));
}
