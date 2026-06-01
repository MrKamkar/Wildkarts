package com.wildkarts.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.wildkarts.WildKartsGame;

/**
 * Launcher desktopowy WildKarts.
 * Konfiguruje rozmiar okna, tytuł, VSync i uruchamia grę.
 */
public class Lwjgl3Launcher {

    /**
     * Punkt wejścia aplikacji desktopowej LWJGL3.
     *
     * @param args argumenty wiersza poleceń (nieużywane)
     */
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("WildKarts");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);

        config.setWindowSizeLimits(640, 360, -1, -1);

        config.setWindowIcon("textures/icon.png");

        new Lwjgl3Application(new WildKartsGame(), config);
        System.exit(0);
    }
}
