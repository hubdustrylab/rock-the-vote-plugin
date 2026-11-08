package dev.hubdustry.rtv.maps;

import arc.files.Fi;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.maps.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MapCatalogTest {
    @TempDir Path directory;

    @Test void customMapIdsIncludeSubdirectories() {
        Fi previous = Vars.customMapDirectory;
        try {
            Vars.customMapDirectory = new Fi(directory.toString());
            var first = new Map(new Fi(directory.resolve("first/same.msav").toString()),
                    64, 64, new StringMap(), true);
            var second = new Map(new Fi(directory.resolve("second/same.msav").toString()),
                    64, 64, new StringMap(), true);
            assertEquals("custom:first/same.msav", MapCatalog.id(first));
            assertNotEquals(MapCatalog.id(first), MapCatalog.id(second));
        } finally {
            Vars.customMapDirectory = previous;
        }
    }
}
