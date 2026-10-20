package dev.hubdustry.rtv.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class LocalizerTest {
    @Test
    void bundledLanguagesHaveAllKeysAndMatchingArguments() throws Exception {
        var english = bundled("en");
        var pattern = java.util.regex.Pattern.compile("\\{\\d+}");
        for (String locale : java.util.List.of("vi", "ko")) {
            var translated = bundled(locale);
            assertEquals(english.stringPropertyNames(), translated.stringPropertyNames(), locale);
            for (String key : english.stringPropertyNames()) {
                var sourceArgs = pattern.matcher(english.getProperty(key)).results()
                        .map(java.util.regex.MatchResult::group).collect(java.util.stream.Collectors.toSet());
                var translatedArgs = pattern.matcher(translated.getProperty(key)).results()
                        .map(java.util.regex.MatchResult::group).collect(java.util.stream.Collectors.toSet());
                assertEquals(sourceArgs, translatedArgs, locale + ":" + key);
            }
        }
    }

    private java.util.Properties bundled(String locale) throws Exception {
        var properties = new java.util.Properties();
        try (var input = Localizer.class.getResourceAsStream("/rtv/i18n/" + locale + ".properties");
             var reader = new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void usesConfiguredDefaultForBlankAndRegionalLocales() throws Exception {
        var localizer = new Localizer("vi", Files.createTempDirectory("rtv-locales"));
        assertEquals("vi", localizer.resolve(null));
        assertEquals("vi", localizer.resolve("vi-VN"));
        assertEquals("Chọn", localizer.text("vi-VN", "choose"));
    }

    @Test
    void fallsBackPerKeyForPartialExternalLocale() throws Exception {
        var directory = Files.createTempDirectory("rtv-locales");
        Files.createDirectories(directory.resolve("locales"));
        Files.writeString(directory.resolve("locales/xx.properties"), "title=Custom {0}\n");
        var localizer = new Localizer("en", directory);
        assertEquals("Custom map", localizer.text("xx", "title", "map"));
        assertEquals("Choose", localizer.text("xx", "choose"));
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
