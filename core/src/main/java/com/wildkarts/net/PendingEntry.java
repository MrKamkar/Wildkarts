package com.wildkarts.net;

import com.esotericsoftware.kryonet.Connection;

/**
 * Internal class used by UdpReliabilityManager to track packets awaiting ACK.
 */
public class PendingEntry {
    public final Connection connection;
    public final ReliablePacket packet;
    public long lastSentTime;
    public int retryCount;

    public PendingEntry(Connection connection, ReliablePacket packet) {
        this.connection = connection;
        this.packet = packet;
        this.lastSentTime = System.currentTimeMillis();
        this.retryCount = 0;
    }
}
