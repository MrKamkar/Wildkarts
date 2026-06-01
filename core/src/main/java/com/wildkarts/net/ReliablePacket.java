package com.wildkarts.net;

/**
 * Klasa bazowa pakietów wymagających potwierdzenia (ACK).
 * {@link UdpReliabilityManager} retransmituje je, dopóki nie otrzyma {@link AckPacket}
 * z pasującym {@code sequenceId}.
 *
 * <p>Przykłady: {@link com.wildkarts.net.packets.JoinRequest}, {@link com.wildkarts.net.packets.MapData}.</p>
 */
public abstract class ReliablePacket extends NetPacket {
}
