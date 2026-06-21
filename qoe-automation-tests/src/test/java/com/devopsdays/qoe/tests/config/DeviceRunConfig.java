package com.devopsdays.qoe.tests.config;

/**
 * DeviceRunConfig — single source of truth for all test run-mode and device-lab
 * parameters. Every test class creates its Appium / WebDriver session through
 * helpers in this class so there is one place to add a new provider.
 *
 * ─── How to configure ────────────────────────────────────────────────────────
 *
 * Pass system properties with -D on the Maven command line, or set the
 * corresponding environment variables in CI. Properties take precedence over
 * the environment variable alternative where both are listed.
 *
 * ── Core ─────────────────────────────────────────────────────────────────────
 *   run.mode          local (default) | lab
 *   lab.provider      browserstack (default) | saucelabs | aws_device_farm | local_grid
 *   device.platform   android (default) | ios
 *
 * ── Local device / emulator ──────────────────────────────────────────────────
 *   appium.server.url   http://localhost:4723   (Appium 2.x default)
 *   apk.path            absolute path to app-debug.apk  (Android)
 *   ipa.path            absolute path to .ipa or .app bundle  (iOS)
 *   device.name         "Android Emulator" | "iPhone 15 Simulator" (default per platform)
 *   device.udid         UDID of a physical device — leave blank for emulator/simulator
 *   os.version          Android API level or iOS version (optional, aids device matching)
 *
 * ── BrowserStack ─────────────────────────────────────────────────────────────
 *   bs.username         OR env BROWSERSTACK_USERNAME
 *   bs.access.key       OR env BROWSERSTACK_ACCESS_KEY
 *   bs.app              bs://... URL from BrowserStack app upload
 *   bs.device           Samsung Galaxy S23  (default Android)
 *                       iPhone 15           (default iOS)
 *   bs.os.version       13.0  (Android) | 17  (iOS)
 *   bs.build.name       CI build label — defaults to $GITHUB_RUN_NUMBER or "local"
 *   bs.session.name     set per-test via DriverFactory; defaults to test class name
 *   bs.project.name     project name shown in BS dashboard — defaults to "DevOpsDays QoE"
 *   bs.real.device      true (default) | false  — use real device vs emulator in BS
 *
 * ── Sauce Labs ───────────────────────────────────────────────────────────────
 *   sl.username         OR env SAUCE_USERNAME
 *   sl.access.key       OR env SAUCE_ACCESS_KEY
 *   sl.region           us-west-1 (default) | eu-central-1 | us-east-1
 *   sl.app              sauce-storage:app-debug.apk  OR https://... public URL
 *   sl.device.name      Samsung Galaxy S23 FE  (real device name in SL catalog)
 *   sl.platform.version 13
 *
 * ── AWS Device Farm ──────────────────────────────────────────────────────────
 *   aws.device.farm.endpoint  https://devicefarm.us-west-2.amazonaws.com/wd/hub
 *   aws.device.farm.project   ARN of the Device Farm project
 *   (credentials via AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env vars)
 *
 * ── Web (WebPlayerQoETest) ───────────────────────────────────────────────────
 *   web.browser          chrome (default) | firefox | safari | edge
 *   grid.url             http://localhost:4444/wd/hub  (local_grid provider)
 *   bs.browser           chrome | firefox | safari | edge  (BrowserStack web)
 *   bs.browser.version   latest (default)
 *   bs.os                Windows | OS X
 *   bs.os.version.web    11 | Ventura | ...
 *
 * ─── Example invocations ─────────────────────────────────────────────────────
 *
 *  Local Android emulator (default):
 *    mvn test -Dtest=MobileQoETest
 *
 *  Local physical Android device (connected via USB):
 *    mvn test -Dtest=MobileQoETest -Ddevice.udid=RF8M31XXXXX
 *
 *  Local iOS simulator:
 *    mvn test -Dtest=iOSQoETest -Ddevice.platform=ios
 *
 *  BrowserStack Android:
 *    mvn test -Dtest=MobileQoETest \
 *      -Drun.mode=lab -Dlab.provider=browserstack \
 *      -Dbs.username=$BS_USERNAME -Dbs.access.key=$BS_KEY \
 *      -Dbs.app=bs://abc123 -Dbs.device="Samsung Galaxy S23" -Dbs.os.version=13.0
 *
 *  BrowserStack iOS:
 *    mvn test -Dtest=iOSQoETest \
 *      -Drun.mode=lab -Dlab.provider=browserstack -Ddevice.platform=ios \
 *      -Dbs.username=$BS_USERNAME -Dbs.access.key=$BS_KEY \
 *      -Dbs.app=bs://xyz789 -Dbs.device="iPhone 15" -Dbs.os.version=17
 *
 *  Sauce Labs Android:
 *    mvn test -Dtest=MobileQoETest \
 *      -Drun.mode=lab -Dlab.provider=saucelabs \
 *      -Dsl.username=$SL_USERNAME -Dsl.access.key=$SL_KEY \
 *      -Dsl.app="sauce-storage:app-debug.apk"
 *
 *  BrowserStack Web:
 *    mvn test -Dtest=WebPlayerQoETest \
 *      -Drun.mode=lab -Dlab.provider=browserstack \
 *      -Dbs.username=$BS_USERNAME -Dbs.access.key=$BS_KEY \
 *      -Dweb.browser=chrome -Dbs.os=Windows -Dbs.os.version.web=11
 *
 *  Selenium Grid (web):
 *    mvn test -Dtest=WebPlayerQoETest \
 *      -Drun.mode=lab -Dlab.provider=local_grid \
 *      -Dgrid.url=http://selenium-grid:4444/wd/hub
 */
public final class DeviceRunConfig {

    private DeviceRunConfig() {}

    // ── Core ──────────────────────────────────────────────────────────────────

    public static final String RUN_MODE     = prop("run.mode",       "local");
    public static final boolean IS_LAB      = "lab".equalsIgnoreCase(RUN_MODE);
    public static final String LAB_PROVIDER = prop("lab.provider",   "browserstack");
    public static final String PLATFORM     = prop("device.platform","android");
    public static final boolean IS_IOS      = "ios".equalsIgnoreCase(PLATFORM);
    public static final boolean IS_ANDROID  = "android".equalsIgnoreCase(PLATFORM);

    // ── Local ─────────────────────────────────────────────────────────────────

    public static final String APPIUM_URL   = prop("appium.server.url", "http://localhost:4723");
    public static final String APK_PATH     = prop("apk.path",
            System.getProperty("user.home") +
            "/Documents/projects/platform/android-player" +
            "/app/build/outputs/apk/debug/app-debug.apk");
    public static final String IPA_PATH     = prop("ipa.path", "");
    public static final String DEVICE_NAME  = prop("device.name",
            IS_IOS ? "iPhone 15 Simulator" : "Android Emulator");
    public static final String DEVICE_UDID  = prop("device.udid", "");
    public static final String OS_VERSION   = prop("os.version", "");

    // ── BrowserStack ─────────────────────────────────────────────────────────

    public static final String BS_USERNAME     = envOrProp("BROWSERSTACK_USERNAME", "bs.username", "");
    public static final String BS_ACCESS_KEY   = envOrProp("BROWSERSTACK_ACCESS_KEY","bs.access.key","");
    public static final String BS_APP          = prop("bs.app", "");
    public static final String BS_DEVICE       = prop("bs.device",
            IS_IOS ? "iPhone 15" : "Samsung Galaxy S23");
    public static final String BS_OS_VERSION   = prop("bs.os.version",
            IS_IOS ? "17" : "13.0");
    public static final String BS_BUILD_NAME   = prop("bs.build.name",
            System.getenv().getOrDefault("GITHUB_RUN_NUMBER", "local"));
    public static final String BS_PROJECT_NAME = prop("bs.project.name", "DevOpsDays QoE");
    public static final boolean BS_REAL_DEVICE = Boolean.parseBoolean(
            prop("bs.real.device", "true"));

    // BrowserStack web browser caps
    public static final String BS_BROWSER         = prop("bs.browser",         "chrome");
    public static final String BS_BROWSER_VERSION = prop("bs.browser.version", "latest");
    public static final String BS_OS              = prop("bs.os",               "Windows");
    public static final String BS_OS_VERSION_WEB  = prop("bs.os.version.web",  "11");

    // ── Sauce Labs ────────────────────────────────────────────────────────────

    public static final String SL_USERNAME         = envOrProp("SAUCE_USERNAME",    "sl.username",    "");
    public static final String SL_ACCESS_KEY       = envOrProp("SAUCE_ACCESS_KEY",  "sl.access.key",  "");
    public static final String SL_REGION           = prop("sl.region",           "us-west-1");
    public static final String SL_APP              = prop("sl.app",              "");
    public static final String SL_DEVICE_NAME      = prop("sl.device.name",
            IS_IOS ? "iPhone 15 Simulator" : "Samsung Galaxy S23 FE");
    public static final String SL_PLATFORM_VERSION = prop("sl.platform.version",
            IS_IOS ? "17" : "13");

    // ── AWS Device Farm ───────────────────────────────────────────────────────

    public static final String AWS_DF_ENDPOINT = prop("aws.device.farm.endpoint",
            "https://devicefarm.us-west-2.amazonaws.com/wd/hub");
    public static final String AWS_DF_PROJECT  = prop("aws.device.farm.project", "");

    // ── Web ───────────────────────────────────────────────────────────────────

    public static final String WEB_BROWSER = prop("web.browser", "chrome");
    public static final String GRID_URL    = prop("grid.url",    "http://localhost:4444/wd/hub");

    // ── Derived URLs ──────────────────────────────────────────────────────────

    /** BrowserStack Appium hub URL (credentials embedded). */
    public static String bsAppiumUrl() {
        return "https://" + BS_USERNAME + ":" + BS_ACCESS_KEY
                + "@hub.browserstack.com/wd/hub";
    }

    /** BrowserStack Selenium hub URL (web tests). */
    public static String bsSeleniumUrl() {
        return "https://" + BS_USERNAME + ":" + BS_ACCESS_KEY
                + "@hub.browserstack.com/wd/hub";
    }

    /** Sauce Labs Appium/Selenium hub URL. */
    public static String slHubUrl() {
        return "https://" + SL_USERNAME + ":" + SL_ACCESS_KEY
                + "@ondemand." + SL_REGION + ".saucelabs.com/wd/hub";
    }

    /**
     * Resolve the Appium server URL for mobile tests.
     * Local: uses appium.server.url property.
     * Lab: routes to the provider's cloud hub.
     */
    public static String resolveAppiumUrl() {
        if (!IS_LAB) return APPIUM_URL;
        return switch (LAB_PROVIDER.toLowerCase()) {
            case "browserstack"    -> bsAppiumUrl();
            case "saucelabs"       -> slHubUrl();
            case "aws_device_farm" -> AWS_DF_ENDPOINT;
            default                -> APPIUM_URL;    // local_grid / custom
        };
    }

    /**
     * Resolve the WebDriver remote URL for web tests.
     * Local: not used (ChromeDriver/GeckoDriver is launched in-process).
     * Lab: routes to the provider's hub.
     */
    public static String resolveWebDriverUrl() {
        if (!IS_LAB) return "";
        return switch (LAB_PROVIDER.toLowerCase()) {
            case "browserstack" -> bsSeleniumUrl();
            case "saucelabs"    -> slHubUrl();
            case "local_grid"   -> GRID_URL;
            default             -> GRID_URL;
        };
    }

    /** Whether local APK/IPA installation is needed (local mode only). */
    public static boolean needsLocalAppInstall() {
        return !IS_LAB;
    }

    /** Print a human-readable summary — called in @BeforeClass of each test. */
    public static void printSummary(String testClass) {
        String redactedUrl = resolveAppiumUrl()
                .replaceAll(":[^@:]+@", ":***@");
        System.out.printf("""
                %n┌─────────────────────────────────────────────────────┐
                │  Run Configuration %-34s│
                ├─────────────────────────────────────────────────────┤
                │  test class    : %-35s│
                │  run.mode      : %-35s│
                │  device.platform: %-34s│
                │  lab.provider  : %-35s│
                │  Appium URL    : %-35s│
                └─────────────────────────────────────────────────────┘%n""",
                "",
                shorten(testClass, 35),
                RUN_MODE,
                PLATFORM,
                IS_LAB ? LAB_PROVIDER : "— (local)",
                shorten(redactedUrl, 35));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String prop(String key, String defaultVal) {
        return System.getProperty(key, defaultVal);
    }

    private static String envOrProp(String envKey, String propKey, String defaultVal) {
        String env = System.getenv(envKey);
        return (env != null && !env.isBlank()) ? env : prop(propKey, defaultVal);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
