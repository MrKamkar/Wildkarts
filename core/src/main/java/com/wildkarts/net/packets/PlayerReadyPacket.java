package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Client -> Server: signals that the local player has toggled their lobby
 * "Ready" state. Server collects these and starts the countdown once every
 * connected player has signaled ready.
 */
public class PlayerReadyPacket extends ReliablePacket {

    public boolean ready;

    public PlayerReadyPacket() {
    }

    public PlayerReadyPacket(boolean ready) {
        this.ready = ready;
    }
}
