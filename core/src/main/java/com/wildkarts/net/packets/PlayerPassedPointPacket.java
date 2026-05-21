package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Client -> Server: the client believes it has reached the track point
 * {@code pointIndex} (which equals its current {@code nextTrackPointIndex}).
 * The server validates against its last known position for this player
 * and, on success, applies lap / sector / finish logic authoritatively.
 *
 * Sent as a reliable packet so a momentary UDP loss does not cause a
 * missed checkpoint.
 */
public class PlayerPassedPointPacket extends ReliablePacket {

    public int playerId;
    public int pointIndex;

    /** Player position at the moment of detection (used for validation). */
    public float x;
    public float y;

    public PlayerPassedPointPacket() {
    }

    public PlayerPassedPointPacket(int playerId, int pointIndex, float x, float y) {
        this.playerId = playerId;
        this.pointIndex = pointIndex;
        this.x = x;
        this.y = y;
    }
}
