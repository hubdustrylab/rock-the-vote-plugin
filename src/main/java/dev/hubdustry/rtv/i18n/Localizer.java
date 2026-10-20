package dev.hubdustry.rtv.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads UTF-8 messages and applies per-key locale fallback. */
public final class Localizer {
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)\\}");
    private final Map<String, Map<String, String>> messages = new TreeMap<>();
    private final String configuredDefault;

    public Localizer(String configuredDefault, Path directory) throws IOException {
        this.configuredDefault = normalize(configuredDefault == null || configuredDefault.isBlank() ? "en" : configuredDefault);
        loadBundled("en");
        loadBundled("vi");
        loadBundled("ko");
        if (directory != null) loadExternal(directory.resolve("locales"));
    }

    public String text(String locale, String key, Object... args) {
        String template = null;
        for (String candidate : candidates(locale)) {
            Map<String, String> localeMessages = messages.get(candidate);
            if (localeMessages != null && localeMessages.containsKey(key)) {
                template = localeMessages.get(key);
                break;
            }
        }
        if (template == null) template = key;
        Matcher matcher = ARGUMENT.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = index < args.length && args[index] != null
                    ? String.valueOf(args[index]) : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String resolve(String locale) {
        for (String candidate : candidates(locale)) {
            if (messages.containsKey(candidate)) return candidate;
        }
        return "en";
    }

    public Set<String> supportedLocales() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(messages.keySet()));
    }

    private Iterable<String> candidates(String locale) {
        String requested = locale == null || locale.isBlank() ? configuredDefault : normalize(locale);
        Set<String> result = new LinkedHashSet<>();
        addLocaleAndLanguage(result, requested);
        addLocaleAndLanguage(result, configuredDefault);
        result.add("en");
        return result;
    }

    private static void addLocaleAndLanguage(Set<String> result, String locale) {
        result.add(locale);
        int dash = locale.indexOf('-');
        if (dash > 0) result.add(locale.substring(0, dash));
    }

    private void loadBundled(String locale) throws IOException {
        String resource = "/rtv/i18n/" + locale + ".properties";
        InputStream input = Localizer.class.getResourceAsStream(resource);
        if (input == null) throw new IOException("Missing bundled locale resource: " + resource);
        load(locale, input);
    }

    private void loadExternal(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.list(directory)) {
            for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(".properties")).toList()) {
                String name = file.getFileName().toString();
                load(name.substring(0, name.length() - ".properties".length()), Files.newInputStream(file));
            }
        }
    }

    private void load(String locale, InputStream input) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = input;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> target = messages.computeIfAbsent(normalize(locale), ignored -> new TreeMap<>());
        for (String key : properties.stringPropertyNames()) target.put(key, properties.getProperty(key));
    }

    private static String normalize(String locale) {
        return (locale == null || locale.isBlank() ? "en" : locale).replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
