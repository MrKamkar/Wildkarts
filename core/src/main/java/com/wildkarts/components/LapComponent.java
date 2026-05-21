package com.wildkarts.components;

import com.badlogic.ashley.core.Component;

/**
 * Per-racer state: lap counter, sector progress, lap/sector timing, and
 * race position. Attached to every go-kart that participates in the race.
 *
 * Track points come from {@code TrackGenerator.getManualPoints()}. The car
 * starts at point 0 and must reach points 1, 2, ..., N-1, 0 (one full lap).
 * The point list is split into {@code RaceComponent.totalSectors} sectors;
 * crossing a sector boundary stamps a sector time and computes a delta
 * against the racer's personal best for that sector.
 */
public class LapComponent implements Component {

    // ─── Lap / Checkpoint Progress ────────────────────────────────────

    /** Current lap number (1-based; starts at 1, increments on finish-line cross). */
    public int currentLap = 1;

    /** Index of the next track point the racer must reach. */
    public int nextTrackPointIndex = 1;

    /** True once {@code currentLap > RaceComponent.maxLaps}. */
    public boolean finished = false;

    // ─── Sector Timing ────────────────────────────────────────────────

    /** Sector index currently being driven (0..totalSectors-1). */
    public int currentSector = 0;

    /** Time accumulated in the current sector since it began (seconds). */
    public float currentSectorElapsed = 0f;

    /** Sector times recorded for the current lap. Index = sector. */
    public final float[] currentLapSectorTimes = new float[3];

    /**
     * Personal best time for each sector across all laps.
     * 0f means "no recorded best yet".
     */
    public final float[] bestSectorTimes = new float[3];

    /**
     * Delta of the most recently completed sector vs the racer's best.
     * Negative = faster than best (green), positive = slower (red),
     * 0 = first time through the sector.
     */
    public float lastSectorDelta = 0f;

    // ─── Race Position ────────────────────────────────────────────────

    /** Current race position assigned by {@code LapSectorSystem} (1 = leader). */
    public int racePosition = 1;

    /**
     * Squared distance from the car to its next track point.
     * Cached each frame and used as a tie-breaker when sorting racers.
     */
    public float distanceToNextPointSq = Float.MAX_VALUE;

    // ─── Lobby / Ready ────────────────────────────────────────────────

    /** Whether this racer has signaled "ready" while in WAITING_FOR_PLAYERS. */
    public boolean ready = false;

    // ─── Multiplayer Sync Helpers ─────────────────────────────────────

    /**
     * Index of the most recently requested point in a
     * {@code PlayerPassedPointPacket}. Acts as a debounce so the client
     * does not flood the server while standing inside the trigger radius.
     * Reset to {@code -1} when the server confirms the point pass by
     * sending back a {@code SectorTimePacket} with a new
     * {@code nextTrackPointIndex}.
     */
    public int lastRequestedPointIndex = -1;
}
