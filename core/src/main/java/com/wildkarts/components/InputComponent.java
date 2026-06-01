package com.wildkarts.components;

import com.badlogic.ashley.core.Component;

/**
 * Przechowuje znormalizowane wartości wejścia gracza.
 * Oddzielone od fizyki — kluczowy projekt pod tryb wieloosobowy.
 *
 * <p>Tryb solo: wypełniane przez {@link com.wildkarts.systems.InputSystem} z klawiatury.
 * Multiplayer: wypełniane z pakietów sieciowych.</p>
 */
public class InputComponent implements Component {

    /** Gaz / hamulec. Zakres: -1 (pełny tył) do 1 (pełny przód). */
    public float throttle = 0f;

    /** Kierownica. Zakres: -1 (lewo) do 1 (prawo). */
    public float steering = 0f;

    /** Ręczny / drift — zmniejsza boczną przyczepność gdy aktywny. */
    public boolean braking = false;
}
