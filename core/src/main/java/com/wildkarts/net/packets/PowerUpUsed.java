package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Wysyłany, gdy gracz używa power-upu. Musi dotrzeć niezawodnie,
 * aby wszyscy klienci mogli wyświetlić efekt i zastosować logikę gry.
 */
public class PowerUpUsed extends ReliablePacket {

    /** Identyfikator gracza używającego power-upu. */
    public int playerId;

    /** Typ power-upu (np. 0 = boost, 1 = shell, 2 = banana). */
    public int powerUpType;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public PowerUpUsed() {
    }

    /**
     * Tworzy pakiet użycia power-upu.
     *
     * @param playerId    identyfikator gracza
     * @param powerUpType typ power-upu
     */
    public PowerUpUsed(int playerId, int powerUpType) {
        this.playerId = playerId;
        this.powerUpType = powerUpType;
    }
}
