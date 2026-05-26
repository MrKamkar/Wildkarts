package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Server -> single client: authoritative result of a validated
 * {@link PlayerPassedPointPacket}. The server has accepted the point pass
 * and is reporting back the updated progress state for the receiving
 * player. The client overwrites its local LapComponent with these values.
 *
 * If {@code sectorIndex < 0}, the passed point was an intermediate point
 * (not a sector boundary) — only the advancement fields are meaningful.
 */
public class SectorTimePacket extends ReliablePacket {

    public int playerId;

    /** Index of the sector just completed, or -1 if not a sector boundary. */
    public int sectorIndex;

    /** Time spent in the sector that just ended (seconds). */
    public float sectorTime;

    /** Delta vs. the player's personal best for this sector (s, signed). */
    public float delta;

    /** Updated personal best for this sector. */
    public float bestSectorTime;

    public int currentLap;
    public int nextTrackPointIndex;
    public int currentSector;
    public boolean finished;

    /** Authoritative race timer at the moment the sector ended. */
    public float raceTimerSnapshot;

    /** Time of the just-completed lap (sum of sectors). 0 if not a lap boundary. */
    public float lastLapTime;

    /** Player's personal best full lap time during practice. 0 if no lap completed. */
    public float bestPracticeLapTime;

    public SectorTimePacket() {
    }
}
