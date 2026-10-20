package dev.hubdustry.rtv.ui;

import mindustry.gen.Player;
import mindustry.Vars;
import mindustry.mod.DataImagePacker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Loads operator supplied PNG previews with bounded dimensions and memory. */
public final class ImageLibrary {
    private static final String CLOCK_NAME = "rtv-ui-clock";
    public static final String CLOCK_REGION = DataImagePacker.serverRegionPrefix + CLOCK_NAME;
    private static final String CLOCK_RESOURCE = "/rtv/ui/clock.png";
    private static final Pattern SAFE = Pattern.compile("[a-z0-9-]+");
    public static final int MAX_BYTES_PER_IMAGE = 1 << 20;
    public static final int MAX_TOTAL_BYTES = 8 << 20;
    public static final int MAX_ASSETS = 64;
    public static final int MAX_DIMENSION = 1024;
    private final Map<String, byte[]> images;
    private final Map<String, String> mapLookup;

    private ImageLibrary(Map<String, byte[]> images, Map<String, String> mapLookup) {
        this.images = Map.copyOf(images);
        this.mapLookup = Map.copyOf(mapLookup);
    }

    public static ImageLibrary load(Path directory) throws IOException {
        Map<String, byte[]> loaded = new LinkedHashMap<>();
        loaded.put(CLOCK_NAME, bundledClock());
        if (directory == null || !Files.isDirectory(directory)) return new ImageLibrary(loaded, Map.of());
        Path imageDir = directory.resolve("images");
        int operatorCount = 0;
        int total = 0;
        if (Files.isDirectory(imageDir)) try (var stream = Files.list(imageDir)) {
            for (Path file : stream.sorted().toList()) {
                if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                String base = file.getFileName().toString().substring(0, file.getFileName().toString().length() - 4);
                if (!SAFE.matcher(base).matches()) throw new IOException("Invalid image filename: " + file.getFileName());
                long size = Files.size(file);
                if (size > MAX_BYTES_PER_IMAGE) throw new IOException("Image exceeds 1 MiB: " + file.getFileName());
                if (operatorCount >= MAX_ASSETS) throw new IOException("Image asset limit exceeded: " + MAX_ASSETS);
                if (total + size > MAX_TOTAL_BYTES) throw new IOException("Image byte limit exceeded: " + MAX_TOTAL_BYTES);
                byte[] bytes;
                try (InputStream input = Files.newInputStream(file)) {
                    bytes = input.readNBytes(MAX_BYTES_PER_IMAGE + 1);
                }
                if (bytes.length > MAX_BYTES_PER_IMAGE || total + bytes.length > MAX_TOTAL_BYTES) {
                    throw new IOException("Image byte limit exceeded: " + file.getFileName());
                }
                if (bytes.length < 8 || bytes[0] != (byte)0x89 || bytes[1] != 0x50 || bytes[2] != 0x4e
                        || bytes[3] != 0x47 || bytes[4] != 0x0d || bytes[5] != 0x0a || bytes[6] != 0x1a || bytes[7] != 0x0a) {
                    throw new IOException("Invalid or truncated PNG signature: " + file.getFileName());
                }
                var info = ImageIO.getImageReadersByFormatName("png");
                if (!info.hasNext()) throw new IOException("PNG reader is unavailable");
                var reader = info.next();
                try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    reader.setInput(input, true, true);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w > MAX_DIMENSION || h > MAX_DIMENSION) throw new IOException("Image dimensions exceed 1024: " + file.getFileName());
                    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (decoded == null || decoded.getWidth() != w || decoded.getHeight() != h) {
                        throw new IOException("Malformed or truncated PNG: " + file.getFileName());
                    }
                } catch (RuntimeException e) {
                    throw new IOException("Malformed PNG: " + file.getFileName(), e);
                } finally {
                    reader.dispose();
                }
                loaded.put("rtv-image-" + base, bytes);
                operatorCount++;
                total += bytes.length;
            }
        }
        Map<String, String> lookup = new LinkedHashMap<>();
        Path props = directory.resolve("map-images.properties");
        if (Files.isRegularFile(props)) {
            Properties p = new Properties();
            try (Reader reader = Files.newBufferedReader(props, StandardCharsets.UTF_8)) { p.load(reader); }
            for (String mapId : p.stringPropertyNames()) {
                String base = p.getProperty(mapId, "").trim();
                if (base.isEmpty()) continue;
                if (!SAFE.matcher(base).matches()) throw new IOException("Invalid image reference for map: " + mapId);
                String region = "rtv-image-" + base;
                if (!loaded.containsKey(region)) throw new IOException("Missing image reference: " + base + " for map " + mapId);
                lookup.put(mapId, DataImagePacker.serverRegionPrefix + region);
            }
        }
        return new ImageLibrary(loaded, lookup);
    }

    public String imageFor(String mapId) { return mapLookup.getOrDefault(mapId, ""); }
    public Set<String> regions() {
        return images.keySet().stream().map(name -> DataImagePacker.serverRegionPrefix + name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Sends one operator supplied PNG to one client. */
    public void sendTo(Player player) {
        if (player == null || player.con == null) return;
        for (var entry : images.entrySet()) {
            Vars.netServer.sendTexture(player.con, entry.getKey(), entry.getValue());
        }
    }

    public void removeFrom(Player player) {
        if (player == null || player.con == null) return;
        for (String name : images.keySet()) {
            Vars.netServer.removeTexture(player.con, name);
        }
    }

    private static byte[] bundledClock() throws IOException {
        try (InputStream input = ImageLibrary.class.getResourceAsStream(CLOCK_RESOURCE)) {
            if (input == null) throw new IOException("Missing bundled UI resource: " + CLOCK_RESOURCE);
            return input.readAllBytes();
        }
    }
}
