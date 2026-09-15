import { JSDOM } from '../frontend/node_modules/jsdom/lib/api.js';
import { populateGlobal } from '../frontend/node_modules/vitest/dist/environments.js';

export default {
  name: 'local-jsdom',
  viteEnvironment: 'client',
  setup(global) {
    const dom = new JSDOM('<!doctype html><html><body></body></html>', {
      url: 'http://localhost/', pretendToBeVisual: true,
    });
    const { keys, originals } = populateGlobal(global, dom.window, { bindFunctions: true });
    return {
      teardown() {
        dom.window.close();
        keys.forEach((key) => delete global[key]);
        originals.forEach((value, key) => { global[key] = value; });
      },
    };
  },
};
