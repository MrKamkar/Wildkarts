package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Klient → serwer: sygnalizuje przełączenie stanu „Gotowy” w lobby.
 * Serwer zbiera te sygnały i rozpoczyna odliczanie, gdy wszyscy połączeni gracze są gotowi.
 */
public class PlayerReadyPacket extends ReliablePacket {

    /** {@code true} gdy gracz jest gotowy, {@code false} gdy cofnął gotowość. */
    public boolean ready;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public PlayerReadyPacket() {
    }

    /**
     * Tworzy pakiet gotowości gracza.
     *
     * @param ready czy gracz jest gotowy
     */
    public PlayerReadyPacket(boolean ready) {
        this.ready = ready;
    }
}
