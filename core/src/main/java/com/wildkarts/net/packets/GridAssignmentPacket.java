package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Server -> All clients: assigns grid (starting) positions based on practice
 * lap times. Sent just before the COUNTDOWN transition. Parallel arrays describe
 * every connected player.
 *
 * Clients must teleport all cars (local + remote) to the given coordinates
 * and zero their velocities.
 */
public class GridAssignmentPacket extends ReliablePacket {

    public int[] playerIds;
    public int[] gridPositions;
    public float[] xs;
    public float[] ys;
    public float[] angles;
    public float[] bestLapTimes;

    public GridAssignmentPacket() {
    }
}
