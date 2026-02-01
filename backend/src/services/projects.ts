import { readdir, readFile, stat, access } from 'fs/promises';
import { join, basename } from 'path';
import { config } from '../config.js';
import type { Project, SessionsIndex } from '../types/index.js';

let projectsCache: Project[] | null = null;
let lastScanTime: number = 0;
const CACHE_TTL_MS = 30000; // 30 seconds cache

/**
 * Check if a path exists (async).
 */
async function pathExists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

/**
 * Scan ~/.claude/projects/ directory and discover all Claude projects.
 * Projects are identified by folders containing sessions-index.json.
 */
export async function loadProjects(): Promise<Project[]> {
  const claudeProjectsPath = config.claudeProjectsPath;

  if (!(await pathExists(claudeProjectsPath))) {
    console.warn(`Claude projects path not found: ${claudeProjectsPath}`);
    return [];
  }

  const projects: Project[] = [];

  try {
    const entries = await readdir(claudeProjectsPath);

    for (const entry of entries) {
      // Skip hidden files and special entries
      if (entry.startsWith('.')) continue;

      const projectDir = join(claudeProjectsPath, entry);

      // Check if it's a directory
      try {
        const stats = await stat(projectDir);
        if (!stats.isDirectory()) continue;
      } catch {
        continue;
      }

      // Check for sessions-index.json
      const sessionsIndexPath = join(projectDir, 'sessions-index.json');
      if (!(await pathExists(sessionsIndexPath))) continue;

      try {
        const indexData = await readFile(sessionsIndexPath, 'utf-8');
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
        if (!(await pathExists(projectPath))) {
          console.warn(`Project path does not exist: ${projectPath}`);
          continue;
        }

        // Extract project name from path
        const projectName = basename(projectPath);

        // Try to detect dev server port from package.json
        let devServerPort: number | undefined;
        const packageJsonPath = join(projectPath, 'package.json');
        if (await pathExists(packageJsonPath)) {
          try {
            const packageJson = JSON.parse(await readFile(packageJsonPath, 'utf-8'));
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
export async function listProjects(): Promise<Project[]> {
  const now = Date.now();
  if (projectsCache !== null && now - lastScanTime < CACHE_TTL_MS) {
    return projectsCache;
  }
  return loadProjects();
}

/**
 * Get a specific project by ID.
 */
export async function getProject(id: string): Promise<Project | undefined> {
  const projects = await listProjects();
  return projects.find((p) => p.id === id);
}

/**
 * Force reload projects from disk.
 */
export async function reloadProjects(): Promise<Project[]> {
  projectsCache = null;
  lastScanTime = 0;
  return loadProjects();
}

/**
 * Add a custom project (not from Claude's directory).
 * Useful for adding projects that haven't been used with Claude CLI yet.
 */
export async function addCustomProject(project: Project): Promise<void> {
  if (projectsCache === null) {
    await loadProjects();
  }

  // Check if already exists
  if (projectsCache!.find(p => p.id === project.id)) {
    return;
  }

  projectsCache!.push(project);
  projectsCache!.sort((a, b) => a.name.localeCompare(b.name));
}
