package com.wildkarts.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.wildkarts.util.SpriteAnchorUtil;

/**
 * Ładuje tekstury samochodów i trawy używane na ekranie gry.
 * W razie braku pliku PNG generuje kolorowy placeholder, aby gra mogła działać.
 */
public class GameAssetLoader {

    private static final String ASSETS_PATH = "sprites/";
    private static final String TEXTURES_PATH = "textures/";
    private static final String CAR_STRAIGHT_FILE = ASSETS_PATH + "car_straight.png";
    private static final String CAR_TURN_LEFT_FILE = ASSETS_PATH + "car_turn_left.png";
    private static final String CAR_TURN_RIGHT_FILE = ASSETS_PATH + "car_turn_right.png";
    private static final String GRASS_TEXTURE_FILE = TEXTURES_PATH + "grass.png";

    /** Zestaw załadowanych tekstur i kotwic sprite'ów samochodu. */
    public static final class LoadedAssets {
        public Texture carStraightTexture;
        public Texture carTurnLeftTexture;
        public Texture carTurnRightTexture;
        public Texture grassTexture;
        public SpriteAnchorUtil.Anchor carStraightAnchor;
        public SpriteAnchorUtil.Anchor carTurnLeftAnchor;
        public SpriteAnchorUtil.Anchor carTurnRightAnchor;
    }

    /**
     * Ładuje wszystkie graficzne zasoby gry i konfiguruje filtry tekstur.
     *
     * @return obiekt z załadowanymi teksturami i kotwicami sprite'ów
     */
    public LoadedAssets loadAll() {
        LoadedAssets assets = new LoadedAssets();

        FileHandle straightFile = Gdx.files.internal(CAR_STRAIGHT_FILE);
        assets.carStraightTexture = loadTextureOrPlaceholder(CAR_STRAIGHT_FILE,
                32, 64, new Color(0.2f, 0.6f, 1.0f, 1f));
        assets.carStraightAnchor = SpriteAnchorUtil.fromFile(straightFile);

        FileHandle turnLeftFile = Gdx.files.internal(CAR_TURN_LEFT_FILE);
        if (!turnLeftFile.exists()) {
            Gdx.app.log("Assets", "Missing " + CAR_TURN_LEFT_FILE + " — using straight sprite until you add the file.");
            turnLeftFile = straightFile;
        }
        assets.carTurnLeftTexture = loadTextureOrPlaceholder(turnLeftFile.path(),
                32, 64, new Color(0.2f, 0.6f, 1.0f, 1f));
        assets.carTurnLeftAnchor = SpriteAnchorUtil.fromFile(turnLeftFile);

        FileHandle turnRightFile = Gdx.files.internal(CAR_TURN_RIGHT_FILE);
        if (!turnRightFile.exists()) {
            Gdx.app.log("Assets", "Missing " + CAR_TURN_RIGHT_FILE + " — using straight sprite until you add the file.");
            turnRightFile = straightFile;
        }
        assets.carTurnRightTexture = loadTextureOrPlaceholder(turnRightFile.path(),
                32, 64, new Color(0.2f, 0.5f, 0.9f, 1f));
        assets.carTurnRightAnchor = SpriteAnchorUtil.fromFile(turnRightFile);

        assets.grassTexture = loadTextureOrPlaceholder(GRASS_TEXTURE_FILE,
                256, 256, new Color(0.18f, 0.45f, 0.15f, 1f));

        assets.carStraightTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        assets.carTurnLeftTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        assets.carTurnRightTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        assets.grassTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        assets.grassTexture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);

        Gdx.app.log("Assets", "Car sprites — straight: " + CAR_STRAIGHT_FILE
                + ", turn left: " + turnLeftFile.path()
                + ", turn right: " + turnRightFile.path());

        return assets;
    }

    /**
     * Zwalnia pamięć zajmowaną przez załadowane tekstury.
     *
     * @param assets zestaw tekstur do zwolnienia; null jest bezpiecznie ignorowany
     */
    public void dispose(LoadedAssets assets) {
        if (assets == null) return;
        if (assets.carStraightTexture != null) assets.carStraightTexture.dispose();
        if (assets.carTurnLeftTexture != null) assets.carTurnLeftTexture.dispose();
        if (assets.carTurnRightTexture != null) assets.carTurnRightTexture.dispose();
        if (assets.grassTexture != null) assets.grassTexture.dispose();
    }

    /**
     * Próbuje wczytać teksturę z podanej ścieżki wewnętrznej gry.
     * Gdy plik nie istnieje lub jest uszkodzony, tworzy jednolity placeholder.
     *
     * @param path         ścieżka wewnętrzna pliku graficznego
     * @param fallbackW    szerokość placeholdera w pikselach
     * @param fallbackH    wysokość placeholdera w pikselach
     * @param fallbackColor kolor wypełnienia placeholdera
     * @return załadowana tekstura lub placeholder
     */
    private Texture loadTextureOrPlaceholder(String path, int fallbackW, int fallbackH, Color fallbackColor) {
        try {
            if (Gdx.files.internal(path).exists()) {
                Texture tex = new Texture(Gdx.files.internal(path));
                Gdx.app.log("Assets", "Loaded: " + path);
                return tex;
            }
        } catch (Exception e) {
            Gdx.app.error("Assets", "Failed to load " + path + ": " + e.getMessage());
        }

        Gdx.app.log("Assets", "Using placeholder for missing asset: " + path);
        Pixmap pixmap = new Pixmap(fallbackW, fallbackH, Pixmap.Format.RGBA8888);
        pixmap.setColor(fallbackColor);
        pixmap.fill();
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }
}
