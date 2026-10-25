package com.gathin.tvhome;

import java.util.Set;

/** Tracks all foreground/background transitions, not only the stock launcher's. */
final class HomeForeground {
    /** How long the stock HOME must stay in front before this port takes over. The local Xmen port
     *  shortens upstream's 6 s to 1 s so PatchWall's loading screen is barely visible. */
    private static final long MIN_FOREGROUND_MS = 1000;
    private String pkg;
    private String cls;
    private long since;
    private boolean attempted;

    void foreground(String packageName, String className, long timestamp) {
        pkg = packageName;
        cls = className;
        since = timestamp;
        attempted = false;
    }

    void background(String packageName, String className) {
        if (pkg != null && pkg.equals(packageName) && (cls == null || cls.equals(className))) clear();
    }

    void clear() {
        pkg = null;
        cls = null;
        since = 0;
        attempted = false;
    }

    boolean takeHome(String stockPackage, Set<String> homes, long now) {
        if (attempted || !HomeRedirectPolicy.matches(stockPackage, homes, pkg, cls)
                || now < since || now - since < MIN_FOREGROUND_MS || now - since > 120000) return false;
        attempted = true;
        return true;
    }
}
