import { Router, Request, Response, NextFunction } from 'express';
import { listProjects, getProject } from '../services/projects.js';
import { getSessionsForProject, getSessionHistory } from '../services/sessions.js';
import { config } from '../config.js';
import type { HealthResponse } from '../types/index.js';

const router = Router();

// Async handler wrapper
const asyncHandler = (fn: (req: Request, res: Response, next: NextFunction) => Promise<void>) => {
  return (req: Request, res: Response, next: NextFunction) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
};

// Validate projectId format (alphanumeric + hyphen, can start with hyphen for path-encoded IDs)
function isValidProjectId(id: string): boolean {
  return /^-?[a-zA-Z0-9-]+$/.test(id) && id.length > 0 && id.length < 500;
}

// GET /health
router.get('/health', (_req: Request, res: Response) => {
  const response: HealthResponse = {
    status: 'ok',
    uptime: process.uptime(),
    claudeCliInstalled: config.claudeCliInstalled,
  };
  res.json(response);
});

// GET /projects
router.get(
  '/projects',
  asyncHandler(async (_req: Request, res: Response) => {
    const projects = await listProjects();
    res.json(projects);
  })
);

// GET /projects/:id/sessions
router.get(
  '/projects/:id/sessions',
  asyncHandler(async (req: Request, res: Response) => {
    const { id } = req.params;

    if (!isValidProjectId(id)) {
      res.status(400).json({ error: 'Invalid project ID format' });
      return;
    }

    const project = await getProject(id);
    if (!project) {
      res.status(404).json({ error: 'Project not found' });
      return;
    }

    const sessions = await getSessionsForProject(project.path);
    res.json(sessions);
  })
);

// Validate sessionId format (UUID)
function isValidSessionId(id: string): boolean {
  return /^[a-f0-9-]{36}$/.test(id);
}

// GET /projects/:id/sessions/:sessionId/history
router.get(
  '/projects/:id/sessions/:sessionId/history',
  asyncHandler(async (req: Request, res: Response) => {
    const { id, sessionId } = req.params;

    if (!isValidProjectId(id)) {
      res.status(400).json({ error: 'Invalid project ID format' });
      return;
    }

    if (!isValidSessionId(sessionId)) {
      res.status(400).json({ error: 'Invalid session ID format' });
      return;
    }

    const project = await getProject(id);
    if (!project) {
      res.status(404).json({ error: 'Project not found' });
      return;
    }

    const history = await getSessionHistory(project.path, sessionId);
    res.json(history);
  })
);

// Error handler
router.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error('API Error:', err);
  res.status(500).json({ error: 'Internal server error' });
});

export default router;
