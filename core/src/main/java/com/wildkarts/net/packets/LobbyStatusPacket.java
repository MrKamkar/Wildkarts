package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Server -> All clients: lobby roster update. Used by the Ready UI to
 * display "X / Y players ready" while in WAITING_FOR_PLAYERS state.
 */
public class LobbyStatusPacket extends ReliablePacket {

    public int readyPlayers;
    public int totalPlayers;

    public LobbyStatusPacket() {
    }

    public LobbyStatusPacket(int readyPlayers, int totalPlayers) {
        this.readyPlayers = readyPlayers;
        this.totalPlayers = totalPlayers;
    }
}
