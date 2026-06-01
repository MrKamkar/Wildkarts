package com.wildkarts.components;

import com.badlogic.ashley.core.Component;

/**
 * Pojedynczy komponent na encji „menedżera wyścigu”.
 * Śledzi bieżącą fazę wyścigu, konfigurację i globalne timery.
 */
public class RaceComponent implements Component {

    /** Aktualna faza wyścigu. */
    public RaceState currentState = RaceState.WAITING_FOR_PLAYERS;

    /** Pozostałe sekundy odliczania przed startem. */
    public float countdownTimer = 3.0f;

    /** Całkowity czas od rozpoczęcia fazy {@link RaceState#RACING} (sekundy). */
    public float raceTimer = 0.0f;

    /** Wymagana liczba okrążeń do ukończenia wyścigu. */
    public int maxLaps = 3;

    /** Liczba sektorów, na które podzielony jest tor. */
    public int totalSectors = 3;

    /** Łączna liczba punktów kontrolnych toru (z {@link com.wildkarts.track.TrackGenerator}). */
    public int totalTrackPoints = 0;

    /** Liczba graczy, którzy zgłosili gotowość. */
    public int readyPlayers = 0;

    /** Liczba graczy wymagana do rozpoczęcia {@link RaceState#COUNTDOWN}. */
    public int requiredPlayers = 1;

    /**
     * Gdy {@code true}, klienckie {@link com.wildkarts.systems.RaceStateSystem}
     * i {@link com.wildkarts.systems.LapSectorSystem} nie zmieniają stanu samodzielnie —
     * tylko odzwierciedlają wartości wysyłane przez serwer.
     */
    public boolean serverAuthoritative = false;
}
