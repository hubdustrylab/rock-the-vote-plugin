package dev.hubdustry.rtv.uiaccess;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PanelAccessTest {
    @Test void openCatalogStaysValidAndStillRejectsReplaysAndClosedGrants() {
        var access = new PanelAccess();
        long token = access.issueUntilClosed("alice", Set.of("search"));
        assertFalse(access.consume("bob", token, "search", 600_000));
        assertFalse(access.consume("alice", token, "map:hidden", 600_000));
        assertTrue(access.consume("alice", token, "search", 600_000));
        assertFalse(access.consume("alice", token, "search", 600_001));
        token = access.issueUntilClosed("alice", Set.of("search"));
        access.remove("alice");
        assertFalse(access.consume("alice", token, "search", 600_002));
    }

    @Test void rejectsForgedForeignAndReplayedActions() {
        var access = new PanelAccess();
        long token = access.issue("alice", 0, Set.of("yes"));
        assertFalse(access.consume("bob", token, "yes", 1));
        assertFalse(access.consume("alice", token + 1, "yes", 1));
        assertFalse(access.consume("alice", token, "map:hidden", 1));
        assertTrue(access.consume("alice", token, "yes", 1));
        assertFalse(access.consume("alice", token, "yes", 1));
    }

    @Test void replacementAndExpiryInvalidateOldPanels() {
        var access = new PanelAccess();
        long old = access.issue("alice", 0, Set.of("yes"));
        long current = access.issue("alice", 1, Set.of("no"));
        assertFalse(access.consume("alice", old, "yes", 2));
        assertFalse(access.consume("alice", current, "no", 120_001));
    }

    @Test void cancellationAndWorldResetInvalidatePanels() {
        var access = new PanelAccess();
        long token = access.issue("alice", 0, Set.of("yes"));
        assertFalse(access.consume("alice", token, null, 1));
        assertFalse(access.consume("alice", token, "yes", 2));
        token = access.issue("alice", 0, Set.of("yes"));
        access.clear();
        assertFalse(access.consume("alice", token, "yes", 2));
    }
}
