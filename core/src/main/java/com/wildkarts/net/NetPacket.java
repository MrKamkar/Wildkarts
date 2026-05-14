package com.wildkarts.net;

/**
 * Base class for all network packets.
 * Every packet carries a sequence ID for tracking and ordering.
 *
 * Subclasses: ReliablePacket (ACK required), UnreliablePacket (fire-and-forget).
 */
public abstract class NetPacket {

    /** Monotonically increasing ID assigned by the sender. */
    public long sequenceId;
}
