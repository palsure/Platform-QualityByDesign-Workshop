import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const apiProxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
} as const;

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: true,
    proxy: apiProxy,
  },
  preview: {
    proxy: apiProxy,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    setupFiles: ['allure-vitest/setup'],
    // Run test files in parallel worker threads (much faster on CI runners
    // with multiple vCPUs). Vitest defaults to a thread pool, but we set the
    // bounds explicitly so the behaviour is consistent locally and in CI.
    pool: 'threads',
    poolOptions: {
      threads: {
        // Use up to 4 worker threads on CI (GitHub-hosted runners have 2-4 vCPUs);
        // locally Vitest auto-picks based on os.availableParallelism().
        minThreads: 1,
        maxThreads: process.env.CI ? 4 : undefined,
        useAtomics: true,
      },
    },
    fileParallelism: true,
    reporters: [
      'default',
      ['junit', { outputFile: 'test-results/vitest-junit.xml' }],
      './allure-vitest-reporter.mjs',
    ],
  },
});
