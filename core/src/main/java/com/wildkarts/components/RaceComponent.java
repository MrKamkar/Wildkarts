package com.wildkarts.components;

import com.badlogic.ashley.core.Component;

/**
 * Singleton component attached to a "race manager" entity.
 * Tracks the current phase of the race, configuration, and global timers.
 */
public class RaceComponent implements Component {

    public RaceState currentState = RaceState.WAITING_FOR_PLAYERS;

    /** Seconds remaining in the pre-race countdown. */
    public float countdownTimer = 3.0f;

    /** Total elapsed time since RACING began (seconds). */
    public float raceTimer = 0.0f;

    // ─── Configuration ────────────────────────────────────────────────

    /** Number of laps required to finish the race. */
    public int maxLaps = 3;

    /** Number of sectors the track is divided into. */
    public int totalSectors = 3;

    /** Total number of control points on the track (sourced from TrackGenerator). */
    public int totalTrackPoints = 0;

    // ─── Ready / Lobby ────────────────────────────────────────────────

    /** Number of players who signaled "ready". */
    public int readyPlayers = 0;

    /** Number of players that must be ready before COUNTDOWN starts. */
    public int requiredPlayers = 1;

    // ─── Multiplayer Authority ────────────────────────────────────────

    /**
     * When true, the client-side {@link com.wildkarts.systems.RaceStateSystem}
     * and {@link com.wildkarts.systems.LapSectorSystem} do not mutate race
     * state on their own — they only mirror values pushed by the server.
     */
    public boolean serverAuthoritative = false;
}
