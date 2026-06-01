package com.wildkarts.net.packets;

import com.wildkarts.net.UnreliablePacket;

/**
 * Serwer → wszyscy klienci: migawka klasyfikacji wyścigu.
 * Równoległe tablice opisują każdego połączonego gracza.
 * Wysyłane ok. 6 Hz (co N-ty tick serwera) przez UDP.
 *
 * <p>Wszystkie tablice mają tę samą długość. Indeks {@code i} opisuje tego samego
 * gracza we wszystkich czterech tablicach.</p>
 */
public class RacePositionsUpdatePacket extends UnreliablePacket {

    /** Identyfikatory graczy. */
    public int[] playerIds;

    /** Aktualne pozycje wyścigowe (1 = lider). */
    public int[] positions;

    /** Aktualne okrążenia graczy. */
    public int[] currentLaps;

    /** Indeksy następnych punktów kontrolnych. */
    public int[] nextTrackPointIndices;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public RacePositionsUpdatePacket() {
    }
}
