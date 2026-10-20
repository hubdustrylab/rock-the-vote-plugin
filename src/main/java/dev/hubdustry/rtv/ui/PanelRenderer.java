package dev.hubdustry.rtv.ui;

import arc.util.Scaling;
import dev.hubdustry.rtv.config.PluginSettings;
import dev.hubdustry.rtv.i18n.Localizer;
import dev.hubdustry.rtv.maps.MapSearch;
import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiBuilder.NodeBuilder;

import java.util.Objects;

/** Builds native map cards and a compact vote panel. */
public final class PanelRenderer {
    // Bound each slot packet. Every block remains in the same scroll pane.
    private static final int SLOT_BATCH_SIZE = 360;
    private final PluginSettings config;
    private final Localizer localizer;

    public PanelRenderer(PluginSettings config, Localizer localizer) {
        this.config = Objects.requireNonNull(config, "config");
        this.localizer = Objects.requireNonNull(localizer, "localizer");
    }

    public record MapCard(String id, String name, int width, int height, String imageRegion) {
        public MapCard {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (width < 0 || height < 0) throw new IllegalArgumentException("map dimensions must not be negative");
            imageRegion = imageRegion == null ? "" : imageRegion;
        }
    }

    public enum BallotState { NONE, YES, NO }

    public NodeBuilder<?> catalog(String locale, int mapCount, String query) {
        var root = UiBuilder.table().id("rtv-catalog").grow().margin(8f).align("top");
        var search = UiBuilder.table().growX().padBottom(12f);
        search.add(UiBuilder.buttonTable().id("rtv-search-submit").style("cleart").margin(0f)
                .clicked("search").size(40f).add(UiBuilder.image("zoom").size(28f)));
        search.add(UiBuilder.field(query == null ? "" : query).id("rtv-search").hint(text(locale, "searchMaps"))
                .maxLength(MapSearch.MAX_QUERY_LENGTH).enter("search").growX().height(40f));
        if (query != null && !query.isBlank()) {
            search.add(UiBuilder.buttonTable().id("rtv-search-clear").style("cleart").margin(0f)
                    .clicked("search-clear").size(40f).add(UiBuilder.image("cancel").size(24f)));
        }
        root.add(search);
        root.row();
        if (!config.bannerRegion().isBlank()) {
            root.add(UiBuilder.image(config.bannerRegion()).scaling(Scaling.fit).height(72f).growX().padBottom(8f));
            root.row();
        }
        var scroll = UiBuilder.pane().id("rtv-map-scroll").grow().minWidth(0f).minHeight(0f);
        scroll.add(catalogGridFrame(mapCount));
        root.add(scroll);
        return UiBuilder.table().add(root);
    }

    public NodeBuilder<?> catalogGridFrame(int mapCount) {
        if (catalogBlockCount(mapCount) > 1) {
            return UiBuilder.table().add(UiBuilder.table().id("rtv-map-grid").align("topLeft"));
        }
        return responsiveGrid(mapCount, "rtv-map-grid");
    }

    public static int catalogBlockCount(int mapCount) {
        return mapCount <= 0 ? 0 : 1 + (mapCount - 1) / SLOT_BATCH_SIZE;
    }

    public NodeBuilder<?> catalogBlockFrame(int mapCount, int block) {
        return responsiveGrid(mapCount, "rtv-map-grid-" + block);
    }

    private NodeBuilder<?> responsiveGrid(int mapCount, String gridId) {
        var tall = UiBuilder.table().condition("height >= 560");
        tall.add(gridFrame(mapCount, gridId, 200f, 230f, 0f).condition("width >= 600"));
        var narrow = UiBuilder.table().condition("width < 600");
        narrow.add(gridFrame(mapCount, gridId, 152f, 164f, 60f).condition("width >= 388"));
        narrow.add(gridFrame(mapCount, gridId, 136f, 148f, 60f).condition("width < 388"));
        tall.add(narrow);
        return UiBuilder.table().add(tall)
                .add(gridFrame(mapCount, gridId, 128f, 140f, 60f).condition("height < 560"));
    }

    private NodeBuilder<?> gridFrame(int mapCount, String gridId, float cardWidth, float step, float inset) {
        var variants = UiBuilder.table();
        int maxColumns = Math.min(32, Math.max(1, mapCount));
        for (int columns = 1; columns <= maxColumns; columns++) {
            var range = UiBuilder.table();
            if (columns < maxColumns) range.condition("width < " + ((columns + 1) * step + inset));
            range.add(UiBuilder.table().id(gridId).wrap(true).growX().align("topLeft")
                    .maxWidth(columns * (cardWidth + 12f))
                    .condition("width >= " + (columns == 1 ? 0f : columns * step + inset)));
            variants.add(range);
        }
        return variants;
    }

    /** Send the layout separately from card data to fit the native 25 kB packet buffer. */
    public NodeBuilder<?> catalogSlots(String locale, int mapCount) {
        int blocks = catalogBlockCount(mapCount);
        if (blocks > 1) {
            var root = UiBuilder.table();
            for (int block = 0; block < blocks; block++) {
                root.add(UiBuilder.table().id("rtv-map-block-" + block).growX());
                root.row();
            }
            return root;
        }
        return catalogSlotRange(locale, 0, mapCount);
    }

    public NodeBuilder<?> catalogBlockSlots(String locale, int mapCount, int block) {
        int start = Math.multiplyExact(block, SLOT_BATCH_SIZE);
        return catalogSlotRange(locale, start, Math.min(mapCount, start + SLOT_BATCH_SIZE));
    }

    private NodeBuilder<?> catalogSlotRange(String locale, int start, int end) {
        var slots = UiBuilder.table();
        for (int i = start; i < end; i++) {
            slots.add(UiBuilder.table().id("rtv-slot-" + i).pad(6f));
        }
        if (end == 0) {
            slots.add(UiBuilder.label(text(locale, "noSearchResults")).wrap().labelAlign("center")
                    .width(240f).height(80f));
        }
        return slots;
    }

    public NodeBuilder<?> catalogCard(String locale, MapCard map, int index, boolean selected) {
        var tall = UiBuilder.table().condition("height >= 560");
        tall.add(mapCard(locale, map, index, selected, 200f).condition("width >= 600"));
        var narrow = UiBuilder.table().condition("width < 600");
        narrow.add(mapCard(locale, map, index, selected, 152f).condition("width >= 388"));
        narrow.add(mapCard(locale, map, index, selected, 136f).condition("width < 388"));
        tall.add(narrow);
        return UiBuilder.table().add(tall)
                .add(mapCard(locale, map, index, selected, 128f).condition("height < 560"));
    }

    private NodeBuilder<?> mapCard(String locale, MapCard map, int index, boolean selected, float cardWidth) {
        Objects.requireNonNull(map, "map");
        float previewSize = cardWidth - 18f;
        var card = UiBuilder.buttonTable().id("rtv-map-" + index)
                .style(selected ? "squareTogglet" : "grayt").margin(9f)
                .clicked("map:" + map.id()).checked(selected).width(cardWidth).height(previewSize + 88f);
        card.add(UiBuilder.label(cardTitle(map.name(), previewSize)).wrap().labelAlign("center").height(40f).growX());
        card.row();
        card.add(UiBuilder.image("whiteui").color("454545").height(2f).growX().padBottom(4f));
        card.row();
        card.add(UiBuilder.stack().size(previewSize)
                .add(UiBuilder.image(map.imageRegion().isBlank() ? "nomap" : map.imageRegion())
                        .placeholder("nomap").scaling(Scaling.fit))
                .add(UiBuilder.image("white-pane").color("606060")));
        card.row();
        card.add(UiBuilder.label(text(locale, "mapDetails", map.width(), map.height()))
                .labelAlign("center").height(24f).growX());
        return card;
    }

    private static String cardTitle(String name, float width) {
        int wordLimit = Math.max(6, (int) (width / 11f));
        var result = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!result.isEmpty()) result.append(' ');
            if (word.codePointCount(0, word.length()) > wordLimit) {
                result.append(word, 0, word.offsetByCodePoints(0, wordLimit - 2)).append("...");
            } else result.append(word);
        }
        int limit = wordLimit * 2;
        String title = result.toString();
        return title.codePointCount(0, title.length()) > limit
                ? title.substring(0, title.offsetByCodePoints(0, limit - 2)) + "..." : title;
    }

    public NodeBuilder<?> vote(String locale, String mapName, int yes, int required, int no, int seconds,
                               boolean eligible, String imageRegion) {
        return vote(locale, mapName, yes, required, no, seconds, eligible, imageRegion, BallotState.NONE);
    }

    public NodeBuilder<?> vote(String locale, String mapName, int yes, int required, int no, int seconds,
                               boolean eligible, String imageRegion, BallotState ballot) {
        var tall = UiBuilder.table().condition("height >= 560")
                .add(voteBody(locale, mapName, yes, required, no, seconds, eligible, imageRegion, ballot,
                        Math.min(config.panelWidth(), 460), 280f).condition("width >= 600"))
                .add(voteBody(locale, mapName, yes, required, no, seconds, eligible, imageRegion, ballot,
                        Math.min(config.panelWidth(), 360), 280f).condition("width < 600"));
        var shortScreen = UiBuilder.table().condition("height < 560");
        shortScreen.add(horizontalVote(locale, mapName, yes, required, no, seconds, eligible, imageRegion, ballot)
                .condition("width >= 600"));
        shortScreen.add(voteBody(locale, mapName, yes, required, no, seconds, eligible, imageRegion, ballot,
                Math.min(config.panelWidth(), 360), 128f).condition("width < 600"));
        return UiBuilder.table().add(tall).add(shortScreen);
    }

    private NodeBuilder<?> voteBody(String locale, String mapName, int yes, int required, int no, int seconds,
                                    boolean eligible, String imageRegion, BallotState ballot, float width, float previewSize) {
        var root = UiBuilder.table().id("rtv-vote").width(width).pad(8f);
        root.add(UiBuilder.image(imageRegion == null || imageRegion.isBlank() ? "nomap" : imageRegion)
                .placeholder("nomap").scaling(Scaling.fit).size(Math.min(previewSize, width - 16f)).padBottom(12f));
        root.row();
        root.add(UiBuilder.label(mapName == null ? "" : mapName).wrap().labelAlign("center")
                .growX().padBottom(20f));
        root.row();
        root.add(UiBuilder.table().id("rtv-status").growX()
                .add(voteStatus(locale, yes, required, no, seconds)));
        root.row();
        var choices = UiBuilder.table().growX().padTop(20f);
        choices.add(ballotButton(locale, "no", "cancel", ballot == BallotState.NO, eligible)
                .growX().uniformX().padRight(4f));
        choices.add(ballotButton(locale, "yes", "ok", ballot == BallotState.YES, eligible)
                .growX().uniformX().padLeft(4f));
        root.add(choices);
        if (!eligible) {
            root.row();
            root.add(UiBuilder.label(text(locale, "notEligible")).wrap().labelAlign("center")
                    .color("AAAAAA").growX().padTop(8f));
        }
        return root;
    }

    private NodeBuilder<?> horizontalVote(String locale, String mapName, int yes, int required, int no, int seconds,
                                          boolean eligible, String imageRegion, BallotState ballot) {
        var root = UiBuilder.table().id("rtv-vote").width(600f).pad(8f);
        var preview = UiBuilder.table().width(192f);
        preview.add(UiBuilder.image(imageRegion == null || imageRegion.isBlank() ? "nomap" : imageRegion)
                .placeholder("nomap").scaling(Scaling.fit).size(176f));
        preview.row();
        preview.add(UiBuilder.label(mapName == null ? "" : mapName).wrap().labelAlign("center")
                .width(192f).height(44f).padTop(8f));
        root.add(preview);
        var detail = UiBuilder.table().growX().padLeft(16f);
        detail.add(UiBuilder.table().id("rtv-status").growX()
                .add(voteStatus(locale, yes, required, no, seconds)));
        detail.row();
        var choices = UiBuilder.table().growX().padTop(20f);
        choices.add(ballotButton(locale, "no", "cancel", ballot == BallotState.NO, eligible)
                .growX().uniformX().padRight(4f));
        choices.add(ballotButton(locale, "yes", "ok", ballot == BallotState.YES, eligible)
                .growX().uniformX().padLeft(4f));
        detail.add(choices);
        if (!eligible) {
            detail.row();
            detail.add(UiBuilder.label(text(locale, "notEligible")).wrap().labelAlign("center").growX().padTop(8f));
        }
        root.add(detail);
        return root;
    }

    private NodeBuilder<?> ballotButton(String locale, String action, String icon, boolean checked, boolean eligible) {
        var button = UiBuilder.buttonTable().id("rtv-" + action).style("togglet").margin(0f)
                .clicked(action).checked(checked).disabled(!eligible).height(56f);
        var face = UiBuilder.table().background(checked && eligible ? "button-over" : "button")
                .margin(10f).grow();
        face.add(UiBuilder.image(icon).size(28f).color(eligible ? "FFFFFF" : "888888"));
        face.add(UiBuilder.label(text(locale, action)).labelAlign("center").growX()
                .color(eligible ? "FFFFFF" : "888888"));
        face.add(UiBuilder.space().width(28f));
        button.add(face);
        return button;
    }

    /** Contains both counters and progress so that a status update replaces them together. */
    public NodeBuilder<?> voteStatus(String locale, int yes, int required, int no, int seconds) {
        var content = UiBuilder.table().growX();
        var counters = UiBuilder.table().growX();
        counters.add(stat("ok", Math.max(0, yes) + " / " + Math.max(1, required)).growX().uniformX());
        counters.add(stat("cancel", Integer.toString(Math.max(0, no))).growX().uniformX());
        counters.add(stat(ImageLibrary.CLOCK_REGION, text(locale, "seconds", Math.max(0, seconds))).growX().uniformX());
        content.add(counters);
        content.row();
        int segments = Math.max(1, Math.min(24, required));
        int filled = required <= 0 ? 0 : (int) Math.min(segments, Math.max(0L, (long) yes * segments / required));
        var bar = UiBuilder.table().id("rtv-progress").growX().padTop(12f);
        for (int i = 0; i < segments; i++) {
            bar.add(UiBuilder.image("whiteui").height(12f).color(i < filled ? config.accentColor() : "444444")
                    .growX().uniformX().padLeft(2f).padRight(2f));
        }
        content.add(bar);
        return content;
    }

    private NodeBuilder<?> stat(String icon, String value) {
        return UiBuilder.table().add(UiBuilder.image(icon).size(24f).padRight(8f))
                .add(UiBuilder.label(value).labelAlign("center"));
    }

    private String text(String locale, String key, Object... args) {
        return localizer.text(locale, key, args);
    }
}
