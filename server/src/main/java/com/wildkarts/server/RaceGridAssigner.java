package com.wildkarts.server;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.wildkarts.net.packets.GridAssignmentPacket;
import com.wildkarts.track.TrackGenerator;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Oblicza pozycje startowe na siatce wyścigowej na podstawie czasów treningowych.
 */
public class RaceGridAssigner {

    private static final float GRID_SLOT_SPACING = 3.0f;
    private static final float GRID_START_OFFSET = 2.0f;

    private final Comparator<ServerPlayerState> gridOrder = (a, b) -> {
        if (a.bestPracticeLapTime <= 0f && b.bestPracticeLapTime <= 0f) return 0;
        if (a.bestPracticeLapTime <= 0f) return 1;
        if (b.bestPracticeLapTime <= 0f) return -1;
        return Float.compare(a.bestPracticeLapTime, b.bestPracticeLapTime);
    };

    /**
     * Sortuje graczy według najlepszego czasu treningowego i buduje pakiet siatki startowej.
     *
     * @param players        posortowana lista buforowa (zostanie wyczyszczona i wypełniona)
     * @param playersById    mapa wszystkich graczy
     * @param trackGenerator generator toru z pozycją startową
     * @return pakiet przypisania siatki gotowy do wysłania klientom
     */
    public GridAssignmentPacket computeGrid(List<ServerPlayerState> players,
                                            Collection<ServerPlayerState> playersById,
                                            TrackGenerator trackGenerator) {
        players.clear();
        players.addAll(playersById);
        players.sort(gridOrder);

        Vector2 startPos = trackGenerator.getStartPosition();
        float startAngle = trackGenerator.getStartAngle();

        float roadDirAngle = startAngle + MathUtils.HALF_PI;
        float rdx = MathUtils.cos(roadDirAngle);
        float rdy = MathUtils.sin(roadDirAngle);

        int n = players.size();
        GridAssignmentPacket packet = new GridAssignmentPacket();
        packet.playerIds = new int[n];
        packet.gridPositions = new int[n];
        packet.xs = new float[n];
        packet.ys = new float[n];
        packet.angles = new float[n];
        packet.bestLapTimes = new float[n];

        for (int i = 0; i < n; i++) {
            ServerPlayerState state = players.get(i);
            float offset = GRID_START_OFFSET + i * GRID_SLOT_SPACING;
            float gx = startPos.x - rdx * offset;
            float gy = startPos.y - rdy * offset;

            packet.playerIds[i] = state.playerId;
            packet.gridPositions[i] = i + 1;
            packet.xs[i] = gx;
            packet.ys[i] = gy;
            packet.angles[i] = startAngle;
            packet.bestLapTimes[i] = state.bestPracticeLapTime;

            state.resetForRace();

            Gdx.app.log("ServerRaceManager", String.format(
                    "Grid P%d: player %d (%s) — best: %.2fs — pos: (%.1f, %.1f)",
                    i + 1, state.playerId, state.name,
                    state.bestPracticeLapTime, gx, gy));
        }

        return packet;
    }
}
