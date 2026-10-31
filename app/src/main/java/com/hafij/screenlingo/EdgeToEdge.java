package com.hafij.screenlingo;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Draws behind the system bars: the gradient header runs under the status bar. */
final class EdgeToEdge {
    private EdgeToEdge() {}

    /** Pads {@code header} for the status bar and {@code bottom} for the navigation bar and keyboard. */
    @SuppressWarnings("deprecation")
    static void apply(Activity activity, View root, View header, View bottom) {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        int headerTop = header.getPaddingTop();
        int bottomPad = bottom.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bot;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                bot = Math.max(bars.bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                bot = insets.getSystemWindowInsetBottom();
            }
            header.setPadding(header.getPaddingLeft(), headerTop + top, header.getPaddingRight(),
                    header.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(), bottom.getPaddingRight(),
                    bottomPad + bot);
            return insets;
        });
    }
}
