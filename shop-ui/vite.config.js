import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Public demo through one ngrok tunnel: the tunnel points at this server and
// `/identity` is forwarded to the backend, so the browser stays same-origin.
const API_TARGET = process.env.API_PROXY_TARGET || 'http://localhost:8081';
const proxy = {
  '/identity': {
    target: API_TARGET,
    changeOrigin: true,
    configure: (p) => {
      // The backend only whitelists localhost origins for CORS; behind the proxy
      // the call is same-origin for the visitor, so drop the tunnel's Origin.
      p.on('proxyReq', (req) => req.removeHeader('origin'));
    },
  },
};
const allowedHosts = ['.ngrok-free.dev', '.ngrok-free.app', '.ngrok.app', '.ngrok.dev'];

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true, proxy, allowedHosts },
  preview: { port: 5173, strictPort: true, proxy, allowedHosts },
  test: { environment: 'jsdom', globals: true },
});
