package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Sent by the server to confirm a player has joined.
 * Contains the player's assigned ID for the session.
 */
public class JoinAccepted extends ReliablePacket {

    /** Unique player ID assigned by the server. */
    public int playerId;

    public JoinAccepted() {
    }

    public JoinAccepted(int playerId) {
        this.playerId = playerId;
    }
}
