package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Wysyłany przez serwer do klienta — sygnał rozpoczęcia gry (spawn auta, włączenie sterowania).
 */
public class StartGamePacket extends ReliablePacket {

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public StartGamePacket() {
    }
}
