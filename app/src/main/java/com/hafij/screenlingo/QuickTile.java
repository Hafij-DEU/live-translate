package com.hafij.screenlingo;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: starts the bubble, or translates the screen when it is already on. */
public class QuickTile extends TileService {

    static void refresh(Context context) {
        try {
            requestListeningState(context, new ComponentName(context, QuickTile.class));
        } catch (RuntimeException ignored) {
            // The tile is not added to the panel.
        }
    }

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(TranslateService.instance() != null ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onClick() {
        Intent intent = new Intent(this, ProjectionActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ProjectionActivity.EXTRA_CAPTURE, TranslateService.instance() != null);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
