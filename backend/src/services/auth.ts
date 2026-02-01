import { randomBytes } from 'crypto';
import { existsSync, readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import qrcode from 'qrcode-terminal';

// Get the backend directory (where this file is located)
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const BACKEND_DIR = join(__dirname, '..', '..');
const API_KEY_FILE = join(BACKEND_DIR, '.remote-cli-key');
const API_KEY_LENGTH = 32;

let cachedApiKey: string | null = null;

/**
 * Get or generate the API key.
 * On first run, generates a random key and saves it.
 * On subsequent runs, reads the existing key.
 */
export function getApiKey(): string {
  if (cachedApiKey) {
    return cachedApiKey;
  }

  if (existsSync(API_KEY_FILE)) {
    cachedApiKey = readFileSync(API_KEY_FILE, 'utf-8').trim();
  } else {
    cachedApiKey = randomBytes(API_KEY_LENGTH).toString('hex');
    writeFileSync(API_KEY_FILE, cachedApiKey, { mode: 0o600 });
    console.log(`API key generated and saved to ${API_KEY_FILE}`);
  }

  return cachedApiKey;
}

/**
 * Validate an API key against the stored key.
 */
export function validateApiKey(key: string | undefined): boolean {
  if (!key) {
    return false;
  }
  const validKey = getApiKey();
  // Constant-time comparison to prevent timing attacks
  if (key.length !== validKey.length) {
    return false;
  }
  let result = 0;
  for (let i = 0; i < key.length; i++) {
    result |= key.charCodeAt(i) ^ validKey.charCodeAt(i);
  }
  return result === 0;
}

/**
 * Display QR code for easy API key entry.
 * The QR code contains: host:port:apiKey
 */
export function displayQRCode(host: string, port: number): void {
  const apiKey = getApiKey();
  // Format: claude-remote://host:port/apiKey
  const connectionString = `claude-remote://${host}:${port}/${apiKey}`;

  console.log('\n========================================');
  console.log('Scan this QR code with RemoteCli for Claude app:');
  console.log('========================================\n');

  qrcode.generate(connectionString, { small: true });

  console.log('\n----------------------------------------');
  console.log(`Host: your.tailscale.device.address`);
  console.log(`Port: ${port}`);
  console.log(`API Key: ${apiKey}`);
  console.log('----------------------------------------\n');
}
