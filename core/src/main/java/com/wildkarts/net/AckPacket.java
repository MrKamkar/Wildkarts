package com.wildkarts.net;

/**
 * Acknowledgement packet — sent in response to any ReliablePacket.
 * Contains the sequenceId of the packet being acknowledged.
 *
 * This is NOT a NetPacket subclass — it's a lightweight control message
 * that doesn't need its own sequence tracking.
 */
public class AckPacket {

    /** The sequenceId of the ReliablePacket being acknowledged. */
    public long acknowledgedId;

    /** No-arg constructor required by Kryo. */
    public AckPacket() {
    }

    public AckPacket(long acknowledgedId) {
        this.acknowledgedId = acknowledgedId;
    }
}
