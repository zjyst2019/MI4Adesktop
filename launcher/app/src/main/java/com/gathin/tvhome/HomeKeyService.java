package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.ComponentName;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * With stock_home_redirect set, it also sends the stock launcher's home screen back here, for firmware that turns
 * that launcher on again after it was disabled (XGIMI's xrmservice does, on its own remote keys).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same maps InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();

    /** Waits for the user to unlock when Android binds this service at boot; null once the config is read. */
    private BroadcastReceiver unlockReceiver;
    /** The stock launcher's HOME activities (stock_home_redirect), disabled ones included. */
    private final Set<String> stockHome = new HashSet<>();
    private String stockPackage = "";
    /** The connected instance, so Home can re-apply the key filter after boot (ensureKeyFilter). */
    private static HomeKeyService running;
    /** config key_filter: whether this helper asks Android to filter keys. */
    private boolean keyFilter = true;
    /** When this process started, in uptime: early means the boot-time bind, which does not filter here. */
    private static final long PROCESS_START_MS = android.os.SystemClock.elapsedRealtime();
    /** How long after boot a process can be the boot-time bind (Home needs the forced rebind then). */
    private static final long BOOT_BIND_WINDOW_MS = 5 * 60 * 1000L;
    /** The one forced rebind has been requested: never toggle the accessibility list again in this process. */
    private static boolean rebindRequested;
    private final Handler usageHandler = new Handler(Looper.getMainLooper());
    private final HomeForeground foreground = new HomeForeground();
    private long usageCursor;
    private boolean usageWasActive;
    private final Runnable usageCheck = new Runnable() {
        @Override public void run() {
            checkUsageHome();
            usageHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        if (Build.VERSION.SDK_INT < 24) {
            loadConfig();
            return;
        }
        // The service is directBootAware: Android 11 skips a service that is not while the user is still
        // locked at boot, writes accessibility_enabled=0 and, on TCL G03 firmware, never binds it after the
        // unlock. Config.load reads credential-protected storage, so wait for the unlock before reading it.
        UserManager users = getSystemService(UserManager.class);
        if (users.isUserUnlocked()) {
            loadConfig();
            return;
        }
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopWaitingForUnlock();
                loadConfig();
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked()) {
            stopWaitingForUnlock();
            loadConfig();
        }
    }

    private void stopWaitingForUnlock() {
        if (unlockReceiver == null) return;
        unregisterReceiver(unlockReceiver);
        unlockReceiver = null;
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        stopWaitingForUnlock();
        usageHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * Makes sure the helper is actually filtering Home, called by Home from onResume. On this firmware the
     * bind Android makes at boot leaves key filtering off: the service is listed and receives window events,
     * but the physical Home key goes to the stock launcher, and setServiceInfo does not bring the filter
     * back. A bind made later (the service re-enabled by hand, or after an install) does filter. So the first
     * Home of a process started at boot asks the system to bind the helper again: it is taken off the
     * enabled-accessibility list for two seconds and put straight back, as a manual re-enable does.
     */
    static void ensureKeyFilter() {
        HomeKeyService service = running;
        if (service == null || !service.keyFilter) return;
        AccessibilityServiceInfo info = service.getServiceInfo();
        // A copy written back at boot has kept the flag yet not filtered; the rebind below is what
        // restores it. This only makes sure the request itself is never missing.
        if (info != null && (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) == 0) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            service.setServiceInfo(info);
        }
        service.forceRebindAfterBoot();
    }

    /** One forced rebind per process, and only for a process that carries the boot-time bind. */
    private void forceRebindAfterBoot() {
        if (rebindRequested || PROCESS_START_MS >= BOOT_BIND_WINDOW_MS) return;
        rebindRequested = true;
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.i("TvHome.Home",
                    "Key filtering can stay off after boot: WRITE_SECURE_SETTINGS is not granted");
            return;
        }
        ContentResolver resolver = getContentResolver();
        String key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
        final String list = Settings.Secure.getString(resolver, key);
        if (list == null) return;
        List<String> rest = new ArrayList<>();
        boolean found = false;
        for (String entry : list.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(entry.trim());
            if (component != null && getPackageName().equals(component.getPackageName())
                    && HomeKeyService.class.getName().equals(component.getClassName())) {
                found = true;
            } else if (!entry.trim().isEmpty()) {
                rest.add(entry.trim());
            }
        }
        if (!found) return;   // turned off by the owner: leave the list alone
        // null clears the setting; Android reads that as "no services" just like an empty list.
        String without = rest.isEmpty() ? null : TextUtils.join(":", rest);
        try {
            if (!Settings.Secure.putString(resolver, key, without)) {
                android.util.Log.w("TvHome.Home", "Could not take the helper off the accessibility list");
                return;
            }
        } catch (SecurityException denied) {
            android.util.Log.w("TvHome.Home", "No WRITE_SECURE_SETTINGS to re-bind the helper", denied);
            return;
        }
        android.util.Log.i("TvHome.Home",
                "Re-binding the helper once after boot; " + rest.size() + " other services kept");
        // Not the service's usageHandler: Android destroys the service when the entry comes off, and that
        // handler drops every callback. This handler belongs to the process and outlives the unbind.
        final Handler restoreHandler = new Handler(Looper.getMainLooper());
        restoreHandler.postDelayed(() -> restoreHelper(restoreHandler, key, list, 0), 2000);
    }

    /** Puts the exact original list back; retries, since leaving the helper off would break Home. */
    private void restoreHelper(Handler handler, String key, String list, int attempt) {
        boolean restored;
        try {
            restored = Settings.Secure.putString(getContentResolver(), key, list);
        } catch (SecurityException denied) {
            restored = false;
        }
        if (!restored && attempt < 3) {
            android.util.Log.w("TvHome.Home", "Restoring the accessibility list failed; retrying");
            handler.postDelayed(() -> restoreHelper(handler, key, list, attempt + 1), 1000);
        }
    }

    private void loadConfig() {
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop. Here, not
        // in onServiceConnected: the note is in credential-protected storage too, readable only after the unlock.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        keyFilter = config.keyFilter;
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        stockHome.clear();
        stockPackage = config.stockHomeRedirect;
        if (!config.stockHomeRedirect.isEmpty()) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(config.stockHomeRedirect);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, PackageManager.GET_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name);
            }
            if ("com.mitv.tvhome".equals(stockPackage)) {
                try {
                    getPackageManager().getActivityInfo(new ComponentName(stockPackage,
                            "com.mitv.tvhome.MainActivity"), 0);
                    stockHome.add("com.mitv.tvhome.MainActivity");
                } catch (PackageManager.NameNotFoundException unavailable) {
                    // Do not treat BootActivity or the whole package as a HOME screen.
                }
            }
        }
        // The upstream metadata requests keys only (eventTypes=0). Subscribe explicitly
        // or stock_home_redirect never receives the stock launcher's window events.
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            // Set the flag here rather than trusting the copy from the XML metadata: at boot Android has
            // been seen to hand back an info without it, and writing that copy back turned filtering off
            // until the service was re-enabled by hand.
            if (config.keyFilter) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.eventTypes = stockHome.isEmpty() ? 0 : AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.packageNames = null;
            setServiceInfo(info);
        }
        android.util.Log.i("TvHome.Home", "Connected; stock HOME windows=" + stockHome.size()
                + "; key filtering=" + config.keyFilter
                + "; flags=0x" + (info == null ? "null" : Integer.toHexString(info.flags)));
        if (!stockHome.isEmpty()) openHome("helper connected");
        usageCursor = Math.max(0, System.currentTimeMillis() - 120000);
        usageWasActive = true;
        usageHandler.removeCallbacks(usageCheck);
        if (!stockHome.isEmpty()) usageHandler.postDelayed(usageCheck, 1000);
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, appScancodes.get(event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()))) return true;
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : SystemSettings.intent(this, Settings.ACTION_SETTINGS);
                try {
                    startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (RemoteKeys.handle(this, event, appKeys.get(event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS
                && event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            openHome("remote HOME key");
        }
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // stock_home_redirect: the firmware turned the stock launcher back on and its home screen came up.
        if (stockHome.isEmpty() || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (HomeRedirectPolicy.matches(stockPackage, stockHome,
                event.getPackageName(), event.getClassName())) {
            openHome("stock HOME window");
        }
    }

    private void openHome(String reason) {
        try {
            // Local Xmen port: no RESET_TASK_IF_NEEDED. Combined with clearTaskOnLaunch it recreated
            // the task on every Home press (~3 s on this 1 GB box); NEW_TASK alone resumes in <200 ms.
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            android.util.Log.i("TvHome.Home", "Opened HOME: " + reason);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            android.util.Log.w("TvHome.Home", "HOME redirect unavailable", unavailable);
        }
    }

    private void checkUsageHome() {
        long now = System.currentTimeMillis();
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        boolean active = power != null && power.isInteractive() && keyguard != null
                && !keyguard.isKeyguardLocked() && ops != null
                && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        if (!active || now < usageCursor) {
            foreground.clear();
            usageCursor = now;
            usageWasActive = false;
            return;
        }
        if (!usageWasActive) {
            foreground.clear();
            usageCursor = now;
            usageWasActive = true;
        }
        UsageStatsManager usage = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (usage == null) return;
        try {
            UsageEvents events = usage.queryEvents(usageCursor, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foreground.foreground(event.getPackageName(), event.getClassName(), event.getTimeStamp());
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    foreground.background(event.getPackageName(), event.getClassName());
                }
            }
            usageCursor = now;
            if (foreground.takeHome(stockPackage, stockHome, now)) openHome("usage foreground (1s)");
        } catch (SecurityException unavailable) {
            foreground.clear();
            usageCursor = now;
        }
    }

    @Override
    public void onInterrupt() {
    }
}
