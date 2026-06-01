package com.wildkarts.screen;

/**
 * Stan ekranu gry określający, czy gracz edytuje tor, czeka na mapę, czy już jeździ.
 */
public enum GameState {
    /** Tryb edytora — klikanie na mapie dodaje punkty kontrolne toru. */
    EDITING,
    /** Oczekiwanie na pobranie mapy (tryb wieloosobowy). */
    LOADING,
    /** Aktywna rozgrywka — jazda, wyścig, UI gry. */
    PLAYING
}
