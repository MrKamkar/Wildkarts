package com.wildkarts;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.wildkarts.net.GameClient;

/**
 * Ekran menu głównego — wyświetla tytuł gry, pola nick/IP i przyciski nawigacji.
 * Style UI są generowane programowo, bez zewnętrznego pliku skin.
 */
public class MainMenuScreen implements Screen {

    private final Game game;
    private Stage stage;

    private FreeTypeFontGenerator fontGenerator;
    private BitmapFont font;
    private Texture backgroundTexture;
    private Texture buttonUpTexture;
    private Texture buttonDownTexture;
    private Texture singlePlayerUpTexture;
    private Texture singlePlayerDownTexture;
    private Texture textFieldBgTexture;
    private Texture cursorTexture;

    private Label statusLabel;

    /**
     * Tworzy ekran menu powiązany z instancją gry LibGDX.
     *
     * @param game główna instancja gry
     */
    public MainMenuScreen(Game game) {
        this.game = game;
    }

    /**
     * Inicjalizuje scenę menu, buduje widgety i uruchamia muzykę tła.
     */
    @Override
    public void show() {
        stage = new Stage(new StretchViewport(1920, 1080));
        Gdx.input.setInputProcessor(stage);

        backgroundTexture = new Texture(Gdx.files.internal("textures/background_menu.png"));
        font = createFont(28);

        buttonUpTexture = createColorTexture(1, 1, new Color(0.25f, 0.25f, 0.3f, 0.85f));
        buttonDownTexture = createColorTexture(1, 1, new Color(0.45f, 0.45f, 0.5f, 0.9f));
        singlePlayerUpTexture = createColorTexture(1, 1, new Color(0.12f, 0.5f, 0.22f, 0.85f));
        singlePlayerDownTexture = createColorTexture(1, 1, new Color(0.18f, 0.65f, 0.3f, 0.9f));
        textFieldBgTexture = createColorTexture(1, 1, new Color(0.15f, 0.15f, 0.2f, 0.8f));
        cursorTexture = createColorTexture(2, 15, Color.WHITE);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.up = new TextureRegionDrawable(new TextureRegion(buttonUpTexture));
        buttonStyle.down = new TextureRegionDrawable(new TextureRegion(buttonDownTexture));

        TextButton.TextButtonStyle singlePlayerStyle = new TextButton.TextButtonStyle();
        singlePlayerStyle.font = font;
        singlePlayerStyle.fontColor = Color.WHITE;
        singlePlayerStyle.up = new TextureRegionDrawable(new TextureRegion(singlePlayerUpTexture));
        singlePlayerStyle.down = new TextureRegionDrawable(new TextureRegion(singlePlayerDownTexture));

        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = new TextureRegionDrawable(new TextureRegion(textFieldBgTexture));
        textFieldStyle.cursor = new TextureRegionDrawable(new TextureRegion(cursorTexture));
        textFieldStyle.messageFontColor = new Color(0.6f, 0.6f, 0.6f, 1f);

        TextField nickField = new TextField("", textFieldStyle);
        nickField.setMessageText("Gracz123");

        TextField ipField = new TextField("localhost", textFieldStyle);

        Label.LabelStyle statusStyle = new Label.LabelStyle();
        statusStyle.font = font;
        statusStyle.fontColor = Color.LIGHT_GRAY;
        statusLabel = new Label("", statusStyle);

        TextButton singlePlayerButton = new TextButton("MAP EDITOR", singlePlayerStyle);
        singlePlayerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new GameScreen((WildKartsGame) game, false));
                dispose();
            }
        });

        TextButton connectButton = new TextButton("CONNECT", buttonStyle);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                statusLabel.setText("Connecting...");
                connectButton.setDisabled(true);

                GameClient client = new GameClient();
                String nick = nickField.getText().trim();
                if (nick.isEmpty()) nick = nickField.getMessageText();
                client.playerName = nick;
                ((WildKartsGame) game).setGameClient(client);

                client.onJoinAccepted = () -> {
                    game.setScreen(new GameScreen((WildKartsGame) game, true));
                    dispose();
                };

                client.onConnectionFailed = () -> {
                    statusLabel.setText("Connection failed.");
                    connectButton.setDisabled(false);
                };

                client.onDisconnected = () -> {
                    Screen oldScreen = game.getScreen();
                    game.setScreen(new MainMenuScreen(game));
                    if (oldScreen != null) oldScreen.dispose();
                };

                client.connect(ipField.getText());
            }
        });

        TextButton exitButton = new TextButton("EXIT", buttonStyle);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.setBackground(new TextureRegionDrawable(new TextureRegion(backgroundTexture)));

        Table content = new Table();
        content.center().left();
        content.padLeft(100f).padTop(300f);

        Label.LabelStyle nickLabelStyle = new Label.LabelStyle();
        nickLabelStyle.font = font;
        nickLabelStyle.fontColor = Color.LIGHT_GRAY;

        content.add(new Label("NICK:", nickLabelStyle)).left().padBottom(6f).row();
        content.add(nickField).width(360f).height(52f).left().padBottom(24f).row();
        content.add(new Label("SERVER IP:", nickLabelStyle)).left().padBottom(6f).row();
        content.add(ipField).width(360f).height(52f).left().padBottom(30f).row();
        content.add(connectButton).width(360f).height(64f).left().padBottom(24f).row();
        content.add(singlePlayerButton).width(360f).height(64f).left().padBottom(24f).row();
        content.add(exitButton).width(360f).height(64f).left().padBottom(20f).row();
        content.add(statusLabel).left();

        rootTable.add(content).expand().top().left();
        stage.addActor(rootTable);

        startMenuMusic();
    }

    /**
     * Uruchamia muzykę tła menu głównego.
     */
    private void startMenuMusic() {
        ScreenMusic.playTheme(ScreenMusic.MENU_THEME_PATH);
    }

    /**
     * Tworzy czcionkę bitmapową — FreeType gdy dostępny plik Roboto, w przeciwnym razie domyślna.
     *
     * @param size rozmiar czcionki w pikselach
     * @return gotowa czcionka do użycia w UI
     */
    private BitmapFont createFont(int size) {
        if (Gdx.files.internal("fonts/Roboto-Medium.ttf").exists()) {
            fontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Roboto-Medium.ttf"));
            FreeTypeFontParameter param = new FreeTypeFontParameter();
            param.size = size;
            param.minFilter = TextureFilter.Linear;
            param.magFilter = TextureFilter.Linear;
            return fontGenerator.generateFont(param);
        }
        BitmapFont fallback = new BitmapFont();
        fallback.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
        fallback.getData().setScale(size / 15f);
        return fallback;
    }

    /**
     * Tworzy jednokolorową teksturę o podanym rozmiarze.
     *
     * @param width  szerokość w pikselach
     * @param height wysokość w pikselach
     * @param color  kolor wypełnienia
     * @return nowa tekstura LibGDX
     */
    private Texture createColorTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    /**
     * Czyści ekran, aktualizuje i rysuje scenę menu.
     *
     * @param delta czas od ostatniej klatki w sekundach
     */
    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    /**
     * Dopasowuje viewport sceny do nowego rozmiaru okna.
     *
     * @param width  nowa szerokość okna
     * @param height nowa wysokość okna
     */
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /** Pauzuje ekran — brak akcji w menu. */
    @Override
    public void pause() {
    }

    /** Wznawia ekran — brak akcji w menu. */
    @Override
    public void resume() {
    }

    /** Ukrywa ekran — brak akcji w menu. */
    @Override
    public void hide() {
    }

    /** Zwalnia zasoby sceny, czcionek i tekstur menu. */
    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
        if (fontGenerator != null) fontGenerator.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();
        if (buttonUpTexture != null) buttonUpTexture.dispose();
        if (buttonDownTexture != null) buttonDownTexture.dispose();
        if (singlePlayerUpTexture != null) singlePlayerUpTexture.dispose();
        if (singlePlayerDownTexture != null) singlePlayerDownTexture.dispose();
        if (textFieldBgTexture != null) textFieldBgTexture.dispose();
        if (cursorTexture != null) cursorTexture.dispose();
    }
}
