package com.wildkarts;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;

/**
 * Pojedynczy zapętlony utwór tła (menu, wyścig itd.).
 * W całej grze gra jednocześnie tylko jeden motyw muzyczny.
 */
public final class ScreenMusic {

    /** Ścieżka do motywu menu głównego. */
    public static final String MENU_THEME_PATH = "music/main_menu_theme.mp3";

    /** Ścieżka do motywu wyścigu. */
    public static final String RACE_THEME_PATH = "music/race_theme.mp3";

    private static final float DEFAULT_VOLUME = 0.45f;

    private static Music music;

    private ScreenMusic() {
    }

    /**
     * Odtwarza motyw z domyślną głośnością.
     *
     * @param path ścieżka do pliku w zasobach wewnętrznych
     */
    public static void playTheme(String path) {
        playTheme(path, DEFAULT_VOLUME);
    }

    /**
     * Zatrzymuje bieżący utwór i odtwarza nowy motyw w pętli.
     *
     * @param path   ścieżka do pliku w zasobach wewnętrznych
     * @param volume głośność od 0.0 do 1.0
     */
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

    /** Zatrzymuje i zwalnia aktualnie odtwarzaną muzykę. */
    public static void stop() {
        if (music != null) {
            music.stop();
            music.dispose();
            music = null;
        }
    }
}
