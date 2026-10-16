package com.hafij.screenlingo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Full-screen overlay that paints each translation over the text block it replaces. */
final class ResultView extends View {
    static final class Item {
        final Rect box;
        final String text;
        final int background;

        Item(Rect box, String text, int background) {
            this.box = box;
            this.text = text;
            this.background = background;
        }
    }

    private final List<RectF> rects = new ArrayList<>();
    private final List<StaticLayout> layouts = new ArrayList<>();
    private final List<Paint> backgrounds = new ArrayList<>();
    private final Paint hintBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String hint;
    private final float density;
    private final int[] location = new int[2];

    ResultView(Context context, List<Item> items) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        hintBackground.setColor(Color.argb(200, 0, 0, 0));
        hintPaint.setColor(Color.WHITE);
        hintPaint.setTextSize(13 * density);
        hint = context.getString(R.string.result_hint);
        for (Item item : items) add(item);
    }

    private void add(Item item) {
        float pad = 3 * density;
        float min = 9 * density;
        int width = Math.max(item.box.width(), (int) (48 * density));
        int height = Math.max(item.box.height(), (int) min);
        float size = Math.min(22 * density, height * 0.8f);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(isLight(item.background) ? Color.rgb(20, 20, 20) : Color.rgb(240, 240, 240));
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(item.background | 0xFF000000);
        backgrounds.add(background);
        StaticLayout layout;
        while (true) {
            paint.setTextSize(size);
            layout = StaticLayout.Builder.obtain(item.text, 0, item.text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();
            if (layout.getHeight() <= height || size <= min) break;
            size = Math.max(min, size - Math.max(1f, size * 0.08f));
        }
        rects.add(new RectF(item.box.left - pad, item.box.top - pad,
                item.box.left + width + pad, item.box.top + Math.max(height, layout.getHeight()) + pad));
        layouts.add(layout);
    }

    private static boolean isLight(int color) {
        double luminance = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color);
        return luminance > 140;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Boxes are in screen pixels; shift by wherever the window actually landed.
        getLocationOnScreen(location);
        canvas.save();
        canvas.translate(-location[0], -location[1]);
        float radius = 4 * density;
        float pad = 3 * density;
        for (int i = 0; i < rects.size(); i++) {
            RectF r = rects.get(i);
            canvas.drawRoundRect(r, radius, radius, backgrounds.get(i));
            canvas.save();
            canvas.translate(r.left + pad, r.top + pad);
            layouts.get(i).draw(canvas);
            canvas.restore();
        }
        canvas.restore();

        float textWidth = hintPaint.measureText(hint);
        float cx = getWidth() / 2f;
        float bottom = getHeight() - 48 * density;
        float h = 32 * density;
        RectF pill = new RectF(cx - textWidth / 2 - 16 * density, bottom - h, cx + textWidth / 2 + 16 * density, bottom);
        canvas.drawRoundRect(pill, h / 2, h / 2, hintBackground);
        canvas.drawText(hint, cx - textWidth / 2, pill.centerY() - (hintPaint.ascent() + hintPaint.descent()) / 2, hintPaint);
    }
}
