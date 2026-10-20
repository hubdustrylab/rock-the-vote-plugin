package dev.hubdustry.rtv.smoke;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.Events;
import arc.func.Cons;
import dev.hubdustry.rtv.RockTheVotePlugin;
import dev.hubdustry.rtv.maps.MapCatalog;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.mod.Plugin;
import mindustry.net.NetConnection;
import mindustry.server.ServerControl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Native controller smoke probe. It uses synthetic players and never opens a socket.
 * It validates the real RockTheVotePlugin command path and map transition.
 */
public final class RtvNativeSmokeProbe extends Plugin {
    private final List<ProbeConnection> connections = new ArrayList<>();

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("rtv-probe", "Run the real RTV controller integration probe.", args -> runProbe());
    }

    private void runProbe() {
        if (Vars.mods == null || Vars.netServer == null || ServerControl.instance == null) {
            throw new IllegalStateException("RTV_PROBE_BLOCKED: server core is not initialized");
        }
        var maps = new MapCatalog().available(Gamemode.survival, true);
        Map current = maps.isEmpty() ? null : maps.get(0);
        Map target = maps.size() < 2 ? null : maps.get(1);
        if (current == null || target == null) {
            throw new IllegalStateException("RTV_PROBE_BLOCKED: need current and target survival maps");
        }

        Vars.world.loadMap(current, current.applyRules(Gamemode.survival));
        Vars.logic.play();
        ServerControl.instance.lastMode = Gamemode.survival;
        Player first = synthetic("probe-one", "probe-one");
        Player second = synthetic("probe-two", "probe-two");
        int[] worldLoads = {0};
        Cons<WorldLoadEvent> counter = event -> worldLoads[0]++;
        Events.on(WorldLoadEvent.class, counter);
        try {
            if (Vars.net.active()) throw new IllegalStateException("RTV_PROBE_FAIL: network is active");
            RockTheVotePlugin plugin = loadedPlugin();
            String targetId = MapCatalog.id(target);
            String start = "/rtv map " + targetId;
            String vote = "/rtv yes";

            String firstResult = Vars.netServer.clientCommands.handleMessage(start, first).runCommand;
            if (firstResult == null) throw new IllegalStateException("RTV_PROBE_FAIL: map command was not parsed");
            Object sessionAfterStart = field(plugin, "session");
            if (sessionAfterStart == null) throw new IllegalStateException("RTV_PROBE_FAIL: real plugin did not create a session");
            if (Vars.state.map == target) throw new IllegalStateException("RTV_PROBE_FAIL: map switched before second vote");

            Vars.netServer.clientCommands.handleMessage(vote, second);
            invokeUpdate(plugin);
            invokeUpdate(plugin);
            if (field(plugin, "session") != null) {
                throw new IllegalStateException("RTV_PROBE_FAIL: session remained after passing vote");
            }
            if (Vars.state.map == null || !MapCatalog.id(Vars.state.map).equals(targetId)) {
                throw new IllegalStateException("RTV_PROBE_FAIL: target map not loaded: " + targetId);
            }
            if (worldLoads[0] != 1) throw new IllegalStateException("RTV_PROBE_FAIL: expected exactly one world load");
            Log.info("RTV_PROBE_PASS controller=RockTheVotePlugin syntheticPlayers=2 target=@", targetId);
        } finally {
            Events.remove(WorldLoadEvent.class, counter);
            first.remove();
            second.remove();
            for (ProbeConnection connection : connections) connection.close();
            connections.clear();
        }
    }

    private RockTheVotePlugin loadedPlugin() {
        var loaded = Vars.mods.getMod(RockTheVotePlugin.class);
        if (loaded == null || !(loaded.main instanceof RockTheVotePlugin plugin)) {
            throw new IllegalStateException("RTV_PROBE_BLOCKED: RockTheVotePlugin is not loaded");
        }
        return plugin;
    }

    private Player synthetic(String uuid, String name) {
        Player player = Player.create();
        ProbeConnection connection = new ProbeConnection("probe/" + uuid);
        connection.uuid = uuid;
        connection.usid = uuid;
        connection.hasConnected = true;
        connection.player = player;
        player.con = connection;
        player.name(name);
        player.locale("en");
        player.add();
        connections.add(connection);
        return player;
    }

    private static Object field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RTV_PROBE_FAIL: cannot inspect " + name, e);
        }
    }

    private static void invokeUpdate(RockTheVotePlugin plugin) {
        try {
            var update = RockTheVotePlugin.class.getDeclaredMethod("update");
            update.setAccessible(true);
            update.invoke(plugin);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RTV_PROBE_FAIL: cannot invoke plugin update", e);
        }
    }

    private static final class ProbeConnection extends NetConnection {
        private final List<Object> sent = new ArrayList<>();

        private ProbeConnection(String address) {
            super(address);
        }

        @Override
        public void send(Object object, boolean reliable) {
            sent.add(object);
        }

        @Override
        public void close() {
            hasDisconnected = true;
        }
    }
}
