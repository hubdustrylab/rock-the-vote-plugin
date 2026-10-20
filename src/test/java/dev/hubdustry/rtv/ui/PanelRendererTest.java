package dev.hubdustry.rtv.ui;

import arc.util.io.Reads;
import arc.util.io.Writes;
import dev.hubdustry.rtv.config.PluginSettings;
import dev.hubdustry.rtv.i18n.Localizer;
import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanelRendererTest {
    @Test
    void rendersFullCatalogWithNativeWrappingAndSearch() throws Exception {
        Path root = Files.createTempDirectory("rtv-locales");
        Localizer localizer = new Localizer("en", root);
        PanelRenderer renderer = new PanelRenderer(PluginSettings.defaults(), localizer);
        List<PanelRenderer.MapCard> cards = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            cards.add(new PanelRenderer.MapCard("custom:map-" + i, "Map " + i, 64, 32,
                    "net-rtv-image-map-" + i));
        }

        String dsl = serialize(renderer.catalog("en", cards.size(), ""));
        String slots = serialize(renderer.catalogSlots("en", cards.size()));
        String card = serialize(renderer.catalogCard("en", cards.get(1), 1, true));
        assertTrue(dsl.contains("rtv-map-grid"));
        assertTrue(card.contains("map:custom:map-1"));
        assertTrue(card.contains("buttonTable"));
        assertTrue(card.contains("net-rtv-image-map-1"));
        assertTrue(slots.contains("rtv-slot-17"));
        assertTrue(dsl.contains("rtv-map-scroll"));
        assertTrue(dsl.contains("rtv-search"));
        assertFalse(dsl.contains("page:"));
        assertFalse(dsl.contains("currentMap"));
        assertFalse(dsl.contains("close"));
    }

    @Test
    void largeCatalogKeepsEverySlotWithinNativePacketLimit() throws Exception {
        var renderer = new PanelRenderer(PluginSettings.defaults(),
                new Localizer("en", Files.createTempDirectory("rtv-locales")));
        int mapCount = 1000;
        serialize(renderer.catalog("en", mapCount, ""));
        String skeleton = serialize(renderer.catalogSlots("en", mapCount));
        var slotIds = new java.util.HashSet<Integer>();
        var pattern = java.util.regex.Pattern.compile("rtv-slot-(\\d+)");
        for (int block = 0; block < PanelRenderer.catalogBlockCount(mapCount); block++) {
            assertTrue(skeleton.contains("rtv-map-block-" + block));
            serialize(renderer.catalogBlockFrame(mapCount, block));
            var matcher = pattern.matcher(serialize(renderer.catalogBlockSlots("en", mapCount, block)));
            while (matcher.find()) assertTrue(slotIds.add(Integer.parseInt(matcher.group(1))));
        }
        assertEquals(mapCount, slotIds.size());
        assertTrue(slotIds.contains(0));
        assertTrue(slotIds.contains(mapCount - 1));
    }

    @Test
    void rendersVerticalVoteWithProgressAndBallotHighlight() throws Exception {
        Localizer localizer = new Localizer("en", Files.createTempDirectory("rtv-locales"));
        PanelRenderer renderer = new PanelRenderer(PluginSettings.defaults(), localizer);
        String dsl = renderer.vote("en", "Ground Zero", 3, 5, 1, 12, true,
                "net-rtv-image-ground-zero", PanelRenderer.BallotState.YES).toString();
        assertTrue(dsl.contains("Ground Zero"));
        assertTrue(dsl.contains("rtv-progress"));
        assertTrue(dsl.contains("ok"));
        assertTrue(dsl.contains("cancel"));
        assertTrue(dsl.contains(ImageLibrary.CLOCK_REGION));
        assertTrue(dsl.contains("whiteui"));
        assertTrue(dsl.contains("yes"));
        assertTrue(dsl.contains("no"));
        assertFalse(dsl.contains("close"));
        assertFalse(dsl.contains("Vote for"));
    }

    @Test
    void statusRefreshKeepsProgressWithoutReplacingItsOwnTarget() throws Exception {
        var renderer = new PanelRenderer(PluginSettings.defaults(),
                new Localizer("en", Files.createTempDirectory("rtv-locales")));
        String dsl = serialize(renderer.voteStatus("en", 2, 3, 1, 9));
        assertTrue(dsl.contains("rtv-progress"));
        assertTrue(dsl.contains("2 / 3"));
        assertTrue(dsl.contains("9s"));
        assertFalse(dsl.contains("rtv-status"));
    }

    private static String serialize(UiBuilder.NodeBuilder<?> node) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        node.write(new Writes(new DataOutputStream(bytes)));
        assertTrue(bytes.size() < 24000, "Panel payload: " + bytes.size());
        return UiBuilder.NodeBuilder.read(new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))).toString();
    }
}
