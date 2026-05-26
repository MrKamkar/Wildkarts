package com.wildkarts.net;

import com.esotericsoftware.kryo.Kryo;
import com.wildkarts.net.packets.*;

/**
 * Central network configuration — port constants and Kryo class registration.
 *
 * IMPORTANT: Registration order must be IDENTICAL on client and server.
 * KryoNet uses class IDs internally; mismatched order = deserialization failures.
 */
public final class Network {

    /** TCP port — used by KryoNet for connection management. */
    public static final int TCP_PORT = 54555;

    /** UDP port — all game traffic flows through here. */
    public static final int UDP_PORT = 54556;

    private Network() {
        // Utility class — no instantiation
    }

    /**
     * Registers all packet classes with Kryo.
     * Must be called on both client and server Kryo instances.
     *
     * @param kryo the Kryo instance from KryoNet's Client or Server
     */
    public static void register(Kryo kryo) {
        // Primitive array types used in packets
        kryo.register(float[].class);
        kryo.register(int[].class);

        // Control packets
        kryo.register(AckPacket.class);

        // Reliable game packets
        kryo.register(JoinRequest.class);
        kryo.register(JoinAccepted.class);
        kryo.register(MapData.class);
        kryo.register(MapReadyPacket.class);
        kryo.register(StartGamePacket.class);
        kryo.register(PowerUpUsed.class);

        // Race / lobby packets (reliable)
        kryo.register(PlayerReadyPacket.class);
        kryo.register(LobbyStatusPacket.class);
        kryo.register(RaceStateChangedPacket.class);
        kryo.register(PlayerPassedPointPacket.class);
        kryo.register(SectorTimePacket.class);
        kryo.register(GridAssignmentPacket.class);

        // Unreliable game packets
        kryo.register(PlayerPositionPacket.class);
        kryo.register(PlayerDisconnectedPacket.class);
        kryo.register(RacePositionsUpdatePacket.class);
    }
}
