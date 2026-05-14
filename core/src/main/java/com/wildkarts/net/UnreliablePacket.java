package com.wildkarts.net;

/**
 * Base class for high-frequency, fire-and-forget packets.
 * No ACK is expected — if the packet is lost, a newer one will replace it.
 *
 * Examples: PlayerPositionPacket (sent every frame).
 */
public abstract class UnreliablePacket extends NetPacket {
}
