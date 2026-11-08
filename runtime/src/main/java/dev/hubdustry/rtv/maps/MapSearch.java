package dev.hubdustry.rtv.maps;

import java.util.Locale;

/** Provides bounded, case-insensitive map name matching for UI search. */
public final class MapSearch {
    public static final int MAX_QUERY_LENGTH = 80;

    private MapSearch() {
    }

    /** Return a safe query, or null when the callback value is invalid. */
    public static String normalizedQuery(Object value) {
        if (value == null) return "";
        if (!(value instanceof String text)) return null;
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_QUERY_LENGTH) return null;
        return normalized.toLowerCase(Locale.ROOT);
    }

    /** Return true when the normalized query occurs in the map name. */
    public static boolean matches(String mapName, String query) {
        if (mapName == null || query == null) return false;
        String normalizedName = mapName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalizedName.contains(query);
    }
}
