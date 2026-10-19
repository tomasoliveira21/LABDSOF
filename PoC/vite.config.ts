import { defineConfig } from 'vitest/config';

export default defineConfig({
  // Relative base so the production build works from any sub-path (e.g. GitHub Pages).
  base: './',
  server: { port: 5173, open: false },
  test: {
    include: ['tests/**/*.test.ts'],
    environment: 'node',
  },
});
