package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Sent by client to server when the map has been fully reconstructed and built.
 */
public class MapReadyPacket extends ReliablePacket {
    public MapReadyPacket() {
    }
}
