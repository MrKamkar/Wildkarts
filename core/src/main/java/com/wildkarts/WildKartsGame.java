package com.wildkarts;

import com.badlogic.gdx.Game;
import com.wildkarts.net.GameClient;

/**
 * Główny punkt wejścia gry.
 * Rozszerza {@link Game} (nie {@code ApplicationAdapter}), aby obsługiwać wiele ekranów.
 *
 * <p>Przyszłe ekrany: {@link MainMenuScreen}, ekran wyścigu, lobby itd.</p>
 */
public class WildKartsGame extends Game {

    private GameClient gameClient;

    /**
     * Inicjalizuje grę i ustawia ekran menu głównego.
     */
    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }

    /**
     * Zwraca aktywnego klienta sieciowego (lub {@code null} w trybie offline).
     *
     * @return instancja {@link GameClient} albo {@code null}
     */
    public GameClient getGameClient() {
        return gameClient;
    }

    /**
     * Ustawia klienta sieciowego używanego podczas gry wieloosobowej.
     *
     * @param gameClient klient do przypisania
     */
    public void setGameClient(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    /**
     * Aktualizuje klienta sieciowego przed renderowaniem bieżącego ekranu.
     */
    @Override
    public void render() {
        if (gameClient != null)
            gameClient.update();
        super.render();
    }

    /**
     * Zatrzymuje muzykę i zwalnia zasoby klienta sieciowego.
     */
    @Override
    public void dispose() {
        ScreenMusic.stop();
        if (gameClient != null)
            gameClient.dispose();
        super.dispose();
    }
}
