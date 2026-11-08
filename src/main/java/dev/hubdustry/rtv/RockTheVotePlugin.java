package dev.hubdustry.rtv;

import arc.util.CommandHandler;
import arc.util.serialization.JsonReader;
import mindustry.mod.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Standalone entry point. The reusable voting feature lives in the runtime module. */
public final class RockTheVotePlugin extends Plugin {
    private final RockTheVote voting = createVoting();

    @Override
    public void registerClientCommands(CommandHandler handler) {
        voting.registerClientCommands(handler);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        voting.registerServerCommands(handler);
    }

    @Override
    public void init() {
        voting.init(getConfigFolder().file().toPath());
    }

    private static RockTheVote createVoting() {
        try (var input = RockTheVotePlugin.class.getResourceAsStream("/rtv/standalone.json")) {
            if (input == null) throw new IllegalStateException("Missing standalone plugin identity");
            var identity = new JsonReader().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return new RockTheVote(identity.getString("command"), identity.getString("displayName"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read standalone plugin identity", e);
        }
    }
}
