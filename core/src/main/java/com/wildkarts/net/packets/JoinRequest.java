package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Sent by the client when requesting to join the server.
 * The server must ACK this and respond with a JoinAccepted.
 */
public class JoinRequest extends ReliablePacket {

    /** Display name chosen by the player. */
    public String playerName;

    public JoinRequest() {
    }

    public JoinRequest(String playerName) {
        this.playerName = playerName;
    }
}
