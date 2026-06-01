package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Serwer → wszyscy klienci: aktualizacja stanu lobby.
 * Używane przez UI gotowości do wyświetlania „X / Y graczy gotowych”.
 */
public class LobbyStatusPacket extends ReliablePacket {

    /** Liczba graczy gotowych. */
    public int readyPlayers;

    /** Łączna liczba graczy w lobby. */
    public int totalPlayers;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public LobbyStatusPacket() {
    }

    /**
     * Tworzy pakiet statusu lobby.
     *
     * @param readyPlayers liczba gotowych graczy
     * @param totalPlayers łączna liczba graczy
     */
    public LobbyStatusPacket(int readyPlayers, int totalPlayers) {
        this.readyPlayers = readyPlayers;
        this.totalPlayers = totalPlayers;
    }
}
