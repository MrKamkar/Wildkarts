package com.wildkarts.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/**
 * Computes a sprite's visual center relative to the texture center (normalized -0.5..0.5).
 * Used to keep the car body pinned when swapping between straight / turn artwork.
 */
public final class SpriteAnchorUtil {

    private SpriteAnchorUtil() {
    }

    public static final class Anchor {
        /** Horizontal offset from texture center, in units of texture width. */
        public final float offsetU;
        /** Vertical offset from texture center, in units of texture height. */
        public final float offsetV;

        public Anchor(float offsetU, float offsetV) {
            this.offsetU = offsetU;
            this.offsetV = offsetV;
        }

        public Anchor flippedVertically() {
            return new Anchor(offsetU, -offsetV);
        }
    }

    public static Anchor fromFile(FileHandle file) {
        if (file == null || !file.exists()) {
            return new Anchor(0f, 0f);
        }
        Pixmap pixmap = new Pixmap(file);
        try {
            return fromPixmap(pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static Anchor fromPixmap(Pixmap pixmap) {
        int w = pixmap.getWidth();
        int h = pixmap.getHeight();
        if (w <= 0 || h <= 0) {
            return new Anchor(0f, 0f);
        }

        double sumX = 0d;
        double sumY = 0d;
        long count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((pixmap.getPixel(x, y) >>> 24) > 10) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }

        if (count == 0) {
            return new Anchor(0f, 0f);
        }

        float cx = (float) (sumX / count);
        float cy = (float) (sumY / count);
        return new Anchor(cx / w - 0.5f, cy / h - 0.5f);
    }
}
