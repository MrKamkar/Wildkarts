package com.wildkarts.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.wildkarts.WildKartsGame;
import com.wildkarts.factory.CarFactory;
import com.wildkarts.net.GameClient;
import com.wildkarts.screen.GameAssetLoader.LoadedAssets;
import com.wildkarts.systems.CarDebugRenderSystem;
import com.wildkarts.systems.DriftSoundSystem;
import com.wildkarts.systems.HudRenderSystem;
import com.wildkarts.systems.InputSystem;
import com.wildkarts.systems.LapSectorSystem;
import com.wildkarts.systems.MovementSystem;
import com.wildkarts.systems.PhysicsSystem;
import com.wildkarts.systems.RaceStateSystem;
import com.wildkarts.systems.RenderSystem;
import com.wildkarts.systems.SkidmarkSystem;
import com.wildkarts.systems.TerrainSystem;
import com.wildkarts.track.TrackGenerator;
import com.wildkarts.track.TrackRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Współdzielony kontekst ekranu gry — przechowuje referencje do świata Box2D,
 * silnika ECS, systemów, UI i zasobów graficznych używanych przez kontrolery.
 */
public class GameScreenContext {

    /** Szerokość widoku kamery w metrach Box2D. */
    public static final float VIEWPORT_WIDTH_METERS = 40f;

    public final WildKartsGame game;
    public final boolean isMultiplayerMode;
    public final GameClient gameClient;
    public final Map<Integer, Entity> remotePlayers = new HashMap<>();

    public World world;
    public Engine engine;
    public OrthographicCamera camera;
    public Entity playerCar;
    public Entity raceEntity;

    public RaceStateSystem raceStateSystem;
    public LapSectorSystem lapSectorSystem;
    public InputSystem inputSystem;
    public TerrainSystem terrainSystem;
    public MovementSystem movementSystem;
    public RenderSystem renderSystem;
    public SkidmarkSystem skidmarkSystem;
    public CarDebugRenderSystem carDebugRenderSystem;
    public HudRenderSystem hudRenderSystem;
    public DriftSoundSystem driftSoundSystem;
    public PhysicsSystem physicsSystem;

    public TrackGenerator trackGenerator;
    public TrackRenderer trackRenderer;
    public CarFactory carFactory;
    public LoadedAssets assets;

    public GameState currentState;

    public Stage editorStage;
    public Stage loadingStage;
    public Stage playStage;
    public Stage escapeMenuStage;
    public Stage resultsStage;

    public BitmapFont uiFont;
    public Skin uiSkin;
    public Label pointCountLabel;
    public Label loadingLabel;
    public Table lobbyPanel;
    public TextButton readyButton;
    public Label lobbyStatusLabel;
    public Table resultsPanel;
    public ShapeRenderer overlayRenderer;

    public boolean isEscapeMenuOpen;
    public boolean isResultsScreenOpen;

    /**
     * Tworzy kontekst powiązany z instancją gry i trybem rozgrywki.
     *
     * @param game              główna instancja gry LibGDX
     * @param isMultiplayerMode true gdy gra w trybie sieciowym
     * @param gameClient        klient sieciowy; null w trybie jednoosobowym
     */
    public GameScreenContext(WildKartsGame game, boolean isMultiplayerMode, GameClient gameClient) {
        this.game = game;
        this.isMultiplayerMode = isMultiplayerMode;
        this.gameClient = gameClient;
    }
}
