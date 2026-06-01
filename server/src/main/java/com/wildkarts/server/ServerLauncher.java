package com.wildkarts.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

/**
 * Punkt wejścia bezgraficznego serwera autorytatywnego.
 */
public class ServerLauncher {

    /**
     * Uruchamia serwer gry w trybie headless (~30 ticków/s).
     *
     * @param args argumenty wiersza poleceń (nieużywane)
     */
    public static void main(String[] args) {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = 30;

        new HeadlessApplication(new GameServer(), config);
    }
}
