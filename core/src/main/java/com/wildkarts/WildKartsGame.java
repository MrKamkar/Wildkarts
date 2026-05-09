package com.wildkarts;

import com.badlogic.gdx.Game;

/**
 * Main game entry point.
 * Extends Game (not ApplicationAdapter) to support multiple screens.
 * 
 * Future screens: MainMenuScreen, RaceScreen, LobbyScreen, etc.
 */
public class WildKartsGame extends Game {

    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
