package com.wildkarts.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * Oblicza wizualny środek sprite'a względem środka tekstury (znormalizowany -0.5…0.5).
 * Używane, aby karoseria auta nie przesuwała się przy zamianie grafiki prostej / skrętu.
 */
public final class SpriteAnchorUtil {

    private SpriteAnchorUtil() {
    }

    /**
     * Przesunięcie kotwicy względem środka tekstury (w jednostkach szerokości/wysokości tekstury).
     */
    public static final class Anchor {

        /** Przesunięcie poziome od środka tekstury (ułamek szerokości). */
        public final float offsetU;

        /** Przesunięcie pionowe od środka tekstury (ułamek wysokości). */
        public final float offsetV;

        /**
         * Tworzy kotwicę z podanymi przesunięciami.
         *
         * @param offsetU przesunięcie poziome
         * @param offsetV przesunięcie pionowe
         */
        public Anchor(float offsetU, float offsetV) {
            this.offsetU = offsetU;
            this.offsetV = offsetV;
        }

        /**
         * Zwraca kopię kotwicy z odwróconym przesunięciem pionowym.
         *
         * @return nowa kotwica z {@code offsetV} pomnożonym przez -1
         */
        public Anchor flippedVertically() {
            return new Anchor(offsetU, -offsetV);
        }
    }

    /**
     * Oblicza kotwicę na podstawie średniej pozycji nieprzezroczystych pikseli w pliku PNG.
     *
     * @param file plik grafiki (może być {@code null})
     * @return kotwica lub (0, 0) gdy plik nie istnieje
     */
    public static Anchor fromFile(FileHandle file) {
        if (file == null || !file.exists())
            return new Anchor(0f, 0f);
        Pixmap pixmap = new Pixmap(file);
        try {
            return fromPixmap(pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    /**
     * Oblicza środek ciężkości widocznych pikseli w pixmapie.
     */
    private static Anchor fromPixmap(Pixmap pixmap) {
        int w = pixmap.getWidth();
        int h = pixmap.getHeight();
        if (w <= 0 || h <= 0)
            return new Anchor(0f, 0f);

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

        if (count == 0)
            return new Anchor(0f, 0f);

        float cx = (float) (sumX / count);
        float cy = (float) (sumY / count);
        return new Anchor(cx / w - 0.5f, cy / h - 0.5f);
    }
}
