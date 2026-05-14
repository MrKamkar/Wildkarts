package com.wildkarts.net;

/**
 * Marker base class for packets that MUST be acknowledged.
 * The UdpReliabilityManager will retransmit these until an AckPacket
 * with the matching sequenceId is received.
 *
 * Examples: JoinRequest, MapData, PowerUpUsed.
 */
public abstract class ReliablePacket extends NetPacket {
}
