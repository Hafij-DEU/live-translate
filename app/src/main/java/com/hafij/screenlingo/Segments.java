package com.hafij.screenlingo;

import android.graphics.Rect;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns OCR blocks into translation units. ML Kit sometimes groups a heading with the text under
 * it, so a block is split wherever the line height jumps or a large gap opens; lines are then
 * rejoined into sentences, undoing end-of-line hyphenation.
 */
final class Segments {
    static final class Segment {
        final Rect box;
        final String text;

        Segment(Rect box, String text) {
            this.box = box;
            this.text = text;
        }
    }

    private static final float HEIGHT_JUMP = 1.35f;
    private static final float GAP_LINES = 1.2f;

    private Segments() {}

    static List<Segment> from(Text text, boolean spaceBetweenLines) {
        List<Segment> out = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            List<Text.Line> group = new ArrayList<>();
            Rect previous = null;
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box == null || line.getText().trim().isEmpty()) continue;
                if (previous != null && startsNewSegment(previous, box)) {
                    add(out, group, spaceBetweenLines);
                    group = new ArrayList<>();
                }
                group.add(line);
                previous = box;
            }
            add(out, group, spaceBetweenLines);
        }
        return out;
    }

    private static boolean startsNewSegment(Rect previous, Rect next) {
        float a = Math.max(1, previous.height());
        float b = Math.max(1, next.height());
        boolean heightJump = Math.max(a, b) / Math.min(a, b) > HEIGHT_JUMP;
        boolean bigGap = next.top - previous.bottom > GAP_LINES * Math.min(a, b);
        return heightJump || bigGap;
    }

    private static void add(List<Segment> out, List<Text.Line> lines, boolean spaceBetweenLines) {
        if (lines.isEmpty()) return;
        Rect box = new Rect(lines.get(0).getBoundingBox());
        StringBuilder joined = new StringBuilder();
        for (Text.Line line : lines) {
            box.union(line.getBoundingBox());
            String part = line.getText().trim();
            if (joined.length() == 0) {
                joined.append(part);
            } else if (spaceBetweenLines && endsWithHyphen(joined) && startsLowercase(part)) {
                joined.setLength(joined.length() - 1); // "transla-" + "tion" -> "translation"
                joined.append(part);
            } else {
                if (spaceBetweenLines) joined.append(' ');
                joined.append(part);
            }
        }
        String text = joined.toString();
        // Page numbers, prices and icons-as-text read better left alone.
        if (hasLetter(text)) out.add(new Segment(box, text));
    }

    private static boolean endsWithHyphen(CharSequence s) {
        char last = s.charAt(s.length() - 1);
        return (last == '-' || last == '­') && s.length() > 1 && Character.isLetter(s.charAt(s.length() - 2));
    }

    private static boolean startsLowercase(String s) {
        return !s.isEmpty() && Character.isLowerCase(s.codePointAt(0));
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }
}
