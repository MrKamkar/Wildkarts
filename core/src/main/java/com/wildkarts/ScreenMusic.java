package com.wildkarts;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;

/**
 * Single looping screen music track (menu, race, etc.).
 * Only one theme plays at a time across the whole game.
 */
public final class ScreenMusic {

    public static final String MENU_THEME_PATH = "music/main_menu_theme.mp3";
    public static final String RACE_THEME_PATH = "music/race_theme.mp3";

    private static final float DEFAULT_VOLUME = 0.45f;

    private static Music music;

    private ScreenMusic() {
    }

    public static void playTheme(String path) {
        playTheme(path, DEFAULT_VOLUME);
    }

    public static void playTheme(String path, float volume) {
        stop();
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            Gdx.app.log("ScreenMusic", "Music not found: " + path);
            return;
        }
        try {
            music = Gdx.audio.newMusic(file);
            music.setLooping(true);
            music.setVolume(volume);
            music.play();
            Gdx.app.log("ScreenMusic", "Playing: " + path + " (volume " + volume + ")");
        } catch (Exception e) {
            Gdx.app.error("ScreenMusic", "Failed to play: " + path, e);
            stop();
        }
    }

    public static void stop() {
        if (music != null) {
            music.stop();
            music.dispose();
            music = null;
        }
    }
}
