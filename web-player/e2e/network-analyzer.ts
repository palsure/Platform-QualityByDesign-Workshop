/**
 * network-analyzer.ts
 *
 * Pure analysis logic — no Playwright or Allure dependencies.
 * Receives raw captured entries and produces a structured NetworkReport
 * with issues, HLS metrics, QoE API metrics, and a human-readable
 * Markdown summary for Allure attachment.
 */

// ── Types ──────────────────────────────────────────────────────────────────────

export type RequestCategory =
  | 'hls-manifest'
  | 'hls-segment'
  | 'qoe-api'
  | 'app-asset'
  | 'other';

export type IssueSeverity = 'critical' | 'warning' | 'info';

export interface NetworkEntry {
  id:           number;
  url:          string;
  method:       string;
  /** HTTP status code; null means the request never got a response (network error). */
  status:       number | null;
  durationMs:   number;
  category:     RequestCategory;
  failed:       boolean;
  slow:         boolean;
  errorText?:   string;
  /** Performance.now()-style offset from test start (ms). */
  startedAt:    number;
}

export interface ConsoleEntry {
  type:      'log' | 'info' | 'warn' | 'error';
  text:      string;
  startedAt: number;
}

export interface PageError {
  message:   string;
  startedAt: number;
}

export interface NetworkIssue {
  severity: IssueSeverity;
  type:     string;
  message:  string;
  url?:     string;
  detail?:  string;
}

export interface CategoryStats {
  count:         number;
  failed:        number;
  totalMs:       number;
  avgMs:         number;
  p95Ms:         number;
  maxMs:         number;
  errorRate:     number;   // 0–100
}

export interface NetworkReport {
  summary: {
    total:         number;
    failed:        number;
    slow:          number;
    byCategory:    Record<RequestCategory, CategoryStats>;
    overallHealth: 'healthy' | 'degraded' | 'critical';
  };
  issues:          NetworkIssue[];
  consoleErrors:   ConsoleEntry[];
  pageErrors:      PageError[];
  markdownReport:  string;
}

// ── Slow-request thresholds (ms) per category ─────────────────────────────────

const SLOW_MS: Record<RequestCategory, number> = {
  'hls-manifest': 5_000,
  'hls-segment':  8_000,
  'qoe-api':      2_000,
  'app-asset':    5_000,
  'other':        5_000,
};

const SEGMENT_ERROR_RATE_THRESHOLD = 5; // percent — warn if above

// ── Categorisation ─────────────────────────────────────────────────────────────

export function categorize(url: string): RequestCategory {
  const clean = url.split('?')[0];
  if (clean.endsWith('.m3u8') || clean.includes('playlist') || clean.includes('manifest'))
    return 'hls-manifest';
  if (clean.match(/\.(ts|m4s|aac|fmp4|mp4)$/))
    return 'hls-segment';
  if (url.includes('/api/') || url.includes('/v1/'))
    return 'qoe-api';
  if (clean.match(/\.(js|jsx|ts|tsx|css|html|woff2?|png|svg|ico|webp|gif|json|map)$/))
    return 'app-asset';
  return 'other';
}

export function isSlowRequest(entry: Pick<NetworkEntry, 'durationMs' | 'category'>): boolean {
  return entry.durationMs > SLOW_MS[entry.category];
}

// ── Stats helper ──────────────────────────────────────────────────────────────

function calcStats(entries: NetworkEntry[]): CategoryStats {
  const n = entries.length;
  if (n === 0)
    return { count: 0, failed: 0, totalMs: 0, avgMs: 0, p95Ms: 0, maxMs: 0, errorRate: 0 };

  const failed    = entries.filter(e => e.failed).length;
  const totalMs   = entries.reduce((s, e) => s + e.durationMs, 0);
  const avgMs     = Math.round(totalMs / n);
  const maxMs     = Math.max(...entries.map(e => e.durationMs));
  const sorted    = [...entries].sort((a, b) => a.durationMs - b.durationMs);
  const p95Idx    = Math.min(Math.floor(n * 0.95), n - 1);
  const p95Ms     = sorted[p95Idx].durationMs;
  const errorRate = Math.round((failed / n) * 100);

  return { count: n, failed, totalMs, avgMs, p95Ms, maxMs, errorRate };
}

// ── Main analysis ─────────────────────────────────────────────────────────────

export function analyze(
  entries:    NetworkEntry[],
  consoleLogs: ConsoleEntry[],
  pageErrors:  PageError[],
): NetworkReport {

  const issues: NetworkIssue[] = [];

  // ── Per-category stats ───────────────────────────────────────────
  const categories: RequestCategory[] = [
    'hls-manifest', 'hls-segment', 'qoe-api', 'app-asset', 'other',
  ];
  const byCategory = {} as Record<RequestCategory, CategoryStats>;
  for (const cat of categories) {
    byCategory[cat] = calcStats(entries.filter(e => e.category === cat));
  }

  const totalFailed = entries.filter(e => e.failed).length;
  const totalSlow   = entries.filter(e => e.slow).length;

  // ── Issue detection ──────────────────────────────────────────────

  // HLS manifest errors
  const manifestErrors = entries.filter(e => e.category === 'hls-manifest' && e.failed);
  for (const e of manifestErrors) {
    issues.push({
      severity: 'critical',
      type:     'HLS_MANIFEST_FAILED',
      message:  `HLS manifest failed to load${e.status ? ` (HTTP ${e.status})` : ' (network error)'}`,
      url:      e.url,
      detail:   e.errorText,
    });
  }

  // Slow manifest
  const slowManifests = entries.filter(e => e.category === 'hls-manifest' && e.slow && !e.failed);
  for (const e of slowManifests) {
    issues.push({
      severity: 'warning',
      type:     'SLOW_HLS_MANIFEST',
      message:  `HLS manifest took ${fmtMs(e.durationMs)} to load (threshold ${fmtMs(SLOW_MS['hls-manifest'])})`,
      url:      e.url,
    });
  }

  // High segment error rate
  const segStats = byCategory['hls-segment'];
  if (segStats.count > 0 && segStats.errorRate > SEGMENT_ERROR_RATE_THRESHOLD) {
    issues.push({
      severity: 'critical',
      type:     'HIGH_SEGMENT_ERROR_RATE',
      message:  `HLS segment error rate ${segStats.errorRate}% exceeds ${SEGMENT_ERROR_RATE_THRESHOLD}% threshold`,
      detail:   `${segStats.failed} of ${segStats.count} segments failed`,
    });
  }

  // Individual segment errors (up to 3 in report)
  const segErrors = entries.filter(e => e.category === 'hls-segment' && e.failed).slice(0, 3);
  for (const e of segErrors) {
    issues.push({
      severity: 'warning',
      type:     'HLS_SEGMENT_FAILED',
      message:  `HLS segment failed${e.status ? ` (HTTP ${e.status})` : ' (network error)'}`,
      url:      e.url,
      detail:   e.errorText,
    });
  }

  // Slow segments (P95 check)
  if (segStats.count > 0 && segStats.p95Ms > SLOW_MS['hls-segment']) {
    issues.push({
      severity: 'warning',
      type:     'SLOW_HLS_SEGMENTS',
      message:  `HLS segment P95 download time ${fmtMs(segStats.p95Ms)} exceeds ${fmtMs(SLOW_MS['hls-segment'])} threshold`,
      detail:   `avg: ${fmtMs(segStats.avgMs)}, max: ${fmtMs(segStats.maxMs)}`,
    });
  }

  // QoE API: all calls failed (likely CORS or backend down)
  const apiStats = byCategory['qoe-api'];
  if (apiStats.count > 0 && apiStats.failed === apiStats.count) {
    issues.push({
      severity: 'critical',
      type:     'QOE_API_UNREACHABLE',
      message:  `All ${apiStats.count} QoE API request(s) failed — backend may be down or CORS misconfigured`,
    });
  } else if (apiStats.count > 0 && apiStats.failed > 0) {
    issues.push({
      severity: 'warning',
      type:     'QOE_API_PARTIAL_FAILURE',
      message:  `${apiStats.failed} of ${apiStats.count} QoE API requests failed`,
    });
  }

  // Slow QoE API
  if (apiStats.avgMs > SLOW_MS['qoe-api'] && apiStats.count > 0) {
    issues.push({
      severity: 'warning',
      type:     'SLOW_QOE_API',
      message:  `QoE API average response time ${fmtMs(apiStats.avgMs)} exceeds ${fmtMs(SLOW_MS['qoe-api'])} threshold`,
      detail:   `P95: ${fmtMs(apiStats.p95Ms)}, max: ${fmtMs(apiStats.maxMs)}`,
    });
  }

  // No QoE API calls at all (silent mode or misconfiguration)
  if (apiStats.count === 0) {
    issues.push({
      severity: 'info',
      type:     'NO_QOE_API_CALLS',
      message:  'No QoE API requests observed — silent mode may be active or the collector is not configured',
    });
  }

  // CORS errors (typically show as status=0 or specific error text)
  const corsErrors = entries.filter(
    e => e.failed && (e.status === 0 || e.errorText?.toLowerCase().includes('cors')),
  );
  for (const e of corsErrors) {
    issues.push({
      severity: 'critical',
      type:     'CORS_ERROR',
      message:  'CORS policy blocked a network request',
      url:      e.url,
      detail:   e.errorText,
    });
  }

  // Console errors that look like HLS.js problems
  const hlsConsoleErrors = consoleLogs.filter(
    l => l.type === 'error' && (
      l.text.includes('Hls') || l.text.includes('hls') ||
      l.text.includes('manifest') || l.text.includes('segment') ||
      l.text.includes('media') || l.text.includes('codec')
    ),
  );
  for (const e of hlsConsoleErrors.slice(0, 3)) {
    issues.push({
      severity: 'warning',
      type:     'HLS_CONSOLE_ERROR',
      message:  'HLS.js logged an error to the browser console',
      detail:   e.text.slice(0, 300),
    });
  }

  // Uncaught page errors
  for (const e of pageErrors.slice(0, 3)) {
    issues.push({
      severity: 'critical',
      type:     'UNCAUGHT_PAGE_ERROR',
      message:  'Uncaught JavaScript error',
      detail:   e.message.slice(0, 300),
    });
  }

  // ── Overall health ────────────────────────────────────────────────
  const criticalCount = issues.filter(i => i.severity === 'critical').length;
  const warningCount  = issues.filter(i => i.severity === 'warning').length;
  const overallHealth =
    criticalCount > 0 ? 'critical' :
    warningCount  > 0 ? 'degraded' : 'healthy';

  // ── Markdown report ───────────────────────────────────────────────
  const markdownReport = buildMarkdownReport({
    summary: { total: entries.length, failed: totalFailed, slow: totalSlow, byCategory, overallHealth },
    issues,
    consoleErrors: consoleLogs.filter(l => l.type === 'error'),
    pageErrors,
  });

  return {
    summary: {
      total: entries.length,
      failed: totalFailed,
      slow: totalSlow,
      byCategory,
      overallHealth,
    },
    issues,
    consoleErrors: consoleLogs.filter(l => l.type === 'error'),
    pageErrors,
    markdownReport,
  };
}

// ── Markdown builder ──────────────────────────────────────────────────────────

interface ReportInput {
  summary: NetworkReport['summary'];
  issues:  NetworkIssue[];
  consoleErrors: ConsoleEntry[];
  pageErrors:    PageError[];
}

function buildMarkdownReport(r: ReportInput): string {
  const { summary, issues, consoleErrors, pageErrors } = r;
  const lines: string[] = [];

  const healthEmoji = { healthy: '✅', degraded: '⚠️', critical: '🔴' } as const;
  const sevEmoji    = { critical: '🔴', warning: '⚠️', info: 'ℹ️' } as const;

  lines.push('## Network Analysis Report');
  lines.push('');

  // Summary table
  lines.push('### Summary');
  lines.push('');
  lines.push('| Metric | Value |');
  lines.push('|--------|-------|');
  lines.push(`| Overall health | ${healthEmoji[summary.overallHealth]} **${summary.overallHealth.toUpperCase()}** |`);
  lines.push(`| Total requests | ${summary.total} |`);
  lines.push(`| Failed requests | ${summary.failed} |`);
  lines.push(`| Slow requests | ${summary.slow} |`);
  lines.push(`| Issues found | ${issues.length} (${issues.filter(i=>i.severity==='critical').length} critical, ${issues.filter(i=>i.severity==='warning').length} warnings) |`);
  lines.push('');

  // Issues
  if (issues.length === 0) {
    lines.push('### Issues');
    lines.push('');
    lines.push('✅ No network issues detected.');
    lines.push('');
  } else {
    lines.push('### Issues Found');
    lines.push('');
    for (const issue of issues) {
      lines.push(`${sevEmoji[issue.severity]} **${issue.type}** — ${issue.message}`);
      if (issue.url) lines.push(`   > URL: \`${truncate(issue.url, 120)}\``);
      if (issue.detail) lines.push(`   > ${issue.detail}`);
      lines.push('');
    }
  }

  // HLS breakdown
  const m = summary.byCategory['hls-manifest'];
  const s = summary.byCategory['hls-segment'];
  if (m.count > 0 || s.count > 0) {
    lines.push('### HLS Stream Analysis');
    lines.push('');
    lines.push('| | Requests | Errors | Error Rate | Avg | P95 | Max |');
    lines.push('|--|----------|--------|-----------|-----|-----|-----|');
    if (m.count > 0) lines.push(`| Manifests | ${m.count} | ${m.failed} | ${m.errorRate}% | ${fmtMs(m.avgMs)} | ${fmtMs(m.p95Ms)} | ${fmtMs(m.maxMs)} |`);
    if (s.count > 0) lines.push(`| Segments  | ${s.count} | ${s.failed} | ${s.errorRate}% | ${fmtMs(s.avgMs)} | ${fmtMs(s.p95Ms)} | ${fmtMs(s.maxMs)} |`);
    lines.push('');
  }

  // QoE API breakdown
  const a = summary.byCategory['qoe-api'];
  if (a.count > 0) {
    lines.push('### QoE API');
    lines.push('');
    lines.push('| Metric | Value |');
    lines.push('|--------|-------|');
    lines.push(`| Requests | ${a.count} |`);
    lines.push(`| Failures | ${a.failed} |`);
    lines.push(`| Avg response | ${fmtMs(a.avgMs)} |`);
    lines.push(`| P95 response | ${fmtMs(a.p95Ms)} |`);
    lines.push(`| Max response | ${fmtMs(a.maxMs)} |`);
    lines.push('');
  }

  // Console errors
  if (consoleErrors.length > 0) {
    lines.push('### Browser Console Errors');
    lines.push('');
    for (const e of consoleErrors.slice(0, 10)) {
      lines.push(`- \`${truncate(e.text, 200)}\``);
    }
    if (consoleErrors.length > 10) lines.push(`- *(${consoleErrors.length - 10} more errors omitted)*`);
    lines.push('');
  }

  // Page errors
  if (pageErrors.length > 0) {
    lines.push('### Uncaught JavaScript Errors');
    lines.push('');
    for (const e of pageErrors) {
      lines.push(`- \`${truncate(e.message, 200)}\``);
    }
    lines.push('');
  }

  return lines.join('\n');
}

// ── HAR export (HAR 1.2) ──────────────────────────────────────────────────────

/**
 * Converts captured NetworkEntry[] to a standard HAR 1.2 JSON string.
 * The resulting file can be imported into Chrome DevTools, Charles Proxy,
 * Fiddler, etc. for deep inspection.
 */
export function toHAR(entries: NetworkEntry[], pageTitle: string): string {
  // Reconstruct absolute start timestamps from relative offsets
  const baseEpoch = Date.now() - (entries.at(-1)?.startedAt ?? 0);

  const harEntries = entries.map(e => ({
    startedDateTime: new Date(baseEpoch + e.startedAt).toISOString(),
    time:            e.durationMs,
    request: {
      method:      e.method,
      url:         e.url,
      httpVersion: 'HTTP/1.1',
      headers:     [] as object[],
      queryString: [] as object[],
      cookies:     [] as object[],
      headersSize: -1,
      bodySize:    -1,
    },
    response: {
      status:      e.status ?? 0,
      statusText:  e.status ? statusText(e.status) : 'Network Error',
      httpVersion: 'HTTP/1.1',
      headers:     [] as object[],
      cookies:     [] as object[],
      content:     { size: -1, mimeType: mimeForCategory(e.category) },
      redirectURL: '',
      headersSize: -1,
      bodySize:    -1,
    },
    cache:   {},
    timings: {
      blocked: -1,
      dns:     -1,
      connect: -1,
      send:    0,
      wait:    Math.max(e.durationMs - 1, 0),
      receive: 1,
      ssl:     -1,
    },
    // Non-standard extensions (prefixed with _)
    _category:  e.category,
    _failed:    e.failed,
    _slow:      e.slow,
    _errorText: e.errorText ?? null,
  }));

  return JSON.stringify({
    log: {
      version: '1.2',
      creator: { name: 'playwright-qoe-capture', version: '1.0.0' },
      browser: { name: 'Chromium (headless)', version: 'unknown' },
      pages:   [{
        startedDateTime: new Date(baseEpoch).toISOString(),
        id:              'page_1',
        title:           pageTitle,
        pageTimings:     { onContentLoad: -1, onLoad: -1 },
      }],
      entries: harEntries,
    },
  }, null, 2);
}

// ── Timeline export ───────────────────────────────────────────────────────────

/**
 * Produces a human-readable chronological log of network requests and browser
 * console events, suitable for attaching as a plain-text Allure artifact.
 */
export function toTimeline(
  entries:     NetworkEntry[],
  consoleLogs: ConsoleEntry[],
  pageErrors:  PageError[],
): string {
  type AnyEvent =
    | { kind: 'req';     startedAt: number; data: NetworkEntry  }
    | { kind: 'console'; startedAt: number; data: ConsoleEntry  }
    | { kind: 'error';   startedAt: number; data: PageError     };

  const all: AnyEvent[] = [
    ...entries.map(e     => ({ kind: 'req'     as const, startedAt: e.startedAt, data: e })),
    ...consoleLogs.map(e => ({ kind: 'console' as const, startedAt: e.startedAt, data: e })),
    ...pageErrors.map(e  => ({ kind: 'error'   as const, startedAt: e.startedAt, data: e })),
  ].sort((a, b) => a.startedAt - b.startedAt);

  const lines: string[] = [
    '# Network & Console Timeline',
    `# Generated: ${new Date().toISOString()}`,
    `# Columns: [+offset_ms]  category        method  status  duration  url`,
    '',
  ];

  for (const ev of all) {
    const t = `+${String(ev.startedAt).padStart(7, ' ')}ms`;

    if (ev.kind === 'req') {
      const e      = ev.data;
      const status = e.status != null ? String(e.status) : 'ERR';
      const flag   = e.failed ? ' ❌' : e.slow ? ' ⚠️' : '   ';
      const cat    = e.category.padEnd(14, ' ');
      const dur    = fmtMs(e.durationMs).padStart(8);
      lines.push(`${t}  ${cat}  ${e.method.padEnd(6)}  ${status.padEnd(3)}  ${dur}  ${truncate(e.url, 110)}${flag}`);
      if (e.errorText) {
        lines.push(`${''.padEnd(t.length + 2)}  └─ ${e.errorText}`);
      }
    } else if (ev.kind === 'console') {
      if (ev.data.type === 'error' || ev.data.type === 'warn') {
        const icon = ev.data.type === 'error' ? '🖥️ ❌' : '🖥️ ⚠️';
        lines.push(`${t}  ${icon}  [console.${ev.data.type}]  ${truncate(ev.data.text, 110)}`);
      }
    } else {
      lines.push(`${t}  💥 [pageerror]  ${truncate(ev.data.message, 110)}`);
    }
  }

  return lines.join('\n');
}

// ── Internal helpers ──────────────────────────────────────────────────────────

function statusText(code: number): string {
  const map: Record<number, string> = {
    200: 'OK', 201: 'Created', 204: 'No Content', 206: 'Partial Content',
    301: 'Moved Permanently', 302: 'Found', 304: 'Not Modified',
    400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden',
    404: 'Not Found', 405: 'Method Not Allowed', 429: 'Too Many Requests',
    500: 'Internal Server Error', 502: 'Bad Gateway', 503: 'Service Unavailable',
  };
  return map[code] ?? '';
}

function mimeForCategory(cat: RequestCategory): string {
  switch (cat) {
    case 'hls-manifest': return 'application/vnd.apple.mpegurl';
    case 'hls-segment':  return 'video/mp2t';
    case 'qoe-api':      return 'application/json';
    case 'app-asset':    return 'application/octet-stream';
    default:             return 'application/octet-stream';
  }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

function fmtMs(ms: number): string {
  if (ms < 1_000) return `${ms}ms`;
  return `${(ms / 1_000).toFixed(2)}s`;
}

function truncate(s: string, n: number): string {
  return s.length > n ? s.slice(0, n) + '…' : s;
}
