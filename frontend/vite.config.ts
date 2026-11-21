import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// In development the UI calls /api/... on its own origin and Vite forwards it to the
// Spring Boot backend. The browser still sends an Origin header (http://localhost:5173) on
// POST/PATCH, and the backend's CORS check rejects origins that are not on its allow-list with
// 403. Seen from the backend this is a server-to-server call, so the proxy drops Origin and no
// CORS configuration is needed for local development.
const backend = process.env.FLEETTRACK_API ?? 'http://localhost:8080';

const proxy = {
  target: backend,
  configure: (p: { on: (event: 'proxyReq', fn: (req: { removeHeader: (name: string) => void }) => void) => void }) => {
    p.on('proxyReq', req => req.removeHeader('origin'));
  },
};

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': proxy,
      '/actuator': proxy,
    },
  },
  test: {
    environment: 'node',
  },
});
