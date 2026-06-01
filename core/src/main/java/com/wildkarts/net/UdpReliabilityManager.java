package com.wildkarts.net;

import com.esotericsoftware.kryonet.Connection;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Zarządza niezawodną dostawą UDP przez KryoNet.
 * Automatycznie przypisuje identyfikatory sekwencji i retransmituje pakiety bez ACK.
 */
public class UdpReliabilityManager {

    private static final long RETRY_INTERVAL_MS = 500;
    private static final int MAX_RETRIES = 10;

    private final AtomicLong sequenceCounter = new AtomicLong(1);
    private final Map<Long, PendingEntry> pendingPackets = new ConcurrentHashMap<>();

    private Runnable onMaxRetriesExceeded;

    /**
     * Wysyła pakiet niezawodny przez UDP.
     * Menedżer przypisuje {@code sequenceId} i obsługuje retransmisję.
     *
     * @param connection połączenie KryoNet
     * @param packet     pakiet niezawodny do wysłania
     */
    public void send(Connection connection, ReliablePacket packet) {
        packet.sequenceId = sequenceCounter.getAndIncrement();
        pendingPackets.put(packet.sequenceId, new PendingEntry(connection, packet));
        connection.sendUDP(packet);
    }

    /**
     * Wywoływane po odebraniu {@link AckPacket}.
     *
     * @param acknowledgedId potwierdzony identyfikator sekwencji
     */
    public void onAckReceived(long acknowledgedId) {
        pendingPackets.remove(acknowledgedId);
    }

    /**
     * Wywoływane regularnie (np. w pętli render/update) w celu obsługi retransmisji.
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
                    if (onMaxRetriesExceeded != null)
                        onMaxRetriesExceeded.run();
                } else {
                    pending.lastSentTime = now;
                    pending.connection.sendUDP(pending.packet);
                }
            }
        }
    }

    /** Czyści kolejkę oczekujących pakietów i resetuje licznik sekwencji. */
    public void reset() {
        pendingPackets.clear();
        sequenceCounter.set(1);
    }

    /**
     * Sprawdza, czy są jeszcze pakiety oczekujące na potwierdzenie.
     *
     * @return {@code true} gdy kolejka nie jest pusta
     */
    public boolean hasPending() {
        return !pendingPackets.isEmpty();
    }

    /**
     * Ustawia callback wywoływany po przekroczeniu maksymalnej liczby ponownych prób.
     *
     * @param callback akcja do wykonania (może być {@code null})
     */
    public void setOnMaxRetriesExceeded(Runnable callback) {
        this.onMaxRetriesExceeded = callback;
    }
}
