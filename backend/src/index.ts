import express from 'express';
import { createServer } from 'http';
import { Server } from 'socket.io';
import { config, validateConfig } from './config.js';
import apiRouter from './handlers/api.js';
import { registerSocketHandlers, cleanupAllExecutions } from './handlers/socket.js';
import { loadProjects } from './services/projects.js';

// Validate configuration (exits if Claude CLI not installed)
validateConfig();

// Initialize Express
const app = express();
app.use(express.json());

// Mount API routes
app.use('/', apiRouter);

// Create HTTP server
const httpServer = createServer(app);

// Initialize Socket.io
const io = new Server(httpServer, {
  cors: {
    origin: '*', // Allow all origins (Tailscale handles security)
    methods: ['GET', 'POST'],
  },
});

// Register socket handlers
registerSocketHandlers(io);

// Load projects at startup
loadProjects();

// Start server
httpServer.listen(config.port, '0.0.0.0', () => {
  console.log(`Claude Remote Backend running on port ${config.port}`);
});

// Graceful shutdown
function shutdown(signal: string): void {
  console.log(`Shutting down...`);

  // Kill all running processes
  cleanupAllExecutions();

  // Close Socket.io connections
  io.close();

  // Close HTTP server
  httpServer.close(() => {
    process.exit(0);
  });

  // Force exit after 10 seconds
  setTimeout(() => {
    process.exit(1);
  }, 10000);
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
