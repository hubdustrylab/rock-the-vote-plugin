package dev.hubdustry.rtv.maps;

import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.io.MapIO;
import arc.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashSet;

/** Read the current map list. Do not retain map objects across a reload. */
public final class MapCatalog {
    private record Stamp(long length, long modified, boolean readable) {}
    private final java.util.Map<Map, Stamp> metadata = new IdentityHashMap<>();

    public List<Map> available(Gamemode mode, boolean includeDefaultMaps) {
        List<Map> result = new ArrayList<>();
        var seen = new HashSet<String>();
        var duplicateIds = new HashSet<String>();
        for (Map map : Vars.maps.all()) {
            if ((map.custom || includeDefaultMaps) && map.file.exists() && readMetadata(map) && mode.valid(map)
                    && (Vars.state.map == null || !id(map).equals(id(Vars.state.map)))
                    && map.teams.size > 0) {
                result.add(map);
                if (!seen.add(id(map))) duplicateIds.add(id(map));
            }
        }
        result.removeIf(map -> duplicateIds.contains(id(map)));
        metadata.keySet().removeIf(map -> !Vars.maps.all().contains(map, true));
        result.sort(Comparator.comparing(Map::plainName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MapCatalog::id));
        return List.copyOf(result);
    }

    public Map find(String id, Gamemode mode, boolean includeDefaultMaps) {
        return available(mode, includeDefaultMaps).stream()
                .filter(map -> id(map).equals(id)).findFirst().orElse(null);
    }

    public static String id(Map map) {
        if (map.custom && Vars.customMapDirectory != null) {
            var root = Vars.customMapDirectory.file().toPath().toAbsolutePath().normalize();
            var file = map.file.file().toPath().toAbsolutePath().normalize();
            if (file.startsWith(root)) {
                return "custom:" + root.relativize(file).toString().replace('\\', '/');
            }
        }
        return (map.custom ? "custom:" : "builtin:") + map.file.name();
    }

    public void invalidate() {
        metadata.clear();
    }

    private boolean readMetadata(Map map) {
        long length = map.file.length();
        long modified = map.file.lastModified();
        Stamp old = metadata.get(map);
        if (old != null && old.length == length && old.modified == modified) return old.readable;
        boolean readable = false;
        try {
            // Headless servers do not create previews. This native reader fills spawn and team metadata.
            var preview = MapIO.generatePreview(map);
            preview.dispose();
            readable = true;
        } catch (IOException | RuntimeException e) {
            Log.warn("Rock the Vote skipped unreadable map @: @", id(map), e.getMessage());
        }
        metadata.put(map, new Stamp(length, modified, readable));
        return readable;
    }
}
