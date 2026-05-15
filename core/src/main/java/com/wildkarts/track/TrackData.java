package com.wildkarts.track;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Data Transfer Object for track synchronization.
 * Contains all parameters needed to reconstruct the track on a client.
 */
public class TrackData {
    public Array<Vector2> points;
    public int gridWidth;
    public int gridHeight;
    public float trackHalfWidth;

    public TrackData() {
    }

    public TrackData(Array<Vector2> points, int gridWidth, int gridHeight, float trackHalfWidth) {
        this.points = points;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.trackHalfWidth = trackHalfWidth;
    }
}
