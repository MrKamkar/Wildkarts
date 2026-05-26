package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Server -> All clients: sent when the race reaches FINISHED state.
 * Contains the final standings so clients can display a leaderboard.
 */
public class RaceResultsPacket extends ReliablePacket {

    /** Ordered player IDs (index 0 = 1st place). */
    public int[] playerIds;

    /** Player names in the same order. */
    public String[] playerNames;

    /** Final race times (seconds) for each player. 0 if DNF. */
    public float[] finishTimes;

    public RaceResultsPacket() {
    }
}
