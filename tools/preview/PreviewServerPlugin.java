package dev.hubdustry.rtv.preview;

import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import arc.files.Fi;
import arc.util.CommandHandler;
import arc.util.Log;
import dev.hubdustry.rtv.maps.MapCatalog;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.io.MapIO;
import mindustry.mod.Plugin;
import mindustry.net.NetConnection;
import mindustry.gen.Player;
import mindustry.server.ServerControl;
import arc.struct.StringMap;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Native loopback preview helper. It is loaded only by the preview runner. */
public final class PreviewServerPlugin extends Plugin {
    private final List<PreviewConnection> connections = new ArrayList<>();

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("rtv-preview-setup", "Prepare native Rock the Vote preview assets and players.", args -> setup());
    }

    private void setup() {
        try {
            clearPlayers();
            if (Vars.netServer == null || ServerControl.instance == null || !Vars.state.isGame()) {
                throw new IllegalStateException("RTV_PREVIEW_SERVER_FAIL server is not hosting a map");
            }

            MapCatalog catalog = new MapCatalog();
            List<Map> maps = catalog.available(Gamemode.survival, true);
            if (maps.isEmpty()) {
                throw new IllegalStateException("RTV_PREVIEW_SERVER_FAIL need valid survival maps");
            }
            List<Map> originals = List.copyOf(maps);
            addFixtureMaps(maps);
            catalog = new MapCatalog();
            maps = catalog.available(Gamemode.survival, true);
            Map target = maps.get(0);
            // Headless servers do not load block colours during normal client setup.
            Vars.content.loadColors();
            writePreviews(originals);

            var response = ServerControl.instance.handler.handleMessage("rtv reload");
            if (response == null) {
                throw new IllegalStateException("RTV_PREVIEW_SERVER_FAIL rtv reload returned no response");
            }
            seedPlayers(4);
            Log.info("RTV_PREVIEW_SERVER_READY target=@ maps=@", MapCatalog.id(target), maps.size());
        } catch (Exception error) {
            Log.err("@", error.getMessage());
            throw new IllegalStateException(error);
        }
    }

    private void addFixtureMaps(List<Map> existing) throws IOException {
        int requested = Integer.getInteger("rtv.preview.mapCount", 0);
        if (requested <= existing.size()) return;
        // Use the smallest valid map. The metadata reader creates one native preview per fixture.
        Map source = existing.stream()
                .min(java.util.Comparator.comparingLong(map -> (long) map.width * map.height))
                .orElseThrow();
        Fi directory = Vars.customMapDirectory;
        Files.createDirectories(directory.file().toPath());
        for (int index = existing.size(); index < requested; index++) {
            Fi file = directory.child("rtv-preview-" + index + ".msav");
            source.file.copyTo(file);
            StringMap tags = new StringMap();
            tags.put("name", "ZZ Preview " + index);
            Map fixture = new Map(file, source.width, source.height, tags, true, source.version, source.build);
            fixture.teams = source.teams;
            fixture.spawns = source.spawns;
            Vars.maps.all().add(fixture);
        }
    }

    private void writePreviews(List<Map> maps) throws IOException {
        Path root = Vars.dataDirectory.file().toPath().resolve("mods/rock-the-vote");
        Path images = root.resolve("images");
        Files.createDirectories(images);
        Properties mapping = new Properties();
        for (Map map : maps) {
            String stem = stem(map);
            Path output = images.resolve(stem + ".png");
            Pixmap preview = MapIO.generatePreview(map);
            try {
                if (preview.width > 1024 || preview.height > 1024) {
                    throw new IOException("RTV_PREVIEW_SERVER_FAIL preview exceeds 1024: " + MapCatalog.id(map));
                }
                PixmapIO.writePng(new Fi(output.toFile()), preview);
            } finally {
                preview.dispose();
            }
            long size = Files.size(output);
            if (size > (1L << 20)) {
                throw new IOException("RTV_PREVIEW_SERVER_FAIL preview exceeds 1 MiB: " + MapCatalog.id(map));
            }
            mapping.setProperty(MapCatalog.id(map), stem);
        }
        try (Writer writer = Files.newBufferedWriter(root.resolve("map-images.properties"), StandardCharsets.UTF_8)) {
            mapping.store(writer, "Generated by rtv-preview-setup");
        }
    }

    private static String stem(Map map) {
        String value = MapCatalog.id(map).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        value = value.replaceAll("^-+|-+$", "");
        if (value.isBlank()) value = "map";
        return value.substring(0, Math.min(value.length(), 80));
    }

    private void seedPlayers(int count) {
        for (int i = 1; i <= count; i++) {
            String uuid = "rtv-preview-" + i;
            Player player = Player.create();
            PreviewConnection connection = new PreviewConnection("rtv-preview/" + i);
            connection.uuid = uuid;
            connection.usid = uuid;
            connection.hasConnected = true;
            connection.player = player;
            player.con = connection;
            player.name("RTV Preview " + i);
            player.locale("en");
            player.add();
            connections.add(connection);
        }
    }

    private void clearPlayers() {
        for (PreviewConnection connection : connections) {
            if (connection.player != null) connection.player.remove();
            connection.close();
        }
        connections.clear();
    }

    private static final class PreviewConnection extends NetConnection {
        private PreviewConnection(String address) { super(address); }

        @Override
        public void send(Object object, boolean reliable) { }

        @Override
        public void close() { hasDisconnected = true; }
    }
}
