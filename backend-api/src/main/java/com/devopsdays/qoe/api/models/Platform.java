package com.devopsdays.qoe.api.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canonical set of supported playback platforms.
 *
 * <p>Each constant carries:
 * <ul>
 *   <li>{@link #key} — the wire / API value (snake_case, used in JSON and query params)</li>
 *   <li>{@link #category} — broad device family for grouping in reports</li>
 *   <li>{@link #displayName} — human-readable label</li>
 * </ul>
 */
@Schema(enumAsRef = true, description = "Supported playback platform")
public enum Platform {

    // ── Browser / web ─────────────────────────────────────────────────────────
    WEB("web", Category.BROWSER, "Web Browser"),

    // ── Mobile ────────────────────────────────────────────────────────────────
    IPHONE("iphone", Category.MOBILE, "iPhone"),
    IPAD("ipad", Category.MOBILE, "iPad"),
    ANDROID_PHONE("androidphone", Category.MOBILE, "Android Phone"),
    ANDROID_TABLET("androidtablet", Category.MOBILE, "Android Tablet"),

    // ── TV ────────────────────────────────────────────────────────────────────
    APPLE_TV("appletv", Category.TV, "Apple TV"),
    ANDROID_TV("androidtv", Category.TV, "Android TV"),
    FIRE_TV("firetv", Category.TV, "Amazon Fire TV"),
    SAMSUNG_TV("samsungtv", Category.TV, "Samsung Smart TV (Tizen)"),
    LG_TV("lgtv", Category.TV, "LG Smart TV (webOS)"),
    ROKU("roku", Category.TV, "Roku"),
    CHROMECAST("chromecast", Category.TV, "Chromecast"),

    // ── Gaming / set-top ──────────────────────────────────────────────────────
    XBOX("xbox", Category.GAMING, "Xbox"),
    PLAYSTATION("playstation", Category.GAMING, "PlayStation"),

    // ── Desktop native ────────────────────────────────────────────────────────
    DESKTOP_MACOS("desktop_macos", Category.DESKTOP, "Desktop macOS"),
    DESKTOP_WINDOWS("desktop_windows", Category.DESKTOP, "Desktop Windows"),
    DESKTOP_LINUX("desktop_linux", Category.DESKTOP, "Desktop Linux"),

    // ── CI / internal ─────────────────────────────────────────────────────────
    API("api", Category.INTERNAL, "API / Backend"),
    AUTOMATION("automation", Category.INTERNAL, "Test Automation");

    // ─────────────────────────────────────────────────────────────────────────

    public enum Category { BROWSER, MOBILE, TV, GAMING, DESKTOP, INTERNAL }

    private final String key;
    private final Category category;
    private final String displayName;

    Platform(String key, Category category, String displayName) {
        this.key = key;
        this.category = category;
        this.displayName = displayName;
    }

    @JsonValue
    public String getKey() { return key; }

    public Category getCategory() { return category; }

    public String getDisplayName() { return displayName; }

    /** All wire keys — useful for error messages. */
    public static Set<String> allKeys() {
        return Arrays.stream(values()).map(Platform::getKey).collect(Collectors.toSet());
    }

    /** Case-insensitive lookup by wire key. */
    @JsonCreator
    public static Platform fromKey(String raw) {
        if (raw == null) throw new IllegalArgumentException("platform must not be null");
        String normalized = raw.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(p -> p.key.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown platform: '" + raw + "'. Allowed: " + allKeys()));
    }

    /** Returns empty Optional instead of throwing — for optional validation paths. */
    public static Optional<Platform> tryFromKey(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        return Arrays.stream(values())
                .filter(p -> p.key.equals(raw.trim().toLowerCase()))
                .findFirst();
    }
}
