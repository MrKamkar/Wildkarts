package com.wildkarts.components;

import com.badlogic.ashley.core.Component;
import com.wildkarts.track.TrackGenerator;

/**
 * Oznacza encję jako „świadomą terenu”.
 * {@link com.wildkarts.systems.TerrainSystem} odczytuje pozycję Box2D, sprawdza typ kafelka
 * w siatce {@link TrackGenerator} i modyfikuje limity prędkości w {@link CarComponent}
 * (droga = pełna prędkość, trawa = mocno ograniczona).
 *
 * <p>Domyślne wartości prędkości zapisywane są przy inicjalizacji, aby różne typy aut
 * mogły mieć różne prędkości bazowe.</p>
 */
public class TerrainComponent implements Component {

    /** Referencja do generatora toru do wyszukiwania kafelków. */
    public TrackGenerator trackGenerator;

    /** Aktualny typ kafelka pod encją (aktualizowany przez {@link com.wildkarts.systems.TerrainSystem}). */
    public int currentTile = TrackGenerator.TILE_ROAD;

    /** Oryginalna maksymalna prędkość do przodu (zapisana z {@link CarComponent} przy starcie). */
    public float defaultMaxForwardSpeed = 80f;

    /** Oryginalna siła silnika (zapisana z {@link CarComponent} przy starcie). */
    public float defaultEngineForce = 60f;

    /** Oryginalny opór toczenia (zapisany z {@link CarComponent} przy starcie). */
    public float defaultRollingResistance = 0.25f;

    /** Oryginalny współczynnik oporu aerodynamicznego (zapisany z {@link CarComponent} przy starcie). */
    public float defaultAeroDragCoeff = 0.005f;

    /** Oryginalne tłumienie liniowe Box2D (zapisane z {@link CarComponent} przy starcie). */
    public float defaultLinearDamping = 0.1f;

    /** Oryginalne tłumienie kątowe Box2D (zapisane z {@link CarComponent} przy starcie). */
    public float defaultAngularDamping = 3.0f;
}
