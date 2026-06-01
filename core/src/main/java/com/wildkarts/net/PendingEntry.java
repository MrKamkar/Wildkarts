package com.wildkarts.net;

import com.esotericsoftware.kryonet.Connection;

/**
 * Wewnętrzny wpis używany przez {@link UdpReliabilityManager} do śledzenia pakietów oczekujących na ACK.
 */
public class PendingEntry {

    /** Połączenie, na które wysłano pakiet. */
    public final Connection connection;

    /** Pakiet oczekujący na potwierdzenie. */
    public final ReliablePacket packet;

    /** Czas ostatniego wysłania (ms). */
    public long lastSentTime;

    /** Liczba ponownych prób wysłania. */
    public int retryCount;

    /**
     * Rejestruje pakiet jako oczekujący na potwierdzenie.
     *
     * @param connection połączenie docelowe
     * @param packet     pakiet niezawodny
     */
    public PendingEntry(Connection connection, ReliablePacket packet) {
        this.connection = connection;
        this.packet = packet;
        this.lastSentTime = System.currentTimeMillis();
        this.retryCount = 0;
    }
}
