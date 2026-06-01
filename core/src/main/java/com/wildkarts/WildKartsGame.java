package com.wildkarts;

import com.badlogic.gdx.Game;
import com.wildkarts.net.GameClient;

/**
 * Main game entry point.
 * Extends Game (not ApplicationAdapter) to support multiple screens.
 * 
 * Future screens: MainMenuScreen, RaceScreen, LobbyScreen, etc.
 */
public class WildKartsGame extends Game {

    private GameClient gameClient;

    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    public void setGameClient(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    @Override
    public void render() {
        if (gameClient != null) {
            gameClient.update();
        }
        super.render();
    }

    @Override
    public void dispose() {
        ScreenMusic.stop();
        if (gameClient != null) {
            gameClient.dispose();
        }
        super.dispose();
    }
}
