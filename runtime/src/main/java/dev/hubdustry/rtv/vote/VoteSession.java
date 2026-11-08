package dev.hubdustry.rtv.vote;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure Java state machine for one map-change vote.
 *
 * <p>The electorate is copied at construction. A disconnected player remains
 * eligible, and a player who joins later cannot vote. The initiator casts YES
 * automatically.</p>
 */
public final class VoteSession {
    private final String targetId;
    private final Set<String> electorate;
    private final Map<String, Ballot> ballots = new HashMap<>();
    private final int requiredVotes;
    private final long deadlineMillis;
    private Outcome terminalOutcome;

    public VoteSession(
            String targetId,
            Set<String> electorate,
            String initiator,
            double requiredRatio,
            long deadlineMillis) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (electorate == null || electorate.isEmpty()) {
            throw new IllegalArgumentException("electorate must not be empty");
        }
        if (electorate.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("electorate contains a blank player id");
        }
        if (initiator == null || !electorate.contains(initiator)) {
            throw new IllegalArgumentException("initiator must belong to electorate");
        }
        if (!Double.isFinite(requiredRatio) || requiredRatio <= 0.0 || requiredRatio > 1.0) {
            throw new IllegalArgumentException("requiredRatio must be in (0, 1]");
        }
        if (deadlineMillis < 0L) {
            throw new IllegalArgumentException("deadlineMillis must not be negative");
        }

        this.targetId = targetId;
        this.electorate = Collections.unmodifiableSet(new HashSet<>(electorate));
        this.requiredVotes = BigDecimal.valueOf(requiredRatio)
                .multiply(BigDecimal.valueOf(this.electorate.size()))
                .setScale(0, RoundingMode.CEILING).intValueExact();
        this.deadlineMillis = deadlineMillis;
        this.ballots.put(initiator, Ballot.YES);
    }

    /** Cast or change an eligible player's ballot. Returns true only on a change. */
    public boolean cast(String uuid, Ballot ballot, long now) {
        Objects.requireNonNull(ballot, "ballot");
        if (uuid == null || !eligible(uuid) || outcome(now) != Outcome.OPEN) {
            return false;
        }
        Ballot previous = ballots.put(uuid, ballot);
        if (ballot == previous) {
            return false;
        }
        evaluate(now);
        return true;
    }

    /** Return the current outcome and freeze it after a terminal decision. */
    public Outcome outcome(long now) {
        return evaluate(now);
    }

    public int yesCount() {
        return count(Ballot.YES);
    }

    public int noCount() {
        return count(Ballot.NO);
    }

    public int requiredVotes() {
        return requiredVotes;
    }

    public int voterCount() {
        return electorate.size();
    }

    public String targetId() {
        return targetId;
    }

    public long deadlineMillis() {
        return deadlineMillis;
    }

    public boolean eligible(String uuid) {
        return uuid != null && electorate.contains(uuid);
    }

    /** Return the player's current ballot, or null if no ballot was cast. */
    public Ballot ballot(String uuid) {
        return ballots.get(uuid);
    }

    private Outcome evaluate(long now) {
        if (terminalOutcome != null) {
            return terminalOutcome;
        }
        if (now >= deadlineMillis) {
            terminalOutcome = Outcome.EXPIRED;
        } else if (yesCount() >= requiredVotes) {
            terminalOutcome = Outcome.PASSED;
        } else if (yesCount() + (voterCount() - ballots.size()) < requiredVotes) {
            terminalOutcome = Outcome.REJECTED;
        }
        return terminalOutcome == null ? Outcome.OPEN : terminalOutcome;
    }

    private int count(Ballot ballot) {
        int count = 0;
        for (Ballot current : ballots.values()) {
            if (current == ballot) {
                count++;
            }
        }
        return count;
    }
}
