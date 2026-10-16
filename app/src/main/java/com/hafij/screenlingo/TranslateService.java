package com.hafij.screenlingo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Foreground service that owns the screen capture session, the floating bubble and the
 * translation overlay. One VirtualDisplay lives for the whole session (Android 14+ allows
 * only one per MediaProjection); it is resized on rotation instead of recreated.
 */
public class TranslateService extends Service {
    static final String ACTION_START = "com.hafij.screenlingo.START";
    static final String ACTION_STOP = "com.hafij.screenlingo.STOP";
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL = "capture";
    private static final int NOTIFICATION_ID = 1;
    private static final long HIDE_BUBBLE_MS = 250;

    private static WeakReference<TranslateService> current = new WeakReference<>(null);

    static TranslateService instance() {
        return current.get();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private Toast slowToast;
    private final Runnable slowHint = () -> {
        slowToast = Toast.makeText(this, R.string.working, Toast.LENGTH_LONG);
        slowToast.show();
    };
    private HandlerThread captureThread;
    private Handler capture;
    private Context ui;
    private Display screen;
    private WindowManager windows;
    private MediaProjection projection;
    private VirtualDisplay display;
    private volatile ImageReader reader;
    private Image latest; // capture thread only

    private ImageView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private ResultView result;
    private boolean busy;

    private TextRecognizer recognizer;
    private String recognizerScript;
    private TranslateEngine engine;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        screen = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        ui = Build.VERSION.SDK_INT >= 30
                ? createDisplayContext(screen).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                : this;
        windows = ui.getSystemService(WindowManager.class);
        captureThread = new HandlerThread("screen-capture");
        captureThread.start();
        capture = new Handler(captureThread.getLooper());
        engine = new TranslateEngine();
        current = new WeakReference<>(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
        } else if (ACTION_START.equals(action) && projection == null) {
            start(intent);
        } else if (projection == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void start(Intent intent) {
        // Android 14+ requires the media-projection foreground state before getMediaProjection().
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (data != null) {
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(code, data);
        }
        if (projection == null) {
            stopSelf();
            return;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, main);
        Rect bounds = screenBounds();
        reader = newReader(bounds.width(), bounds.height());
        display = projection.createVirtualDisplay("ScreenLingo", bounds.width(), bounds.height(), densityDpi(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, capture);
        showBubble();
        QuickTile.refresh(this);
    }

    // ---------------------------------------------------------------- capture

    private ImageReader newReader(int width, int height) {
        ImageReader r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        r.setOnImageAvailableListener(this::onFrame, capture);
        return r;
    }

    /** Keeps only the newest frame so a capture works even when the screen is static. */
    private void onFrame(ImageReader source) {
        Image image;
        try {
            image = source.acquireNextImage();
        } catch (IllegalStateException e) {
            return;
        }
        if (image == null) return;
        if (source != reader) {
            image.close();
            return;
        }
        if (latest != null) latest.close();
        latest = image;
    }

    /** Hides the bubble and overlay, waits for a clean frame, then reads and translates it. */
    void captureScreen(long extraDelayMs) {
        if (busy || projection == null) return;
        busy = true;
        dismissResult();
        if (bubble != null) bubble.setVisibility(View.INVISIBLE);
        main.postDelayed(() -> capture.post(() -> grabFrame(true)), HIDE_BUBBLE_MS + extraDelayMs);
    }

    private void grabFrame(boolean allowResize) {
        Rect bounds = screenBounds();
        ImageReader r = reader;
        if (r == null) return;
        if (allowResize && (bounds.width() != r.getWidth() || bounds.height() != r.getHeight())) {
            // Rotated since the session started: point the display at a correctly sized surface.
            reader = newReader(bounds.width(), bounds.height());
            display.resize(bounds.width(), bounds.height(), densityDpi());
            display.setSurface(reader.getSurface());
            if (latest != null) {
                latest.close();
                latest = null;
            }
            r.close();
            capture.postDelayed(() -> grabFrame(false), 400);
            return;
        }
        if (latest == null) {
            main.post(() -> fail(getString(R.string.no_frame)));
            return;
        }
        Bitmap bitmap = toBitmap(latest);
        main.post(() -> recognize(bitmap));
    }

    private static Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowPixels = plane.getRowStride() / plane.getPixelStride();
        Bitmap padded = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        if (rowPixels == width) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private Rect screenBounds() {
        if (Build.VERSION.SDK_INT >= 30) return windows.getMaximumWindowMetrics().getBounds();
        DisplayMetrics metrics = new DisplayMetrics();
        screen.getRealMetrics(metrics);
        return new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
    }

    private int densityDpi() {
        return ui.getResources().getDisplayMetrics().densityDpi;
    }

    // ---------------------------------------------------------------- OCR + translation

    private void recognize(Bitmap bitmap) {
        String script = Prefs.script(this);
        if (recognizer == null || !script.equals(recognizerScript)) {
            if (recognizer != null) recognizer.close();
            recognizer = Ocr.create(script);
            recognizerScript = script;
        }
        main.postDelayed(slowHint, 4000);
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(text -> translate(text, bitmap))
                .addOnFailureListener(e -> {
                    bitmap.recycle();
                    fail(e.getMessage());
                });
    }

    private void translate(Text text, Bitmap screenshot) {
        List<Text.TextBlock> blocks = new ArrayList<>();
        List<Integer> backgrounds = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            if (block.getBoundingBox() != null && !block.getText().trim().isEmpty()) {
                blocks.add(block);
                backgrounds.add(backgroundAround(screenshot, block.getBoundingBox()));
            }
        }
        screenshot.recycle();
        if (blocks.isEmpty()) {
            fail(getString(R.string.no_text));
            return;
        }
        String target = Prefs.target(this);
        List<String> spaced = new ArrayList<>();
        for (Text.TextBlock block : blocks) spaced.add(joinLines(block, " "));
        engine.resolveSource(Prefs.source(this), spaced)
                .onSuccessTask(source -> {
                    if (source == null) {
                        return Tasks.forException(new IllegalStateException(getString(R.string.no_language)));
                    }
                    List<String> parts = new ArrayList<>();
                    String separator = Languages.joinsWithoutSpaces(source) ? "" : " ";
                    for (Text.TextBlock block : blocks) parts.add(joinLines(block, separator));
                    if (source.equals(target)) return Tasks.forResult(parts);
                    return engine.translate(source, target, parts);
                })
                .addOnSuccessListener(translated -> showResult(blocks, backgrounds, translated))
                .addOnFailureListener(e -> fail(e.getMessage()));
    }

    /** The most common colour just outside the box, which is the surface the text sits on. */
    private static int backgroundAround(Bitmap bitmap, Rect box) {
        int left = Math.max(0, box.left - 2);
        int top = Math.max(0, box.top - 2);
        int right = Math.min(bitmap.getWidth() - 1, box.right + 2);
        int bottom = Math.min(bitmap.getHeight() - 1, box.bottom + 2);
        Map<Integer, Integer> votes = new HashMap<>();
        int best = 0xFFFFFFFF;
        int bestVotes = 0;
        for (int i = 0; i <= 16; i++) {
            int x = left + (right - left) * i / 16;
            int y = top + (bottom - top) * i / 16;
            int[] samples = {
                    bitmap.getPixel(x, top), bitmap.getPixel(x, bottom),
                    bitmap.getPixel(left, y), bitmap.getPixel(right, y)};
            for (int color : samples) {
                int key = color & 0xF0F0F0; // quantise so anti-aliasing noise votes together
                int count = votes.merge(key, 1, Integer::sum);
                if (count > bestVotes) {
                    bestVotes = count;
                    best = color;
                }
            }
        }
        return best;
    }

    /** OCR splits paragraphs into visual lines; rejoin them so the translator sees sentences. */
    private static String joinLines(Text.TextBlock block, String separator) {
        StringBuilder out = new StringBuilder();
        for (Text.Line line : block.getLines()) {
            if (out.length() > 0) out.append(separator);
            out.append(line.getText().trim());
        }
        return out.toString();
    }

    private void showResult(List<Text.TextBlock> blocks, List<Integer> backgrounds, List<String> translated) {
        cancelSlowHint();
        busy = false;
        if (projection == null) return;
        List<ResultView.Item> items = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            items.add(new ResultView.Item(blocks.get(i).getBoundingBox(), translated.get(i), backgrounds.get(i)));
            if (all.length() > 0) all.append("\n\n");
            all.append(translated.get(i));
        }
        result = new ResultView(ui, items);
        result.setOnClickListener(v -> dismissResult());
        result.setOnLongClickListener(v -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), all));
            toast(getString(R.string.copied));
            return true;
        });
        WindowManager.LayoutParams params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        windows.addView(result, params);
    }

    private void dismissResult() {
        if (result != null) {
            windows.removeView(result);
            result = null;
        }
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
    }

    private void fail(String message) {
        cancelSlowHint();
        busy = false;
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
        if (message != null) toast(message);
    }

    private void cancelSlowHint() {
        main.removeCallbacks(slowHint);
        if (slowToast != null) {
            slowToast.cancel();
            slowToast = null;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- bubble

    private void showBubble() {
        float density = ui.getResources().getDisplayMetrics().density;
        int size = (int) (52 * density);
        bubble = new ImageView(ui);
        bubble.setImageResource(R.drawable.ic_bubble);
        bubble.setContentDescription(getString(R.string.action_translate));
        bubbleParams = overlayParams(size, size,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        bubbleParams.x = Prefs.bubbleX(this, (int) (8 * density));
        bubbleParams.y = Prefs.bubbleY(this, (int) (220 * density));
        bubble.setOnTouchListener(new BubbleTouch(ViewConfiguration.get(ui).getScaledTouchSlop()));
        windows.addView(bubble, bubbleParams);
    }

    private static WindowManager.LayoutParams overlayParams(int width, int height, int flags) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
        return params;
    }

    /** Tap translates, drag moves, long-press stops the session. */
    private final class BubbleTouch implements View.OnTouchListener {
        private final int slop;
        private float downX;
        private float downY;
        private int startX;
        private int startY;
        private boolean dragging;
        private boolean stopped;
        private final Runnable longPress = () -> {
            stopped = true;
            toast(getString(R.string.stopped));
            stopSelf();
        };

        BubbleTouch(int slop) {
            this.slop = slop;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = bubbleParams.x;
                    startY = bubbleParams.y;
                    dragging = false;
                    stopped = false;
                    main.postDelayed(longPress, ViewConfiguration.getLongPressTimeout() * 2L);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) {
                        dragging = true;
                        main.removeCallbacks(longPress);
                    }
                    if (dragging && bubble != null) {
                        bubbleParams.x = startX + (int) dx;
                        bubbleParams.y = startY + (int) dy;
                        windows.updateViewLayout(bubble, bubbleParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    main.removeCallbacks(longPress);
                    if (dragging) {
                        Prefs.setBubble(TranslateService.this, bubbleParams.x, bubbleParams.y);
                    } else if (!stopped) {
                        view.performClick();
                        captureScreen(0);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    main.removeCallbacks(longPress);
                    return true;
                default:
                    return false;
            }
        }
    }

    // ---------------------------------------------------------------- notification + teardown

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW));
        int immutable = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), immutable);
        // An activity launch collapses the notification shade before the frame is taken.
        PendingIntent translate = PendingIntent.getActivity(this, 1,
                new Intent(this, ProjectionActivity.class)
                        .putExtra(ProjectionActivity.EXTRA_CAPTURE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                immutable);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, TranslateService.class).setAction(ACTION_STOP), immutable);
        Icon icon = Icon.createWithResource(this, R.drawable.ic_glyph);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_glyph)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(icon, getString(R.string.action_translate), translate).build())
                .addAction(new Notification.Action.Builder(icon, getString(R.string.action_stop), stop).build())
                .build();
    }

    @Override
    public void onDestroy() {
        current = new WeakReference<>(null);
        main.removeCallbacksAndMessages(null);
        if (result != null) {
            windows.removeView(result);
            result = null;
        }
        if (bubble != null) {
            windows.removeView(bubble);
            bubble = null;
        }
        if (display != null) display.release();
        MediaProjection p = projection;
        projection = null;
        if (p != null) p.stop();
        ImageReader r = reader;
        reader = null;
        capture.post(() -> {
            if (latest != null) latest.close();
            latest = null;
            if (r != null) r.close();
        });
        captureThread.quitSafely();
        if (recognizer != null) recognizer.close();
        engine.close();
        QuickTile.refresh(this);
        super.onDestroy();
    }
}
