package com.wildkarts.server;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryonet.Server;
import com.wildkarts.net.packets.RacePositionsUpdatePacket;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Oblicza pozycje wyścigowe graczy i wysyła aktualizacje tablicy wyników.
 */
public class RacePositionBroadcaster {

    private final Server server;

    private final Comparator<ServerPlayerState> raceOrder = (a, b) -> {
        if (a.currentLap != b.currentLap)
            return Integer.compare(b.currentLap, a.currentLap);
        if (a.nextTrackPointIndex != b.nextTrackPointIndex)
            return Integer.compare(b.nextTrackPointIndex, a.nextTrackPointIndex);
        return Float.compare(a.distanceToNextPointSq, b.distanceToNextPointSq);
    };

    /**
     * Tworzy broadcaster pozycji powiązany z serwerem KryoNet.
     *
     * @param server instancja serwera gry
     */
    public RacePositionBroadcaster(Server server) {
        this.server = server;
    }

    /**
     * Zwraca komparator używany do sortowania graczy według pozycji wyścigowej.
     *
     * @return komparator kolejności wyścigu
     */
    public Comparator<ServerPlayerState> getRaceOrder() {
        return raceOrder;
    }

    /**
     * Aktualizuje kwadrat odległości gracza do następnego punktu kontrolnego.
     *
     * @param state       stan gracza
     * @param trackPoints lista punktów toru
     */
    public void updateDistanceToNextPoint(ServerPlayerState state, Array<Vector2> trackPoints) {
        if (!state.hasPosition) return;
        if (state.nextTrackPointIndex < 0 || state.nextTrackPointIndex >= trackPoints.size) return;
        Vector2 next = trackPoints.get(state.nextTrackPointIndex);
        float dx = next.x - state.lastX;
        float dy = next.y - state.lastY;
        state.distanceToNextPointSq = dx * dx + dy * dy;
    }

    /**
     * Przypisuje pozycje wyścigowe (1, 2, 3…) wszystkim graczom na podstawie postępu.
     *
     * @param sortBuffer bufor sortowania (zostanie wyczyszczony i wypełniony)
     * @param players    kolekcja wszystkich graczy
     */
    public void assignPositions(List<ServerPlayerState> sortBuffer,
                                Collection<ServerPlayerState> players) {
        sortBuffer.clear();
        sortBuffer.addAll(players);
        sortBuffer.sort(raceOrder);
        for (int i = 0; i < sortBuffer.size(); i++)
            sortBuffer.get(i).racePosition = i + 1;
    }

    /**
     * Wysyła pakiet UDP z aktualnymi pozycjami wszystkich graczy.
     *
     * @param players kolekcja graczy z przypisanymi pozycjami
     */
    public void broadcastPositions(Collection<ServerPlayerState> players) {
        int n = players.size();
        if (n == 0) return;

        RacePositionsUpdatePacket packet = new RacePositionsUpdatePacket();
        packet.playerIds = new int[n];
        packet.positions = new int[n];
        packet.currentLaps = new int[n];
        packet.nextTrackPointIndices = new int[n];

        int i = 0;
        for (ServerPlayerState state : players) {
            packet.playerIds[i] = state.playerId;
            packet.positions[i] = state.racePosition;
            packet.currentLaps[i] = state.currentLap;
            packet.nextTrackPointIndices[i] = state.nextTrackPointIndex;
            i++;
        }

        server.sendToAllUDP(packet);
    }
}
