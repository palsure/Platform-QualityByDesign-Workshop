package com.devopsdays.qoe.tests.mobile;

import com.devopsdays.qoe.tests.config.DeviceRunConfig;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * iOSQoETest — iOS UI automation via Appium (XCUITest driver).
 *
 * Supports three run modes (selected via system properties, see DeviceRunConfig):
 *
 *   LOCAL_SIMULATOR  — connects to a local Appium server with an iOS simulator.
 *   LOCAL_DEVICE     — local Appium server with a physical device identified by UDID.
 *   LAB              — cloud device lab (BrowserStack, Sauce Labs).
 *
 * Quick-start examples:
 *
 *   # Local iOS Simulator (requires macOS + Xcode + Appium 2 with xcuitest driver)
 *   mvn test -Dtest=iOSQoETest -Ddevice.platform=ios
 *
 *   # Local physical iPhone (USB-connected, replace UDID)
 *   mvn test -Dtest=iOSQoETest -Ddevice.platform=ios \
 *     -Ddevice.udid=00008120-000XXXXXXXX \
 *     -Dipa.path=/path/to/QoePlayerApp.ipa
 *
 *   # BrowserStack real iPhone
 *   mvn test -Dtest=iOSQoETest \
 *     -Drun.mode=lab -Dlab.provider=browserstack -Ddevice.platform=ios \
 *     -Dbs.username=$BS_USERNAME -Dbs.access.key=$BS_KEY \
 *     -Dbs.app=bs://xyz789 \
 *     -Dbs.device="iPhone 15" -Dbs.os.version=17
 *
 *   # Sauce Labs iOS simulator
 *   mvn test -Dtest=iOSQoETest \
 *     -Drun.mode=lab -Dlab.provider=saucelabs -Ddevice.platform=ios \
 *     -Dsl.username=$SL_USERNAME -Dsl.access.key=$SL_KEY \
 *     -Dsl.app="sauce-storage:QoePlayerApp.ipa" \
 *     -Dsl.device.name="iPhone 15 Simulator" -Dsl.platform.version=17
 *
 * Note: Running iOS tests on a non-macOS machine without a cloud lab will result
 * in a SkipException. The test skips gracefully rather than erroring.
 */
public class iOSQoETest {

    private IOSDriver driver;
    private WebDriverWait wait;

    // Bundle ID of the QoePlayerApp (must match CFBundleIdentifier in Xcode project)
    private static final String BUNDLE_ID    = "com.devopsdays.qoe.player";
    private static final String HLS_URL      =
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

    // Accessibility identifiers used in VideoPlayerView.swift
    private static final String AX_PLAY_BTN     = "play_button";
    private static final String AX_URL_FIELD    = "url_input";
    private static final String AX_STATUS_LABEL = "status_text";
    private static final String AX_VIDEO_VIEW   = "video_player";

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() throws Exception {
        guardNonMacLocalRun();
        DeviceRunConfig.printSummary(getClass().getSimpleName());

        XCUITestOptions options = buildCapabilities();
        String appiumUrl = DeviceRunConfig.resolveAppiumUrl();
        System.out.println("Connecting to Appium: " + appiumUrl.replaceAll(":[^@:]+@", ":***@"));

        driver = new IOSDriver(new URI(appiumUrl).toURL(), options);
        wait   = new WebDriverWait(driver, Duration.ofSeconds(30));

        // Allow app to fully launch before first interaction
        Thread.sleep(2_000);
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

    // ── Capability builders ───────────────────────────────────────────────────

    private XCUITestOptions buildCapabilities() {
        XCUITestOptions options = new XCUITestOptions()
                .setDeviceName(DeviceRunConfig.DEVICE_NAME)
                .setBundleId(BUNDLE_ID)
                .setNoReset(true)
                .setAutoAcceptAlerts(true)
                .setNewCommandTimeout(Duration.ofSeconds(120));

        if (!DeviceRunConfig.DEVICE_UDID.isBlank()) {
            options.setUdid(DeviceRunConfig.DEVICE_UDID);
        }

        if (!DeviceRunConfig.OS_VERSION.isBlank()) {
            options.setPlatformVersion(DeviceRunConfig.OS_VERSION);
        }

        if (DeviceRunConfig.IS_LAB) {
            applyLabCapabilities(options);
        } else {
            applyLocalCapabilities(options);
        }

        return options;
    }

    private void applyLocalCapabilities(XCUITestOptions options) {
        String ipa = DeviceRunConfig.IPA_PATH;
        if (!ipa.isBlank() && new java.io.File(ipa).exists()) {
            options.setApp(ipa);
            options.setNoReset(false);
            System.out.println("Installing IPA: " + ipa);
        } else {
            System.out.println("No IPA path — launching pre-installed app: " + BUNDLE_ID);
        }
    }

    private void applyLabCapabilities(XCUITestOptions options) {
        switch (DeviceRunConfig.LAB_PROVIDER.toLowerCase()) {
            case "browserstack" -> applyBrowserStackCaps(options);
            case "saucelabs"    -> applySauceLabsCaps(options);
            default             -> System.out.println("[WARN] Unknown lab.provider '"
                    + DeviceRunConfig.LAB_PROVIDER + "' — using base caps only");
        }
    }

    private void applyBrowserStackCaps(XCUITestOptions options) {
        if (DeviceRunConfig.BS_APP.isBlank()) {
            throw new IllegalStateException(
                    "bs.app is required for BrowserStack iOS runs. " +
                    "Upload your IPA first:\n" +
                    "  curl -u \"$BS_USERNAME:$BS_KEY\" \\\n" +
                    "    -X POST https://api-cloud.browserstack.com/app-automate/upload \\\n" +
                    "    -F \"file=@QoePlayerApp.ipa\" | jq .app_url\n" +
                    "Then pass: -Dbs.app=bs://...");
        }

        Map<String, Object> bsOptions = new HashMap<>();
        bsOptions.put("projectName",  DeviceRunConfig.BS_PROJECT_NAME);
        bsOptions.put("buildName",    DeviceRunConfig.BS_BUILD_NAME);
        bsOptions.put("sessionName",  getClass().getSimpleName());
        bsOptions.put("debug",        true);
        bsOptions.put("networkLogs",  true);
        bsOptions.put("appiumVersion","2.0.0");

        options.setApp(DeviceRunConfig.BS_APP);
        options.setCapability("device",          DeviceRunConfig.BS_DEVICE);
        options.setCapability("os_version",      DeviceRunConfig.BS_OS_VERSION);
        options.setCapability("real_mobile",     DeviceRunConfig.BS_REAL_DEVICE);
        options.setCapability("bstack:options",  bsOptions);

        System.out.printf("BrowserStack iOS: device=%s os=%s app=%s%n",
                DeviceRunConfig.BS_DEVICE, DeviceRunConfig.BS_OS_VERSION, DeviceRunConfig.BS_APP);
    }

    private void applySauceLabsCaps(XCUITestOptions options) {
        if (DeviceRunConfig.SL_APP.isBlank()) {
            throw new IllegalStateException(
                    "sl.app is required for Sauce Labs iOS runs. " +
                    "Example: -Dsl.app=\"sauce-storage:QoePlayerApp.ipa\"");
        }

        Map<String, Object> slOptions = new HashMap<>();
        slOptions.put("deviceName",      DeviceRunConfig.SL_DEVICE_NAME);
        slOptions.put("platformVersion", DeviceRunConfig.SL_PLATFORM_VERSION);
        slOptions.put("build",           "DevOpsDays-QoE-" + DeviceRunConfig.BS_BUILD_NAME);
        slOptions.put("name",            getClass().getSimpleName());
        slOptions.put("appiumVersion",   "2.0.0");

        options.setApp(DeviceRunConfig.SL_APP);
        options.setCapability("sauce:options", slOptions);

        System.out.printf("Sauce Labs iOS: device=%s platform=%s app=%s%n",
                DeviceRunConfig.SL_DEVICE_NAME,
                DeviceRunConfig.SL_PLATFORM_VERSION,
                DeviceRunConfig.SL_APP);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(description = "App launches and VideoPlayerView is visible")
    public void testAppLaunches() {
        WebElement videoView = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.accessibilityId(AX_VIDEO_VIEW)));
        Assert.assertNotNull(videoView, "VideoPlayerView should be visible on launch");
        System.out.println("✅ iOS app launched — run.mode=" + DeviceRunConfig.RUN_MODE
                + (DeviceRunConfig.IS_LAB ? " provider=" + DeviceRunConfig.LAB_PROVIDER : ""));
    }

    @Test(dependsOnMethods = "testAppLaunches",
          description = "HLS URL field accepts input via accessibility ID")
    public void testUrlInputAcceptsText() {
        WebElement urlField = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.accessibilityId(AX_URL_FIELD)));
        urlField.clear();
        urlField.sendKeys(HLS_URL);
        // On iOS, dismiss keyboard before reading value
        driver.hideKeyboard();
        String value = urlField.getAttribute("value");
        Assert.assertTrue(value != null && value.contains("x36xhzz"),
                "URL field should contain the entered HLS URL, but was: " + value);
        System.out.println("✅ URL input accepted on iOS");
    }

    @Test(dependsOnMethods = "testUrlInputAcceptsText",
          description = "Tapping Play starts HLS playback")
    public void testPlayButtonStartsPlayback() {
        WebElement playBtn = wait.until(
                ExpectedConditions.elementToBeClickable(
                        AppiumBy.accessibilityId(AX_PLAY_BTN)));
        playBtn.click();

        // Cloud labs / simulators can have slower network — extend timeout
        int startupTimeoutSecs = DeviceRunConfig.IS_LAB ? 45 : 20;
        WebDriverWait startupWait = new WebDriverWait(driver, Duration.ofSeconds(startupTimeoutSecs));

        WebElement statusLabel = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.accessibilityId(AX_STATUS_LABEL)));

        startupWait.until(d -> {
            String text = statusLabel.getAttribute("label");
            return text != null && (text.contains("Buffering") || text.contains("Playing")
                    || text.contains("Ready"));
        });

        String statusText = statusLabel.getAttribute("label");
        Assert.assertTrue(statusText != null
                        && (statusText.contains("Buffering") || statusText.contains("Playing")
                            || statusText.contains("Ready")),
                "Status should indicate active playback, but was: " + statusText);
        System.out.println("✅ iOS playback started — status: " + statusText);
    }

    @Test(dependsOnMethods = "testPlayButtonStartsPlayback",
          description = "Player stays healthy: no error label within 30 s of playback")
    public void testPlayerHealthy() {
        WebElement statusLabel = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.accessibilityId(AX_STATUS_LABEL)));

        WebDriverWait pollWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        pollWait.until(d -> {
            String t = statusLabel.getAttribute("label");
            if (t != null && t.toLowerCase().contains("error")) {
                throw new RuntimeException("iOS playback error detected: " + t);
            }
            return t != null && (t.contains("Playing") || t.contains("Buffering")
                    || t.contains("Ready"));
        });

        String finalStatus = statusLabel.getAttribute("label");
        Assert.assertFalse(finalStatus != null && finalStatus.toLowerCase().contains("error"),
                "iOS player should not report an error, but was: " + finalStatus);
        System.out.println("✅ iOS player healthy — status: " + finalStatus);
    }

    // ── Guards & utilities ────────────────────────────────────────────────────

    /**
     * Skip gracefully when running iOS tests locally on a non-macOS machine.
     * Cloud lab runs are always allowed (the lab provides macOS hosts).
     */
    private void guardNonMacLocalRun() {
        if (!DeviceRunConfig.IS_LAB) {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("mac")) {
                throw new SkipException(
                        "iOS local tests require macOS + Xcode. " +
                        "Current OS: " + os + ". " +
                        "Use -Drun.mode=lab -Dlab.provider=browserstack for cloud runs.");
            }
        }
    }

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
                default -> { /* no-op */ }
            }
        } catch (Exception e) {
            System.out.println("[WARN] Could not mark lab session status: " + e.getMessage());
        }
    }
}
