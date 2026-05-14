package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Sent when a player uses a power-up. Must arrive reliably
 * so all clients can display the effect and apply game logic.
 */
public class PowerUpUsed extends ReliablePacket {

    public int playerId;

    /** Power-up type identifier (e.g., 0 = boost, 1 = shell, 2 = banana). */
    public int powerUpType;

    public PowerUpUsed() {
    }

    public PowerUpUsed(int playerId, int powerUpType) {
        this.playerId = playerId;
        this.powerUpType = powerUpType;
    }
}
