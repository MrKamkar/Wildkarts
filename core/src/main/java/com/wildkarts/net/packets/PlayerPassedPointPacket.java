package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Klient → serwer: klient uważa, że minął punkt toru {@code pointIndex}
 * (równy jego {@code nextTrackPointIndex}).
 * Serwer weryfikuje względem ostatniej znanej pozycji gracza
 * i po sukcesie autorytatywnie stosuje logikę okrążeń / sektorów / mety.
 *
 * <p>Pakiet niezawodny — chwilowa utrata UDP nie powoduje pominięcia checkpointu.</p>
 */
public class PlayerPassedPointPacket extends ReliablePacket {

    /** Identyfikator gracza zgłaszającego minięcie punktu. */
    public int playerId;

    /** Indeks miniętego punktu kontrolnego. */
    public int pointIndex;

    /** Pozycja gracza w momencie wykrycia (do walidacji). */
    public float x;

    /** Pozycja gracza w momencie wykrycia (do walidacji). */
    public float y;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public PlayerPassedPointPacket() {
    }

    /**
     * Tworzy zgłoszenie minięcia punktu kontrolnego.
     *
     * @param playerId   identyfikator gracza
     * @param pointIndex indeks punktu
     * @param x          pozycja X
     * @param y          pozycja Y
     */
    public PlayerPassedPointPacket(int playerId, int pointIndex, float x, float y) {
        this.playerId = playerId;
        this.pointIndex = pointIndex;
        this.x = x;
        this.y = y;
    }
}
