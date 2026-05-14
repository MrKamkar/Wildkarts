package com.wildkarts.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

/**
 * Entry point for the headless authoritative server.
 */
public class ServerLauncher {
    public static void main(String[] args) {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        // The server needs an update loop, 30 ticks/second is highly efficient for Raspberry Pi 5
        config.updatesPerSecond = 30;

        new HeadlessApplication(new GameServer(), config);
    }
}
