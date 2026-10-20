package dev.hubdustry.rtv.preview;

import arc.*;
import arc.backend.sdl.*;
import arc.graphics.PixmapIO;
import arc.math.geom.Vec2;
import arc.graphics.g2d.TextureRegion;
import arc.scene.*;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.ScrollPane;
import arc.util.*;
import mindustry.Vars;
import mindustry.desktop.DesktopLauncher;
import mindustry.game.EventType.*;
import mindustry.gen.Call;

import java.nio.file.*;
import java.util.*;

public final class PreviewClient {
    private static final long START = System.currentTimeMillis();
    private static Path output;
    private static String mapId;
    private static int state;
    private static long nextAction;
    private static long panelReadyAt;
    private static boolean failed;
    private static boolean catalogCaptured;
    private static boolean searchTested;
    private static final boolean resizeTest = Boolean.getBoolean("rtv.preview.resize");
    private static int resizePhase;

    public static void main(String[] args) throws Exception {
        output = Path.of(System.getProperty("rtv.preview.output", "build/preview-output")).toAbsolutePath();
        java.nio.file.Files.createDirectories(output);
        mapId = System.getProperty("rtv.preview.mapId");
        if (mapId == null || mapId.isBlank()) throw new IllegalArgumentException("rtv.preview.mapId is required");
        int width = Integer.getInteger("rtv.preview.width", 1440);
        int height = Integer.getInteger("rtv.preview.height", 900);
        System.setProperty("nodiscord", "true");
        Locale.setDefault(Locale.ENGLISH);
        System.setProperty("mindustry.data.dir", output.resolve("data").toString());
        var launcher = new DesktopLauncher(new String[]{"nodiscord"});
        launcher.add(new ApplicationListener() {
            @Override public void init() {
                Core.settings.put("shownDisclaimer", true);
                Core.settings.put("tutorial", false);
                Core.settings.put("name", "RTV Preview");
                Core.settings.put("locale", "en");
                Events.on(ClientLoadEvent.class, event -> arc.util.Timer.schedule(PreviewClient::connect, 1f));
                Events.run(Trigger.uiDrawEnd, PreviewClient::tick);
            }
        });
        var config = new SdlConfig();
        config.width = width;
        config.height = height;
        config.initialVisible = false;
        config.disableAudio = true;
        config.title = "Rock the Vote Preview";
        try {
            new SdlApplication(launcher, config);
        } catch (Throwable error) {
            fail(error);
            throw error;
        }
        System.exit(failed ? 1 : 0);
    }

    private static void connect() {
        if (Vars.ui == null || Vars.ui.join == null) return;
        Vars.player.name = "RTV Preview";
        Core.settings.put("name", Vars.player.name);
        Vars.ui.join.connect("127.0.0.1", Integer.getInteger("rtv.preview.port"));
    }

    private static void tick() {
        try {
            if (System.currentTimeMillis() - START > 120_000) throw new IllegalStateException("preview timeout at state " + state);
            if (System.currentTimeMillis() < nextAction) return;
            if (state == 0) {
                if (!Vars.net.active() || Vars.netClient.isConnecting() || Vars.ui.loadfrag.shown()) return;
                Call.sendChatMessage("/rtv");
                state = 1;
                nextAction = System.currentTimeMillis() + 700;
            } else if (state == 1) {
                Element dialog = menuDialog();
                Element panel = panel(dialog, "rtv-catalog");
                if (panel == null) return;
                requireAbsent(panel, "Choose the next map", "subtitle", "currentMap", "choose", "close");
                if (!imagesReady(dialog, Integer.getInteger("rtv.preview.catalogImages", 3))) return;
                requireScroll(dialog);
                requirePanelWidth(dialog, "rtv-catalog");
                if (countMapCards(panel) < Integer.getInteger("rtv.preview.cardCount", 6)) return;
                if (panelReadyAt == 0) panelReadyAt = System.currentTimeMillis();
                if (System.currentTimeMillis() - panelReadyAt < 1000) return;
                requireCatalogLayout(panel);
                if (resizeTest && resizePhase == 0) {
                    TextField search = textFieldById(panel, "rtv-search");
                    if (search == null) throw new IllegalStateException("Catalog search field is missing before resize");
                    search.setText("unsent draft");
                    ((SdlGraphics)Core.graphics).setWindowSize(844, 390);
                    resizePhase = 1;
                    state = 20;
                    panelReadyAt = 0;
                    nextAction = System.currentTimeMillis() + 800;
                    return;
                }
                if (!catalogCaptured) {
                    capture("catalog.png");
                    catalogCaptured = true;
                    panelReadyAt = 0;
                }
                if (!searchTested) {
                    TextField search = textFieldById(panel, "rtv-search");
                    Button submit = buttonById(panel, "rtv-search-submit");
                    if (search == null || submit == null) throw new IllegalStateException("Catalog search controls are missing");
                    search.setText("archi");
                    submit.fireClick();
                    state = 13;
                    panelReadyAt = 0;
                    nextAction = System.currentTimeMillis() + 500;
                    return;
                }
                state = 12;
                nextAction = System.currentTimeMillis() + 300;
            } else if (state == 20) {
                Element panel = panel(menuDialog(), "rtv-catalog");
                if (panel == null) return;
                TextField search = textFieldById(panel, "rtv-search");
                if (search == null || !"unsent draft".equals(search.getText())) return;
                requireResponsiveCards(panel, 128f, 5);
                ((SdlGraphics)Core.graphics).setWindowSize(390, 844);
                resizePhase = 2;
                state = 21;
                nextAction = System.currentTimeMillis() + 800;
            } else if (state == 21) {
                Element panel = panel(menuDialog(), "rtv-catalog");
                if (panel == null) return;
                TextField search = textFieldById(panel, "rtv-search");
                if (search == null || !"unsent draft".equals(search.getText())) return;
                requireResponsiveCards(panel, 152f, 2);
                search.setText("");
                resizePhase = 3;
                state = 1;
                panelReadyAt = 0;
                nextAction = System.currentTimeMillis() + 300;
            } else if (state == 13) {
                Element panel = panel(menuDialog(), "rtv-catalog");
                if (panel == null) return;
                TextField search = textFieldById(panel, "rtv-search");
                if (search == null || !"archi".equals(search.getText())) return;
                if (countMapCards(panel) != 1 || !hasText(panel, "Archipel")) {
                    throw new IllegalStateException("Search archi did not return Archipelago");
                }
                search.setText("zzzz");
                Button submit = buttonById(panel, "rtv-search-submit");
                if (submit == null) throw new IllegalStateException("Catalog search submit button is missing");
                submit.fireClick();
                state = 14;
                nextAction = System.currentTimeMillis() + 500;
            } else if (state == 14) {
                Element panel = panel(menuDialog(), "rtv-catalog");
                if (panel == null) return;
                TextField search = textFieldById(panel, "rtv-search");
                if (search == null || !"zzzz".equals(search.getText())) return;
                if (countMapCards(panel) != 0) throw new IllegalStateException("Unknown search returned map cards");
                Button clear = buttonById(panel, "rtv-search-clear");
                if (clear == null) throw new IllegalStateException("Catalog search clear button is missing");
                clear.fireClick();
                state = 15;
                nextAction = System.currentTimeMillis() + 500;
            } else if (state == 15) {
                Element panel = panel(menuDialog(), "rtv-catalog");
                if (panel == null) return;
                TextField search = textFieldById(panel, "rtv-search");
                if (search == null || !search.getText().isEmpty()) return;
                if (countMapCards(panel) < Integer.getInteger("rtv.preview.cardCount", 6)) return;
                searchTested = true;
                panelReadyAt = 0;
                state = 12;
                nextAction = System.currentTimeMillis() + 300;
            } else if (state == 12) {
                Element dialog = menuDialog();
                Element panel = panel(dialog, "rtv-catalog");
                if (panel == null) return;
                scrollCatalog(panel, Integer.getInteger("rtv.preview.cardCount", 6));
                state = 16;
                nextAction = System.currentTimeMillis() + 250;
            } else if (state == 16) {
                Element dialog = menuDialog();
                Element panel = panel(dialog, "rtv-catalog");
                if (panel == null) return;
                int expected = Integer.getInteger("rtv.preview.cardCount", 6);
                requireLastCardVisible(panel, expected);
                capture("catalog-bottom.png");
                resetCatalogScroll(panel);
                state = 17;
                nextAction = System.currentTimeMillis() + 250;
            } else if (state == 17) {
                Element dialog = menuDialog();
                Element panel = panel(dialog, "rtv-catalog");
                if (panel == null) return;
                Button card = firstMapCard(panel);
                if (card == null) throw new IllegalStateException("Catalog map card is missing");
                card.fireClick();
                panelReadyAt = 0;
                state = 3;
                nextAction = System.currentTimeMillis() + 800;
            } else if (state == 3) {
                Element dialog = menuDialog();
                Element panel = panel(dialog, "rtv-vote");
                if (panel == null) return;
                requireAbsent(panel, "votePrompt", "close");
                if (!imagesReady(dialog, Integer.getInteger("rtv.preview.voteImages", 1))) return;
                requireScroll(dialog);
                requirePanelWidth(dialog, "rtv-vote");
                requireVoteLayout(panel);
                Button no = voteButton(panel, false);
                Button yes = voteButton(panel, true);
                if (no == null || yes == null) throw new IllegalStateException("Vote buttons are missing");
                if (no.x >= yes.x) throw new IllegalStateException("Vote order must be No then Yes");
                no.fireClick();
                state = 4;
                nextAction = System.currentTimeMillis() + 600;
            } else if (state == 4) {
                Element panel = panel(menuDialog(), "rtv-vote");
                if (panel == null) return;
                Button no = voteButton(panel, false);
                Button yes = voteButton(panel, true);
                if (no == null || yes == null || !no.isChecked() || yes.isChecked()) return;
                yes.fireClick();
                state = 5;
                panelReadyAt = 0;
                nextAction = System.currentTimeMillis() + 600;
            } else if (state == 5) {
                Element dialog = menuDialog();
                Element panel = panel(dialog, "rtv-vote");
                if (panel == null) return;
                Button no = voteButton(panel, false);
                Button yes = voteButton(panel, true);
                if (no == null || yes == null || no.isChecked() || !yes.isChecked()) return;
                if (!imagesReady(dialog, Integer.getInteger("rtv.preview.voteImages", 1))) return;
                requireScroll(dialog);
                requirePanelWidth(dialog, "rtv-vote");
                requireVoteLayout(panel);
                if (panelReadyAt == 0) panelReadyAt = System.currentTimeMillis();
                if (System.currentTimeMillis() - panelReadyAt < 1000) return;
                capture("vote.png");
                System.out.println("RTV_PREVIEW_CLIENT_PASS");
                state = 4;
                Core.app.exit();
            }
        } catch (Throwable error) {
            failed = true;
            fail(error);
            Core.app.exit();
        }
    }

    private static Element menuDialog() {
        for (Element child : Core.scene.root.getChildren()) {
            if (child.visible && child.getClass().getName().endsWith("Menus$MenuDialog")) return child;
        }
        return null;
    }

    private static boolean hasText(Element element, String needle) {
        if (!element.visible) return false;
        if (element instanceof Label label && label.getText().toString().contains(needle)) return true;
        if (element instanceof Group group) for (Element child : group.getChildren()) if (hasText(child, needle)) return true;
        return false;
    }

    private static Element panel(Element dialog, String name) {
        if (!(dialog instanceof Group group)) return null;
        Element result = group.find(name);
        return result != null && result.visible ? result : null;
    }

    private static void requireAbsent(Element element, String... needles) {
        for (String needle : needles) if (hasText(element, needle)) {
            throw new IllegalStateException("Unexpected catalog/vote body text: " + needle);
        }
    }

    private static List<Button> buttons(Element element) {
        List<Button> result = new ArrayList<>();
        if (element instanceof Button button) result.add(button);
        for (Element child : children(element)) result.addAll(buttons(child));
        return result;
    }

    private static Button voteButton(Element panel, boolean yes) {
        Button identified = buttonById(panel, yes ? "rtv-yes" : "rtv-no");
        if (identified != null) return identified;
        String needle = yes ? "Yes" : "No";
        for (Button button : buttons(panel)) {
            if (hasText(button, needle) || hasText(button, yes ? "✓" : "×")) return button;
        }
        return null;
    }

    private static Button firstMapCard(Element panel) {
        Button identified = buttonById(panel, "rtv-map-0");
        if (identified != null) return identified;
        for (Button button : buttons(panel)) {
            if (hasStreamedImage(button) && !hasText(button, "‹") && !hasText(button, "›")) return button;
        }
        return null;
    }

    private static Button buttonById(Element panel, String id) {
        Element found = panel instanceof Group group ? group.find(id) : null;
        return found instanceof Button button ? button : null;
    }

    private static void requireCards(Element panel, int expected) {
        int count = countMapCards(panel);
        if (count < Math.max(1, expected)) throw new IllegalStateException("Expected " + expected + " map cards, found " + count);
    }

    private static int countMapCards(Element panel) {
        int count = 0;
        for (Button button : buttons(panel)) if (isMapCard(button)) count++;
        return count;
    }

    private static boolean isMapCard(Button button) {
        String name = button.name;
        if (name == null || !name.startsWith("rtv-map-")) return false;
        if (name.length() == 8) return false;
        for (int i = 8; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return true;
    }

    private static void requireResponsiveCards(Element panel, float expectedWidth, int expectedColumns) {
        Set<Integer> columns = new HashSet<>();
        float width = 0f;
        int count = 0;
        for (Button button : buttons(panel)) {
            if (!isMapCard(button)) continue;
            count++;
            width = Math.max(width, button.getWidth());
            columns.add(Math.round(button.localToStageCoordinates(new Vec2()).x / 4f));
        }
        if (count == 0 || columns.size() < expectedColumns - 1 || Math.abs(width - expectedWidth) > 16f) {
            throw new IllegalStateException("Responsive catalog mismatch: cards=" + count
                    + " columns=" + columns.size() + " width=" + width + " expected=" + expectedWidth);
        }
    }

    private static TextField textFieldById(Element panel, String id) {
        Element found = panel instanceof Group group ? group.find(id) : null;
        return found instanceof TextField field ? field : null;
    }

    private static void requireCatalogLayout(Element panel) {
        float[] panelBounds = bounds(panel);
        Element scrollElement = panel instanceof Group group ? group.find("rtv-map-scroll") : null;
        if (!(scrollElement instanceof ScrollPane scroll)) throw new IllegalStateException("Map catalog scroll pane is missing");
        Element content = scroll.getWidget();
        if (content == null) throw new IllegalStateException("Map catalog scroll content is missing");
        float[] contentBounds = bounds(content);
        Set<Integer> columns = new HashSet<>();
        float topRowY = Float.NEGATIVE_INFINITY;
        float topRowHeight = 0f;
        for (Button card : buttons(panel)) {
            if (!isMapCard(card)) continue;
            float[] cardBounds = bounds(card);
            if (cardBounds[0] < contentBounds[0] - 2f || cardBounds[1] < contentBounds[1] - 2f
                    || cardBounds[0] + cardBounds[2] > contentBounds[0] + contentBounds[2] + 2f
                    || cardBounds[1] + cardBounds[3] > contentBounds[1] + contentBounds[3] + 2f) {
                throw new IllegalStateException("Map card exceeds scroll content bounds");
            }
            columns.add(Math.round(cardBounds[0] / 4f));
            if (cardBounds[1] > topRowY + 4f) {
                topRowY = cardBounds[1];
                topRowHeight = cardBounds[3];
            } else if (Math.abs(cardBounds[1] - topRowY) <= 4f) {
                topRowHeight = Math.max(topRowHeight, cardBounds[3]);
            }
        }
        if (columns.size() < (Core.graphics.getWidth() <= 400 ? 1 : 2)) {
            throw new IllegalStateException("Map catalog did not wrap into the expected columns");
        }
        if (scroll.isScrollX()) {
            Element grid = content instanceof Group group ? group.find("rtv-map-grid") : null;
            throw new IllegalStateException("Map catalog has horizontal scrolling: pane=" + scroll.getWidth()
                    + " widget=" + content.getWidth() + " scrollWidth=" + scroll.getScrollWidth()
                    + " panePref=" + scroll.getPrefWidth() + " widgetPref=" + content.getPrefWidth()
                    + " grid=" + (grid == null ? "missing" : grid.getWidth() + "/" + grid.getPrefWidth())
                    + " paneHeight=" + scroll.getHeight() + " widgetHeight=" + content.getHeight());
        }
        if (content.getHeight() > scroll.getHeight() + 2f && !scroll.isScrollY()) {
            throw new IllegalStateException("Map catalog needs vertical scrolling");
        }
        if (topRowHeight > scroll.getHeight() + 2f) {
            throw new IllegalStateException("Top map row exceeds the scroll viewport: cardHeight="
                    + topRowHeight + " viewportHeight=" + scroll.getHeight());
        }
    }

    private static void scrollCatalog(Element panel, int expectedCards) {
        Element scrollElement = panel instanceof Group group ? group.find("rtv-map-scroll") : null;
        if (!(scrollElement instanceof ScrollPane scroll)) throw new IllegalStateException("Map catalog scroll pane is missing");
        if (!scroll.isScrollY()) return;
        scroll.setScrollPercentY(1f);
        scroll.updateVisualScroll();
        if (scroll.getScrollPercentY() < 0.99f) throw new IllegalStateException("Catalog did not reach scroll bottom");
    }

    private static void requireLastCardVisible(Element panel, int expectedCards) {
        Element scrollElement = panel instanceof Group group ? group.find("rtv-map-scroll") : null;
        Element last = panel instanceof Group group ? group.find("rtv-map-" + Math.max(0, expectedCards - 1)) : null;
        if (!(scrollElement instanceof ScrollPane scroll) || !(last instanceof Button)) {
            throw new IllegalStateException("Last map card is missing after scroll");
        }
        float[] pane = bounds(scroll);
        float[] card = bounds(last);
        boolean intersects = card[0] < pane[0] + pane[2] && card[0] + card[2] > pane[0]
                && card[1] < pane[1] + pane[3] && card[1] + card[3] > pane[1];
        if (!intersects) throw new IllegalStateException("Last map card is not visible in the scroll viewport");
    }

    private static void resetCatalogScroll(Element panel) {
        Element scrollElement = panel instanceof Group group ? group.find("rtv-map-scroll") : null;
        if (!(scrollElement instanceof ScrollPane scroll)) throw new IllegalStateException("Map catalog scroll pane is missing");
        scroll.setScrollPercentY(0f);
        scroll.updateVisualScroll();
    }

    private static void requireVoteLayout(Element panel) {
        Element progress = panel instanceof Group group ? group.find("rtv-progress") : null;
        float ratio = Core.graphics.getWidth() > Core.graphics.getHeight() ? 0.55f : 0.8f;
        if (progress == null || progress.getWidth() < panel.getWidth() * ratio) {
            throw new IllegalStateException("Vote progress bar is too narrow");
        }
        Button no = voteButton(panel, false);
        Button yes = voteButton(panel, true);
        if (no == null || yes == null || no.getWidth() + yes.getWidth() < panel.getWidth() * ratio) {
            throw new IllegalStateException("Vote buttons do not fill the vote panel");
        }
        float[] panelBounds = bounds(panel);
        if (panelBounds[0] < -2f || panelBounds[1] < -2f
                || panelBounds[0] + panelBounds[2] > Core.scene.getWidth() + 2f
                || panelBounds[1] + panelBounds[3] > Core.scene.getHeight() + 2f) {
            throw new IllegalStateException("Vote panel exceeds the viewport");
        }
    }

    private static float[] bounds(Element element) {
        Vec2 origin = element.localToStageCoordinates(new Vec2());
        return new float[]{origin.x, origin.y, element.getWidth(), element.getHeight()};
    }

    private static boolean hasStreamedImage(Element element) {
        for (Image image : images(element)) {
            if (!(image.getDrawable() instanceof TextureRegionDrawable drawable)) continue;
            TextureRegion region = drawable.getRegion();
            String name = region instanceof arc.graphics.g2d.TextureAtlas.AtlasRegion atlas ? atlas.name : "";
            if (name.startsWith("net-rtv-image-") && region.texture != null
                    && region.texture.width > 0 && region.texture.height > 0) return true;
        }
        return false;
    }

    private static boolean imagesReady(Element element, int expected) {
        int count = 0;
        for (Image image : images(element)) {
            if (!(image.getDrawable() instanceof TextureRegionDrawable drawable)) continue;
            TextureRegion region = drawable.getRegion();
            String name = region instanceof arc.graphics.g2d.TextureAtlas.AtlasRegion atlas ? atlas.name : "";
            if (region != null && name.startsWith("net-rtv-image-")
                    && region.texture != null && region.texture.width > 0 && region.texture.height > 0) count++;
        }
        return count >= Math.max(1, expected);
    }

    private static void requirePanelWidth(Element dialog, String name) {
        Element panel = dialog instanceof Group group ? group.find(name) : null;
        if (panel == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            throw new IllegalStateException("The panel does not have a bounded content width: " + name);
        }
    }

    private static void requireScroll(Element element) {
        for (Element child : children(element)) {
            if (child instanceof ScrollPane pane && pane.getWidth() <= 0f) throw new IllegalStateException("Menu scroll pane has zero width");
            requireScroll(child);
        }
    }

    private static List<Image> images(Element element) {
        List<Image> result = new ArrayList<>();
        if (element instanceof Image image) result.add(image);
        for (Element child : children(element)) result.addAll(images(child));
        return result;
    }

    private static Iterable<Element> children(Element element) {
        return element instanceof Group group ? group.getChildren() : List.of();
    }

    private static void hide(Element dialog) {
        if (dialog instanceof Dialog d) d.hide();
        else throw new IllegalStateException("Menu dialog is not a Dialog");
    }

    private static void capture(String name) throws Exception {
        var pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight(), true);
        try { PixmapIO.writePng(new arc.files.Fi(output.resolve(name.replace(".png", "-full.png")).toFile()), pixmap); }
        finally { pixmap.dispose(); }
        Element dialog = menuDialog();
        String panelName = name.startsWith("catalog") ? "rtv-catalog" : "rtv-vote";
        Element panel = dialog instanceof Group group ? group.find(panelName) : null;
        if (panel == null) return;
        Vec2 position = panel.localToStageCoordinates(new Vec2());
        float scaleX = Core.graphics.getWidth() / Core.scene.getWidth();
        float scaleY = Core.graphics.getHeight() / Core.scene.getHeight();
        int padding = 48;
        int x = Math.max(0, Math.round(position.x * scaleX) - padding);
        int y = Math.max(0, Math.round(position.y * scaleY) - padding);
        int width = Math.min(Core.graphics.getWidth() - x, Math.round(panel.getWidth() * scaleX) + padding * 2);
        int height = Math.min(Core.graphics.getHeight() - y, Math.round(panel.getHeight() * scaleY) + padding * 2);
        var panelPixels = ScreenUtils.getFrameBufferPixmap(x, y, width, height, true);
        try { PixmapIO.writePng(new arc.files.Fi(output.resolve(name).toFile()), panelPixels); }
        finally { panelPixels.dispose(); }
    }

    private static void fail(Throwable error) {
        try {
            capture("failure.png");
        } catch (Throwable ignored) { }
        System.err.println("RTV_PREVIEW_CLIENT_FAIL " + error);
    }
}
