import { fileURLToPath } from 'node:url';
import { startVitest } from '../frontend/node_modules/vitest/dist/node.js';
import react from '../frontend/node_modules/@vitejs/plugin-react/dist/index.js';

const context = await startVitest('test', [], {
  root: fileURLToPath(new URL('../frontend/', import.meta.url)),
  run: true, config: false, pool: 'threads',
  execArgv: ['--preserve-symlinks', '--preserve-symlinks-main'],
  environment: fileURLToPath(new URL('./frontend-test-environment.mjs', import.meta.url)),
  setupFiles: ['./src/test/setup.ts'], clearMocks: true,
}, {
  configFile: false, plugins: [react()], resolve: { preserveSymlinks: true },
});
await context.close();
