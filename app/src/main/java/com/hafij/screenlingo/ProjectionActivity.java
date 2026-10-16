package com.hafij.screenlingo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Invisible trampoline: gathers overlay, notification and screen-capture consent, then starts
 * the service. With {@link #EXTRA_CAPTURE} it instead asks the running service for a capture,
 * which lets the tile and notification collapse the system shade first.
 */
public class ProjectionActivity extends Activity {
    static final String EXTRA_CAPTURE = "capture";
    private static final int REQUEST_PROJECTION = 1;
    private static final int REQUEST_NOTIFICATIONS = 2;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TranslateService running = TranslateService.instance();
        if (running != null) {
            if (getIntent().getBooleanExtra(EXTRA_CAPTURE, false)) running.captureScreen(450);
            finish();
            return;
        }
        if (getIntent().getBooleanExtra(EXTRA_CAPTURE, false)) {
            finish();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        askForProjection();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        // The notification is optional; capture works without it.
        askForProjection();
    }

    private void askForProjection() {
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        Intent consent = Build.VERSION.SDK_INT >= 34
                ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                : manager.createScreenCaptureIntent();
        startActivityForResult(consent, REQUEST_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROJECTION) return;
        if (resultCode == RESULT_OK && data != null) {
            startForegroundService(new Intent(this, TranslateService.class)
                    .setAction(TranslateService.ACTION_START)
                    .putExtra(TranslateService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(TranslateService.EXTRA_RESULT_DATA, data));
        } else {
            Toast.makeText(this, R.string.capture_denied, Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
