package com.wildkarts.components;

/**
 * Fazy wyścigu obsługiwane przez {@link com.wildkarts.systems.RaceStateSystem}.
 */
public enum RaceState {
    /** Oczekiwanie na gotowość graczy w lobby. */
    WAITING_FOR_PLAYERS,
    /** Trening — okrążenia bez oficjalnego wyścigu. */
    PRACTICE,
    /** Odliczanie przed startem (3…2…1…). */
    COUNTDOWN,
    /** Aktywny wyścig. */
    RACING,
    /** Wyścig zakończony. */
    FINISHED
}
