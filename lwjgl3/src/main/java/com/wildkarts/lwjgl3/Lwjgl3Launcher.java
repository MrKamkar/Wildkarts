package com.wildkarts.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.wildkarts.WildKartsGame;

/**
 * Desktop launcher for WildKarts.
 * Configures window size, title, VSync, and launches the game.
 */
public class Lwjgl3Launcher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("WildKarts");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);

        // Smooth window resize
        config.setWindowSizeLimits(640, 360, -1, -1);

        config.setWindowIcon("textures/icon.png");

        // Blocks until the window is closed; force-exit afterwards so any
        // lingering non-daemon threads (e.g. KryoNet) can't hang the process.
        new Lwjgl3Application(new WildKartsGame(), config);
        System.exit(0);
    }
}
