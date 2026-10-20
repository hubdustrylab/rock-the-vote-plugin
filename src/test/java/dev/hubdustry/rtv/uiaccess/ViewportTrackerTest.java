package dev.hubdustry.rtv.uiaccess;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewportTrackerTest {
    @Test
    void requiresStableResizeBeforeRefresh() {
        ViewportTracker tracker = new ViewportTracker();
        assertFalse(tracker.poll(800, 600, 0));
        assertFalse(tracker.poll(840, 600, 100));
        assertFalse(tracker.poll(840, 600, 349));
        assertTrue(tracker.poll(840, 600, 350));
    }

    @Test
    void detectsOrientationSwapAndRateLimitsRefresh() {
        ViewportTracker tracker = new ViewportTracker();
        assertFalse(tracker.poll(800, 600, 0));
        assertFalse(tracker.poll(600, 800, 100));
        assertTrue(tracker.poll(600, 800, 400));
        assertFalse(tracker.poll(800, 600, 500));
        assertTrue(tracker.poll(800, 600, 901));
    }

    @Test
    void ignoresSmallJitterAndInvalidValues() {
        ViewportTracker tracker = new ViewportTracker();
        assertFalse(tracker.poll(800, 600, 0));
        assertFalse(tracker.poll(801, 600, 100));
        assertFalse(tracker.poll(799, 600, 400));
        assertFalse(tracker.poll(Float.NaN, 600, 500));
        assertFalse(tracker.poll(Float.POSITIVE_INFINITY, 600, 600));
        assertFalse(tracker.poll(0, 600, 700));
    }

    @Test
    void candidateVariationResetsDebounce() {
        ViewportTracker tracker = new ViewportTracker();
        assertFalse(tracker.poll(1000, 700, 0));
        assertFalse(tracker.poll(1100, 700, 100));
        assertFalse(tracker.poll(1125, 700, 200));
        assertFalse(tracker.poll(1125, 700, 349));
        assertTrue(tracker.poll(1125, 700, 450));
    }
}
