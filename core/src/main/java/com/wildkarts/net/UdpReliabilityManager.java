package com.wildkarts.net;

import com.esotericsoftware.kryonet.Connection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages reliable UDP delivery over KryoNet.
 * Automatically assigns sequence IDs and retransmits packets if an ACK is not received.
 */
public class UdpReliabilityManager {

    private static final long RETRY_INTERVAL_MS = 500;
    private static final int MAX_RETRIES = 10;

    private final AtomicLong sequenceCounter = new AtomicLong(1);
    private final Map<Long, PendingEntry> pendingPackets = new ConcurrentHashMap<>();

    private Runnable onMaxRetriesExceeded;

    /**
     * Sends a reliable packet over UDP.
     * The manager assigns the sequenceId and handles retransmission.
     *
     * @param connection the KryoNet connection to send on
     * @param packet     the reliable packet to send
     */
    public void send(Connection connection, ReliablePacket packet) {
        packet.sequenceId = sequenceCounter.getAndIncrement();
        pendingPackets.put(packet.sequenceId, new PendingEntry(connection, packet));
        connection.sendUDP(packet);
    }

    /**
     * Call this when an AckPacket is received.
     *
     * @param acknowledgedId the sequenceId that was acknowledged
     */
    public void onAckReceived(long acknowledgedId) {
        pendingPackets.remove(acknowledgedId);
    }

    /**
     * Must be called regularly (e.g., in the render/update loop) to process retransmissions.
     */
    public void update() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, PendingEntry>> iterator = pendingPackets.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, PendingEntry> entry = iterator.next();
            PendingEntry pending = entry.getValue();

            if (now - pending.lastSentTime > RETRY_INTERVAL_MS) {
                pending.retryCount++;
                if (pending.retryCount > MAX_RETRIES) {
                    iterator.remove();
                    if (onMaxRetriesExceeded != null) {
                        onMaxRetriesExceeded.run();
                    }
                } else {
                    pending.lastSentTime = now;
                    pending.connection.sendUDP(pending.packet);
                }
            }
        }
    }

    public void reset() {
        pendingPackets.clear();
        sequenceCounter.set(1);
    }

    public boolean hasPending() {
        return !pendingPackets.isEmpty();
    }

    public void setOnMaxRetriesExceeded(Runnable callback) {
        this.onMaxRetriesExceeded = callback;
    }

    private static class PendingEntry {
        final Connection connection;
        final ReliablePacket packet;
        long lastSentTime;
        int retryCount;

        PendingEntry(Connection connection, ReliablePacket packet) {
            this.connection = connection;
            this.packet = packet;
            this.lastSentTime = System.currentTimeMillis();
            this.retryCount = 0;
        }
    }
}
