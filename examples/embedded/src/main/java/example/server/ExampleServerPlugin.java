package example.server;

import arc.util.CommandHandler;
import arc.util.Log;
import dev.hubdustry.rtv.RockTheVote;
import mindustry.mod.Plugin;

/** A host plugin that owns its metadata, commands, and data directory. */
public final class ExampleServerPlugin extends Plugin {
    private final RockTheVote voting = new RockTheVote("mapvote", "Next Map");

    @Override
    public void registerClientCommands(CommandHandler handler) {
        voting.registerClientCommands(handler);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("example-status", "", "Report whether the host plugin is loaded.",
                args -> Log.info("The example server plugin is loaded."));
        voting.registerServerCommands(handler);
    }

    @Override
    public void init() {
        voting.init(getConfigFolder().child("map-voting").file().toPath());
    }
}
