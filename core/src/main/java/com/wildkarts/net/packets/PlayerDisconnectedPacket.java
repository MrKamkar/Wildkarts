package com.wildkarts.net.packets;

/**
 * Sent by the server when a player disconnects.
 * Clients use this to remove the corresponding car entity.
 */
public class PlayerDisconnectedPacket {
    public int playerId;

    public PlayerDisconnectedPacket() {}

    public PlayerDisconnectedPacket(int playerId) {
        this.playerId = playerId;
    }
}
