package com.gathin.tvhome;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class DeviceEntriesTest {
    @Test public void onlyVerifiedStoreAndMediaBrowserAreFallbacks() {
        String[][] entries = DeviceEntries.components();
        assertEquals(2, entries.length);
        assertArrayEquals(new String[]{"com.xiaomi.mitv.appstore", "com.xiaomi.mitv.appstore.MainActivity"}, entries[0]);
        assertArrayEquals(new String[]{"com.xiaomi.mitv.mediaexplorer",
                "com.xiaomi.mitv.mediaexplorer.NewScraperMainEntryActivity"}, entries[1]);
    }

    @Test public void componentsAreUniqueAndNotInternalTestActivities() {
        Set<String> packages = new HashSet<>();
        for (String[] entry : DeviceEntries.components()) {
            assertTrue(LibraryApps.validPackage(entry[0]));
            assertTrue(entry[1].startsWith(entry[0] + "."));
            assertTrue(packages.add(entry[0]));
            assertFalse(entry[1].contains("Test"));
            assertFalse(entry[1].contains("Factory"));
        }
    }

    @Test public void callersCannotMutateDefaultEntries() {
        DeviceEntries.components()[0][0] = "changed";
        assertEquals("com.xiaomi.mitv.appstore", DeviceEntries.components()[0][0]);
    }

    @Test public void userRemovalStillWinsOverDeviceDefaults() {
        assertTrue(LibraryApps.visible(true, false, false, false));
        assertFalse(LibraryApps.visible(true, false, false, true));
        assertFalse(LibraryApps.visible(true, true, false, false));
    }
}
