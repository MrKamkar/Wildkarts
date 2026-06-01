package com.wildkarts.net;

import com.esotericsoftware.kryo.Kryo;
import com.wildkarts.net.packets.GridAssignmentPacket;
import com.wildkarts.net.packets.JoinAccepted;
import com.wildkarts.net.packets.JoinRequest;
import com.wildkarts.net.packets.LobbyStatusPacket;
import com.wildkarts.net.packets.MapData;
import com.wildkarts.net.packets.MapReadyPacket;
import com.wildkarts.net.packets.PlayerDisconnectedPacket;
import com.wildkarts.net.packets.PlayerPassedPointPacket;
import com.wildkarts.net.packets.PlayerPositionPacket;
import com.wildkarts.net.packets.PlayerReadyPacket;
import com.wildkarts.net.packets.PowerUpUsed;
import com.wildkarts.net.packets.RacePositionsUpdatePacket;
import com.wildkarts.net.packets.RaceResultsPacket;
import com.wildkarts.net.packets.RaceStateChangedPacket;
import com.wildkarts.net.packets.SectorTimePacket;
import com.wildkarts.net.packets.StartGamePacket;

/**
 * Centralna konfiguracja sieci — stałe portów i rejestracja klas Kryo.
 *
 * <p>WAŻNE: kolejność rejestracji musi być IDENTYCZNA po stronie klienta i serwera.
 * KryoNet używa wewnętrznych identyfikatorów klas; rozbieżna kolejność = błędy deserializacji.</p>
 */
public final class Network {

    /** Port TCP — używany przez KryoNet do zarządzania połączeniem. */
    public static final int TCP_PORT = 54555;

    /** Port UDP — cały ruch gry przepływa tą ścieżką. */
    public static final int UDP_PORT = 54556;

    private Network() {
    }

    /**
     * Rejestruje wszystkie klasy pakietów w instancji Kryo.
     * Musi być wywołane na obu instancjach Kryo (klient i serwer).
     *
     * @param kryo instancja Kryo z KryoNet {@code Client} lub {@code Server}
     */
    public static void register(Kryo kryo) {
        kryo.register(float[].class);
        kryo.register(int[].class);
        kryo.register(String[].class);

        kryo.register(AckPacket.class);

        kryo.register(JoinRequest.class);
        kryo.register(JoinAccepted.class);
        kryo.register(MapData.class);
        kryo.register(MapReadyPacket.class);
        kryo.register(StartGamePacket.class);
        kryo.register(PowerUpUsed.class);

        kryo.register(PlayerReadyPacket.class);
        kryo.register(LobbyStatusPacket.class);
        kryo.register(RaceStateChangedPacket.class);
        kryo.register(PlayerPassedPointPacket.class);
        kryo.register(SectorTimePacket.class);
        kryo.register(GridAssignmentPacket.class);
        kryo.register(RaceResultsPacket.class);

        kryo.register(PlayerPositionPacket.class);
        kryo.register(PlayerDisconnectedPacket.class);
        kryo.register(RacePositionsUpdatePacket.class);
    }
}
