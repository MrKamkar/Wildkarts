package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Serwer → wszyscy klienci: przypisuje pozycje startowe (siatka) na podstawie czasów treningowych.
 * Wysyłany tuż przed przejściem do {@link com.wildkarts.components.RaceState#COUNTDOWN}.
 * Równoległe tablice opisują każdego połączonego gracza.
 *
 * <p>Klienci muszą teleportować wszystkie auta (lokalne i zdalne) do podanych współrzędnych
 * i wyzerować prędkości.</p>
 */
public class GridAssignmentPacket extends ReliablePacket {

    /** Identyfikatory graczy. */
    public int[] playerIds;

    /** Pozycje na siatce startowej (1 = pole na czele). */
    public int[] gridPositions;

    /** Współrzędne X pozycji startowych. */
    public float[] xs;

    /** Współrzędne Y pozycji startowych. */
    public float[] ys;

    /** Kąty startowe w radianach. */
    public float[] angles;

    /** Najlepsze czasy okrążeń treningowych (s). */
    public float[] bestLapTimes;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public GridAssignmentPacket() {
    }
}
