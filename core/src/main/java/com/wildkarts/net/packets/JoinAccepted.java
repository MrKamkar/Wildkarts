package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Wysyłany przez serwer w celu potwierdzenia dołączenia gracza.
 * Zawiera przypisany identyfikator gracza na czas sesji.
 */
public class JoinAccepted extends ReliablePacket {

    /** Unikalny identyfikator gracza nadany przez serwer. */
    public int playerId;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public JoinAccepted() {
    }

    /**
     * Tworzy potwierdzenie dołączenia z przypisanym identyfikatorem.
     *
     * @param playerId identyfikator gracza
     */
    public JoinAccepted(int playerId) {
        this.playerId = playerId;
    }
}
