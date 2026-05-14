package com.wildkarts.net.packets;

import com.wildkarts.net.UnreliablePacket;

/**
 * High-frequency position update — sent every tick, no ACK needed.
 * If a packet is lost, the next one will contain fresher data anyway.
 */
public class PlayerPositionPacket extends UnreliablePacket {

    public int playerId;
    public float x;
    public float y;
    public float angle;
    public float velocityX;
    public float velocityY;

    public PlayerPositionPacket() {
    }

    public PlayerPositionPacket(int playerId, float x, float y, float angle,
                                float velocityX, float velocityY) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }
}
