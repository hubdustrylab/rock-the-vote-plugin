package dev.hubdustry.rtv.vote;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VoteSessionTest {
    @Test
    void roundsDecimalThresholdWithoutBinaryFloatingPointError() {
        var players = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) players.add("player-" + i);
        var session = new VoteSession("map", players, "player-0", 0.14, 1000);
        assertEquals(14, session.requiredVotes());
        players.clear();
        assertEquals(100, session.voterCount());
    }

    @Test
    void freezesElectorateAndAutoCastsInitiator() {
        Set<String> players = Set.of("a", "b", "c");
        VoteSession session = new VoteSession("map-b", players, "a", 0.6, 1_000);

        assertEquals(3, session.voterCount());
        assertEquals(2, session.requiredVotes());
        assertEquals(1, session.yesCount());
        assertTrue(session.eligible("c"));
        assertFalse(session.eligible("late"));
        assertEquals(Ballot.YES, session.ballot("a"));
        assertNull(session.ballot("b"));
        assertNull(session.ballot("late"));
    }

    @Test
    void acceptsVoteChangesButNotSameBallotOrIneligiblePlayers() {
        VoteSession session = new VoteSession("map-b", Set.of("a", "b", "c", "d"), "a", 0.5, 1_000);

        assertTrue(session.cast("b", Ballot.NO, 10));
        assertEquals(Ballot.NO, session.ballot("b"));
        assertFalse(session.cast("b", Ballot.NO, 11));
        assertTrue(session.cast("b", Ballot.YES, 12));
        assertEquals(Ballot.YES, session.ballot("b"));
        assertFalse(session.cast("late", Ballot.YES, 13));
        assertEquals(2, session.yesCount());
        assertEquals(0, session.noCount());
    }

    @Test
    void passesAtCeilingThresholdAndRejectsWhenImpossible() {
        VoteSession session = new VoteSession("map-b", Set.of("a", "b", "c", "d", "e"), "a", 0.6, 1_000);
        assertEquals(3, session.requiredVotes());
        assertEquals(Outcome.OPEN, session.outcome(1));
        assertTrue(session.cast("b", Ballot.YES, 2));
        assertEquals(Outcome.OPEN, session.outcome(2));
        assertTrue(session.cast("c", Ballot.YES, 3));
        assertEquals(Outcome.PASSED, session.outcome(3));
        assertFalse(session.cast("a", Ballot.NO, 4));

        VoteSession rejected = new VoteSession("map-c", Set.of("a", "b", "c"), "a", 1.0, 1_000);
        assertTrue(rejected.cast("b", Ballot.NO, 2));
        assertEquals(Outcome.REJECTED, rejected.outcome(2));
        assertFalse(rejected.cast("c", Ballot.YES, 3));
    }

    @Test
    void expiresAtDeadlineAndCannotTransitionAfterTerminal() {
        VoteSession session = new VoteSession("map-b", Set.of("a", "b"), "a", 1.0, 100);
        assertEquals(Outcome.EXPIRED, session.outcome(100));
        assertFalse(session.cast("b", Ballot.YES, 100));
        assertEquals(Outcome.EXPIRED, session.outcome(0));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new VoteSession("map", Set.of("a"), "a", 0.0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new VoteSession("map", Set.of("a"), "b", 1.0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new VoteSession("map", Set.of("a"), "a", 1.0, -1));
    }
}
