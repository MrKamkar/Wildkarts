package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Contains a chunk of the track JSON data.
 */
public class MapData extends ReliablePacket {

    /** Total number of chunks for this map. */
    public int totalChunks;

    /** Current chunk index (0 to totalChunks-1). */
    public int chunkIndex;

    /** The actual JSON string fragment. */
    public String data;

    public MapData() {
    }

    public MapData(int totalChunks, int chunkIndex, String data) {
        this.totalChunks = totalChunks;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }
}
