package dev.hubdustry.rtv;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import dev.hubdustry.rtv.config.PluginSettings;
import dev.hubdustry.rtv.i18n.Localizer;
import dev.hubdustry.rtv.maps.MapCatalog;
import dev.hubdustry.rtv.maps.MapSearch;
import dev.hubdustry.rtv.ui.ImageLibrary;
import dev.hubdustry.rtv.ui.PanelRenderer;
import dev.hubdustry.rtv.uiaccess.PanelAccess;
import dev.hubdustry.rtv.uiaccess.ViewportTracker;
import dev.hubdustry.rtv.vote.Ballot;
import dev.hubdustry.rtv.vote.Outcome;
import dev.hubdustry.rtv.vote.VoteSession;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.mod.Plugin;
import mindustry.net.NetConnection;
import mindustry.server.ServerControl;
import mindustry.ui.Menus;
import mindustry.ui.builder.MenuBuilder;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder.NodeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Keep all session changes on the server application thread. */
public final class RockTheVotePlugin extends Plugin {
    private static final long CLOCK_ORIGIN = System.nanoTime();
    private final MapCatalog catalog = new MapCatalog();
    private final PanelAccess access = new PanelAccess();
    private final java.util.Map<String, String> locales = new HashMap<>();
    private final java.util.Map<String, String> selectedMaps = new HashMap<>();
    private final java.util.Map<String, String> searchQueries = new HashMap<>();
    private final java.util.Map<String, CatalogView> catalogViews = new HashMap<>();
    private final java.util.Map<String, Long> lastRequest = new HashMap<>();
    private final java.util.Map<String, NetConnection> imageRecipients = new HashMap<>();
    private final Set<String> viewers = new HashSet<>();
    private PluginSettings settings;
    private Localizer messages;
    private PanelRenderer panels;
    private ImageLibrary images;
    private Path directory;
    private VoteSession session;
    private String targetName;
    private String pendingMap;
    private Gamemode voteMode;
    private long switchAt;
    private long nextVoteAt;
    private long mapStartedAt;
    private long lastRefresh;
    private int menuId;
    private boolean enabled = true;

    private record CatalogView(List<PanelRenderer.MapCard> cards, NetConnection connection, ViewportTracker viewport) {}

    @Override
    public void init() {
        directory = Vars.dataDirectory.child("mods/rock-the-vote").file().toPath();
        try {
            loadConfiguration();
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Rock the Vote configuration failed: " + e.getMessage(), e);
        }
        menuId = Menus.registerMenuBuilder(this::onMenu);
        mapStartedAt = now();
        Events.on(WorldLoadEvent.class, event -> {
            resetVote(false);
            imageRecipients.clear();
            selectedMaps.clear();
            searchQueries.clear();
            mapStartedAt = now();
        });
        Events.on(GameOverEvent.class, event -> resetVote(true));
        Events.on(PlayerLeave.class, event -> {
            String uuid = event.player.uuid();
            access.remove(uuid);
            viewers.remove(uuid);
            locales.remove(uuid);
            selectedMaps.remove(uuid);
            searchQueries.remove(uuid);
            catalogViews.remove(uuid);
            lastRequest.remove(uuid);
            imageRecipients.remove(uuid);
        });
        Events.run(Trigger.update, this::update);
        Log.info("Rock the Vote loaded. Configuration: @", directory);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("rtv", "[action] [value...]", "Open map voting. Use /rtv help for commands.",
                this::clientCommand);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("rtv", "[action]", "Use status, maps, on, off, cancel, or reload.", args -> {
            if (settings == null) {
                Log.info("Rock the Vote is not ready.");
                return;
            }
            String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
            if (action.equals("maps")) {
                for (Map map : catalog.available(mode(), settings.includeDefaultMaps())) {
                    Log.info("@ | @", MapCatalog.id(map), map.plainName());
                }
            } else if (action.equals("status")) {
                Log.info("@", status(settings.defaultLocale()));
            } else {
                String result = adminAction(action);
                Log.info("@", messages.text(settings.defaultLocale(), result == null ? "usage" : result));
            }
        });
    }

    private void clientCommand(String[] args, Player player) {
        if (settings == null || !connected(player) || !throttle(player)) return;
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String value = args.length > 1 ? args[1] : "";
        switch (action) {
            case "", "maps" -> open(player);
            case "yes" -> cast(player, Ballot.YES);
            case "no" -> cast(player, Ballot.NO);
            case "status" -> player.sendMessage(status(locale(player)));
            case "help" -> tell(player, "usage");
            case "lang" -> {
                String requested = value.replace('_', '-').toLowerCase(Locale.ROOT);
                if (!messages.supportedLocales().contains(requested)) {
                    tell(player, "languageInvalid", String.join(", ", messages.supportedLocales()));
                } else {
                    locales.put(player.uuid(), requested);
                    tell(player, "languageSet", requested);
                }
            }
            case "map" -> start(player, value);
            case "on", "off", "cancel", "reload" -> {
                if (!player.admin) {
                    tell(player, "permission");
                } else {
                    tell(player, adminAction(action));
                }
            }
            default -> tell(player, "usage");
        }
    }

    private String adminAction(String action) {
        switch (action) {
            case "on":
                enabled = true;
                return "enabled";
            case "off":
                enabled = false;
                resetVote(true);
                return "disabled";
            case "cancel":
                resetVote(true);
                return "voteCancelled";
            case "reload":
                try {
                    // Read every new component before replacing the active configuration.
                    ImageLibrary previousImages = images;
                    loadConfiguration();
                    catalog.invalidate();
                    resetVote(true);
                    for (Player player : Groups.player) {
                        if (connected(player) && imageRecipients.get(player.uuid()) == player.con) {
                            previousImages.removeFrom(player);
                        }
                    }
                    imageRecipients.clear();
                    return "reloaded";
                } catch (IOException | IllegalArgumentException e) {
                    Log.err("Rock the Vote reload failed: @", e.getMessage());
                    return "reloadFailed";
                }
            default:
                return null;
        }
    }

    private void loadConfiguration() throws IOException {
        PluginSettings nextSettings = PluginSettings.load(directory);
        Localizer nextMessages = new Localizer(nextSettings.defaultLocale(), directory);
        ImageLibrary nextImages = ImageLibrary.load(directory);
        if (nextSettings.bannerRegion().startsWith("net-rtv-image-")
                && !nextImages.regions().contains(nextSettings.bannerRegion())) {
            throw new IllegalArgumentException("The bannerRegion setting refers to a missing PNG image.");
        }
        Files.createDirectories(directory.resolve("images"));
        Files.createDirectories(directory.resolve("locales"));
        settings = nextSettings;
        messages = nextMessages;
        images = nextImages;
        panels = new PanelRenderer(settings, messages);
    }

    private void open(Player player) {
        if (!enabled) {
            tell(player, "disabled");
            return;
        }
        if (!playing()) {
            tell(player, "notPlaying");
            return;
        }
        if (pendingMap != null) {
            tell(player, "busy");
            return;
        }
        if (session != null) {
            showVote(player);
            return;
        }
        // Leave room for the action prefix in the engine result field.
        List<Map> available = catalog.available(mode(), settings.includeDefaultMaps()).stream()
                .filter(map -> MapCatalog.id(map).length() <= 480).toList();
        if (available.isEmpty()) {
            tell(player, "noMaps");
            return;
        }
        String query = searchQueries.getOrDefault(player.uuid(), "");
        available = available.stream().filter(map -> MapSearch.matches(map.plainName(), query)).toList();
        List<PanelRenderer.MapCard> cards = new ArrayList<>();
        Set<String> actions = new HashSet<>();
        actions.add("close");
        actions.add("search");
        if (!query.isEmpty()) actions.add("search-clear");
        for (Map map : available) {
            String id = MapCatalog.id(map);
            cards.add(new PanelRenderer.MapCard(id, safe(map.plainName()), map.width, map.height, images.imageFor(id)));
            actions.add("map:" + id);
        }
        show(player, panels.catalog(locale(player), cards.size(), query), actions);
        var viewport = new ViewportTracker();
        viewport.poll(player.con.viewWidth, player.con.viewHeight, now());
        catalogViews.put(player.uuid(), new CatalogView(List.copyOf(cards), player.con, viewport));
        populateCatalog(player, cards);
    }

    private void populateCatalog(Player player, List<PanelRenderer.MapCard> cards) {
        MenuBuilder.of(panels.catalogSlots(locale(player), cards.size())).id(menuId).update(player, "rtv-map-grid");
        int blocks = PanelRenderer.catalogBlockCount(cards.size());
        if (blocks > 1) {
            for (int block = 0; block < blocks; block++) {
                MenuBuilder.of(panels.catalogBlockFrame(cards.size(), block)).id(menuId)
                        .update(player, "rtv-map-block-" + block);
                MenuBuilder.of(panels.catalogBlockSlots(locale(player), cards.size(), block)).id(menuId)
                        .update(player, "rtv-map-grid-" + block);
            }
        }
        for (int i = 0; i < cards.size(); i++) {
            var card = cards.get(i);
            MenuBuilder.of(panels.catalogCard(locale(player), card, i, card.id().equals(selectedMaps.get(player.uuid()))))
                    .id(menuId).update(player, "rtv-slot-" + i);
        }
    }

    private void showVote(Player player) {
        if (session == null) return;
        Set<String> actions = new HashSet<>(Set.of("close"));
        boolean eligible = session.eligible(player.uuid());
        if (eligible) actions.addAll(Set.of("yes", "no"));
        show(player, panels.vote(locale(player), targetName, session.yesCount(), session.requiredVotes(),
                session.noCount(), seconds(session.deadlineMillis() - now()), eligible,
                images.imageFor(session.targetId()), ballotState(player)), actions);
    }

    private PanelRenderer.BallotState ballotState(Player player) {
        Ballot ballot = session == null ? null : session.ballot(player.uuid());
        return ballot == Ballot.YES ? PanelRenderer.BallotState.YES
                : ballot == Ballot.NO ? PanelRenderer.BallotState.NO : PanelRenderer.BallotState.NONE;
    }

    private void show(Player player, NodeBuilder<?> body, Set<String> actions) {
        if (!connected(player)) return;
        if (imageRecipients.get(player.uuid()) != player.con) {
            images.sendTo(player);
            imageRecipients.put(player.uuid(), player.con);
        }
        long time = now();
        long token = session == null ? access.issueUntilClosed(player.uuid(), actions)
                : access.issue(player.uuid(), time, Math.max(120_000, session.deadlineMillis() - time + 1000), actions);
        viewers.add(player.uuid());
        MenuBuilder.of(body).id(menuId).token(token).title(messages.text(locale(player), "title"))
                .fillScreen(true).hideOnClick(true).hideExisting(true).show(player);
    }

    private void onMenu(Player player, MenuResult result) {
        if (!connected(player) || result == null) return;
        String action = result.result == null ? "close" : result.result;
        if (!access.consume(player.uuid(), result.token, action, now())) return;
        viewers.remove(player.uuid());
        catalogViews.remove(player.uuid());
        if ("close".equals(action) || !throttle(player)) return;
        if (action.equals("search")) {
            String query = MapSearch.normalizedQuery(result.values.get("rtv-search"));
            if (query != null) searchQueries.put(player.uuid(), query);
            open(player);
        } else if (action.equals("search-clear")) {
            searchQueries.remove(player.uuid());
            open(player);
        } else if (action.equals("yes") || action.equals("no")) {
            cast(player, action.equals("yes") ? Ballot.YES : Ballot.NO);
        } else if (action.startsWith("map:")) {
            start(player, action.substring(4));
        }
    }

    private void start(Player player, String mapId) {
        long time = now();
        if (!enabled) { tell(player, "disabled"); return; }
        if (!playing()) { tell(player, "notPlaying"); return; }
        if (session != null || pendingMap != null) { tell(player, "busy"); return; }
        if (time < nextVoteAt) { tell(player, "cooldown", seconds(nextVoteAt - time)); return; }
        long warmupEnds = mapStartedAt + settings.mapStartDelaySeconds() * 1000L;
        if (time < warmupEnds) { tell(player, "warmup", seconds(warmupEnds - time)); return; }
        Gamemode currentMode = mode();
        Map map = catalog.find(mapId, currentMode, settings.includeDefaultMaps());
        if (map == null) { tell(player, "unknownMap"); return; }
        Set<String> electorate = new HashSet<>();
        for (Player voter : Groups.player) {
            if (connected(voter)) electorate.add(voter.uuid());
        }
        if (electorate.size() < settings.minPlayers()) {
            tell(player, "minPlayers", settings.minPlayers());
            return;
        }
        if (!electorate.contains(player.uuid())) { tell(player, "notEligible"); return; }
        session = new VoteSession(mapId, electorate, player.uuid(), settings.requiredRatio(),
                time + settings.voteDurationSeconds() * 1000L);
        selectedMaps.put(player.uuid(), mapId);
        targetName = safe(map.plainName());
        voteMode = currentMode;
        closePanels();
        broadcast("voteStarted", safe(player.name), targetName);
        evaluate(time);
        if (session != null) {
            for (Player voter : Groups.player) showVote(voter);
        }
    }

    private void refreshCatalogs(long time) {
        for (Player player : Groups.player) {
            CatalogView view = catalogViews.get(player.uuid());
            if (view == null || !viewers.contains(player.uuid()) || !connected(player)
                    || view.connection() != player.con) continue;
            if (view.viewport().poll(player.con.viewWidth, player.con.viewHeight, time)) {
                // Keep the search field and its unsent text while the grid changes.
                MenuBuilder.of(panels.catalogGridFrame(view.cards().size())).id(menuId)
                        .update(player, "rtv-map-scroll");
                populateCatalog(player, view.cards());
            }
        }
    }

    private void cast(Player player, Ballot ballot) {
        if (session == null) { tell(player, "noVote"); return; }
        if (!session.eligible(player.uuid())) { tell(player, "notEligible"); return; }
        if (session.cast(player.uuid(), ballot, now())) tell(player, "voteRecorded");
        evaluate(now());
        if (session != null) showVote(player);
    }

    private void evaluate(long time) {
        if (session == null) return;
        Outcome outcome = session.outcome(time);
        if (outcome == Outcome.OPEN) return;
        nextVoteAt = time + settings.cooldownSeconds() * 1000L;
        if (outcome == Outcome.PASSED) {
            pendingMap = session.targetId();
            switchAt = time + settings.changeDelaySeconds() * 1000L;
            broadcast("votePassed", targetName, settings.changeDelaySeconds());
        } else {
            broadcast(outcome == Outcome.EXPIRED ? "voteExpired" : "voteRejected");
        }
        session = null;
        closePanels();
    }

    private void update() {
        if (settings == null) return;
        long time = now();
        if ((session != null || pendingMap != null) && (!playing() || mode() != voteMode)) {
            resetVote(true);
            return;
        }
        evaluate(time);
        if (session == null && pendingMap == null) refreshCatalogs(time);
        if (pendingMap != null && time >= switchAt) {
            changeMap();
            return;
        }
        if (session != null && time - lastRefresh >= 1000) {
            lastRefresh = time;
            for (Player player : Groups.player) {
                if (viewers.contains(player.uuid()) && connected(player)) {
                    MenuBuilder.of(panels.voteStatus(locale(player), session.yesCount(), session.requiredVotes(),
                            session.noCount(), seconds(session.deadlineMillis() - time)))
                            .id(menuId).update(player, "rtv-status");
                }
            }
        }
    }

    private void changeMap() {
        String id = pendingMap;
        pendingMap = null; // Consume the request before the world-load callback.
        Map map = catalog.find(id, voteMode, settings.includeDefaultMaps());
        if (map == null || !playing()) { broadcast("changeFailed"); return; }
        String expectedName = safe(map.plainName());
        try {
            ServerControl.instance.play(false, () -> Vars.world.loadMap(map, map.applyRules(voteMode)));
            if (Vars.state.map != null && MapCatalog.id(Vars.state.map).equals(id) && Vars.state.isGame()
                    && !ServerControl.instance.inGameOverWait) {
                broadcast("changed", expectedName);
                Log.info("Rock the Vote changed the map to @.", id);
            } else {
                broadcast("changeFailed");
                Log.err("Rock the Vote could not confirm the map change to @.", id);
            }
        } catch (RuntimeException e) {
            Log.err("Rock the Vote map change failed.", e);
            broadcast("changeFailed");
        }
    }

    private void resetVote(boolean announce) {
        boolean hadVote = session != null || pendingMap != null;
        session = null;
        pendingMap = null;
        closePanels();
        if (hadVote) {
            nextVoteAt = now() + settings.cooldownSeconds() * 1000L;
            if (announce) broadcast("voteCancelled");
        }
    }

    private void closePanels() {
        access.clear();
        catalogViews.clear();
        for (Player player : Groups.player) {
            if (viewers.contains(player.uuid()) && connected(player)) Call.hideMenuBuilder(player.con, menuId);
        }
        viewers.clear();
    }

    private String status(String locale) {
        if (!enabled) return messages.text(locale, "disabled");
        if (pendingMap != null) return messages.text(locale, "votePassed", targetName, seconds(switchAt - now()));
        if (session == null) return messages.text(locale, "statusIdle");
        return messages.text(locale, "statusActive", targetName, session.yesCount(), session.requiredVotes(),
                session.noCount(), seconds(session.deadlineMillis() - now()));
    }

    private boolean throttle(Player player) {
        long time = now();
        Long previous = lastRequest.get(player.uuid());
        if (previous != null && time - previous < 350) return false;
        lastRequest.put(player.uuid(), time);
        return true;
    }

    private boolean playing() {
        return Vars.state.isGame() && !Vars.state.gameOver && ServerControl.instance != null
                && !ServerControl.instance.inGameOverWait;
    }

    private Gamemode mode() {
        return ServerControl.instance == null || ServerControl.instance.lastMode == null
                ? Gamemode.survival : ServerControl.instance.lastMode;
    }

    private boolean connected(Player player) {
        return player != null && player.con != null && !player.con.hasDisconnected && player.isAdded()
                && player.uuid() != null && !player.uuid().isBlank();
    }

    private String locale(Player player) {
        return locales.getOrDefault(player.uuid(), player.locale);
    }

    private void tell(Player player, String key, Object... args) {
        player.sendMessage(messages.text(locale(player), key, args));
    }

    private void broadcast(String key, Object... args) {
        for (Player player : Groups.player) if (connected(player)) tell(player, key, args);
    }

    static String safe(String value) {
        String text = Strings.stripColors(value == null ? "" : value)
                .replaceAll("[\\p{Cntrl}]", " ").replace("[", "[[");
        return text.substring(0, Math.min(text.length(), 100));
    }

    private static int seconds(long millis) {
        return (int) Math.max(0, (millis + 999) / 1000);
    }

    private static long now() {
        return (System.nanoTime() - CLOCK_ORIGIN) / 1_000_000L;
    }
}
