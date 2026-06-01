package com.wildkarts.track;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Obiekt transferu danych do synchronizacji toru między serwerem a klientami.
 * Zawiera parametry potrzebne do odtworzenia toru po stronie klienta.
 */
public class TrackData {

    /** Punkty kontrolne toru. */
    public Array<Vector2> points;

    /** Szerokość siatki kafelków w kafelkach. */
    public int gridWidth;

    /** Wysokość siatki kafelków w kafelkach. */
    public int gridHeight;

    /** Połowa szerokości jezdni w metrach. */
    public float trackHalfWidth;

    /** Konstruktor bezargumentowy wymagany przez serializację (Kryo/JSON). */
    public TrackData() {
    }

    /**
     * Tworzy pakiet danych toru z pełną konfiguracją.
     *
     * @param points         punkty kontrolne
     * @param gridWidth      szerokość siatki
     * @param gridHeight     wysokość siatki
     * @param trackHalfWidth połowa szerokości jezdni
     */
    public TrackData(Array<Vector2> points, int gridWidth, int gridHeight, float trackHalfWidth) {
        this.points = points;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.trackHalfWidth = trackHalfWidth;
    }
}
