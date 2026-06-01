package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Wysyłany przez klienta do serwera, gdy mapa została w pełni zrekonstruowana i zbudowana.
 */
public class MapReadyPacket extends ReliablePacket {

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public MapReadyPacket() {
    }
}
