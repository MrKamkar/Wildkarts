package com.wildkarts.net.packets;

import com.wildkarts.net.UnreliablePacket;

/**
 * Server -> all clients: leaderboard snapshot. Parallel arrays describe
 * every connected player. Sent at ~6 Hz (every Nth server tick) over UDP.
 *
 * All arrays have the same length. Index {@code i} describes the same
 * player across all four arrays.
 */
public class RacePositionsUpdatePacket extends UnreliablePacket {

    public int[] playerIds;
    public int[] positions;
    public int[] currentLaps;
    public int[] nextTrackPointIndices;

    public RacePositionsUpdatePacket() {
    }
}
