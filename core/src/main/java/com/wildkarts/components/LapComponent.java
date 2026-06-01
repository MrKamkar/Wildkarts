package com.wildkarts.components;

import com.badlogic.ashley.core.Component;

import java.util.Arrays;

/**
 * Stan wyścigu pojedynczego kierowcy: okrążenia, sektory, czasy i pozycja w wyścigu.
 * Dołączany do każdego gokarta biorącego udział w wyścigu.
 *
 * <p>Punkty toru pochodzą z {@code TrackGenerator.getManualPoints()}. Samochód startuje
 * od punktu 0 i musi kolejno minąć punkty 1…N-1, 0 (pełne okrążenie). Tor dzielony
 * jest na {@code RaceComponent.totalSectors} sektorów — minięcie granicy sektora
 * zapisuje czas i oblicza deltę względem rekordu osobistego.</p>
 */
public class LapComponent implements Component {

    /** Aktualny numer okrążenia (liczony od 1). */
    public int currentLap = 1;

    /** Indeks następnego punktu kontrolnego do minięcia. */
    public int nextTrackPointIndex = 1;

    /** True gdy {@code currentLap > RaceComponent.maxLaps}. */
    public boolean finished = false;

    /** Indeks sektora aktualnie przejeżdżanego (0…totalSectors-1). */
    public int currentSector = 0;

    /** Czas w sekundach od początku bieżącego sektora. */
    public float currentSectorElapsed = 0f;

    /** Czasy sektorów bieżącego okrążenia. Indeks = numer sektora. */
    public final float[] currentLapSectorTimes = new float[3];

    /** Osobiste rekordy czasów sektorów (0 = brak rekordu). */
    public final float[] bestSectorTimes = new float[3];

    /**
     * Delta ostatnio ukończonego sektora względem rekordu.
     * Ujemna = szybciej (zielony), dodatnia = wolniej (czerwony), 0 = pierwszy przejazd.
     */
    public float lastSectorDelta = 0f;

    /** Najlepszy czas pełnego okrążenia treningowego (0 = brak). */
    public float bestPracticeLapTime = 0f;

    /** Czas ostatnio ukończonego okrążenia treningowego w sekundach. */
    public float lastPracticeLapTime = 0f;

    /**
     * Resetuje liczniki bieżącego okrążenia, zachowując rekordy osobiste sektorów.
     */
    public void resetCurrentLapTiming() {
        currentSector = 0;
        currentSectorElapsed = 0f;
        Arrays.fill(currentLapSectorTimes, 0f);
        lastSectorDelta = 0f;
    }

    /**
     * Resetuje postęp po ukończeniu okrążenia treningowego — start od linii mety.
     */
    public void resetForNextPracticeLap() {
        currentLap = 1;
        nextTrackPointIndex = 1;
        resetCurrentLapTiming();
    }

    /** Aktualna pozycja wyścigowa przypisana przez {@code LapSectorSystem} (1 = lider). */
    public int racePosition = 1;

    /** Kwadrat odległości do następnego punktu — tie-breaker przy sortowaniu pozycji. */
    public float distanceToNextPointSq = Float.MAX_VALUE;

    /** Czy kierowca zgłosił gotowość w lobby (WAITING_FOR_PLAYERS / PRACTICE). */
    public boolean ready = false;

    /**
     * Indeks ostatnio wysłanego punktu w {@code PlayerPassedPointPacket}.
     * Debounce zapobiegający zalewaniu serwera gdy gracz stoi w promieniu bramki.
     * Reset do -1 gdy serwer potwierdzi minięcie przez {@code SectorTimePacket}.
     */
    public int lastRequestedPointIndex = -1;
}
