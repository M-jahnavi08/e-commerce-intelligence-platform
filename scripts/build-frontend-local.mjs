// Run from the project root with Node's --preserve-symlinks and
// --preserve-symlinks-main flags in restricted Windows environments.
import { fileURLToPath } from 'node:url';
import { build } from '../frontend/node_modules/vite/dist/node/index.js';
import react from '../frontend/node_modules/@vitejs/plugin-react/dist/index.js';
import tailwindcss from '../frontend/node_modules/@tailwindcss/vite/dist/index.mjs';

await build({
  root: fileURLToPath(new URL('../frontend/', import.meta.url)),
  configFile: false,
  plugins: [react(), tailwindcss()],
  resolve: { preserveSymlinks: true },
  build: { emptyOutDir: false },
});
