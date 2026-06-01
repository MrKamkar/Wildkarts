package com.wildkarts.net;

/**
 * Klasa bazowa wszystkich pakietów sieciowych.
 * Każdy pakiet niesie identyfikator sekwencji do śledzenia i porządkowania.
 *
 * <p>Podklasy: {@link ReliablePacket} (wymaga ACK), {@link UnreliablePacket} (bez potwierdzenia).</p>
 */
public abstract class NetPacket {

    /** Monotonicznie rosnący identyfikator przypisywany przez nadawcę. */
    public long sequenceId;
}
