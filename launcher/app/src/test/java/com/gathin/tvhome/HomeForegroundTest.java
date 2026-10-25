package com.gathin.tvhome;

import org.junit.Test;
import java.util.Collections;
import java.util.Set;
import static org.junit.Assert.*;

public class HomeForegroundTest {
    private final Set<String> homes = Collections.singleton("Home");
    private final HomeForeground state = new HomeForeground();

    @Test public void waitsOneSecondAndAttemptsOnlyOnce() {
        state.foreground("stock", "Home", 1000);
        assertFalse(state.takeHome("stock", homes, 1999));
        assertTrue(state.takeHome("stock", homes, 2000));
        assertFalse(state.takeHome("stock", homes, 4000));
    }

    @Test public void newerOtherAppMustCancelStockEvidence() {
        state.foreground("stock", "Home", 1000);
        state.foreground("player", "Video", 2000);
        assertFalse(state.takeHome("stock", homes, 9000));
    }

    @Test public void matchingBackgroundClearsCandidate() {
        state.foreground("stock", "Home", 1000);
        state.background("stock", "Home");
        assertFalse(state.takeHome("stock", homes, 9000));
    }

    @Test public void unrelatedBackgroundDoesNotClearNewForeground() {
        state.foreground("stock", "Home", 1000);
        state.background("player", "Video");
        assertTrue(state.takeHome("stock", homes, 9000));
    }

    @Test public void stockNonHomeActivitiesNeverTrigger() {
        state.foreground("stock", "BootActivity", 1000);
        assertFalse(state.takeHome("stock", homes, 9000));
        state.foreground("stock", "Video", 1000);
        assertFalse(state.takeHome("stock", homes, 9000));
    }

    @Test public void screenPauseAndStaleOrFutureEventsNeverTrigger() {
        state.foreground("stock", "Home", 1000);
        assertFalse(state.takeHome("stock", homes, 500));
        assertFalse(state.takeHome("stock", homes, 122000));
        state.clear();
        assertFalse(state.takeHome("stock", homes, 9000));
    }
}
