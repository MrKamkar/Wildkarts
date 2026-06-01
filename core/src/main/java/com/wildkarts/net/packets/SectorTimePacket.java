package com.wildkarts.net.packets;

import com.wildkarts.net.ReliablePacket;

/**
 * Serwer → pojedynczy klient: autorytatywny wynik zatwierdzonego {@link PlayerPassedPointPacket}.
 * Serwer zaakceptował minięcie punktu i zwraca zaktualizowany postęp odbierającemu graczowi.
 * Klient nadpisuje lokalny {@link com.wildkarts.components.LapComponent} tymi wartościami.
 *
 * <p>Gdy {@code sectorIndex < 0}, minięty punkt był pośredni (nie granica sektora) —
 * znaczenie mają tylko pola postępu.</p>
 */
public class SectorTimePacket extends ReliablePacket {

    /** Identyfikator gracza, którego dotyczy pakiet. */
    public int playerId;

    /** Indeks właśnie ukończonego sektora lub -1 gdy to nie granica sektora. */
    public int sectorIndex;

    /** Czas spędzony w sektorze, który właśnie się zakończył (sekundy). */
    public float sectorTime;

    /** Delta względem rekordu osobistego tego sektora (s, ze znakiem). */
    public float delta;

    /** Zaktualizowany rekord osobisty tego sektora. */
    public float bestSectorTime;

    /** Aktualne okrążenie gracza. */
    public int currentLap;

    /** Indeks następnego punktu kontrolnego do minięcia. */
    public int nextTrackPointIndex;

    /** Aktualny sektor (0 … totalSectors-1). */
    public int currentSector;

    /** Czy gracz ukończył wyścig. */
    public boolean finished;

    /** Autorytatywny timer wyścigu w momencie końca sektora. */
    public float raceTimerSnapshot;

    /** Czas właśnie ukończonego okrążenia (suma sektorów). 0 gdy to nie granica okrążenia. */
    public float lastLapTime;

    /** Rekord pełnego okrążenia treningowego. 0 gdy brak ukończonego okrążenia. */
    public float bestPracticeLapTime;

    /** Konstruktor bezargumentowy wymagany przez Kryo. */
    public SectorTimePacket() {
    }
}
