package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Zawiera fragment danych JSON toru.
 */
public class MapData extends ReliablePacket {

    /** Łączna liczba fragmentów tej mapy. */
    public int totalChunks;

    /** Indeks bieżącego fragmentu (0 … totalChunks-1). */
    public int chunkIndex;

    /** Fragment ciągu JSON. */
    public String data;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public MapData() {
    }

    /**
     * Tworzy jeden fragment danych mapy.
     *
     * @param totalChunks łączna liczba fragmentów
     * @param chunkIndex  indeks tego fragmentu
     * @param data        zawartość fragmentu JSON
     */
    public MapData(int totalChunks, int chunkIndex, String data) {
        this.totalChunks = totalChunks;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }
}
