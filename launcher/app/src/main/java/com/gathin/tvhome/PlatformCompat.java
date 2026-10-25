package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.media.tv.TvInputInfo;
import android.os.Build;
import android.view.View;

import java.util.Locale;

/** Framework fallbacks which core-library desugaring cannot provide. */
final class PlatformCompat {
    private PlatformCompat() { }

    static void noOverlappingRendering(View view) {
        if (Build.VERSION.SDK_INT >= 24) view.forceHasOverlappingRendering(false);
    }

    @SuppressWarnings("deprecation")
    static Locale locale(Configuration configuration) {
        if (Build.VERSION.SDK_INT >= 24 && !configuration.getLocales().isEmpty()) {
            return configuration.getLocales().get(0);
        }
        return configuration.locale == null ? Locale.getDefault() : configuration.locale;
    }

    static boolean inputHidden(TvInputInfo input, Context context) {
        return Build.VERSION.SDK_INT >= 24 && input.isHidden(context);
    }

    static CharSequence inputLabel(TvInputInfo input, Context context) {
        return Build.VERSION.SDK_INT >= 24 ? input.loadCustomLabel(context) : null;
    }
}
