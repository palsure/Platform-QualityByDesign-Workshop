#!/usr/bin/env node
'use strict';

/**
 * Reads the Playwright JSON reporter output, then writes:
 *   - pr-comment.md       — Markdown table for a GitHub PR comment
 *   - slack-payload.json  — Block Kit payload for the Slack action
 * And appends to $GITHUB_OUTPUT:
 *   - gate_passed=true|false
 *   - pass_rate=<integer 0-100>
 *
 * Expected env vars (set by the workflow):
 *   GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_SHA,
 *   PR_NUMBER, BRANCH_NAME, PLAYWRIGHT_OUTCOME,
 *   QUALITY_GATE_THRESHOLD (default: 80)
 */

const fs   = require('fs');
const path = require('path');

// ── Context from env ─────────────────────────────────────────────
const REPO    = process.env.GITHUB_REPOSITORY || 'owner/repo';
const RUN_ID  = process.env.GITHUB_RUN_ID     || '';
const SHA     = (process.env.GITHUB_SHA || '').slice(0, 7) || 'unknown';
const PR      = process.env.PR_NUMBER          || '';
const BRANCH  = process.env.BRANCH_NAME        || 'unknown';
const OUTCOME   = process.env.PLAYWRIGHT_OUTCOME      || 'failure';
const THRESHOLD = parseInt(process.env.QUALITY_GATE_THRESHOLD ?? '80', 10);

const BASE_URL = `https://github.com/${REPO}`;
const RUN_URL  = RUN_ID ? `${BASE_URL}/actions/runs/${RUN_ID}` : BASE_URL;
const PR_URL   = PR      ? `${BASE_URL}/pull/${PR}`            : BASE_URL;

// ── Read JSON report ─────────────────────────────────────────────
const RESULTS_FILE = path.resolve(__dirname, '../../web-player/playwright-results.json');

let report = null;
try {
  report = JSON.parse(fs.readFileSync(RESULTS_FILE, 'utf8'));
} catch {
  console.warn('⚠️  Could not read playwright-results.json — using empty results.');
}

// ── Flatten all specs from nested suites ─────────────────────────
function collectSpecs(suites, acc = []) {
  for (const suite of suites || []) {
    for (const spec of suite.specs || []) {
      const firstTest   = spec.tests?.[0];
      const firstResult = firstTest?.results?.[0];
      acc.push({
        title:    spec.title,
        ok:       spec.ok ?? (firstResult?.status === 'passed'),
        status:   firstResult?.status ?? (spec.ok ? 'passed' : 'failed'),
        duration: firstResult?.duration ?? 0,
      });
    }
    collectSpecs(suite.suites, acc);
  }
  return acc;
}

const specs  = report ? collectSpecs(report.suites) : [];
const rawStats = report?.stats ?? {};

const total    = specs.length || (rawStats.expected ?? 0) + (rawStats.unexpected ?? 0) + (rawStats.skipped ?? 0);
const passed   = specs.filter(s => s.ok).length  || rawStats.expected   || 0;
const failed   = specs.filter(s => !s.ok).length || rawStats.unexpected || 0;
const skipped  = rawStats.skipped || 0;
const passRate = total > 0 ? Math.round((passed / total) * 100) : 0;

// Quality gate: pass rate must meet the threshold AND Playwright must not have crashed
const gatePassed = passRate >= THRESHOLD && OUTCOME !== 'failure';
const allGood    = gatePassed;   // alias kept for existing references

// ── Helpers ───────────────────────────────────────────────────────
const mdIcon       = ok => ok ? '✅' : '❌';
const fmtDur       = ms => ms > 0 ? ` (${(ms / 1000).toFixed(1)}s)` : '';
const overallEmoji = allGood ? '✅' : '❌';

/** Pad a value to `len` chars. Emoji count as 2 visual chars so we subtract 1 extra per emoji. */
function pad(val, len) {
  const s = String(val);
  const emojiWidth = (s.match(/\p{Emoji_Presentation}/gu) || []).length;
  return s + ' '.repeat(Math.max(0, len - s.length + emojiWidth - emojiWidth));  // keep simple
}
function padEnd(val, len) {
  const s = String(val);
  return s.padEnd(len);
}

/** "11 min, 35 sec" from milliseconds */
function fmtMs(ms) {
  if (!ms || ms <= 0) return 'n/a';
  const s = Math.round(ms / 1000);
  const m = Math.floor(s / 60);
  return m > 0 ? `${m} min, ${s % 60} sec` : `${s} sec`;
}

// ── PR comment (Markdown) ────────────────────────────────────────
const mdTableRows = specs.length
  ? specs.map(s => `| ${mdIcon(s.ok)} | ${s.title}${fmtDur(s.duration)} |`).join('\n')
  : '| ℹ️ | No test results found — check the run log |';

const gateEmoji  = gatePassed ? '🟢' : '🔴';
const gateLabel  = gatePassed ? 'PASSED' : 'FAILED';
const gateReason = gatePassed
  ? `Pass rate ${passRate}% ≥ ${THRESHOLD}% threshold`
  : passRate < THRESHOLD
    ? `Pass rate ${passRate}% is below the ${THRESHOLD}% threshold`
    : 'Playwright runner exited with an error';

const summaryBadge = [
  `**${passed}/${total} passed (${passRate}%)**`,
  failed  > 0 ? `**${failed} failed**` : null,
  skipped > 0 ? `${skipped} skipped`   : null,
].filter(Boolean).join(' · ');

const prComment = `## ${overallEmoji} QoE E2E Gate Results

| Quality Gate | Threshold | Actual | Verdict |
|---|---|---|---|
| Pass Rate | ${THRESHOLD}% | ${passRate}% | ${gateEmoji} **${gateLabel}** |

${summaryBadge} &nbsp;·&nbsp; Branch: \`${BRANCH}\` &nbsp;·&nbsp; Commit: \`${SHA}\`

> ${gateReason}

| | Test |
|---|---|
${mdTableRows}

<details>
<summary>🔗 Links</summary>

- [GitHub Actions run](${RUN_URL})
- [Download HTML report](${RUN_URL}#artifacts) *(retained 14 days)*
${PR ? `- [Pull request #${PR}](${PR_URL})` : ''}
</details>

*Updated automatically on every push to this PR.*`;

// ── Slack — table builder ─────────────────────────────────────────
//  Columns: Passed(%)  Failed  Skipped  Total  Status  Reports  Scenario
const C = { pct: 13, fail: 8, skip: 9, total: 8, status: 8, reports: 12 };
const SEP = '─'.repeat(72);

const TABLE_HEADER =
  padEnd('Passed(%)', C.pct) +
  padEnd('Failed',   C.fail) +
  padEnd('Skipped',  C.skip) +
  padEnd('Total',    C.total) +
  padEnd('Status',   C.status) +
  padEnd('Reports',  C.reports) +
  'Scenario';

function tableRow(passCount, failCount, skipCount, tot, ok, reportsLabel, scenario) {
  const pct  = tot > 0 ? Math.round((passCount / tot) * 100) : 0;
  const pStr = `${passCount} (${pct}%)`;
  // Status: emoji (visual width ≈2) + space to fill column
  const statusCol = (ok ? '🟢' : '🔴') + ' '.repeat(C.status - 2);
  return (
    padEnd(pStr,      C.pct)    +
    padEnd(failCount, C.fail)   +
    padEnd(skipCount, C.skip)   +
    padEnd(tot,       C.total)  +
    statusCol                   +
    padEnd(reportsLabel, C.reports) +
    scenario
  );
}

// One row per test scenario (analogous to one row per platform in the screenshot)
const tableRows = specs.length
  ? specs.map(s =>
      tableRow(
        s.ok ? 1 : 0,
        s.ok ? 0 : 1,
        0,
        1,
        s.ok,
        'HTML Report',
        s.title,
      )
    )
  : ['No test results — check the run log'];

// Summary / total row
const totalRow = tableRow(passed, failed, skipped, total, allGood, 'HTML Report', 'TOTAL');

const tableBlock =
  `${TABLE_HEADER}\n${SEP}\n` +
  tableRows.join('\n') +
  `\n${SEP}\n${totalRow}\n${SEP}`;

// ── Slack — footer lines ──────────────────────────────────────────
const durationStr  = fmtMs(report?.stats?.duration);
const buildUrlText = RUN_ID ? `<${RUN_URL}|${REPO}/actions/runs/${RUN_ID}>` : 'n/a';
const artifactText = `<${RUN_URL}|HTML Report>`;
const triggeredBy  = PR ? `Pull Request <${PR_URL}|#${PR}>` : 'Push';

// ── Slack — quality gate banner line ──────────────────────────────
const qgBannerIcon = gatePassed ? ':large_green_circle:' : ':red_circle:';
const qgBanner =
  `${qgBannerIcon}  *Quality Gate: ${gateLabel}*  ` +
  `(${passRate}% passed  |  threshold ${THRESHOLD}%)  —  ${gateReason}`;

// ── Slack Block Kit payload ───────────────────────────────────────
const slackPayload = {
  blocks: [
    // ① Title
    {
      type: 'section',
      text: {
        type: 'mrkdwn',
        text: `*QoE Web Player — E2E Automation Tests Report*`,
      },
    },
    // ② Metadata line
    {
      type: 'section',
      text: {
        type: 'mrkdwn',
        text: `*Branch:* ${BRANCH}  |  *Test Groups:* e2e-gates  |  *Commit:* \`${SHA}\``,
      },
    },
    // ③ Quality Gate verdict banner
    {
      type: 'section',
      text: { type: 'mrkdwn', text: qgBanner },
    },
    // ④ Table in a code block for column-aligned monospace rendering
    {
      type: 'section',
      text: {
        type: 'mrkdwn',
        text: `\`\`\`\n${tableBlock}\n\`\`\``,
      },
    },
    // ⑤ Footer metadata
    {
      type: 'section',
      text: {
        type: 'mrkdwn',
        text: [
          `*Duration:* ${durationStr}`,
          `*BuildUrl:* ${buildUrlText}`,
          `*Artifacts:* ${artifactText}`,
          `*Triggered By:* ${triggeredBy}`,
        ].join('\n'),
      },
    },
  ],
};

// ── Write output files ────────────────────────────────────────────
const OUT = path.resolve(__dirname, '../..');   // repo root
fs.writeFileSync(path.join(OUT, 'pr-comment.md'),      prComment,                            'utf8');
fs.writeFileSync(path.join(OUT, 'slack-payload.json'), JSON.stringify(slackPayload, null, 2), 'utf8');

// ── Write GitHub Actions step outputs ────────────────────────────
// GITHUB_OUTPUT is the file-based output mechanism for Actions (no injection risk).
const ghOutput = process.env.GITHUB_OUTPUT;
if (ghOutput) {
  fs.appendFileSync(ghOutput, `gate_passed=${gatePassed}\n`);
  fs.appendFileSync(ghOutput, `pass_rate=${passRate}\n`);
  fs.appendFileSync(ghOutput, `threshold=${THRESHOLD}\n`);
}

// ── Console summary ───────────────────────────────────────────────
console.log('');
console.log('┌─────────────────────────────────────────────┐');
console.log(`│  Quality Gate (≥${THRESHOLD}%)                          │`);
console.log(`│  Pass rate : ${String(passRate + '%').padEnd(6)}  ${passed}/${total} tests passed    │`);
console.log(`│  Verdict   : ${gatePassed ? 'PASSED ✅' : 'FAILED ❌'}                      │`);
console.log('└─────────────────────────────────────────────┘');
console.log('');
console.log('✅ Wrote pr-comment.md and slack-payload.json');
