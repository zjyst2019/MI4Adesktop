package com.gathin.tvhome;

import java.util.Set;

/** Only a verified stock HOME window may trigger the device-specific redirect. */
final class HomeRedirectPolicy {
    private HomeRedirectPolicy() { }

    static boolean matches(String stockPackage, Set<String> homeClasses,
                           CharSequence eventPackage, CharSequence eventClass) {
        return !stockPackage.isEmpty() && eventPackage != null && eventClass != null
                && stockPackage.contentEquals(eventPackage)
                && homeClasses.contains(eventClass.toString());
    }
}
