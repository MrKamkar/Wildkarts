package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Serwer → wszyscy klienci: wysyłany po osiągnięciu fazy {@link com.wildkarts.components.RaceState#FINISHED}.
 * Zawiera końcową klasyfikację do wyświetlenia tabeli wyników.
 */
public class RaceResultsPacket extends ReliablePacket {

    /** Posortowane identyfikatory graczy (indeks 0 = 1. miejsce). */
    public int[] playerIds;

    /** Nazwy graczy w tej samej kolejności. */
    public String[] playerNames;

    /** Końcowe czasy wyścigu (s) dla każdego gracza. 0 przy DNF. */
    public float[] finishTimes;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public RaceResultsPacket() {
    }
}
