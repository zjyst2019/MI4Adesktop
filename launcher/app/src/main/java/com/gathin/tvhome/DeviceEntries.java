package com.gathin.tvhome;

/** Only user-facing components verified from this device's installed manifests. */
final class DeviceEntries {
    private DeviceEntries() { }

    static String[][] components() {
        return new String[][] {
                {"com.xiaomi.mitv.appstore", "com.xiaomi.mitv.appstore.MainActivity"},
                {"com.xiaomi.mitv.mediaexplorer", "com.xiaomi.mitv.mediaexplorer.NewScraperMainEntryActivity"}
        };
    }
}
