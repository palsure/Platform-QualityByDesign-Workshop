# Local Testing Guide

Step-by-step instructions to run unit tests, E2E tests, and view Allure reports for every module.

---

## Prerequisites

| Tool | Min version | Install |
|---|---|---|
| Java (JDK) | 21+ | `brew install --cask temurin@21` |
| Gradle | via wrapper (`gradlew`) | included in each module |
| Docker Desktop | 24+ | [docs.docker.com](https://docs.docker.com/desktop/mac/install/) |
| Node.js | 18+ | `brew install node` |
| npm | 9+ | bundled with Node |
| Maven | 3.9+ | `brew install maven` |
| Allure CLI | 2.27+ | `brew install allure` |
| Xcode | 15+ | Mac App Store *(iOS only)* |

---

## Start the shared backend stack

Most E2E and automation tests target the live API.  
Start it once and leave it running for all modules:

```bash
# from the repository root
docker compose up -d

# verify everything is healthy
docker compose ps
curl http://localhost:8080/actuator/health
```

**Running services:**

| Service | URL |
|---|---|
| Backend API | http://localhost:8080 |
| Web Player | http://localhost:3000 |
| Nginx (reverse proxy) | http://localhost:8081 |
| PostgreSQL | localhost:5432 |

Stop the stack when done:

```bash
docker compose down
```

---

## 1 — Backend API (`backend-api/`)

The API uses separate Gradle tasks for each test stage.  
E2E tests (BAT, Smoke, Regression) are **fully self-contained**: Testcontainers
spins up a throwaway PostgreSQL container automatically — **no running stack required**.

### Test stages

| Stage | Tag | Gradle task | Purpose |
|---|---|---|---|
| Unit | `@Tag("unit")` | `unitTest` | Fast Mockito tests, no Docker needed |
| BAT | `@Tag("BAT")` | `batTest` | Build Acceptance Tests — core sanity gate |
| Smoke | `@Tag("Smoke")` | `smokeTest` | Broader coverage after BAT passes |
| Regression | `@Tag("Regression")` | `regressionTest` | Full suite, run on-demand or nightly |
| All E2E | `@Tag("e2e")` | `e2eTest` | BAT + Smoke + Regression in one run |

### 1.0 Clean build

Remove all compiled classes, Allure results, and generated reports before a fresh run:

```bash
cd backend-api

# Full clean (classes + Allure results + reports)
./gradlew cleanAll

# Allure-only clean (keeps compiled classes — faster)
./gradlew cleanAllure
```

---

### 1.1 Unit tests

```bash
cd backend-api
./gradlew unitTest
```

- Runs tests tagged `@Tag("unit")` (Mockito, no Docker)
- Fast (~10 s)
- Every test class includes `@ExtendWith(AllureJunit5.class)` and `Allure.step()` calls  
  for full step details in the Allure report

**View the JUnit HTML report:**

```bash
open build/reports/tests/unitTest/index.html
```

**Generate and open the Allure report:**

```bash
./gradlew unitTest allureReportUnit
allure serve build/allure-results/unit --port 5050
# open http://127.0.0.1:5050
```

**Clean + run + report in one command:**

```bash
./gradlew cleanAll unitTest allureReportUnit
allure serve build/allure-results/unit --port 5050
```

---

### 1.2 BAT — Build Acceptance Tests

BAT tests are the critical gate. All must pass before Smoke tests are allowed to run.

```bash
cd backend-api
./gradlew batTest
```

- Docker must be running (Testcontainers starts PostgreSQL automatically)
- 100% pass rate required — any failure blocks the build

**Generate and open the BAT Allure report:**

```bash
./gradlew batTest allureReportBat
allure serve build/allure-results/bat --port 5052
# open http://127.0.0.1:5052
```

**Clean + run + report:**

```bash
./gradlew cleanAll batTest allureReportBat
allure serve build/allure-results/bat --port 5052
```

---

### 1.3 Smoke tests

Smoke tests run after the BAT gate passes. They cover broader API functionality.

```bash
cd backend-api
./gradlew smokeTest
```

**Generate and open the Smoke Allure report:**

```bash
./gradlew smokeTest allureReportSmoke
allure serve build/allure-results/smoke --port 5053
# open http://127.0.0.1:5053
```

**Clean + run + report:**

```bash
./gradlew cleanAll smokeTest allureReportSmoke
allure serve build/allure-results/smoke --port 5053
```

---

### 1.4 Regression tests

Full regression suite — run on-demand or as a nightly job.

```bash
cd backend-api
./gradlew regressionTest
```

**Generate and open the Regression Allure report:**

```bash
./gradlew regressionTest allureReportRegression
allure serve build/allure-results/regression --port 5054
# open http://127.0.0.1:5054
```

**Clean + run + report:**

```bash
./gradlew cleanAll regressionTest allureReportRegression
allure serve build/allure-results/regression --port 5054
```

---

### 1.5 Run all E2E stages together

```bash
cd backend-api
./gradlew e2eTest allureReportE2e
allure serve build/allure-results/e2e --port 5051
# open http://127.0.0.1:5051
```

---

### 1.6 Full pipeline simulation (BAT → Smoke → Regression)

Mimics the CI pipeline locally:

```bash
cd backend-api

# Step 1 — clean slate
./gradlew cleanAll

# Step 2 — unit tests
./gradlew unitTest

# Step 3 — BAT (gate: all must pass)
./gradlew batTest
# Check: exit code 0 = gate PASSED, exit code non-0 = gate FAILED (stop here)

# Step 4 — Smoke (only if BAT passed)
./gradlew smokeTest

# Step 5 — Regression
./gradlew regressionTest

# Step 6 — view all reports simultaneously
allure serve build/allure-results/unit       --port 5050 &
allure serve build/allure-results/bat        --port 5052 &
allure serve build/allure-results/smoke      --port 5053 &
allure serve build/allure-results/regression --port 5054 &
```

Stop all Allure servers:

```bash
pkill -f "allure.*serve"
```

---

### 1.7 Allure report reference

| Report | Command | URL |
|---|---|---|
| Unit | `./gradlew allureReportUnit` | `allure serve build/allure-results/unit --port 5050` |
| BAT | `./gradlew allureReportBat` | `allure serve build/allure-results/bat --port 5052` |
| Smoke | `./gradlew allureReportSmoke` | `allure serve build/allure-results/smoke --port 5053` |
| Regression | `./gradlew allureReportRegression` | `allure serve build/allure-results/regression --port 5054` |
| All E2E | `./gradlew allureReportE2e` | `allure serve build/allure-results/e2e --port 5051` |

> Always use `allure serve <results-dir> --port <port>` (not `open index.html`) to avoid  
> browser security restrictions that block the report from loading.

---

## 2 — Web Player (`web-player/`)

### 2.1 Unit tests (Vitest)

No server required — runs in a jsdom environment.

```bash
cd web-player
npm ci          # first time only
npm test
```

**JUnit XML output:** `web-player/test-results/vitest-junit.xml`

**Watch mode** (re-runs on file save):

```bash
npm run test:watch
```

### 2.2 E2E tests (Playwright) — against the Docker app

The Docker web player at `http://localhost:3000` must be running.

```bash
cd web-player

# install Chromium browser (first time only)
npm run e2e:install

# run E2E tests against localhost:3000
npm run e2e:docker
```

**Watch the browser while tests run:**

```bash
npm run e2e:docker:headed
```

**Debug a single test interactively:**

```bash
npm run e2e:debug
```

**Throttled network tests** (simulates slow connections):

```bash
npm run e2e:docker:throttle
```

**View the Playwright HTML report:**

```bash
npx playwright show-report
```

> The report is saved to `web-player/playwright-report/index.html`.

### 2.3 Allure report (Web E2E)

```bash
# requires allure CLI: brew install allure
npm run allure:report
```

---

## 3 — Android Player (`android-player/`)

Requires **Java 21+** and **Android SDK** (or Android Studio).

### 3.1 Unit tests

```bash
cd android-player
./gradlew test
```

Runs all JUnit 4 unit tests in `app/src/test/`.

**Run only the Debug variant** (faster):

```bash
./gradlew testDebugUnitTest
```

**View the HTML report:**

```bash
open app/build/reports/tests/testDebugUnitTest/index.html
```

**JUnit XML output:** `app/build/test-results/testDebugUnitTest/`

### 3.2 Lint

```bash
./gradlew lint
```

**View lint report:**

```bash
open app/build/reports/lint-results-debug.html
```

### 3.3 Build debug APK

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### 3.4 Instrumented / UI tests  
*(requires a connected device or running emulator)*

```bash
./gradlew connectedDebugAndroidTest
```

---

## 4 — iOS Player (`ios-player/`)

Requires **macOS with Xcode 15+**.

### 4.1 Unit tests — Swift Package

```bash
cd ios-player
swift test
```

Tests inside `Tests/StreamAppTests/` are discovered and run automatically.

**Verbose output** (shows individual test case pass/fail):

```bash
swift test --verbose
```

### 4.2 Build the Xcode app (simulator)

```bash
xcodebuild \
  -project QoePlayerApp.xcodeproj \
  -scheme QoePlayerApp \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  clean build \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO \
  | xcpretty
```

> Install `xcpretty` for readable output: `gem install xcpretty`

### 4.3 Run Xcode tests in the simulator

```bash
xcodebuild test \
  -project QoePlayerApp.xcodeproj \
  -scheme QoePlayerApp \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  | xcpretty --report html --output build/reports/tests/index.html
```

**View the report:**

```bash
open build/reports/tests/index.html
```

---

## 5 — Automation / System Tests (`qoe-automation-tests/`)

These are TestNG integration tests that run **against the live API**.  
The Docker stack must be running (`docker compose up -d`).

### 5.1 Run all automation tests

```bash
cd qoe-automation-tests
mvn test -Dapi.base.url=http://localhost:8080
```

### 5.2 Run a specific test suite

```bash
# API-only suite
mvn test -Dapi.base.url=http://localhost:8080 \
         -DsuiteXmlFile=src/test/resources/testng.xml

# Mobile suite
mvn test -Dapi.base.url=http://localhost:8080 \
         -DsuiteXmlFile=src/test/resources/testng-mobile.xml
```

### 5.3 Surefire XML results

Maven Surefire saves JUnit-compatible XML to:

```
qoe-automation-tests/target/surefire-reports/TEST-*.xml
```

### 5.4 Allure report

```bash
# generate HTML from allure-results/ captured during the test run
mvn allure:report

# open the report (use allure serve to avoid browser file:// restrictions)
allure serve target/allure-results --port 5055
# open http://127.0.0.1:5055
```

---

## Quick-reference cheat sheet

### Run commands

```
┌──────────────────┬──────────────────────────────┬───────────────────────────────────┐
│ Module           │ Unit tests                   │ E2E / Integration                 │
├──────────────────┼──────────────────────────────┼───────────────────────────────────┤
│ backend-api/     │ ./gradlew unitTest            │ ./gradlew batTest      (BAT)      │
│                  │                              │ ./gradlew smokeTest    (Smoke)    │
│                  │                              │ ./gradlew regressionTest          │
│                  │                              │ ./gradlew e2eTest      (all E2E)  │
│ web-player/      │ npm test                     │ npm run e2e:docker                │
│ android-player/  │ ./gradlew test               │ ./gradlew connectedTest           │
│ ios-player/      │ swift test                   │ xcodebuild test …                 │
│ qoe-auto-tests/  │ —                            │ mvn test -Dapi.base.url…          │
└──────────────────┴──────────────────────────────┴───────────────────────────────────┘
```

### Clean commands (backend-api/)

```
┌────────────────────────────┬───────────────────────────────────────────────────────┐
│ Command                    │ What it removes                                       │
├────────────────────────────┼───────────────────────────────────────────────────────┤
│ ./gradlew cleanAll         │ Compiled classes + all Allure results + HTML reports  │
│ ./gradlew cleanAllure      │ Allure results + HTML reports only (keeps classes)    │
│ ./gradlew clean            │ Compiled classes only (standard Gradle clean)         │
└────────────────────────────┴───────────────────────────────────────────────────────┘
```

### Allure report commands (backend-api/)

```
┌──────────────────────────────────┬──────────────────────────────────────────────────┐
│ Stage    │ Generate               │ Serve (browser URL)                             │
├──────────┼────────────────────────┼─────────────────────────────────────────────────┤
│ Unit     │ allureReportUnit       │ allure serve build/allure-results/unit  :5050   │
│ BAT      │ allureReportBat        │ allure serve build/allure-results/bat   :5052   │
│ Smoke    │ allureReportSmoke      │ allure serve build/allure-results/smoke :5053   │
│ Regression│ allureReportRegression│ allure serve build/allure-results/regression :5054│
│ All E2E  │ allureReportE2e        │ allure serve build/allure-results/e2e   :5051   │
└──────────┴────────────────────────┴─────────────────────────────────────────────────┘
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `No matching toolchains found` (Gradle) | Install Java 21: `brew install --cask temurin@21` |
| `Could not connect to Docker` | Start Docker Desktop |
| `disabledWithoutDocker — Docker not available` | Ensure Docker Desktop is running and its socket is accessible |
| `backend is not healthy` | `docker compose logs backend` to inspect errors |
| `npm run e2e:docker` fails immediately | Ensure Docker web-player is running: `docker compose up -d` |
| `xcode-select` error on iOS build | `sudo xcode-select -switch /Applications/Xcode.app` |
| `allure: command not found` | `brew install allure` |
| Allure report shows `Loading…` (blank page) | Use `allure serve <dir> --port <port>` instead of opening `index.html` directly |
| Playwright browsers missing | `npm run e2e:install` (inside `web-player/`) |
| BAT gate blocks downstream tests | Check Allure BAT report — fix failing tests before running Smoke |
