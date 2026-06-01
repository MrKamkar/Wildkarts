package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Serwer → wszyscy klienci: nadaje zmianę fazy wyścigu.
 * Wysyłany przy przejściach WAITING_FOR_PLAYERS → COUNTDOWN, COUNTDOWN → RACING
 * oraz RACING → FINISHED. Stan przesyłany jest jako {@code ordinal()} enumu,
 * aby uniknąć rejestracji typu enum w Kryo.
 */
public class RaceStateChangedPacket extends ReliablePacket {

    /** Ordinal nowego {@link com.wildkarts.components.RaceState}. */
    public int newStateOrdinal;

    /** Autorytatywny timer odliczania serwera w momencie przejścia. */
    public float countdownTimer;

    /** Autorytatywny timer wyścigu serwera w momencie przejścia. */
    public float raceTimer;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public RaceStateChangedPacket() {
    }

    /**
     * Tworzy pakiet zmiany fazy wyścigu.
     *
     * @param newStateOrdinal ordinal nowego stanu
     * @param countdownTimer  timer odliczania
     * @param raceTimer       timer wyścigu
     */
    public RaceStateChangedPacket(int newStateOrdinal, float countdownTimer, float raceTimer) {
        this.newStateOrdinal = newStateOrdinal;
        this.countdownTimer = countdownTimer;
        this.raceTimer = raceTimer;
    }
}
