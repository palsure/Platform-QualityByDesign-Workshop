/**
 * New Relic Browser RUM bootstrap.
 *
 * The agent is **opt-in**: it only loads when the licence key + application ID
 * env vars are present at build time (Vite inlines them via `import.meta.env`).
 * With no env vars set, this module is a complete no-op — `window.newrelic`
 * stays undefined, the bundle stays small, and `recordPageAction()` becomes a
 * silent no-op so callers never need to null-check.
 *
 * Why npm package instead of the copy-paste `<script>` snippet?
 *   • Versioned, lockfile-pinned (no opaque inline blob in `index.html`)
 *   • TypeScript types for `init`, `loader_config`, `info`
 *   • Tree-shakeable: only the browser-agent loader (≈30 KB gzip) ships
 *   • Plays nicely with Vite's HMR and module bundling
 */
import { BrowserAgent } from '@newrelic/browser-agent/loaders/browser-agent';

let agent: BrowserAgent | null = null;

declare global {
  interface Window {
    newrelic?: {
      addPageAction: (name: string, attributes?: Record<string, unknown>) => void;
      noticeError:   (error: Error | string, customAttributes?: Record<string, unknown>) => void;
      setCustomAttribute: (name: string, value: string | number | boolean) => void;
    };
  }
}

export function initNewRelic(): void {
  if (agent) return; // idempotent — guard against React 18 StrictMode double-mount

  const env = import.meta.env;
  const licenseKey   = env.VITE_NEWRELIC_LICENSE_KEY;
  const applicationID = env.VITE_NEWRELIC_APPLICATION_ID;
  const accountID    = env.VITE_NEWRELIC_ACCOUNT_ID;

  if (!licenseKey || !applicationID || !accountID) {
    // Quiet in production builds; only log in dev so attendees see why no data flows.
    if (import.meta.env.DEV) {
      console.info(
        '[newrelic] Browser RUM disabled — set VITE_NEWRELIC_LICENSE_KEY, ' +
        'VITE_NEWRELIC_APPLICATION_ID, and VITE_NEWRELIC_ACCOUNT_ID to enable.',
      );
    }
    return;
  }

  const trustKey = env.VITE_NEWRELIC_TRUST_KEY ?? accountID;
  const agentID  = env.VITE_NEWRELIC_AGENT_ID  ?? applicationID;

  agent = new BrowserAgent({
    init: {
      // Distributed tracing stitches browser timings to backend transactions
      // when the API responds with W3C traceparent headers (already enabled
      // by NEWRELIC_ENABLED=true on the Spring Boot side).
      distributed_tracing: { enabled: true },
      privacy:             { cookies_enabled: true },
      ajax:                { deny_list: ['bam.nr-data.net'] },
    },
    loader_config: {
      accountID,
      trustKey,
      agentID,
      licenseKey,
      applicationID,
    },
    info: {
      beacon:        'bam.nr-data.net',
      errorBeacon:   'bam.nr-data.net',
      licenseKey,
      applicationID,
      sa:            1,
    },
  });
}

/**
 * Fire a custom Insights "PageAction" event. Safe to call before/without
 * `initNewRelic()` — falls back to a silent no-op so the QoE collector
 * doesn't need to know whether RUM is wired up.
 */
export function recordPageAction(
  name: string,
  attributes?: Record<string, unknown>,
): void {
  if (typeof window === 'undefined') return;
  window.newrelic?.addPageAction(name, attributes);
}
