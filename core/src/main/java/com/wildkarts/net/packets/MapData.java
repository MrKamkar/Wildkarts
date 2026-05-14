package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Contains the track layout as flattened float arrays.
 * Using float[] instead of List<Vector2> for efficient Kryo serialization.
 *
 * Reconstruct points: for (int i = 0; i < pointsX.length; i++)
 *     new Vector2(pointsX[i], pointsY[i]);
 */
public class MapData extends ReliablePacket {

    /** X coordinates of track waypoints. */
    public float[] pointsX;

    /** Y coordinates of track waypoints. */
    public float[] pointsY;

    public MapData() {
    }

    public MapData(float[] pointsX, float[] pointsY) {
        this.pointsX = pointsX;
        this.pointsY = pointsY;
    }
}
