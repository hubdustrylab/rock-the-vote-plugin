package dev.hubdustry.rtv.uiaccess;

/** Tracks one client's viewport and reports stable material changes. */
public final class ViewportTracker {
    private static final double CHANGE_RATIO = 0.02;
    private static final double CANDIDATE_VARIATION = 0.01;
    private static final long DEBOUNCE_MILLIS = 250L;
    private static final long REFRESH_MILLIS = 500L;

    private float baselineWidth;
    private float baselineHeight;
    private float candidateWidth;
    private float candidateHeight;
    private long candidateSince;
    private long refreshedAt = Long.MIN_VALUE;
    private boolean initialized;
    private boolean candidate;

    /** Poll a viewport. Return true only when a stable refresh is due. */
    public boolean poll(float width, float height, long now) {
        if (!valid(width, height)) return false;
        if (!initialized) {
            baselineWidth = width;
            baselineHeight = height;
            initialized = true;
            return false;
        }
        if (!materialChange(width, height, baselineWidth, baselineHeight)) {
            candidate = false;
            return false;
        }
        if (!candidate || variation(width, height, candidateWidth, candidateHeight) > CANDIDATE_VARIATION) {
            candidateWidth = width;
            candidateHeight = height;
            candidateSince = now;
            candidate = true;
            return false;
        }
        if (now - candidateSince < DEBOUNCE_MILLIS
                || (refreshedAt != Long.MIN_VALUE && now - refreshedAt < REFRESH_MILLIS)) return false;
        baselineWidth = width;
        baselineHeight = height;
        candidate = false;
        refreshedAt = now;
        return true;
    }

    private static boolean valid(float width, float height) {
        return Float.isFinite(width) && Float.isFinite(height) && width > 0f && height > 0f;
    }

    private static boolean materialChange(float width, float height, float baselineWidth, float baselineHeight) {
        return variation(width, height, baselineWidth, baselineHeight) >= CHANGE_RATIO;
    }

    private static double variation(float width, float height, float otherWidth, float otherHeight) {
        return Math.max(relativeDifference(width, otherWidth), relativeDifference(height, otherHeight));
    }

    private static double relativeDifference(float first, float second) {
        return Math.abs(first - second) / Math.max(first, second);
    }
}
