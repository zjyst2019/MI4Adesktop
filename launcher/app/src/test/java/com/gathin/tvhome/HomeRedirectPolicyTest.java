package com.gathin.tvhome;

import org.junit.Test;
import java.util.Collections;
import java.util.Set;
import static org.junit.Assert.*;

public class HomeRedirectPolicyTest {
    private final Set<String> homes = Collections.singleton("com.mitv.tvhome.MainActivityUserMode");

    @Test public void exactStockHomeMatches() {
        assertTrue(HomeRedirectPolicy.matches("com.mitv.tvhome", homes,
                "com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode"));
    }

    @Test public void stockPlayerAndSettingsRemainUsable() {
        assertFalse(HomeRedirectPolicy.matches("com.mitv.tvhome", homes,
                "com.mitv.tvhome", "com.mitv.videoplayer.VideoPlayerActivity"));
        assertFalse(HomeRedirectPolicy.matches("com.mitv.tvhome", homes,
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
    }

    @Test public void packageMustMatchAsWellAsClass() {
        assertFalse(HomeRedirectPolicy.matches("com.mitv.tvhome", homes,
                "com.other", "com.mitv.tvhome.MainActivityUserMode"));
    }

    @Test public void disabledAndIncompleteEventsDoNotMatch() {
        assertFalse(HomeRedirectPolicy.matches("", homes, "com.mitv.tvhome", "anything"));
        assertFalse(HomeRedirectPolicy.matches("com.mitv.tvhome", homes, null, "anything"));
        assertFalse(HomeRedirectPolicy.matches("com.mitv.tvhome", homes, "com.mitv.tvhome", null));
    }
}
