package dev.hubdustry.rtv;

import arc.util.CommandHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RockTheVoteTest {
    @Test
    void registersHostCommandWithoutReplacingExistingCommands() {
        var client = new CommandHandler("/");
        var server = new CommandHandler("");
        var voting = new RockTheVote("nextmap", "Next Map");
        var existing = server.register("status", "", args -> {});

        voting.registerClientCommands(client);
        voting.registerServerCommands(server);

        assertEquals("nextmap", client.getCommandList().first().text);
        assertTrue(client.getCommandList().first().description.contains("/nextmap help"));
        assertTrue(server.getCommandList().contains(existing));
        assertThrows(IllegalStateException.class, () -> voting.registerServerCommands(server));
        assertEquals(2, server.getCommandList().size);
    }
}
