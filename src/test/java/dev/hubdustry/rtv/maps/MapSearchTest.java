package dev.hubdustry.rtv.maps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapSearchTest {
    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals("ancient caldera", MapSearch.normalizedQuery("  ANCIENT   Caldera  "));
        assertTrue(MapSearch.matches("Ancient Caldera", "ancient caldera"));
        assertTrue(MapSearch.matches("Ancient Caldera", "caldera"));
    }

    @Test
    void acceptsEmptyAndNullQueries() {
        assertEquals("", MapSearch.normalizedQuery(null));
        assertTrue(MapSearch.matches("Any Map", ""));
    }

    @Test
    void rejectsInvalidTypeAndOverlongQuery() {
        assertNull(MapSearch.normalizedQuery(42));
        assertNull(MapSearch.normalizedQuery("x".repeat(MapSearch.MAX_QUERY_LENGTH + 1)));
    }

    @Test
    void matchesVietnameseUnicodeWithoutAccentFolding() {
        String query = MapSearch.normalizedQuery("ĐẢO");
        assertTrue(MapSearch.matches("Đảo Xanh", query));
        assertFalse(MapSearch.matches("Dao Xanh", query));
    }
}
