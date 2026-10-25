package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ordinary launchers plus the two vetted Xiaomi entry points, never arbitrary MAIN activities. */
final class AppCatalog {
    private AppCatalog() { }

    static List<ResolveInfo> additional(PackageManager pm) {
        List<ResolveInfo> out = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : out) {
            if (info.activityInfo != null) seen.add(info.activityInfo.packageName);
        }
        for (String[] entry : DeviceEntries.components()) {
            if (seen.contains(entry[0])) continue;
            try {
                ActivityInfo activity = pm.getActivityInfo(new ComponentName(entry[0], entry[1]), 0);
                if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) continue;
                if (activity.permission != null && pm.checkPermission(activity.permission,
                        "com.peter.tvhome.xmen") != PackageManager.PERMISSION_GRANTED) continue;
                ResolveInfo info = new ResolveInfo();
                info.activityInfo = activity;
                out.add(info);
                seen.add(entry[0]);
            } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
                // Missing or disabled stock apps are never re-enabled by discovery.
            }
        }
        return out;
    }
}
