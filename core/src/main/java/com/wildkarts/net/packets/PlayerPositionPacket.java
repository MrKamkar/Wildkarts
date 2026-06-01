package com.wildkarts.net.packets;

import com.wildkarts.net.UnreliablePacket;

/**
 * Częsta aktualizacja pozycji — wysyłana co tick, bez ACK.
 * Utrata pakietu nie szkodzi — kolejny zawiera świeższe dane.
 */
public class PlayerPositionPacket extends UnreliablePacket {

    /** Identyfikator gracza. */
    public int playerId;

    /** Pozycja X w metrach. */
    public float x;

    /** Pozycja Y w metrach. */
    public float y;

    /** Kąt obrotu w radianach. */
    public float angle;

    /** Składowa X prędkości liniowej. */
    public float velocityX;

    /** Składowa Y prędkości liniowej. */
    public float velocityY;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public PlayerPositionPacket() {
    }

    /**
     * Tworzy pakiet pozycji gracza.
     *
     * @param playerId  identyfikator gracza
     * @param x         pozycja X
     * @param y         pozycja Y
     * @param angle     kąt w radianach
     * @param velocityX prędkość X
     * @param velocityY prędkość Y
     */
    public PlayerPositionPacket(int playerId, float x, float y, float angle,
                                float velocityX, float velocityY) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }
}
