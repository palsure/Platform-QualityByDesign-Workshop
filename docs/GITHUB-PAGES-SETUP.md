# GitHub Pages — Allure Report Links

CI pipelines publish Allure HTML reports to the **`gh-pages`** branch. Slack and PR comments link to:

```text
https://<owner>.github.io/<repo>/allure/<run-number>/<platform>/<stage>/
```

Example (build #8, API BAT):

```text
https://palsure.github.io/Platform-QualityByDesign-Workshop/allure/8/api/bat/
```

A **404 “There isn’t a GitHub Pages site here”** means Pages is not enabled yet, or the report was never pushed to `gh-pages`.

---

## One-time setup (required)

### 1. Enable GitHub Pages

1. Open the repo on GitHub → **Settings → Pages**.
2. Under **Build and deployment**:
   - **Source:** Deploy from a branch
   - **Branch:** `gh-pages` / **`/ (root)`**
3. Click **Save**.

If `gh-pages` does not exist yet, run a pipeline first (see step 2) — the publish action creates the branch on first deploy.

### 2. Run a pipeline that publishes reports

Any of these workflows call `publish-allure` after test stages:

- `streaming-app-api.yml`
- `streaming-app-web.yml`
- `streaming-app-android.yml`
- `streaming-app-ios.yml`

In the Actions log, open a job (e.g. **BAT — Build Acceptance Tests**) and confirm:

```text
✅ Deployed: https://palsure.github.io/Platform-QualityByDesign-Workshop/allure/8/api/bat/
```

If you see:

```text
⚠️ Allure publish failed … Could not push Allure report to gh-pages
```

check **Settings → Actions → General → Workflow permissions** → **Read and write permissions** (needed for `GITHUB_TOKEN` to push `gh-pages`).

### 3. Wait for Pages to go live

After the first successful push to `gh-pages`, wait **1–3 minutes**. **Settings → Pages** should show:

```text
Your site is live at https://palsure.github.io/Platform-QualityByDesign-Workshop/
```

Then open the full report URL (include trailing path `/allure/<run>/<platform>/<stage>/`).

---

## URL pattern

Reports are stored per workflow run number:

| Segment | Meaning |
|---|---|
| `allure/` | Root folder on `gh-pages` |
| `8/` | GitHub Actions `run_number` (build #8) |
| `api/` | Platform: `api`, `web`, `android`, `ios` |
| `bat/` | Stage: `unit`, `bat`, `smoke`, `regression` |

Slack “View Report” links are built in the workflow, e.g.:

```yaml
report-url: https://${{ github.repository_owner }}.github.io/${{ github.event.repository.name }}/allure/${{ github.run_number }}/api/bat
```

No manual URL configuration — fix Pages setup once and links work for every run.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| **404 — “There isn’t a GitHub Pages site here”** | Enable Pages from **`gh-pages`** branch (step 1) |
| **`gh-pages` branch missing** | Re-run pipeline; check publish-allure log for push errors |
| **Push rejected / publish failed after 6 retries** | Re-run workflow; concurrent jobs may race — retry usually succeeds |
| **Pages enabled but sub-path 404** | Confirm that run’s publish step logged `✅ Deployed:` for that exact path |
| **Blank or broken Allure UI** | Ensure `.nojekyll` exists on `gh-pages` (publish action adds it automatically) |
| **Link works for owner but not collaborators** | Pages must be **public** (default for public repos) |

### Verify locally

```bash
git fetch origin gh-pages
git checkout gh-pages
ls allure/8/api/bat/index.html   # should exist after a successful API BAT run
```

---

## Related

- Publish action: [`.github/actions/publish-allure/`](../.github/actions/publish-allure/action.yml)
- Slack notifications: [SLACK-SETUP.md](SLACK-SETUP.md)
