package com.wildkarts;

import com.badlogic.gdx.Game;

/**
 * Main game entry point.
 * Extends Game (not ApplicationAdapter) to support multiple screens.
 * 
 * Future screens: MainMenuScreen, RaceScreen, LobbyScreen, etc.
 */
public class WildKartsGame extends Game {

    private com.wildkarts.net.GameClient gameClient;

    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }

    public com.wildkarts.net.GameClient getGameClient() {
        return gameClient;
    }

    public void setGameClient(com.wildkarts.net.GameClient gameClient) {
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
        if (gameClient != null) {
            gameClient.dispose();
        }
        super.dispose();
    }
}
