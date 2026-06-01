package com.wildkarts.server;

import com.badlogic.gdx.Gdx;
import com.wildkarts.net.UdpReliabilityManager;
import com.wildkarts.net.packets.SectorTimePacket;

/**
 * Śledzi postęp gracza przez sektory i okrążenia toru po stronie serwera.
 */
public class RaceLapTracker {

    private static final int MAX_LAPS = 3;

    private final UdpReliabilityManager reliabilityManager;

    /**
     * Tworzy tracker okrążeń z menedżerem niezawodnej transmisji UDP.
     *
     * @param reliabilityManager menedżer wysyłki pakietów niezawodnych
     */
    public RaceLapTracker(UdpReliabilityManager reliabilityManager) {
        this.reliabilityManager = reliabilityManager;
    }

    /**
     * Rejestruje minięcie punktu toru w fazie treningu (PRACTICE).
     *
     * @param state        stan gracza po stronie serwera
     * @param passedIdx    indeks miniętego punktu kontrolnego
     * @param totalPoints  łączna liczba punktów toru
     */
    public void advancePointPractice(ServerPlayerState state, int passedIdx, int totalPoints) {
        state.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, ServerPlayerState.TOTAL_SECTORS);

        SectorTimePacket response = createBaseResponse(state, completedSectorIdx);

        if (passedIdx == 0) {
            if (completedSectorIdx >= 0)
                recordSectorTime(state, completedSectorIdx, response);

            float lapTime = 0f;
            for (float t : state.currentLapSectorTimes)
                lapTime += t;

            if (lapTime > 0f) {
                response.lastLapTime = lapTime;
                if (state.bestPracticeLapTime <= 0f || lapTime < state.bestPracticeLapTime) {
                    state.bestPracticeLapTime = lapTime;
                    Gdx.app.log("ServerRaceManager", String.format(
                            "Player %d NEW BEST practice lap: %.2fs", state.playerId, lapTime));
                }
                response.bestPracticeLapTime = state.bestPracticeLapTime;
            }

            state.resetForNextPracticeLap();
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(state, completedSectorIdx, response);
        }

        response.currentLap = state.currentLap;
        response.nextTrackPointIndex = state.nextTrackPointIndex;
        response.currentSector = state.currentSector;
        response.finished = false;
        response.raceTimerSnapshot = 0f;

        reliabilityManager.send(state.connection, response);
    }

    /**
     * Rejestruje minięcie punktu toru w fazie wyścigu (RACING).
     *
     * @param state           stan gracza po stronie serwera
     * @param passedIdx       indeks miniętego punktu kontrolnego
     * @param totalPoints     łączna liczba punktów toru
     * @param raceTimer       aktualny czas wyścigu w sekundach
     * @param onAllFinished   callback wywoływany gdy wszyscy gracze ukończyli wyścig
     */
    public void advancePoint(ServerPlayerState state, int passedIdx, int totalPoints,
                             float raceTimer, Runnable onAllFinished) {
        state.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, ServerPlayerState.TOTAL_SECTORS);

        SectorTimePacket response = createBaseResponse(state, completedSectorIdx);

        if (passedIdx == 0) {
            if (completedSectorIdx >= 0)
                recordSectorTime(state, completedSectorIdx, response);
            state.currentLap++;
            Gdx.app.log("ServerRaceManager",
                    "Player " + state.playerId + " completed lap. Now on " + state.currentLap + "/" + MAX_LAPS);

            if (state.currentLap > MAX_LAPS) {
                state.finished = true;
                state.finishTime = raceTimer;
                Gdx.app.log("ServerRaceManager",
                        "Player " + state.playerId + " FINISHED at " + String.format("%.2f", raceTimer) + "s");
                if (onAllFinished != null)
                    onAllFinished.run();
            }
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(state, completedSectorIdx, response);
        }

        response.currentLap = state.currentLap;
        response.nextTrackPointIndex = state.nextTrackPointIndex;
        response.currentSector = state.currentSector;
        response.finished = state.finished;
        response.raceTimerSnapshot = raceTimer;

        reliabilityManager.send(state.connection, response);
    }

    /**
     * Tworzy pusty pakiet odpowiedzi sektorowej z podstawowymi polami gracza.
     *
     * @param state              stan gracza
     * @param completedSectorIdx indeks ukończonego sektora lub -1
     * @return wstępnie wypełniony pakiet sektorowy
     */
    private SectorTimePacket createBaseResponse(ServerPlayerState state, int completedSectorIdx) {
        SectorTimePacket response = new SectorTimePacket();
        response.playerId = state.playerId;
        response.sectorIndex = completedSectorIdx;
        response.bestSectorTime = 0f;
        response.sectorTime = 0f;
        response.delta = 0f;
        response.lastLapTime = 0f;
        response.bestPracticeLapTime = state.bestPracticeLapTime;
        return response;
    }

    /**
     * Zapisuje czas sektora gracza i aktualizuje jego rekordy osobiste.
     *
     * @param state     stan gracza
     * @param sectorIdx indeks ukończonego sektora
     * @param response  pakiet do wypełnienia danymi sektora
     */
    private void recordSectorTime(ServerPlayerState state, int sectorIdx, SectorTimePacket response) {
        float sectorTime = state.currentSectorElapsed;
        state.currentLapSectorTimes[sectorIdx] = sectorTime;

        float prevBest = state.bestSectorTimes[sectorIdx];
        float delta = (prevBest > 0f) ? sectorTime - prevBest : 0f;
        state.lastSectorDelta = delta;

        if (prevBest <= 0f || sectorTime < prevBest)
            state.bestSectorTimes[sectorIdx] = sectorTime;

        state.currentSectorElapsed = 0f;
        state.currentSector = (sectorIdx + 1) % ServerPlayerState.TOTAL_SECTORS;

        response.sectorTime = sectorTime;
        response.delta = delta;
        response.bestSectorTime = state.bestSectorTimes[sectorIdx];

        Gdx.app.log("ServerRaceManager", String.format(
                "Player %d sector %d: %.2fs (delta %+.2fs)",
                state.playerId, sectorIdx + 1, sectorTime, delta));
    }

    /**
     * Określa, który sektor kończy się na danym punkcie kontrolnym toru.
     *
     * @param passedIdx    indeks miniętego punktu
     * @param totalPoints  liczba punktów toru
     * @param totalSectors liczba sektorów
     * @return indeks sektora lub -1 gdy punkt nie kończy sektora
     */
    static int sectorEndingAt(int passedIdx, int totalPoints, int totalSectors) {
        if (totalSectors <= 1) return -1;
        int sectorSize = Math.max(1, totalPoints / totalSectors);
        for (int s = 0; s < totalSectors - 1; s++) {
            int boundary = (s + 1) * sectorSize;
            if (passedIdx == boundary) return s;
        }
        if (passedIdx == 0) return totalSectors - 1;
        return -1;
    }
}
