package com.devopsdays.qoe.api.unit.platform;

import com.devopsdays.qoe.api.models.Platform;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.qameta.allure.junit5.AllureJunit5;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Epic("Platform")
@ExtendWith(AllureJunit5.class)
@DisplayName("Platform")
class PlatformTest {

    @ParameterizedTest(name = "fromKey({0}) round-trips")
    @EnumSource(Platform.class)
    @Feature("Key resolution")
    @Story("Known key round-trips to the same enum value")
    @DisplayName("fromKey resolves every platform back to itself")
    void fromKeyRoundTrips(Platform p) {
        Platform resolved = Allure.step("Resolve Platform.fromKey(\"" + p.getKey() + "\")",
                () -> Platform.fromKey(p.getKey()));

        Allure.step("Assert: resolved value equals " + p.name(), () ->
                assertThat(resolved).isEqualTo(p));
    }

    @ParameterizedTest(name = "fromKey upper/mixed case ''{0}''")
    @ValueSource(strings = {"WEB", "Web", "IPHONE", "Iphone", "ANDROIDPHONE", "Androidphone", "APPLETV", "FireTV", "ANDROIDTV"})
    @Feature("Key resolution")
    @Story("Key lookup is case-insensitive")
    @DisplayName("fromKey is case-insensitive")
    void fromKeyIsCaseInsensitive(String raw) {
        Platform resolved = Allure.step("Resolve Platform.fromKey(\"" + raw + "\")",
                () -> Platform.fromKey(raw));

        Allure.step("Assert: result is non-null", () ->
                assertThat(resolved).isNotNull());
    }

    @Test
    @Feature("Key resolution")
    @Story("allKeys contains every platform")
    @DisplayName("allKeys() covers every Platform enum value")
    void allKeysHasEveryPlatform() {
        int expectedSize = Allure.step("Read expected size from Platform.values()",
                () -> Platform.values().length);

        var keys = Allure.step("Call Platform.allKeys()", Platform::allKeys);

        Allure.step("Assert: allKeys size equals " + expectedSize + " (one per platform enum)", () ->
                assertThat(keys).hasSize(expectedSize));
    }

    @ParameterizedTest(name = "unknown key ''{0}'' throws")
    @ValueSource(strings = {"tvos", "windows_phone", "", "  "})
    @Feature("Key resolution")
    @Story("Unknown key throws IllegalArgumentException")
    @DisplayName("fromKey throws for unknown / blank keys")
    void fromKeyThrowsOnUnknown(String raw) {
        Allure.step("Assert: fromKey(\"" + raw + "\") throws IllegalArgumentException with 'Unknown platform'", () ->
                assertThatThrownBy(() -> Platform.fromKey(raw))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unknown platform"));
    }

    @Test
    @Feature("Key resolution")
    @Story("Null key throws IllegalArgumentException")
    @DisplayName("fromKey(null) throws IllegalArgumentException")
    void fromKeyThrowsOnNull() {
        Allure.step("Assert: fromKey(null) throws IllegalArgumentException", () ->
                assertThatThrownBy(() -> Platform.fromKey(null))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Feature("Key resolution")
    @Story("tryFromKey returns Optional.empty for invalid inputs")
    @DisplayName("tryFromKey returns empty for unknown, null and blank inputs")
    void tryFromKeyReturnsEmptyForUnknown() {
        Allure.step("Assert: tryFromKey(\"tvos\") returns empty Optional", () ->
                assertThat(Platform.tryFromKey("tvos")).isEmpty());
        Allure.step("Assert: tryFromKey(null) returns empty Optional", () ->
                assertThat(Platform.tryFromKey(null)).isEmpty());
        Allure.step("Assert: tryFromKey(\"\") returns empty Optional", () ->
                assertThat(Platform.tryFromKey("")).isEmpty());
    }

    @Test
    @Feature("Category groupings")
    @Story("Each platform belongs to the correct category")
    @DisplayName("categoryGroupingsAreCorrect()")
    void categoryGroupingsAreCorrect() {
        Allure.step("Assert: WEB → BROWSER", () ->
                assertThat(Platform.WEB.getCategory()).isEqualTo(Platform.Category.BROWSER));
        Allure.step("Assert: IPHONE, ANDROID_PHONE → MOBILE", () -> {
            assertThat(Platform.IPHONE.getCategory()).isEqualTo(Platform.Category.MOBILE);
            assertThat(Platform.ANDROID_PHONE.getCategory()).isEqualTo(Platform.Category.MOBILE);
        });
        Allure.step("Assert: APPLE_TV, ANDROID_TV, FIRE_TV, SAMSUNG_TV, LG_TV, ROKU, CHROMECAST → TV", () -> {
            assertThat(Platform.APPLE_TV.getCategory()).isEqualTo(Platform.Category.TV);
            assertThat(Platform.ANDROID_TV.getCategory()).isEqualTo(Platform.Category.TV);
            assertThat(Platform.FIRE_TV.getCategory()).isEqualTo(Platform.Category.TV);
            assertThat(Platform.SAMSUNG_TV.getCategory()).isEqualTo(Platform.Category.TV);
            assertThat(Platform.LG_TV.getCategory()).isEqualTo(Platform.Category.TV);
            assertThat(Platform.ROKU.getCategory()).isEqualTo(Platform.Category.TV);
            assertThat(Platform.CHROMECAST.getCategory()).isEqualTo(Platform.Category.TV);
        });
        Allure.step("Assert: XBOX, PLAYSTATION → GAMING", () -> {
            assertThat(Platform.XBOX.getCategory()).isEqualTo(Platform.Category.GAMING);
            assertThat(Platform.PLAYSTATION.getCategory()).isEqualTo(Platform.Category.GAMING);
        });
        Allure.step("Assert: API, AUTOMATION → INTERNAL", () -> {
            assertThat(Platform.API.getCategory()).isEqualTo(Platform.Category.INTERNAL);
            assertThat(Platform.AUTOMATION.getCategory()).isEqualTo(Platform.Category.INTERNAL);
        });
    }
}
