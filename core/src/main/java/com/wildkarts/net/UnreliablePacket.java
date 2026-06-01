package com.wildkarts.net;

/**
 * Klasa bazowa pakietów wysyłanych często, bez oczekiwania na ACK.
 * Utrata pakietu nie jest krytyczna — kolejny zawiera świeższe dane.
 *
 * <p>Przykład: {@link com.wildkarts.net.packets.PlayerPositionPacket} (co klatkę).</p>
 */
public abstract class UnreliablePacket extends NetPacket {
}
