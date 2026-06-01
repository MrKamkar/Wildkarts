package com.wildkarts.server;

import com.esotericsoftware.kryonet.Connection;

/**
 * Stan pojedynczego gracza utrzymywany po stronie serwera wyścigu.
 */
public class ServerPlayerState {

    /** Liczba sektorów na okrążeniu toru. */
    public static final int TOTAL_SECTORS = 3;

    public final int playerId;
    public final Connection connection;
    public String name = "Player";

    public boolean ready = false;
    public boolean mapLoaded = false;

    public float lastX = 0f;
    public float lastY = 0f;
    public boolean hasPosition = false;

    public float bestPracticeLapTime = 0f;

    public int currentLap = 1;
    public int nextTrackPointIndex = 1;
    public int currentSector = 0;
    public float currentSectorElapsed = 0f;
    public final float[] currentLapSectorTimes = new float[TOTAL_SECTORS];
    public final float[] bestSectorTimes = new float[TOTAL_SECTORS];
    public float lastSectorDelta = 0f;
    public boolean finished = false;

    public int racePosition = 1;
    public float distanceToNextPointSq = Float.MAX_VALUE;
    public float finishTime = 0f;

    /**
     * Tworzy stan gracza powiązany z identyfikatorem i połączeniem sieciowym.
     *
     * @param playerId   unikalny identyfikator gracza
     * @param connection aktywne połączenie KryoNet
     */
    public ServerPlayerState(int playerId, Connection connection) {
        this.playerId = playerId;
        this.connection = connection;
    }

    /**
     * Resetuje postęp wyścigu, zachowując najlepszy czas treningowy i dane połączenia.
     */
    public void resetForRace() {
        currentLap = 1;
        nextTrackPointIndex = 1;
        currentSector = 0;
        currentSectorElapsed = 0f;
        for (int i = 0; i < TOTAL_SECTORS; i++) {
            currentLapSectorTimes[i] = 0f;
            bestSectorTimes[i] = 0f;
        }
        lastSectorDelta = 0f;
        finished = false;
        finishTime = 0f;
        racePosition = 1;
        distanceToNextPointSq = Float.MAX_VALUE;
    }

    /**
     * Resetuje postęp okrążenia treningowego po ukończeniu okrążenia w fazie PRACTICE.
     */
    public void resetForNextPracticeLap() {
        currentLap = 1;
        nextTrackPointIndex = 1;
        currentSector = 0;
        currentSectorElapsed = 0f;
        for (int i = 0; i < TOTAL_SECTORS; i++)
            currentLapSectorTimes[i] = 0f;
        lastSectorDelta = 0f;
    }
}
