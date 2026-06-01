package com.wildkarts;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.LapComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;
import com.wildkarts.factory.CarFactory;
import com.wildkarts.net.GameClient;
import com.wildkarts.net.packets.PlayerPositionPacket;
import com.wildkarts.net.packets.PlayerReadyPacket;
import com.wildkarts.screen.EditorUiController;
import com.wildkarts.screen.GameAssetLoader;
import com.wildkarts.screen.GameNetworkCoordinator;
import com.wildkarts.screen.GameScreenActions;
import com.wildkarts.screen.GameScreenContext;
import com.wildkarts.screen.GameSessionController;
import com.wildkarts.screen.GameState;
import com.wildkarts.screen.PlayUiController;
import com.wildkarts.screen.ProceduralSkinFactory;
import com.wildkarts.systems.CarDebugRenderSystem;
import com.wildkarts.systems.DriftSoundSystem;
import com.wildkarts.systems.HudRenderSystem;
import com.wildkarts.systems.InputSystem;
import com.wildkarts.systems.LapSectorSystem;
import com.wildkarts.systems.MovementSystem;
import com.wildkarts.systems.NetworkSyncSystem;
import com.wildkarts.systems.PhysicsSystem;
import com.wildkarts.systems.RaceStateSystem;
import com.wildkarts.systems.RenderSystem;
import com.wildkarts.systems.SkidmarkSystem;
import com.wildkarts.systems.TerrainSystem;
import com.wildkarts.track.TrackGenerator;
import com.wildkarts.track.TrackRenderer;

/**
 * Główny ekran gry — inicjalizuje świat Box2D, silnik Ashley ECS i encje gry.
 *
 * <p>Kolejność systemów:</p>
 * <ol>
 *   <li>InputSystem — odczyt klawiatury</li>
 *   <li>TerrainSystem — typ nawierzchni pod kołami</li>
 *   <li>MovementSystem — siły napędu i hamowania</li>
 *   <li>PhysicsSystem — krok symulacji Box2D</li>
 *   <li>NetworkSyncSystem — tylko tryb wieloosobowy</li>
 *   <li>RenderSystem — rysowanie encji</li>
 * </ol>
 *
 * <p>W trybie jednoosobowym dostępny jest edytor toru. W trybie wieloosobowym
 * gracz czeka na mapę z serwera, a następnie startuje wyścig.</p>
 */
public class GameScreen extends ScreenAdapter implements GameScreenActions {

    private final GameScreenContext ctx;
    private final GameAssetLoader assetLoader = new GameAssetLoader();

    private EditorUiController editorUi;
    private PlayUiController playUi;
    private GameSessionController sessionController;
    private GameNetworkCoordinator networkCoordinator;

    /**
     * Tworzy ekran gry w wybranym trybie rozgrywki.
     *
     * @param game              główna instancja gry
     * @param isMultiplayerMode true dla trybu sieciowego
     */
    public GameScreen(WildKartsGame game, boolean isMultiplayerMode) {
        GameClient gameClient = isMultiplayerMode ? game.getGameClient() : null;
        this.ctx = new GameScreenContext(game, isMultiplayerMode, gameClient);
    }

    @Override
    public void show() {
        float aspectRatio = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        ctx.camera = new OrthographicCamera(
                GameScreenContext.VIEWPORT_WIDTH_METERS,
                GameScreenContext.VIEWPORT_WIDTH_METERS / aspectRatio);
        ctx.camera.position.set(0, 0, 0);
        ctx.camera.update();

        ctx.assets = assetLoader.loadAll();

        ctx.world = new World(new Vector2(0, 0), true);
        ctx.trackGenerator = new TrackGenerator();
        ctx.trackRenderer = new TrackRenderer(ctx.assets.grassTexture);
        ctx.engine = new Engine();
        ctx.carFactory = new CarFactory(ctx.world, ctx.engine);

        initSystems();

        ctx.uiFont = new BitmapFont();
        ctx.uiSkin = ProceduralSkinFactory.create(ctx.uiFont);

        editorUi = new EditorUiController(ctx, this);
        playUi = new PlayUiController(ctx, this);
        sessionController = new GameSessionController(ctx, editorUi);
        networkCoordinator = new GameNetworkCoordinator(ctx, sessionController, playUi);

        editorUi.setupEditorUI();
        playUi.setupLoadingUI();
        playUi.setupPlayUI();

        if (!ctx.isMultiplayerMode) {
            sessionController.transitionToEditing();
        } else {
            sessionController.transitionToLoading();
            networkCoordinator.registerCallbacks(() -> sessionController.transitionToPlaying());
        }

        if (ctx.isMultiplayerMode)
            ScreenMusic.playTheme(ScreenMusic.RACE_THEME_PATH);
        else
            ScreenMusic.stop();
    }

    /**
     * Rejestruje i konfiguruje wszystkie systemy ECS w silniku Ashley.
     */
    private void initSystems() {
        ctx.raceStateSystem = new RaceStateSystem();
        ctx.raceStateSystem.priority = -1;

        ctx.inputSystem = new InputSystem();
        ctx.inputSystem.priority = 0;

        ctx.terrainSystem = new TerrainSystem();
        ctx.terrainSystem.priority = 1;

        ctx.movementSystem = new MovementSystem();
        ctx.movementSystem.priority = 2;

        ctx.physicsSystem = new PhysicsSystem(ctx.world);
        ctx.physicsSystem.priority = 3;

        ctx.lapSectorSystem = new LapSectorSystem(ctx.trackGenerator, ctx.gameClient);
        ctx.lapSectorSystem.priority = 4;

        ctx.skidmarkSystem = new SkidmarkSystem(ctx.camera);
        ctx.skidmarkSystem.priority = 4;

        ctx.renderSystem = new RenderSystem(ctx.camera, ctx.world, ctx.physicsSystem,
                ctx.assets.carStraightTexture, ctx.assets.carStraightAnchor,
                ctx.assets.carTurnLeftTexture, ctx.assets.carTurnLeftAnchor,
                ctx.assets.carTurnRightTexture, ctx.assets.carTurnRightAnchor);
        ctx.renderSystem.priority = 5;

        ctx.carDebugRenderSystem = new CarDebugRenderSystem(ctx.camera);
        ctx.carDebugRenderSystem.priority = 6;

        ctx.hudRenderSystem = new HudRenderSystem();
        ctx.hudRenderSystem.priority = 7;

        ctx.driftSoundSystem = new DriftSoundSystem();
        ctx.driftSoundSystem.priority = 8;

        ctx.engine.addSystem(ctx.raceStateSystem);
        ctx.engine.addSystem(ctx.inputSystem);
        ctx.engine.addSystem(ctx.terrainSystem);
        ctx.engine.addSystem(ctx.movementSystem);
        ctx.engine.addSystem(ctx.physicsSystem);
        ctx.engine.addSystem(ctx.lapSectorSystem);

        if (ctx.isMultiplayerMode) {
            NetworkSyncSystem syncSystem = new NetworkSyncSystem();
            syncSystem.priority = 4;
            ctx.engine.addSystem(syncSystem);
        }

        ctx.engine.addSystem(ctx.skidmarkSystem);
        ctx.engine.addSystem(ctx.renderSystem);
        ctx.engine.addSystem(ctx.carDebugRenderSystem);
        ctx.engine.addSystem(ctx.hudRenderSystem);
        ctx.engine.addSystem(ctx.driftSoundSystem);
    }

    @Override
    public void transitionToPlaying() {
        sessionController.transitionToPlaying();
    }

    @Override
    public void transitionToEditing() {
        sessionController.transitionToEditing();
    }

    @Override
    public void exitToMainMenu() {
        ctx.isEscapeMenuOpen = false;
        ctx.isResultsScreenOpen = false;
        if (ctx.inputSystem != null)
            ctx.inputSystem.externalInputBlocked = false;

        if (ctx.gameClient != null) {
            ctx.gameClient.dispose();
            ctx.game.setGameClient(null);
        }

        ctx.game.setScreen(new MainMenuScreen(ctx.game));
        dispose();
    }

    @Override
    public void onReadyButtonClicked() {
        if (ctx.isMultiplayerMode) {
            if (ctx.gameClient == null) return;
            LapComponent lap = ctx.playerCar != null
                    ? ctx.playerCar.getComponent(LapComponent.class) : null;
            boolean newReady = lap == null || !lap.ready;
            if (lap != null) lap.ready = newReady;
            ctx.gameClient.sendReliable(new PlayerReadyPacket(newReady));
            ctx.readyButton.setText(newReady ? "UN-READY" : "READY");
            Gdx.app.log("GameScreen", "Sent PlayerReadyPacket(" + newReady + ")");
        } else {
            sessionController.markLocalPlayerReady();
            ctx.readyButton.setDisabled(true);
            ctx.readyButton.setText("READY!");
        }
    }

    /**
     * Oznacza lokalnego gracza jako gotowego — używane przez systemy zewnętrzne.
     */
    public void markLocalPlayerReady() {
        sessionController.markLocalPlayerReady();
    }

    /**
     * Zwraca aktywny komponent wyścigu lub null.
     *
     * @return komponent stanu wyścigu
     */
    public RaceComponent getRaceComponent() {
        return sessionController.getRaceComponent();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.18f, 0.45f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        updateLobbyVisibility();

        if (ctx.currentState == GameState.PLAYING && !ctx.isResultsScreenOpen
                && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (ctx.isMultiplayerMode)
                playUi.toggleEscapeMenu();
            else if (ctx.isEscapeMenuOpen)
                playUi.closeEscapeMenu();
            else
                sessionController.transitionToEditing();
        }

        if (ctx.currentState == GameState.PLAYING && Gdx.input.isKeyJustPressed(Input.Keys.F1))
            ctx.renderSystem.toggleDebugDraw();

        if (ctx.currentState == GameState.PLAYING && Gdx.input.isKeyJustPressed(Input.Keys.F3)
                && ctx.playerCar != null) {
            CarComponent car = ctx.playerCar.getComponent(CarComponent.class);
            if (car != null)
                car.debugOverlayEnabled = !car.debugOverlayEnabled;
        }

        if (ctx.currentState == GameState.PLAYING)
            sessionController.updateCamera();

        ctx.trackRenderer.render(ctx.camera, ctx.trackGenerator);
        ctx.engine.update(delta);

        switch (ctx.currentState) {
            case EDITING -> {
                ctx.trackRenderer.renderEditorOverlay(ctx.camera, ctx.trackGenerator);
                ctx.editorStage.act(delta);
                ctx.editorStage.draw();
            }
            case LOADING -> {
                ctx.loadingStage.act(delta);
                ctx.loadingStage.draw();
            }
            case PLAYING -> {
                ctx.playStage.act(delta);
                ctx.playStage.draw();
                playUi.renderOverlays(delta);
            }
        }

        sendLocalPlayerPosition();
    }

    /**
     * Aktualizuje widoczność panelu lobby i ekranu wyników w trybie solo.
     */
    private void updateLobbyVisibility() {
        if (ctx.currentState == GameState.PLAYING && ctx.lobbyPanel != null) {
            RaceComponent race = sessionController.getRaceComponent();
            boolean showLobby = race != null
                    && (race.currentState == RaceState.WAITING_FOR_PLAYERS
                    || race.currentState == RaceState.PRACTICE);
            ctx.lobbyPanel.setVisible(showLobby);

            if (!ctx.isMultiplayerMode && !ctx.isResultsScreenOpen && race != null
                    && race.currentState == RaceState.FINISHED) {
                playUi.showRaceResults(
                        new int[]{1},
                        new String[]{"Player"},
                        new float[]{race.raceTimer});
            }
        } else if (ctx.lobbyPanel != null) {
            ctx.lobbyPanel.setVisible(false);
        }
    }

    /**
     * Wysyła pozycję lokalnego gracza do serwera w trybie wieloosobowym.
     */
    private void sendLocalPlayerPosition() {
        if (!ctx.isMultiplayerMode || ctx.gameClient == null || ctx.playerCar == null) return;
        PhysicsComponent physics = ctx.playerCar.getComponent(PhysicsComponent.class);
        if (physics == null || physics.body == null) return;
        Vector2 pos = physics.body.getPosition();
        Vector2 vel = physics.body.getLinearVelocity();
        ctx.gameClient.sendUnreliable(new PlayerPositionPacket(
                ctx.gameClient.localPlayerId, pos.x, pos.y, physics.body.getAngle(), vel.x, vel.y));
    }

    @Override
    public void resize(int width, int height) {
        if (ctx.currentState == GameState.EDITING)
            editorUi.setupEditorCamera();
        else {
            float aspectRatio = (float) width / height;
            ctx.camera.viewportWidth = GameScreenContext.VIEWPORT_WIDTH_METERS;
            ctx.camera.viewportHeight = GameScreenContext.VIEWPORT_WIDTH_METERS / aspectRatio;
            ctx.camera.update();
        }

        if (ctx.editorStage != null)
            ctx.editorStage.getViewport().update(width, height, true);
        if (ctx.playStage != null)
            ctx.playStage.getViewport().update(width, height, true);
        if (ctx.escapeMenuStage != null)
            ctx.escapeMenuStage.getViewport().update(width, height, true);
        if (ctx.resultsStage != null)
            ctx.resultsStage.getViewport().update(width, height, true);
        if (ctx.carDebugRenderSystem != null)
            ctx.carDebugRenderSystem.resize(width, height);
        if (ctx.hudRenderSystem != null)
            ctx.hudRenderSystem.resize(width, height);
        if (ctx.loadingStage != null)
            ctx.loadingStage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        ctx.renderSystem.dispose();
        if (ctx.skidmarkSystem != null) ctx.skidmarkSystem.dispose();
        if (ctx.carDebugRenderSystem != null) ctx.carDebugRenderSystem.dispose();
        if (ctx.hudRenderSystem != null) ctx.hudRenderSystem.dispose();
        if (ctx.driftSoundSystem != null) ctx.driftSoundSystem.dispose();
        ctx.trackRenderer.dispose();
        ctx.world.dispose();

        if (ctx.editorStage != null) ctx.editorStage.dispose();
        if (ctx.loadingStage != null) ctx.loadingStage.dispose();
        if (ctx.playStage != null) ctx.playStage.dispose();
        if (ctx.escapeMenuStage != null) ctx.escapeMenuStage.dispose();
        if (ctx.resultsStage != null) ctx.resultsStage.dispose();
        if (ctx.overlayRenderer != null) ctx.overlayRenderer.dispose();
        if (ctx.uiFont != null) ctx.uiFont.dispose();

        assetLoader.dispose(ctx.assets);
    }
}
