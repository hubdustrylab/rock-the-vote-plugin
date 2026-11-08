package dev.hubdustry.rtv.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import mindustry.gen.Iconc;
import org.junit.jupiter.api.Test;

class LocalizerTest {
    @Test
    void bundledLanguagesHaveAllKeysAndMatchingArguments() throws Exception {
        var localizer = new Localizer("en", null);
        var expectedLocales = new TreeSet<String>();
        try (var input = LocalizerTest.class.getResourceAsStream("/locales")) {
            for (String clientCode : new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String expected = clientCode.replace('_', '-').toLowerCase(Locale.ROOT);
                expectedLocales.add(expected);
                assertEquals(expected, localizer.resolve(clientCode), clientCode);
            }
        }
        expectedLocales.add("router"); // Mindustry adds this mode to the list at runtime.
        assertEquals(expectedLocales, localizer.supportedLocales());
        assertEquals(String.valueOf(Iconc.blockRouter).repeat(3), localizer.text("router", "yes"));

        var english = bundled("en");
        var pattern = Pattern.compile("\\{\\d+}");
        for (String locale : expectedLocales) {
            var translated = bundled(locale);
            assertEquals(english.stringPropertyNames(), translated.stringPropertyNames(), locale);
            for (String key : english.stringPropertyNames()) {
                String message = translated.getProperty(key);
                assertFalse(message.isBlank(), locale + ":" + key);
                assertEquals(arguments(pattern, english.getProperty(key)), arguments(pattern, message),
                        locale + ":" + key);
            }
        }
    }

    private static List<String> arguments(Pattern pattern, String message) {
        return pattern.matcher(message).results().map(MatchResult::group).sorted().toList();
    }

    private static Properties bundled(String locale) throws Exception {
        var properties = new Properties();
        try (var input = Localizer.class.getResourceAsStream("/rtv/i18n/" + locale + ".properties");
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void usesConfiguredDefaultForBlankAndRegionalLocales() throws Exception {
        var localizer = new Localizer("vi", Files.createTempDirectory("rtv-locales"));
        assertEquals("vi", localizer.resolve(null));
        assertEquals("vi", localizer.resolve("vi-VN"));
        assertEquals("Tìm bản đồ...", localizer.text("vi-VN", "searchMaps"));
        assertEquals("vi", localizer.resolve("zz-ZZ"));
        assertEquals("pt-br", localizer.resolve("pt"));
        assertEquals("pt-pt", localizer.resolve("pt_PT_POSIX"));
        assertEquals("zh-cn", localizer.resolve("zh-Hans"));
        assertEquals("zh-tw", localizer.resolve("zh-Hant-TW"));
        assertEquals("zh-tw", localizer.resolve("zh_HK"));
        assertEquals("id-id", localizer.resolve("in_ID"));
        assertEquals("uk-ua", localizer.resolve("uk"));
    }

    @Test
    void fallsBackPerKeyForPartialExternalLocale() throws Exception {
        var directory = Files.createTempDirectory("rtv-locales");
        Files.createDirectories(directory.resolve("locales"));
        Files.writeString(directory.resolve("locales/xx.properties"), "title=Custom {0}\n");
        Files.writeString(directory.resolve("locales/zh_TW.properties"), "title=自訂 {0}\n");
        var localizer = new Localizer("en", directory);
        assertEquals("Custom map", localizer.text("xx", "title", "map"));
        assertEquals("Search maps...", localizer.text("xx", "searchMaps"));
        assertEquals("自訂 地圖", localizer.text("zh_TW", "title", "地圖"));
        assertEquals("搜尋地圖…", localizer.text("zh_TW", "searchMaps"));
    }

    @Test
    void preservesUtf8AndLiteralReplacementCharacters() throws Exception {
        var directory = Files.createTempDirectory("rtv-locales");
        Files.createDirectories(directory.resolve("locales"));
        Files.writeString(directory.resolve("locales/xx.properties"), "title=Đổi {0}\n");
        var localizer = new Localizer("en", directory);
        assertEquals("Đổi $1\\path", localizer.text("xx", "title", "$1\\path"));
        assertTrue(localizer.supportedLocales().contains("ko"));
    }
}
