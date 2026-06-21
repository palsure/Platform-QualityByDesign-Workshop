/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  /** When true, metrics are not POSTed (CI / device farm probes). */
  readonly VITE_QOE_SILENT: string;

  // ── New Relic Browser RUM (all optional; agent only loads when set) ────────
  /** New Relic license key (browser/ingest sub-key). */
  readonly VITE_NEWRELIC_LICENSE_KEY?: string;
  /** Numeric application ID from NR → Browser → Application settings. */
  readonly VITE_NEWRELIC_APPLICATION_ID?: string;
  /** Account ID from NR → Administration → Access management. */
  readonly VITE_NEWRELIC_ACCOUNT_ID?: string;
  /** Trust key (defaults to accountID for single-account setups). */
  readonly VITE_NEWRELIC_TRUST_KEY?: string;
  /** Agent ID (defaults to applicationID for SPA configurations). */
  readonly VITE_NEWRELIC_AGENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
