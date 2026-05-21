package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Server -> All clients: broadcasts a race state transition.
 * Sent on WAITING_FOR_PLAYERS -> COUNTDOWN, COUNTDOWN -> RACING, and
 * RACING -> FINISHED. The state is sent as its enum {@code ordinal()} to
 * avoid registering the enum type with Kryo.
 */
public class RaceStateChangedPacket extends ReliablePacket {

    /** Ordinal of the new {@code com.wildkarts.components.RaceState}. */
    public int newStateOrdinal;

    /** Server's authoritative countdown timer at the moment of transition. */
    public float countdownTimer;

    /** Server's authoritative race timer at the moment of transition. */
    public float raceTimer;

    public RaceStateChangedPacket() {
    }

    public RaceStateChangedPacket(int newStateOrdinal, float countdownTimer, float raceTimer) {
        this.newStateOrdinal = newStateOrdinal;
        this.countdownTimer = countdownTimer;
        this.raceTimer = raceTimer;
    }
}
