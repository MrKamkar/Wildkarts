package com.wildkarts;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.wildkarts.net.GameClient;

/**
 * Main menu screen — displays the game title and navigation buttons.
 * Uses programmatic styles (no skin file required).
 */
public class MainMenuScreen implements Screen {

    private final Game game;
    private Stage stage;

    // Disposable resources created programmatically
    private BitmapFont font;
    private Texture buttonUpTexture;
    private Texture buttonDownTexture;
    private Texture textFieldBgTexture;
    private Texture cursorTexture;
    
    private Label statusLabel;

    public MainMenuScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // --- Create font ---
        font = new BitmapFont(); // default Arial-like 15px bitmap font

        // --- Create button textures programmatically ---
        buttonUpTexture = createColorTexture(1, 1, new Color(0.3f, 0.3f, 0.35f, 1f));
        buttonDownTexture = createColorTexture(1, 1, new Color(0.5f, 0.5f, 0.55f, 1f));
        textFieldBgTexture = createColorTexture(1, 1, new Color(0.2f, 0.2f, 0.25f, 1f));
        cursorTexture = createColorTexture(2, 15, Color.WHITE);

        // --- TextButton style (no skin needed) ---
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.up = new TextureRegionDrawable(new TextureRegion(buttonUpTexture));
        buttonStyle.down = new TextureRegionDrawable(new TextureRegion(buttonDownTexture));

        // --- TextField style ---
        com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle textFieldStyle = new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle();
        textFieldStyle.font = font;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = new TextureRegionDrawable(new TextureRegion(textFieldBgTexture));
        textFieldStyle.cursor = new TextureRegionDrawable(new TextureRegion(cursorTexture));

        // --- Title label ---
        Label.LabelStyle titleStyle = new Label.LabelStyle();
        titleStyle.font = font;
        titleStyle.fontColor = Color.GOLD;
        Label titleLabel = new Label("WILDKARTS", titleStyle);

        // --- IP Input Field ---
        com.badlogic.gdx.scenes.scene2d.ui.TextField ipField = new com.badlogic.gdx.scenes.scene2d.ui.TextField("localhost", textFieldStyle);

        // --- Status Label ---
        Label.LabelStyle statusStyle = new Label.LabelStyle();
        statusStyle.font = font;
        statusStyle.fontColor = Color.LIGHT_GRAY;
        statusLabel = new Label("", statusStyle);

        // --- Connect button ---
        TextButton connectButton = new TextButton("CONNECT", buttonStyle);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                statusLabel.setText("Connecting...");
                connectButton.setDisabled(true);
                
                GameClient client = new GameClient();
                ((WildKartsGame) game).setGameClient(client);
                
                client.onJoinAccepted = () -> {
                    game.setScreen(new GameScreen((WildKartsGame) game));
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

        // --- Exit button ---
        TextButton exitButton = new TextButton("Exit", buttonStyle);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        // --- Layout with Table ---
        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(titleLabel).padBottom(40f).row();
        table.add(ipField).width(200f).height(40f).padBottom(10f).row();
        table.add(connectButton).width(200f).height(50f).padBottom(10f).row();
        table.add(statusLabel).padBottom(20f).row();
        table.add(exitButton).width(200f).height(50f);

        stage.addActor(table);
    }

    /**
     * Creates a 1×1 pixel texture filled with the given color.
     * Used to build simple drawable backgrounds for buttons.
     */
    private Texture createColorTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
        if (buttonUpTexture != null) buttonUpTexture.dispose();
        if (buttonDownTexture != null) buttonDownTexture.dispose();
        if (textFieldBgTexture != null) textFieldBgTexture.dispose();
        if (cursorTexture != null) cursorTexture.dispose();
    }
}
