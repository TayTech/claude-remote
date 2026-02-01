import express from 'express';
import { createServer } from 'http';
import { Server } from 'socket.io';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { config, validateConfig } from './config.js';
import apiRouter from './handlers/api.js';
import { registerSocketHandlers, cleanupAllExecutions } from './handlers/socket.js';
import { loadProjects } from './services/projects.js';
import { getApiKey, validateApiKey, displayQRCode } from './services/auth.js';
import { hostname } from 'os';

// Validate configuration (exits if Claude CLI not installed)
validateConfig();

// Initialize API key (generates if not exists)
const apiKey = getApiKey();

// Initialize Express
const app = express();

// Security headers
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:'],
    },
  },
}));

// Rate limiting: 100 requests per minute per IP
const limiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 100,
  message: { error: 'Too many requests, please try again later' },
  standardHeaders: true,
  legacyHeaders: false,
});
app.use(limiter);

app.use(express.json());

// API key authentication middleware
app.use((req, res, next) => {
  // Skip auth for health check
  if (req.path === '/health') {
    return next();
  }

  const providedKey = req.headers['x-api-key'] as string;
  if (!validateApiKey(providedKey)) {
    res.status(401).json({ error: 'Unauthorized: Invalid or missing API key' });
    return;
  }
  next();
});

// Mount API routes
app.use('/', apiRouter);

// Create HTTP server
const httpServer = createServer(app);

// Initialize Socket.io with restricted CORS
const io = new Server(httpServer, {
  cors: {
    origin: false, // Disable CORS (mobile app doesn't need it)
    methods: ['GET', 'POST'],
  },
});

// Socket.io authentication middleware
io.use((socket, next) => {
  const providedKey = socket.handshake.auth.apiKey as string;
  if (!validateApiKey(providedKey)) {
    next(new Error('Unauthorized: Invalid or missing API key'));
    return;
  }
  next();
});

// Register socket handlers
registerSocketHandlers(io);

// Load projects at startup and start server
loadProjects().then(() => {
  httpServer.listen(config.port, '0.0.0.0', () => {
    const host = hostname();
    console.log(`Claude Remote Backend running on port ${config.port}`);
    displayQRCode(host, config.port);
  });
}).catch((err) => {
  console.error('Failed to load projects:', err);
  process.exit(1);
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
