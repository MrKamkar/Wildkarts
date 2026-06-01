package com.wildkarts.net.packets;

/**
 * Wysyłany przez serwer, gdy gracz rozłącza się.
 * Klienci używają go do usunięcia odpowiadającej encji auta.
 */
public class PlayerDisconnectedPacket {

    /** Identyfikator rozłączonego gracza. */
    public int playerId;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public PlayerDisconnectedPacket() {
    }

    /**
     * Tworzy pakiet rozłączenia gracza.
     *
     * @param playerId identyfikator gracza
     */
    public PlayerDisconnectedPacket(int playerId) {
        this.playerId = playerId;
    }
}
