package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Sent by server to client to signal that the game should start (spawn car, enable controls).
 */
public class StartGamePacket extends ReliablePacket {
    public StartGamePacket() {
    }
}
