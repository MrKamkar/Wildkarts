package com.wildkarts.screen;

/**
 * Kontrakt akcji nawigacyjnych ekranu gry, używany przez kontrolery UI
 * bez tworzenia bezpośredniej zależności od całej klasy {@code GameScreen}.
 */
public interface GameScreenActions {

    /**
     * Przechodzi w tryb jazdy — spawnuje samochód i włącza systemy fizyki.
     */
    void transitionToPlaying();

    /**
     * Wraca do trybu edytora toru.
     */
    void transitionToEditing();

    /**
     * Kończy grę i wraca do menu głównego.
     */
    void exitToMainMenu();

    /**
     * Obsługuje kliknięcie przycisku READY w lobby wyścigu.
     */
    void onReadyButtonClicked();
}
