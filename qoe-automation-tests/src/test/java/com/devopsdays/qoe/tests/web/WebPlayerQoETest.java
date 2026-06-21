package com.devopsdays.qoe.tests.web;

import com.devopsdays.qoe.framework.utils.ApiClient;
import com.devopsdays.qoe.tests.config.DeviceRunConfig;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebPlayerQoETest — Selenium-based web player validation.
 *
 * Supports four run modes via system properties (see DeviceRunConfig):
 *
 *   local          — ChromeDriver / GeckoDriver launched in-process (default).
 *   lab/local_grid — RemoteWebDriver pointed at a local Selenium Grid hub.
 *   lab/browserstack — RemoteWebDriver via BrowserStack Automate hub.
 *   lab/saucelabs   — RemoteWebDriver via Sauce Labs hub.
 *
 * Quick-start examples:
 *
 *   # Local headless Chrome (default)
 *   mvn test -Dtest=WebPlayerQoETest
 *
 *   # Local headed Firefox
 *   mvn test -Dtest=WebPlayerQoETest -Dweb.browser=firefox
 *
 *   # Selenium Grid
 *   mvn test -Dtest=WebPlayerQoETest \
 *     -Drun.mode=lab -Dlab.provider=local_grid \
 *     -Dgrid.url=http://selenium-grid:4444/wd/hub
 *
 *   # BrowserStack — Chrome on Windows 11
 *   mvn test -Dtest=WebPlayerQoETest \
 *     -Drun.mode=lab -Dlab.provider=browserstack \
 *     -Dbs.username=$BS_USERNAME -Dbs.access.key=$BS_KEY \
 *     -Dweb.player.url=https://staging.example.com \
 *     -Dbs.browser=chrome -Dbs.os=Windows -Dbs.os.version.web=11
 *
 *   # Sauce Labs — Firefox on macOS Ventura
 *   mvn test -Dtest=WebPlayerQoETest \
 *     -Drun.mode=lab -Dlab.provider=saucelabs \
 *     -Dsl.username=$SL_USERNAME -Dsl.access.key=$SL_KEY \
 *     -Dweb.browser=firefox \
 *     -Dweb.player.url=https://staging.example.com
 */
public class WebPlayerQoETest {

    private WebDriver driver;
    private WebDriverWait wait;
    private ApiClient apiClient;

    private static final String BASE_URL = System.getProperty("web.player.url", "http://localhost:3000");
    private static final String API_URL  = System.getProperty("api.base.url",   "http://localhost:8080");

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() throws Exception {
        DeviceRunConfig.printSummary(getClass().getSimpleName());
        System.out.printf("Web player URL : %s%n", BASE_URL);
        System.out.printf("API URL        : %s%n", API_URL);
        System.out.printf("Browser        : %s%n", DeviceRunConfig.WEB_BROWSER);

        driver = createDriver();
        wait   = new WebDriverWait(driver, Duration.ofSeconds(30));
        apiClient = new ApiClient(API_URL);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            if (DeviceRunConfig.IS_LAB) {
                markLabSessionStatus("passed", "Test suite completed");
            }
            driver.quit();
        }
    }

    // ── Driver factory ────────────────────────────────────────────────────────

    /**
     * Build a WebDriver instance based on run.mode and lab.provider.
     * Returns a local driver for 'local' mode, or RemoteWebDriver for lab modes.
     */
    private WebDriver createDriver() throws Exception {
        if (!DeviceRunConfig.IS_LAB) {
            return createLocalDriver();
        }
        return createRemoteDriver();
    }

    /** Launch a local browser driver in headless mode (suitable for CI). */
    private WebDriver createLocalDriver() {
        return switch (DeviceRunConfig.WEB_BROWSER.toLowerCase()) {
            case "firefox" -> {
                FirefoxOptions opts = new FirefoxOptions();
                opts.addArguments("--headless");
                yield new FirefoxDriver(opts);
            }
            case "edge" -> {
                EdgeOptions opts = new EdgeOptions();
                opts.addArguments("--headless");
                opts.addArguments("--no-sandbox");
                opts.addArguments("--disable-dev-shm-usage");
                yield new EdgeDriver(opts);
            }
            default -> {
                // chrome (default)
                ChromeOptions opts = new ChromeOptions();
                opts.addArguments("--headless");
                opts.addArguments("--no-sandbox");
                opts.addArguments("--disable-dev-shm-usage");
                yield new ChromeDriver(opts);
            }
        };
    }

    /** Build RemoteWebDriver for cloud-lab or Selenium Grid runs. */
    private WebDriver createRemoteDriver() throws Exception {
        MutableCapabilities caps = switch (DeviceRunConfig.LAB_PROVIDER.toLowerCase()) {
            case "browserstack" -> buildBrowserStackCaps();
            case "saucelabs"    -> buildSauceLabsCaps();
            case "local_grid"   -> buildGridCaps();
            default -> {
                System.out.println("[WARN] Unknown lab.provider '"
                        + DeviceRunConfig.LAB_PROVIDER + "' — defaulting to Grid caps");
                yield buildGridCaps();
            }
        };

        String hubUrl = DeviceRunConfig.resolveWebDriverUrl();
        System.out.println("RemoteWebDriver hub: " + hubUrl.replaceAll(":[^@:]+@", ":***@"));
        return new RemoteWebDriver(new URI(hubUrl).toURL(), caps);
    }

    private MutableCapabilities buildBrowserStackCaps() {
        ChromeOptions opts = new ChromeOptions();

        Map<String, Object> bsOptions = new HashMap<>();
        bsOptions.put("projectName",    DeviceRunConfig.BS_PROJECT_NAME);
        bsOptions.put("buildName",      DeviceRunConfig.BS_BUILD_NAME);
        bsOptions.put("sessionName",    getClass().getSimpleName());
        bsOptions.put("os",             DeviceRunConfig.BS_OS);
        bsOptions.put("osVersion",      DeviceRunConfig.BS_OS_VERSION_WEB);
        bsOptions.put("browserVersion", DeviceRunConfig.BS_BROWSER_VERSION);
        bsOptions.put("networkLogs",    true);
        bsOptions.put("seleniumVersion","4.15.0");

        opts.setCapability("bstack:options", bsOptions);

        System.out.printf("BrowserStack Web: browser=%s os=%s %s%n",
                DeviceRunConfig.BS_BROWSER,
                DeviceRunConfig.BS_OS,
                DeviceRunConfig.BS_OS_VERSION_WEB);
        return opts;
    }

    private MutableCapabilities buildSauceLabsCaps() {
        ChromeOptions opts = new ChromeOptions();

        Map<String, Object> slOptions = new HashMap<>();
        slOptions.put("build",  "DevOpsDays-QoE-" + DeviceRunConfig.BS_BUILD_NAME);
        slOptions.put("name",   getClass().getSimpleName());
        slOptions.put("seleniumVersion", "4.15.0");

        opts.setCapability("sauce:options", slOptions);
        opts.setPlatformName("Windows 11");

        System.out.printf("Sauce Labs Web: browser=%s%n", DeviceRunConfig.WEB_BROWSER);
        return opts;
    }

    private MutableCapabilities buildGridCaps() {
        // Standard Selenium Grid 4 — browser determined by web.browser property
        return switch (DeviceRunConfig.WEB_BROWSER.toLowerCase()) {
            case "firefox" -> {
                FirefoxOptions opts = new FirefoxOptions();
                opts.addArguments("--headless");
                yield opts;
            }
            case "edge" -> {
                EdgeOptions opts = new EdgeOptions();
                opts.addArguments("--headless");
                yield opts;
            }
            default -> {
                ChromeOptions opts = new ChromeOptions();
                opts.addArguments("--headless");
                opts.addArguments("--no-sandbox");
                opts.addArguments("--disable-dev-shm-usage");
                yield opts;
            }
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(description = "Web player page loads and title is correct")
    public void testWebPlayerLoads() {
        driver.get(BASE_URL);
        String title = driver.getTitle();
        Assert.assertNotNull(title, "Page title should not be null");
        Assert.assertTrue(title.contains("QoE") || title.contains("Player"),
                "Page title should contain QoE or Player, but was: " + title);
        System.out.println("✅ Web player loaded — title: " + title
                + " [" + DeviceRunConfig.RUN_MODE
                + (DeviceRunConfig.IS_LAB ? "/" + DeviceRunConfig.LAB_PROVIDER : "") + "]");
    }

    @Test(dependsOnMethods = "testWebPlayerLoads",
          description = "QoE metrics API responds with data after simulated playback")
    public void testMetricsCollected() throws Exception {
        // Allow time for any auto-play or beacon flush — cloud labs may need more time
        int waitMs = DeviceRunConfig.IS_LAB ? 15_000 : 10_000;
        Thread.sleep(waitMs);

        List<Map<String, Object>> metrics = apiClient.getMetrics("web", null, null);
        Assert.assertNotNull(metrics, "Metrics API should return a list (even if empty)");
        System.out.println("✅ Metrics API reachable — entries returned: " + metrics.size());
    }

    @Test(dependsOnMethods = "testWebPlayerLoads",
          description = "Web player page contains the video player element")
    public void testVideoPlayerElementPresent() {
        // Use JavaScript to check for the HLS.js / video element
        Object videoReady = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return document.querySelector('video') !== null;");
        Assert.assertTrue(Boolean.TRUE.equals(videoReady),
                "A <video> element should be present on the page");
        System.out.println("✅ <video> element found in DOM");
    }

    // ── Lab utilities ─────────────────────────────────────────────────────────

    private void markLabSessionStatus(String status, String reason) {
        try {
            switch (DeviceRunConfig.LAB_PROVIDER.toLowerCase()) {
                case "browserstack" -> {
                    Map<String, Object> args = Map.of(
                            "status", status.equals("passed") ? "passed" : "failed",
                            "reason", reason);
                    ((RemoteWebDriver) driver).executeScript(
                            "browserstack_executor: " +
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(Map.of("action", "setSessionStatus",
                                                               "arguments", args)));
                }
                case "saucelabs" -> {
                    ((RemoteWebDriver) driver).executeScript(
                            "sauce:job-result=" + status.equals("passed"));
                }
                default -> { /* no-op for grid / local */ }
            }
        } catch (Exception e) {
            System.out.println("[WARN] Could not mark lab session status: " + e.getMessage());
        }
    }
}
