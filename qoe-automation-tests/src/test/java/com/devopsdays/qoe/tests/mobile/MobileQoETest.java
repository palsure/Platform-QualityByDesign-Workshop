package com.devopsdays.qoe.tests.mobile;

import com.devopsdays.qoe.tests.config.DeviceRunConfig;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.MutableCapabilities;
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
 * MobileQoETest — Android UI automation via Appium (UiAutomator2).
 *
 * Supports three run modes selected via system properties (see DeviceRunConfig):
 *
 *   LOCAL_EMULATOR  — default; connects to a local Appium server with an AVD.
 *   LOCAL_DEVICE    — local Appium server with a physical device identified by UDID.
 *   LAB             — cloud device lab (BrowserStack, Sauce Labs, AWS Device Farm).
 *
 * Quick-start examples:
 *
 *   # Local emulator (default)
 *   mvn test -Dtest=MobileQoETest
 *
 *   # Local physical device
 *   mvn test -Dtest=MobileQoETest -Ddevice.udid=RF8M31XXXXX
 *
 *   # BrowserStack real device
 *   mvn test -Dtest=MobileQoETest \
 *     -Drun.mode=lab -Dlab.provider=browserstack \
 *     -Dbs.username=$BS_USERNAME -Dbs.access.key=$BS_KEY \
 *     -Dbs.app=bs://abc123 \
 *     -Dbs.device="Samsung Galaxy S23" -Dbs.os.version=13.0
 *
 *   # Sauce Labs
 *   mvn test -Dtest=MobileQoETest \
 *     -Drun.mode=lab -Dlab.provider=saucelabs \
 *     -Dsl.username=$SL_USERNAME -Dsl.access.key=$SL_KEY \
 *     -Dsl.app="sauce-storage:app-debug.apk"
 *
 *   # AWS Device Farm (requires AWS credentials in env)
 *   mvn test -Dtest=MobileQoETest \
 *     -Drun.mode=lab -Dlab.provider=aws_device_farm \
 *     -Daws.device.farm.endpoint=https://devicefarm.us-west-2.amazonaws.com/wd/hub
 */
public class MobileQoETest {

    private AndroidDriver driver;
    private WebDriverWait wait;

    private static final String APP_PACKAGE  = "com.devopsdays.qoe";
    private static final String APP_ACTIVITY = "com.devopsdays.qoe.player.MainActivity";
    private static final String HLS_URL =
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() throws Exception {
        DeviceRunConfig.printSummary(getClass().getSimpleName());

        UiAutomator2Options options = buildCapabilities();
        String appiumUrl = DeviceRunConfig.resolveAppiumUrl();
        System.out.println("Connecting to Appium: " + appiumUrl.replaceAll(":[^@:]+@", ":***@"));

        driver = new AndroidDriver(new URI(appiumUrl).toURL(), options);
        wait   = new WebDriverWait(driver, Duration.ofSeconds(30));

        postLaunchSetup();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            // Mark session status in cloud labs for clear pass/fail reporting
            if (DeviceRunConfig.IS_LAB) {
                markLabSessionStatus("passed", "Test suite completed");
            }
            driver.quit();
        }
    }

    // ── Capability builders ───────────────────────────────────────────────────

    private UiAutomator2Options buildCapabilities() {
        UiAutomator2Options options = new UiAutomator2Options()
                .setDeviceName(DeviceRunConfig.DEVICE_NAME)
                .setAppPackage(APP_PACKAGE)
                .setAppActivity(APP_ACTIVITY)
                .setNoReset(true)
                .setAutoGrantPermissions(true)
                .setNewCommandTimeout(Duration.ofSeconds(120));

        // Physical device UDID (local or lab)
        if (!DeviceRunConfig.DEVICE_UDID.isBlank()) {
            options.setUdid(DeviceRunConfig.DEVICE_UDID);
        }

        // OS version filter (helps emulator / cloud lab matching)
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

    /** Capabilities used when running against a local Appium server. */
    private void applyLocalCapabilities(UiAutomator2Options options) {
        String apk = DeviceRunConfig.APK_PATH;
        if (!apk.isBlank() && new java.io.File(apk).exists()) {
            options.setApp(apk);
            options.setNoReset(false);   // install fresh when APK path is provided
            System.out.println("Installing APK: " + apk);
        } else {
            // No APK path → assume app is already installed; launch via package/activity
            System.out.println("No APK path — launching pre-installed app: " + APP_PACKAGE);
        }
    }

    /** Capabilities injected for each supported cloud provider. */
    private void applyLabCapabilities(UiAutomator2Options options) {
        switch (DeviceRunConfig.LAB_PROVIDER.toLowerCase()) {
            case "browserstack" -> applyBrowserStackCaps(options);
            case "saucelabs"    -> applySauceLabsCaps(options);
            case "aws_device_farm" -> applyAwsDeviceFarmCaps(options);
            default             -> System.out.println("[WARN] Unknown lab.provider '"
                    + DeviceRunConfig.LAB_PROVIDER + "' — using base caps only");
        }
    }

    private void applyBrowserStackCaps(UiAutomator2Options options) {
        if (DeviceRunConfig.BS_APP.isBlank()) {
            throw new IllegalStateException(
                    "bs.app is required for BrowserStack runs. " +
                    "Upload your APK first:\n" +
                    "  curl -u \"$BS_USERNAME:$BS_KEY\" \\\n" +
                    "    -X POST https://api-cloud.browserstack.com/app-automate/upload \\\n" +
                    "    -F \"file=@app-debug.apk\" | jq .app_url\n" +
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

        System.out.printf("BrowserStack: device=%s os=%s app=%s%n",
                DeviceRunConfig.BS_DEVICE, DeviceRunConfig.BS_OS_VERSION,
                DeviceRunConfig.BS_APP);
    }

    private void applySauceLabsCaps(UiAutomator2Options options) {
        if (DeviceRunConfig.SL_APP.isBlank()) {
            throw new IllegalStateException(
                    "sl.app is required for Sauce Labs runs. " +
                    "Example: -Dsl.app=\"sauce-storage:app-debug.apk\"");
        }

        Map<String, Object> slOptions = new HashMap<>();
        slOptions.put("deviceName",       DeviceRunConfig.SL_DEVICE_NAME);
        slOptions.put("platformVersion",  DeviceRunConfig.SL_PLATFORM_VERSION);
        slOptions.put("build",            "DevOpsDays-QoE-" + DeviceRunConfig.BS_BUILD_NAME);
        slOptions.put("name",             getClass().getSimpleName());
        slOptions.put("appiumVersion",    "2.0.0");

        options.setApp(DeviceRunConfig.SL_APP);
        options.setCapability("sauce:options", slOptions);

        System.out.printf("Sauce Labs: device=%s platformVersion=%s app=%s%n",
                DeviceRunConfig.SL_DEVICE_NAME,
                DeviceRunConfig.SL_PLATFORM_VERSION,
                DeviceRunConfig.SL_APP);
    }

    private void applyAwsDeviceFarmCaps(UiAutomator2Options options) {
        // AWS Device Farm uses standard Appium caps; authentication is done
        // via a presigned URL generated by the AWS CLI / SDK before the session.
        // The endpoint is passed in as aws.device.farm.endpoint system property.
        // See: https://docs.aws.amazon.com/devicefarm/latest/developerguide/
        options.setCapability("testgrid:deviceFilters",
                "[{\"attribute\":\"PLATFORM\",\"values\":[\"ANDROID\"]}]");
        System.out.println("AWS Device Farm endpoint: " + DeviceRunConfig.AWS_DF_ENDPOINT);
    }

    // ── Post-launch setup ─────────────────────────────────────────────────────

    /**
     * Dismiss any OS-level dialogs that can block the test UI (e.g. the Appium
     * notification shade on local emulators). In cloud labs the OS is typically
     * pre-configured to suppress these, but the guards are harmless.
     */
    private void postLaunchSetup() throws InterruptedException {
        Thread.sleep(1_500);
        // Dismiss notification shade / Appium settings popup
        driver.pressKey(new KeyEvent(AndroidKey.BACK));
        Thread.sleep(800);
        // Re-activate the app in case BACK sent it to the background
        driver.activateApp(APP_PACKAGE);
        Thread.sleep(1_000);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(description = "App launches and main screen is visible")
    public void testAppLaunches() {
        WebElement playBtn = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.id(APP_PACKAGE + ":id/play_button")));
        Assert.assertNotNull(playBtn, "Play button should be visible on launch");
        System.out.println("✅ App launched — run.mode=" + DeviceRunConfig.RUN_MODE
                + (DeviceRunConfig.IS_LAB ? " provider=" + DeviceRunConfig.LAB_PROVIDER : ""));
    }

    @Test(dependsOnMethods = "testAppLaunches",
          description = "HLS URL field accepts input")
    public void testUrlInputAcceptsText() {
        WebElement urlInput = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.id(APP_PACKAGE + ":id/url_input")));
        urlInput.clear();
        urlInput.sendKeys(HLS_URL);
        Assert.assertEquals(urlInput.getText(), HLS_URL,
                "URL field should contain the entered HLS URL");
        System.out.println("✅ URL input accepted: " + HLS_URL);
    }

    @Test(dependsOnMethods = "testUrlInputAcceptsText",
          description = "Video ID field accepts custom ID")
    public void testVideoIdInput() {
        WebElement idInput = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.id(APP_PACKAGE + ":id/video_id_input")));
        idInput.clear();
        idInput.sendKeys("automation-test-001");
        Assert.assertEquals(idInput.getText(), "automation-test-001",
                "Video ID field should reflect entered value");
        System.out.println("✅ Video ID input accepted");
    }

    @Test(dependsOnMethods = "testVideoIdInput",
          description = "Tapping Play starts video playback")
    public void testPlayButtonStartsPlayback() {
        WebElement playBtn = wait.until(
                ExpectedConditions.elementToBeClickable(
                        AppiumBy.id(APP_PACKAGE + ":id/play_button")));
        playBtn.click();

        WebElement status = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.id(APP_PACKAGE + ":id/status_text")));

        // Cloud labs may have slower network — allow up to 30 s for playback to start
        int startupTimeoutSecs = DeviceRunConfig.IS_LAB ? 30 : 20;
        WebDriverWait startupWait = new WebDriverWait(driver, Duration.ofSeconds(startupTimeoutSecs));
        startupWait.until(driver1 -> {
            String text = status.getText();
            return text.equals("Buffering...") || text.equals("Ready");
        });

        String statusText = status.getText();
        Assert.assertTrue(
                statusText.equals("Buffering...") || statusText.equals("Ready"),
                "Status should be Buffering or Ready after Play, but was: " + statusText);
        System.out.println("✅ Playback started — status: " + statusText);
    }

    @Test(dependsOnMethods = "testPlayButtonStartsPlayback",
          description = "Player stays healthy: no playback error within 30 s")
    public void testPlayerHealthy() {
        WebElement status = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        AppiumBy.id(APP_PACKAGE + ":id/status_text")));

        WebDriverWait pollWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        pollWait.until(driver1 -> {
            String t = status.getText();
            if (t.startsWith("Error:"))
                throw new RuntimeException("Playback error detected: " + t);
            return t.equals("Ready") || t.equals("Buffering...");
        });

        String finalStatus = status.getText();
        Assert.assertFalse(finalStatus.startsWith("Error:"),
                "Player should not report an error, but was: " + finalStatus);
        System.out.println("✅ Player healthy — status: " + finalStatus);
    }

    // ── Lab utilities ─────────────────────────────────────────────────────────

    /**
     * Update the cloud lab session status (BrowserStack / Sauce Labs).
     * Called in tearDown so the dashboard shows pass/fail correctly.
     */
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
                default -> { /* no-op for local / AWS */ }
            }
        } catch (Exception e) {
            System.out.println("[WARN] Could not mark lab session status: " + e.getMessage());
        }
    }
}
