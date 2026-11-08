package dev.hubdustry.rtv.uiaccess;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bind each panel action to one player, one token, and one deadline. */
public final class PanelAccess {
    private record Grant(long token, long deadline, Set<String> actions) {}
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Grant> grants = new HashMap<>();

    public long issue(String uuid, long now, Set<String> actions) {
        return issue(uuid, now, 120_000, actions);
    }

    public long issue(String uuid, long now, long lifetimeMillis, Set<String> actions) {
        return grant(uuid, now + lifetimeMillis, actions);
    }

    /** Keep an open catalog valid until a callback, replacement, or cleanup removes it. */
    public long issueUntilClosed(String uuid, Set<String> actions) {
        return grant(uuid, Long.MAX_VALUE, actions);
    }

    private long grant(String uuid, long deadline, Set<String> actions) {
        long token = random.nextLong();
        grants.put(uuid, new Grant(token, deadline, Set.copyOf(actions)));
        return token;
    }

    public boolean consume(String uuid, long token, String action, long now) {
        Grant grant = grants.get(uuid);
        if (grant == null || grant.token != token) return false;
        if (now >= grant.deadline) {
            grants.remove(uuid);
            return false;
        }
        if (action == null) {
            grants.remove(uuid);
            return false;
        }
        if (!grant.actions.contains(action)) return false;
        grants.remove(uuid);
        return true;
    }

    public void remove(String uuid) {
        grants.remove(uuid);
    }

    public void clear() {
        grants.clear();
    }
}
