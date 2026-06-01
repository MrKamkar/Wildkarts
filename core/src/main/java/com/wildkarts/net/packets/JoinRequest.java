package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Wysyłany przez klienta przy próbie dołączenia do serwera.
 * Serwer musi potwierdzić ACK i odpowiedzieć {@link JoinAccepted}.
 */
public class JoinRequest extends ReliablePacket {

    /** Wyświetlana nazwa gracza. */
    public String playerName;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public JoinRequest() {
    }

    /**
     * Tworzy żądanie dołączenia z podaną nazwą gracza.
     *
     * @param playerName nazwa wyświetlana
     */
    public JoinRequest(String playerName) {
        this.playerName = playerName;
    }
}
