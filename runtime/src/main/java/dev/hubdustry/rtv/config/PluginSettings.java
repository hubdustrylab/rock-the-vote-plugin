package dev.hubdustry.rtv.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable settings for the Rock the Vote plugin. */
public record PluginSettings(
        String defaultLocale,
        double requiredRatio,
        int minPlayers,
        int voteDurationSeconds,
        int cooldownSeconds,
        int mapStartDelaySeconds,
        int changeDelaySeconds,
        boolean includeDefaultMaps,
        int panelWidth,
        String accentColor,
        String bannerRegion) {

    private static final Pattern LOCALE = Pattern.compile("[A-Za-z]{2,8}(?:[-_][A-Za-z0-9]{2,8})*");
    private static final Pattern COLOR = Pattern.compile("#?[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?");
    private static final Pattern REGION = Pattern.compile("[A-Za-z0-9_./-]*");
    private static final Set<String> KEYS = Set.of("defaultLocale", "requiredRatio", "minPlayers", "voteDurationSeconds",
            "cooldownSeconds", "mapStartDelaySeconds", "changeDelaySeconds", "includeDefaultMaps",
            "panelWidth", "accentColor", "bannerRegion");

    public PluginSettings {
        defaultLocale = require(defaultLocale, "defaultLocale");
        if (!LOCALE.matcher(defaultLocale).matches()) fail("defaultLocale must be a language tag");
        if (!(requiredRatio > 0.0 && requiredRatio <= 1.0)) fail("requiredRatio must be greater than 0 and at most 1");
        if (minPlayers < 1 || minPlayers > 10000) fail("minPlayers must be between 1 and 10000");
        if (voteDurationSeconds < 1 || voteDurationSeconds > 3600) fail("voteDurationSeconds must be between 1 and 3600");
        if (cooldownSeconds < 0 || cooldownSeconds > 86400 || mapStartDelaySeconds < 0 || mapStartDelaySeconds > 86400
                || changeDelaySeconds < 0 || changeDelaySeconds > 86400) fail("delay values must be between 0 and 86400");
        if (panelWidth < 280 || panelWidth > 800) fail("panelWidth must be between 280 and 800");
        accentColor = require(accentColor, "accentColor");
        if (!COLOR.matcher(accentColor).matches()) fail("accentColor must be a 6 or 8 digit hexadecimal color");
        bannerRegion = require(bannerRegion, "bannerRegion");
        if (!REGION.matcher(bannerRegion).matches()) fail("bannerRegion contains unsupported characters");
    }

    public static PluginSettings defaults() {
        return new PluginSettings("en", .6, 1, 60, 120, 30, 5, true, 600, "FFD37F", "");
    }

    public static PluginSettings load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("config.properties");
        Properties properties = new Properties();
        if (Files.notExists(file)) {
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : values(defaults()).entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + System.lineSeparator());
                }
            }
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) fail("unknown property: " + key);
        }
        return parse(properties);
    }

    private static PluginSettings parse(Properties p) {
        return new PluginSettings(
                value(p, "defaultLocale"), decimal(p, "requiredRatio"), integer(p, "minPlayers"),
                integer(p, "voteDurationSeconds"), integer(p, "cooldownSeconds"), integer(p, "mapStartDelaySeconds"),
                integer(p, "changeDelaySeconds"), bool(p, "includeDefaultMaps"),
                integer(p, "panelWidth"), value(p, "accentColor"), p.getProperty("bannerRegion", ""));
    }

    private static String value(Properties properties, String key) {
        return require(properties.getProperty(key), key);
    }

    private static int integer(Properties properties, String key) {
        try {
            return Integer.parseInt(value(properties, key));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid plugin setting: " + key + " must be an integer", error);
        }
    }

    private static double decimal(Properties properties, String key) {
        try {
            return Double.parseDouble(value(properties, key));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid plugin setting: " + key + " must be a number", error);
        }
    }

    private static boolean bool(Properties properties, String key) {
        String value = value(properties, key);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            fail(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static String require(String value, String key) {
        if (value == null || (value.trim().isEmpty() && !key.equals("bannerRegion"))) {
            fail(key + " must not be empty");
        }
        return value.trim();
    }

    private static void fail(String message) {
        throw new IllegalArgumentException("Invalid plugin setting: " + message);
    }

    private static Map<String, String> values(PluginSettings s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("defaultLocale", s.defaultLocale);
        m.put("requiredRatio", Double.toString(s.requiredRatio));
        m.put("minPlayers", Integer.toString(s.minPlayers));
        m.put("voteDurationSeconds", Integer.toString(s.voteDurationSeconds));
        m.put("cooldownSeconds", Integer.toString(s.cooldownSeconds));
        m.put("mapStartDelaySeconds", Integer.toString(s.mapStartDelaySeconds));
        m.put("changeDelaySeconds", Integer.toString(s.changeDelaySeconds));
        m.put("includeDefaultMaps", Boolean.toString(s.includeDefaultMaps));
        m.put("panelWidth", Integer.toString(s.panelWidth));
        m.put("accentColor", s.accentColor);
        m.put("bannerRegion", s.bannerRegion);
        return m;
    }
}
