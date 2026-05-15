package com.wildkarts;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.TerrainComponent;
import com.wildkarts.factory.CarFactory;
import com.wildkarts.systems.InputSystem;
import com.wildkarts.systems.MovementSystem;
import com.wildkarts.systems.PhysicsSystem;
import com.wildkarts.systems.RenderSystem;
import com.wildkarts.systems.TerrainSystem;
import com.wildkarts.track.TrackGenerator;
import com.wildkarts.track.TrackRenderer;

/**
 * Main game screen — initializes Box2D world, Ashley engine, and game entities.
 *
 * System execution order:
 * 0. InputSystem       — reads keyboard → InputComponent
 * 1. TerrainSystem     — checks tile under car, adjusts speed limits
 * 2. MovementSystem    — InputComponent → Box2D forces
 * 3. PhysicsSystem     — world.step() with fixed timestep
 * 4. [NetworkSyncSystem] — multiplayer only
 * 5. RenderSystem      — draws entities
 *
 * In single-player, an editor mode is active: clicking the map places
 * control points, building a CatmullRomSpline track that can be saved/loaded.
 */
import java.util.HashMap;
import java.util.Map;
import com.wildkarts.systems.NetworkSyncSystem;
import com.wildkarts.net.packets.PlayerPositionPacket;

public class GameScreen extends ScreenAdapter {

    private final WildKartsGame game;
    private final boolean isMultiplayerMode;
    private final com.wildkarts.net.GameClient gameClient;

    // Map to track remote players
    private final Map<Integer, com.badlogic.ashley.core.Entity> remotePlayers = new HashMap<>();

    public GameScreen(WildKartsGame game, boolean isMultiplayerMode) {
        this.game = game;
        this.isMultiplayerMode = isMultiplayerMode;
        this.gameClient = isMultiplayerMode ? game.getGameClient() : null;
    }

    // Box2D world — zero gravity for top-down view
    private World world;

    // Ashley ECS engine
    private Engine engine;

    // Camera — shows the game world in Box2D meters
    private OrthographicCamera camera;

    // Reference for camera tracking
    private com.badlogic.ashley.core.Entity playerCar;

    // Systems (kept for disposal and direct access)
    private RenderSystem renderSystem;
    private PhysicsSystem physicsSystem;

    // --- Track system ---
    private TrackGenerator trackGenerator;
    private TrackRenderer trackRenderer;

    // --- Editor mode (single player only) ---
    private boolean editorMode;
    private Stage editorStage;
    private BitmapFont editorFont;
    private Texture editorBtnUpTex;
    private Texture editorBtnDownTex;
    private Label pointCountLabel;

    // --- Car factory (kept for potential respawn) ---
    private CarFactory carFactory;

    /** Pixels-per-meter ratio for camera. */
    private static final float VIEWPORT_WIDTH_METERS = 40f;

    private static final String MAP_FILE = "custom_map.json";

    @Override
    public void show() {
        // --- Camera setup ---
        float aspectRatio = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        camera = new OrthographicCamera(VIEWPORT_WIDTH_METERS, VIEWPORT_WIDTH_METERS / aspectRatio);
        camera.position.set(0, 0, 0);
        camera.update();

        // --- Box2D world (no gravity — top-down!) ---
        world = new World(new Vector2(0, 0), true);

        // --- Track generator ---
        trackGenerator = new TrackGenerator();
        trackRenderer = new TrackRenderer();

        // Try loading a saved map
        trackGenerator.loadMap(MAP_FILE);

        // --- Ashley ECS engine ---
        engine = new Engine();

        // --- Register systems in execution order ---
        // Priority values define order: lower = runs first
        InputSystem inputSystem = new InputSystem();
        inputSystem.priority = 0;

        TerrainSystem terrainSystem = new TerrainSystem();
        terrainSystem.priority = 1;

        MovementSystem movementSystem = new MovementSystem();
        movementSystem.priority = 2;

        physicsSystem = new PhysicsSystem(world);
        physicsSystem.priority = 3;

        renderSystem = new RenderSystem(camera, world, physicsSystem);

        engine.addSystem(inputSystem);
        engine.addSystem(terrainSystem);
        engine.addSystem(movementSystem);
        engine.addSystem(physicsSystem);

        // Only add network sync system in multiplayer mode
        if (isMultiplayerMode) {
            NetworkSyncSystem syncSystem = new NetworkSyncSystem();
            syncSystem.priority = 4;
            engine.addSystem(syncSystem);
        }

        renderSystem.priority = 5;
        engine.addSystem(renderSystem);

        // --- Create player car entity ---
        carFactory = new CarFactory(world, engine);
        Vector2 startPos = trackGenerator.getStartPosition();
        float startAngle = trackGenerator.getStartAngle();
        playerCar = carFactory.createCar(startPos.x, startPos.y, startAngle);

        // Add terrain awareness to player car
        CarComponent carComp = playerCar.getComponent(CarComponent.class);
        TerrainComponent terrain = new TerrainComponent();
        terrain.trackGenerator = trackGenerator;
        terrain.defaultMaxForwardSpeed = carComp.maxForwardSpeed;
        terrain.defaultDriveForce = carComp.driveForce;
        playerCar.add(terrain);

        // --- Handle incoming remote player positions (multiplayer only) ---
        if (isMultiplayerMode && gameClient != null) {
            gameClient.onPlayerPositionReceived = packet -> {
                if (packet.playerId == gameClient.localPlayerId) return; // Ignore our own packets

                com.badlogic.ashley.core.Entity remoteCar = remotePlayers.get(packet.playerId);
                if (remoteCar == null) {
                    // Spawn new remote car
                    remoteCar = carFactory.createRemoteCar(packet.x, packet.y, packet.angle);
                    remotePlayers.put(packet.playerId, remoteCar);
                }

                com.wildkarts.components.NetworkSyncComponent sync =
                    remoteCar.getComponent(com.wildkarts.components.NetworkSyncComponent.class);
                if (sync != null) {
                    com.wildkarts.components.NetworkSyncComponent.Snapshot snap = new com.wildkarts.components.NetworkSyncComponent.Snapshot();
                    snap.timestamp = System.currentTimeMillis();
                    snap.position.set(packet.x, packet.y);
                    snap.angle = packet.angle;
                    snap.velocity.set(packet.velocityX, packet.velocityY);

                    // Approximate angular velocity if not provided in packet
                    snap.angularVelocity = 0;
                    if (!sync.snapshots.isEmpty()) {
                        com.wildkarts.components.NetworkSyncComponent.Snapshot last = sync.snapshots.get(sync.snapshots.size() - 1);
                        float dt = (snap.timestamp - last.timestamp) / 1000f;
                        if (dt > 0) {
                            float diff = (snap.angle - last.angle) % ((float) Math.PI * 2);
                            if (diff > Math.PI) diff -= Math.PI * 2;
                            else if (diff < -Math.PI) diff += Math.PI * 2;
                            snap.angularVelocity = diff / dt;
                        }
                    }

                    sync.snapshots.add(snap);
                    if (sync.snapshots.size() > 20) {
                        sync.snapshots.remove(0);
                    }
                }
            };

            gameClient.onPlayerDisconnected = id -> {
                com.badlogic.ashley.core.Entity remoteCar = remotePlayers.remove(id);
                if (remoteCar != null) {
                    com.wildkarts.components.PhysicsComponent phys =
                        remoteCar.getComponent(com.wildkarts.components.PhysicsComponent.class);
                    if (phys != null && phys.body != null) {
                        world.destroyBody(phys.body);
                    }
                    engine.removeEntity(remoteCar);
                    Gdx.app.log("GameScreen", "Remote player removed: " + id);
                }
            };
        }

        // --- Create boundary walls ---
        createBoundaryWalls();

        // --- Editor mode setup (single player only) ---
        editorMode = !isMultiplayerMode;
        if (editorMode) {
            setupEditorUI();
        }
    }

    // ─── Editor Mode Setup ─────────────────────────────────────────────

    /**
     * Creates the editor UI (SAVE button, point count label) and sets up
     * InputMultiplexer so Stage buttons take priority over map clicks.
     */
    private void setupEditorUI() {
        editorStage = new Stage(new ScreenViewport());
        editorFont = new BitmapFont();

        // Button textures
        editorBtnUpTex = createColorTexture(1, 1, new Color(0.2f, 0.5f, 0.8f, 1f));
        editorBtnDownTex = createColorTexture(1, 1, new Color(0.3f, 0.6f, 0.9f, 1f));

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = editorFont;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up = new TextureRegionDrawable(new TextureRegion(editorBtnUpTex));
        btnStyle.down = new TextureRegionDrawable(new TextureRegion(editorBtnDownTex));

        // SAVE button
        TextButton saveButton = new TextButton("SAVE", btnStyle);
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                trackGenerator.saveMap(MAP_FILE);
            }
        });

        // UNDO button (removes last point)
        TextButton undoButton = new TextButton("UNDO", btnStyle);
        undoButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                trackGenerator.removeLastPoint();
                updatePointCountLabel();
            }
        });

        // Point count label
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = editorFont;
        labelStyle.fontColor = Color.WHITE;
        pointCountLabel = new Label("Points: 0", labelStyle);
        updatePointCountLabel();

        // Layout — top-right corner
        Table table = new Table();
        table.setFillParent(true);
        table.top().right();
        table.pad(10f);

        table.add(pointCountLabel).padBottom(5f).row();
        table.add(saveButton).width(100f).height(35f).padBottom(5f).row();
        table.add(undoButton).width(100f).height(35f);

        editorStage.addActor(table);

        // --- InputMultiplexer: Stage first (catches button clicks), then map clicks ---
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(editorStage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT) {
                    // Convert screen coordinates to world coordinates
                    Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));
                    trackGenerator.addPoint(worldCoords.x, worldCoords.y);
                    updatePointCountLabel();
                    Gdx.app.log("Editor", "Point added at (" + worldCoords.x + ", " + worldCoords.y + ")");
                    return true;
                }
                return false;
            }
        });

        Gdx.input.setInputProcessor(multiplexer);
    }

    private void updatePointCountLabel() {
        if (pointCountLabel != null) {
            int count = trackGenerator.getManualPoints().size;
            pointCountLabel.setText("Points: " + count + (count < 4 ? " (need 4+)" : ""));
        }
    }

    private Texture createColorTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    // ─── Render Loop ───────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        // Clear screen — grass green background (matches tile color at grid edges)
        Gdx.gl.glClearColor(0.18f, 0.45f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Toggle debug draw with F1
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            renderSystem.toggleDebugDraw();
        }

        // Follow player car with camera
        updateCamera();

        // Render track tiles (background layer — before entities)
        trackRenderer.render(camera, trackGenerator);

        // Update all ECS systems (includes entity rendering via RenderSystem)
        engine.update(delta);

        // Render editor overlay on top of everything (control points, spline)
        if (editorMode) {
            trackRenderer.renderEditorOverlay(camera, trackGenerator);

            // Draw editor UI (Stage)
            editorStage.act(delta);
            editorStage.draw();
        }

        // Send local player position to server (multiplayer only)
        if (isMultiplayerMode && gameClient != null && playerCar != null) {
            com.wildkarts.components.PhysicsComponent physics =
                playerCar.getComponent(com.wildkarts.components.PhysicsComponent.class);
            if (physics != null && physics.body != null) {
                Vector2 pos = physics.body.getPosition();
                Vector2 vel = physics.body.getLinearVelocity();
                gameClient.sendUnreliable(new PlayerPositionPacket(
                    gameClient.localPlayerId, pos.x, pos.y, physics.body.getAngle(), vel.x, vel.y
                ));
            }
        }
    }

    /**
     * Smoothly follows the player car with the camera.
     */
    private void updateCamera() {
        if (playerCar != null) {
            com.wildkarts.components.PhysicsComponent physics =
                    playerCar.getComponent(com.wildkarts.components.PhysicsComponent.class);
            if (physics != null && physics.body != null) {
                Vector2 carPos = physics.body.getPosition();
                // Smooth camera follow (lerp)
                camera.position.x += (carPos.x - camera.position.x) * 0.1f;
                camera.position.y += (carPos.y - camera.position.y) * 0.1f;
                camera.update();
            }
        }
    }

    /**
     * Creates static boundary walls to keep the car within a testing area.
     */
    private void createBoundaryWalls() {
        float halfSize = 50f;
        float wallThickness = 1f;

        createWall(0, halfSize, halfSize, wallThickness);    // Top
        createWall(0, -halfSize, halfSize, wallThickness);   // Bottom
        createWall(-halfSize, 0, wallThickness, halfSize);   // Left
        createWall(halfSize, 0, wallThickness, halfSize);    // Right
    }

    private void createWall(float x, float y, float halfWidth, float halfHeight) {
        com.badlogic.gdx.physics.box2d.BodyDef wallDef = new com.badlogic.gdx.physics.box2d.BodyDef();
        wallDef.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody;
        wallDef.position.set(x, y);

        com.badlogic.gdx.physics.box2d.Body wallBody = world.createBody(wallDef);

        com.badlogic.gdx.physics.box2d.PolygonShape wallShape = new com.badlogic.gdx.physics.box2d.PolygonShape();
        wallShape.setAsBox(halfWidth, halfHeight);

        wallBody.createFixture(wallShape, 0f);
        wallShape.dispose();
    }

    @Override
    public void resize(int width, int height) {
        float aspectRatio = (float) width / height;
        camera.viewportWidth = VIEWPORT_WIDTH_METERS;
        camera.viewportHeight = VIEWPORT_WIDTH_METERS / aspectRatio;
        camera.update();

        if (editorStage != null) {
            editorStage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        renderSystem.dispose();
        trackRenderer.dispose();
        world.dispose();

        if (editorStage != null) editorStage.dispose();
        if (editorFont != null) editorFont.dispose();
        if (editorBtnUpTex != null) editorBtnUpTex.dispose();
        if (editorBtnDownTex != null) editorBtnDownTex.dispose();
    }
}
