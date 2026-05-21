package com.wildkarts;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.LapComponent;
import com.wildkarts.components.NetworkSyncComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;
import com.wildkarts.components.TerrainComponent;
import com.wildkarts.factory.CarFactory;
import com.wildkarts.net.GameClient;
import com.wildkarts.net.packets.MapReadyPacket;
import com.wildkarts.net.packets.PlayerPositionPacket;
import com.wildkarts.net.packets.PlayerReadyPacket;
import com.wildkarts.systems.CarDebugRenderSystem;
import com.wildkarts.systems.InputSystem;
import com.wildkarts.systems.LapSectorSystem;
import com.wildkarts.systems.MovementSystem;
import com.wildkarts.systems.NetworkSyncSystem;
import com.wildkarts.systems.PhysicsSystem;
import com.wildkarts.systems.RaceStateSystem;
import com.wildkarts.systems.RenderSystem;
import com.wildkarts.systems.SkidmarkSystem;
import com.wildkarts.systems.TerrainSystem;
import com.wildkarts.track.TrackData;
import com.wildkarts.track.TrackGenerator;
import com.wildkarts.track.TrackRenderer;

import java.util.HashMap;
import java.util.Map;

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
 * In editor mode all vehicle logic is disabled and the camera zooms
 * to show the entire grid.
 */

public class GameScreen extends ScreenAdapter {

    private final WildKartsGame game;
    private final boolean isMultiplayerMode;
    private final GameClient gameClient;

    // Map to track remote players
    private final Map<Integer, Entity> remotePlayers = new HashMap<>();

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
    private Entity playerCar;

    // Systems (kept for disposal and direct access)
    private RaceStateSystem raceStateSystem;
    private LapSectorSystem lapSectorSystem;
    private InputSystem inputSystem;
    private TerrainSystem terrainSystem;
    private MovementSystem movementSystem;
    private RenderSystem renderSystem;
    private SkidmarkSystem skidmarkSystem;
    private CarDebugRenderSystem carDebugRenderSystem;
    private PhysicsSystem physicsSystem;

    // Race manager entity (singleton, carries RaceComponent)
    private Entity raceEntity;

    // --- Track system ---
    private TrackGenerator trackGenerator;
    private TrackRenderer trackRenderer;

    // --- State Management ---
    public enum GameState {
        EDITING, LOADING, PLAYING
    }
    private GameState currentState;

    // --- Editor & Play UI ---
    private Stage editorStage;
    private Stage loadingStage;
    private Stage playStage;
    private BitmapFont uiFont;
    private Skin uiSkin;
    private Label pointCountLabel;
    private Label loadingLabel;

    // Race lobby UI (shown only while WAITING_FOR_PLAYERS)
    private Table lobbyPanel;
    private TextButton readyButton;
    private Label lobbyStatusLabel;

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

        // In multiplayer, load the default map. In Map Editor, start clean.
        // Track generation handled via network in multiplayer
        if (!isMultiplayerMode) {
            trackGenerator.loadMap(MAP_FILE);
        }

        // --- Ashley ECS engine ---
        engine = new Engine();

        // --- Common Factory ---
        carFactory = new CarFactory(world, engine);

        // --- Initialize all systems ---
        raceStateSystem = new RaceStateSystem();
        raceStateSystem.priority = -1;

        inputSystem = new InputSystem();
        inputSystem.priority = 0;

        terrainSystem = new TerrainSystem();
        terrainSystem.priority = 1;

        movementSystem = new MovementSystem();
        movementSystem.priority = 2;

        physicsSystem = new PhysicsSystem(world);
        physicsSystem.priority = 3;

        lapSectorSystem = new LapSectorSystem(trackGenerator, gameClient);
        lapSectorSystem.priority = 4; // After physics, before NetworkSyncSystem's display work

        skidmarkSystem = new SkidmarkSystem(camera);
        skidmarkSystem.priority = 4;

        renderSystem = new RenderSystem(camera, world, physicsSystem);
        renderSystem.priority = 5;

        carDebugRenderSystem = new CarDebugRenderSystem(camera);
        carDebugRenderSystem.priority = 6;

        engine.addSystem(raceStateSystem);
        engine.addSystem(inputSystem);
        engine.addSystem(terrainSystem);
        engine.addSystem(movementSystem);
        engine.addSystem(physicsSystem);
        engine.addSystem(lapSectorSystem);

        if (isMultiplayerMode) {
            NetworkSyncSystem syncSystem = new NetworkSyncSystem();
            syncSystem.priority = 4;
            engine.addSystem(syncSystem);
        }

        engine.addSystem(skidmarkSystem);
        engine.addSystem(renderSystem);
        engine.addSystem(carDebugRenderSystem);

        // --- Setup UI ---
        uiFont = new BitmapFont();
        uiSkin = createProceduralSkin();
        setupEditorUI();
        setupLoadingUI();
        setupPlayUI();

        // --- Initial State ---
        if (!isMultiplayerMode) {
            transitionToEditing();
        } else {
            transitionToLoading();
            
            // --- Handle incoming map data (multiplayer only) ---
            if (gameClient != null) {
                gameClient.setOnMapReceived(jsonStr -> {
                    Gdx.app.log("GameScreen", "Building track from received JSON...");
                    Json json = new Json();
                    TrackData data = json.fromJson(TrackData.class, jsonStr);
                    trackGenerator.importData(data);
                    
                    Gdx.app.log("GameScreen", "Track built. Sending MapReady.");
                    loadingLabel.setText("Map Ready! Waiting for server...");
                    gameClient.sendReliable(new MapReadyPacket());
                });

                gameClient.setOnStartGame(() -> {
                    Gdx.app.log("GameScreen", "Server signaled game start!");
                    transitionToPlaying();
                });

                gameClient.onPlayerPositionReceived = packet -> {
                    if (packet.playerId == gameClient.localPlayerId) return; // Ignore our own packets

                    Entity remoteCar = remotePlayers.get(packet.playerId);
                    if (remoteCar == null) {
                        // Spawn new remote car (with LapComponent for leaderboard sync)
                        remoteCar = carFactory.createRemoteCar(packet.x, packet.y, packet.angle);
                        remoteCar.add(new LapComponent());
                        remotePlayers.put(packet.playerId, remoteCar);
                    }

                    NetworkSyncComponent sync =
                        remoteCar.getComponent(NetworkSyncComponent.class);
                    if (sync != null) {
                        NetworkSyncComponent.Snapshot snap = new NetworkSyncComponent.Snapshot();
                        snap.timestamp = System.currentTimeMillis();
                        snap.position.set(packet.x, packet.y);
                        snap.angle = packet.angle;
                        snap.velocity.set(packet.velocityX, packet.velocityY);

                        // Approximate angular velocity if not provided in packet
                        snap.angularVelocity = 0;
                        if (!sync.snapshots.isEmpty()) {
                            NetworkSyncComponent.Snapshot last = sync.snapshots.get(sync.snapshots.size() - 1);
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
                    Entity remoteCar = remotePlayers.remove(id);
                    if (remoteCar != null) {
                        PhysicsComponent phys =
                            remoteCar.getComponent(PhysicsComponent.class);
                        if (phys != null && phys.body != null) {
                            world.destroyBody(phys.body);
                        }
                        engine.removeEntity(remoteCar);
                        Gdx.app.log("GameScreen", "Remote player removed: " + id);
                    }
                };

                wireRaceNetworkCallbacks();
            }

            // --- Create boundary walls ---
            createBoundaryWalls();
        }
    }

    // ─── State Transitions ─────────────────────────────────────────────

    private void transitionToEditing() {
        currentState = GameState.EDITING;
        
        // Disable driving systems
        raceStateSystem.setProcessing(false);
        lapSectorSystem.setProcessing(false);
        inputSystem.setProcessing(false);
        terrainSystem.setProcessing(false);
        movementSystem.setProcessing(false);
        physicsSystem.setProcessing(false);

        // Remove race entity
        if (raceEntity != null) {
            engine.removeEntity(raceEntity);
            raceEntity = null;
        }

        // Remove player car
        if (playerCar != null) {
            PhysicsComponent phys = playerCar.getComponent(PhysicsComponent.class);
            if (phys != null && phys.body != null) {
                world.destroyBody(phys.body);
            }
            engine.removeEntity(playerCar);
            playerCar = null;
        }

        setupEditorCamera();
        
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(editorStage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT && currentState == GameState.EDITING) {
                    Vector3 worldCoords = camera.unproject(new Vector3(screenX, screenY, 0));
                    trackGenerator.addPoint(worldCoords.x, worldCoords.y);
                    updatePointCountLabel();
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void transitionToLoading() {
        currentState = GameState.LOADING;
        
        // Disable driving systems
        raceStateSystem.setProcessing(false);
        lapSectorSystem.setProcessing(false);
        inputSystem.setProcessing(false);
        terrainSystem.setProcessing(false);
        movementSystem.setProcessing(false);
        physicsSystem.setProcessing(false);

        loadingLabel.setText("Connecting & Downloading Map...");
        Gdx.input.setInputProcessor(loadingStage);
    }

    private void transitionToPlaying() {
        if (trackGenerator.getManualPoints().size < 4 && !isMultiplayerMode) {
            Gdx.app.log("Game", "Cannot play: Need at least 4 points to form a track.");
            return;
        }

        currentState = GameState.PLAYING;
        
        // Enable driving systems
        raceStateSystem.setProcessing(true);
        lapSectorSystem.setProcessing(true);
        inputSystem.setProcessing(true);
        terrainSystem.setProcessing(true);
        movementSystem.setProcessing(true);
        physicsSystem.setProcessing(true);

        // Setup driving camera
        float aspectRatio = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        camera.viewportWidth = VIEWPORT_WIDTH_METERS;
        camera.viewportHeight = VIEWPORT_WIDTH_METERS / aspectRatio;
        camera.update();

        // Spawn player car at start
        Vector2 startPos = trackGenerator.getStartPosition();
        float startAngle = trackGenerator.getStartAngle();
        playerCar = carFactory.createCar(startPos.x, startPos.y, startAngle);

        CarComponent carComp = playerCar.getComponent(CarComponent.class);
        TerrainComponent terrain = new TerrainComponent();
        terrain.trackGenerator = trackGenerator;
        terrain.defaultMaxForwardSpeed = carComp.maxForwardSpeed;
        terrain.defaultEngineForce = carComp.engineForce;
        terrain.defaultRollingResistance = carComp.rollingResistance;
        terrain.defaultAeroDragCoeff = carComp.aerodynamicDragCoeff;
        terrain.defaultLinearDamping = carComp.linearDamping;
        terrain.defaultAngularDamping = carComp.angularDamping;
        playerCar.add(terrain);
        playerCar.add(new LapComponent());

        // Spin up race manager: WAITING_FOR_PLAYERS until the player presses Ready.
        startRaceLobby();

        // Reset Ready UI for a fresh race
        if (readyButton != null) {
            readyButton.setDisabled(false);
            readyButton.setText("READY");
        }

        Gdx.input.setInputProcessor(playStage);
    }

    /**
     * Creates the race manager entity in WAITING_FOR_PLAYERS state.
     * Configures it with track-derived data (total control points, max laps,
     * required players). Transition to COUNTDOWN happens automatically in
     * {@code RaceStateSystem} once {@code readyPlayers >= requiredPlayers}.
     */
    private void startRaceLobby() {
        if (raceEntity != null) {
            engine.removeEntity(raceEntity);
        }
        raceEntity = new Entity();
        RaceComponent race = new RaceComponent();
        race.currentState = RaceState.WAITING_FOR_PLAYERS;
        race.countdownTimer = 3.0f;
        race.raceTimer = 0.0f;
        race.maxLaps = 3;
        race.totalSectors = 3;
        race.totalTrackPoints = trackGenerator.getManualPoints().size;
        race.readyPlayers = 0;
        race.requiredPlayers = 1;
        // Multiplayer: server is authoritative — local FSM only mirrors timers.
        race.serverAuthoritative = isMultiplayerMode;
        raceEntity.add(race);
        engine.addEntity(raceEntity);
    }

    /**
     * Marks the local racer as ready. In single-player this is called
     * immediately after spawn so the countdown starts right away. In
     * multiplayer this would be wired to a "Ready" button / network signal.
     */
    public void markLocalPlayerReady() {
        if (raceEntity == null || playerCar == null) return;
        RaceComponent race = raceEntity.getComponent(RaceComponent.class);
        LapComponent lap = playerCar.getComponent(LapComponent.class);
        if (race == null || lap == null) return;
        if (lap.ready) return;
        lap.ready = true;
        race.readyPlayers++;
        Gdx.app.log("Race", "Local player READY (" + race.readyPlayers
                + "/" + race.requiredPlayers + ")");
    }

    /** Returns the current RaceComponent, or null if no race is active. */
    public RaceComponent getRaceComponent() {
        if (raceEntity == null) return null;
        return raceEntity.getComponent(RaceComponent.class);
    }

    // ─── Multiplayer Race Sync ─────────────────────────────────────────

    /**
     * Wires every race-related packet from {@link GameClient} into local
     * ECS state. All handlers run on the libGDX main thread (GameClient
     * already uses {@code Gdx.app.postRunnable}).
     */
    private void wireRaceNetworkCallbacks() {
        if (gameClient == null) return;

        gameClient.onLobbyStatus = packet -> {
            RaceComponent race = getRaceComponent();
            if (race != null) {
                race.readyPlayers = packet.readyPlayers;
                race.requiredPlayers = packet.totalPlayers;
            }
            if (lobbyStatusLabel != null) {
                lobbyStatusLabel.setText("Players ready: " + packet.readyPlayers
                        + " / " + packet.totalPlayers);
            }
        };

        gameClient.onRaceStateChanged = packet -> {
            RaceComponent race = getRaceComponent();
            if (race == null) return;
            RaceState[] values = RaceState.values();
            if (packet.newStateOrdinal < 0 || packet.newStateOrdinal >= values.length) return;
            race.currentState = values[packet.newStateOrdinal];
            race.countdownTimer = packet.countdownTimer;
            race.raceTimer = packet.raceTimer;
            Gdx.app.log("GameScreen", "Server race state: " + race.currentState);
        };

        gameClient.onSectorTime = packet -> {
            Entity carEntity = findCarEntityByPlayerId(packet.playerId);
            if (carEntity == null) return;
            LapComponent lap = carEntity.getComponent(LapComponent.class);
            if (lap == null) return;

            lap.currentLap = packet.currentLap;
            lap.nextTrackPointIndex = packet.nextTrackPointIndex;
            lap.currentSector = packet.currentSector;
            lap.finished = packet.finished;
            lap.lastRequestedPointIndex = -1; // server confirmed; allow next request

            if (packet.sectorIndex >= 0 && packet.sectorIndex < lap.currentLapSectorTimes.length) {
                lap.currentLapSectorTimes[packet.sectorIndex] = packet.sectorTime;
                lap.bestSectorTimes[packet.sectorIndex] = packet.bestSectorTime;
                lap.lastSectorDelta = packet.delta;
                lap.currentSectorElapsed = 0f;
            }

            RaceComponent race = getRaceComponent();
            if (race != null && packet.raceTimerSnapshot > 0f) {
                race.raceTimer = packet.raceTimerSnapshot;
            }
        };

        gameClient.onRacePositionsUpdate = packet -> {
            if (packet.playerIds == null) return;
            for (int i = 0; i < packet.playerIds.length; i++) {
                int pid = packet.playerIds[i];
                Entity carEntity = findCarEntityByPlayerId(pid);
                if (carEntity == null) continue;
                LapComponent lap = carEntity.getComponent(LapComponent.class);
                if (lap == null) continue;
                lap.racePosition = packet.positions[i];
                lap.currentLap = packet.currentLaps[i];
                // We DON'T overwrite nextTrackPointIndex here for the local
                // player — that field is owned by SectorTimePacket round-trips.
                if (pid != gameClient.localPlayerId) {
                    lap.nextTrackPointIndex = packet.nextTrackPointIndices[i];
                }
            }
        };
    }

    /** Resolves a player id to the local-player car or one of the remote-player cars. */
    private Entity findCarEntityByPlayerId(int playerId) {
        if (gameClient != null && playerId == gameClient.localPlayerId) {
            return playerCar;
        }
        return remotePlayers.get(playerId);
    }

    // ─── Editor Mode Setup ─────────────────────────────────────────────

    /**
     * Configures the camera to show the entire grid from above.
     * Zoom level is dynamically computed from grid dimensions.
     */
    private void setupEditorCamera() {
        float worldWidth = trackGenerator.getGridWidth() * trackGenerator.getTileSize();
        float worldHeight = trackGenerator.getGridHeight() * trackGenerator.getTileSize();
        float aspectRatio = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();

        // Fit the larger dimension with 5% padding
        float neededWidth = worldWidth * 1.05f;
        float neededHeight = worldHeight * 1.05f;

        if (neededWidth / aspectRatio > neededHeight) {
            camera.viewportWidth = neededWidth;
            camera.viewportHeight = neededWidth / aspectRatio;
        } else {
            camera.viewportHeight = neededHeight;
            camera.viewportWidth = neededHeight * aspectRatio;
        }

        camera.position.set(0, 0, 0);
        camera.update();
    }

    /**
     * Creates the editor UI (SAVE button, point count label) and sets up
     * InputMultiplexer so Stage buttons take priority over map clicks.
     */
    private Skin createProceduralSkin() {
        Skin skin = new Skin();
        
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));
        
        skin.add("default", uiFont);
        
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.down = skin.newDrawable("white", Color.GRAY);
        textButtonStyle.font = skin.getFont("default");
        skin.add("default", textButtonStyle);
        
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = skin.getFont("default");
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = skin.newDrawable("white", new Color(0.1f, 0.1f, 0.1f, 1f));
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);
        textFieldStyle.selection = skin.newDrawable("white", Color.BLUE);
        skin.add("default", textFieldStyle);

        List.ListStyle listStyle = new List.ListStyle();
        listStyle.font = skin.getFont("default");
        listStyle.fontColorUnselected = Color.WHITE;
        listStyle.fontColorSelected = Color.WHITE;
        listStyle.selection = skin.newDrawable("white", Color.BLUE);
        listStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        skin.add("default", listStyle);

        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = skin.newDrawable("white", new Color(0.1f, 0.1f, 0.1f, 1f));
        skin.add("default", scrollPaneStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = skin.getFont("default");
        labelStyle.fontColor = Color.WHITE;
        skin.add("default", labelStyle);

        return skin;
    }

    private void setupEditorUI() {
        editorStage = new Stage(new ScreenViewport());

        // ─── Grid Size Panel ───
        TextField widthField = new TextField(String.valueOf(trackGenerator.getGridWidth()), uiSkin);
        TextField heightField = new TextField(String.valueOf(trackGenerator.getGridHeight()), uiSkin);
        TextButton setSizeBtn = new TextButton("Set Grid Size", uiSkin);

        setSizeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                try {
                    int w = Integer.parseInt(widthField.getText());
                    int h = Integer.parseInt(heightField.getText());
                    trackGenerator.setGridSize(w, h);
                    setupEditorCamera();
                    Gdx.app.log("Editor", "Grid resized to " + w + "x" + h);
                } catch (NumberFormatException e) {
                    Gdx.app.log("Editor", "Invalid grid size");
                }
            }
        });

        // ─── Save/Load/Play Panel ───
        TextField nameField = new TextField("custom_map.json", uiSkin);
        TextButton saveButton = new TextButton("SAVE", uiSkin);
        TextButton loadButton = new TextButton("LOAD", uiSkin);
        TextButton playButton = new TextButton("PLAY", uiSkin);

        List<String> mapList = new List<>(uiSkin);
        ScrollPane scrollPane = new ScrollPane(mapList, uiSkin);

        Runnable refreshList = () -> {
            FileHandle mapsDir = Gdx.files.local("Maps");
            if (!mapsDir.exists()) mapsDir.mkdirs();
            FileHandle[] files = mapsDir.list(".json");
            Array<String> names = new Array<>();
            for (FileHandle f : files) {
                names.add(f.name());
            }
            mapList.setItems(names);
        };
        refreshList.run();

        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = nameField.getText();
                if (!name.endsWith(".json")) name += ".json";
                trackGenerator.saveMap("Maps/" + name);
                refreshList.run();
            }
        });

        loadButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = mapList.getSelected();
                if (name != null) {
                    trackGenerator.loadMap("Maps/" + name);
                    nameField.setText(name);
                    updatePointCountLabel();
                    setupEditorCamera();
                }
            }
        });

        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                transitionToPlaying();
            }
        });

        TextButton undoButton = new TextButton("UNDO POINT", uiSkin);
        undoButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                trackGenerator.removeLastPoint();
                updatePointCountLabel();
            }
        });

        pointCountLabel = new Label("Points: 0", uiSkin);
        updatePointCountLabel();

        // ─── Layout ───
        Table table = new Table();
        table.setFillParent(true);
        table.top().right();
        table.pad(10f);

        table.add(new Label("Width:", uiSkin)).padRight(5);
        table.add(widthField).width(50).padBottom(5);
        table.add(new Label("Height:", uiSkin)).padLeft(10).padRight(5);
        table.add(heightField).width(50).padBottom(5).row();
        table.add(setSizeBtn).colspan(4).fillX().padBottom(20).row();

        table.add(pointCountLabel).colspan(4).padBottom(5).row();
        table.add(undoButton).colspan(4).fillX().height(35f).padBottom(5).row();
        table.add(playButton).colspan(4).fillX().height(35f).padBottom(20).row();

        table.add(new Label("File Name:", uiSkin)).colspan(4).row();
        table.add(nameField).colspan(4).fillX().padBottom(5).row();
        table.add(saveButton).colspan(2).fillX().padRight(5).height(35f);
        table.add(loadButton).colspan(2).fillX().height(35f).row();
        table.add(scrollPane).colspan(4).fillX().height(150f).padTop(10);

        editorStage.addActor(table);
    }

    private void setupPlayUI() {
        playStage = new Stage(new ScreenViewport());

        // Top-left: editor return button (single-player only)
        Table topLeft = new Table();
        topLeft.setFillParent(true);
        topLeft.top().left();
        topLeft.pad(10f);

        if (!isMultiplayerMode) {
            TextButton editButton = new TextButton("BACK TO EDITOR", uiSkin);
            editButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    transitionToEditing();
                }
            });
            topLeft.add(editButton).width(150f).height(40f);
        }
        playStage.addActor(topLeft);

        // Center-bottom: race lobby panel (Ready button + lobby status)
        lobbyPanel = new Table();
        lobbyPanel.setFillParent(true);
        lobbyPanel.bottom().pad(40f);

        lobbyStatusLabel = new Label("Waiting for players...", uiSkin);
        lobbyStatusLabel.setAlignment(Align.center);

        readyButton = new TextButton("READY", uiSkin);
        readyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onReadyButtonClicked();
            }
        });

        lobbyPanel.add(lobbyStatusLabel).padBottom(10f).row();
        lobbyPanel.add(readyButton).width(180f).height(50f);

        // Hidden by default — toggled in render() based on race state.
        lobbyPanel.setVisible(false);
        playStage.addActor(lobbyPanel);
    }

    /**
     * Ready button click handler. Single-player marks ready locally;
     * multiplayer sends a {@link PlayerReadyPacket} to the server.
     */
    private void onReadyButtonClicked() {
        if (isMultiplayerMode) {
            if (gameClient == null) return;
            LapComponent lap = playerCar != null
                    ? playerCar.getComponent(LapComponent.class) : null;
            boolean newReady = lap == null || !lap.ready;
            if (lap != null) lap.ready = newReady;
            gameClient.sendReliable(new PlayerReadyPacket(newReady));
            readyButton.setText(newReady ? "UN-READY" : "READY");
            Gdx.app.log("GameScreen", "Sent PlayerReadyPacket(" + newReady + ")");
        } else {
            markLocalPlayerReady();
            readyButton.setDisabled(true);
            readyButton.setText("READY!");
        }
    }

    private void setupLoadingUI() {
        loadingStage = new Stage(new ScreenViewport());
        
        loadingLabel = new Label("Loading...", uiSkin);
        loadingLabel.setAlignment(Align.center);
        
        Table table = new Table();
        table.setFillParent(true);
        table.center();
        table.add(loadingLabel).pad(20).row();
        
        // Add a progress-like indicator or just a pulse effect could be added later
        loadingStage.addActor(table);
    }

    private void updatePointCountLabel() {
        if (pointCountLabel != null) {
            int count = trackGenerator.getManualPoints().size;
            pointCountLabel.setText("Points: " + count + (count < 4 ? " (need 4+)" : ""));
        }
    }



    // ─── Render Loop ───────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        // Clear screen — grass green background (matches tile color at grid edges)
        Gdx.gl.glClearColor(0.18f, 0.45f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Toggle lobby panel based on race phase (only meaningful while PLAYING)
        if (currentState == GameState.PLAYING && lobbyPanel != null) {
            RaceComponent race = getRaceComponent();
            boolean showLobby = race != null && race.currentState == RaceState.WAITING_FOR_PLAYERS;
            lobbyPanel.setVisible(showLobby);
        } else if (lobbyPanel != null) {
            lobbyPanel.setVisible(false);
        }

        // Handle ESC to go back to editor
        if (currentState == GameState.PLAYING && !isMultiplayerMode && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            transitionToEditing();
        }

        // Toggle debug draw with F1 (driving mode only)
        if (currentState == GameState.PLAYING && Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            renderSystem.toggleDebugDraw();
        }

        // Toggle Pacejka HUD with F3
        if (currentState == GameState.PLAYING && Gdx.input.isKeyJustPressed(Input.Keys.F3) && playerCar != null) {
            CarComponent car = playerCar.getComponent(CarComponent.class);
            if (car != null) {
                car.debugOverlayEnabled = !car.debugOverlayEnabled;
            }
        }

        // Follow player car with camera (driving mode only)
        if (currentState == GameState.PLAYING) {
            updateCamera();
        }

        // Render track tiles (background layer — before entities)
        trackRenderer.render(camera, trackGenerator);

        // Update all ECS systems (includes entity rendering via RenderSystem)
        engine.update(delta);

        // Render editor overlay on top of everything (control points, spline)
        switch (currentState) {
            case EDITING -> {
                trackRenderer.renderEditorOverlay(camera, trackGenerator);
                editorStage.act(delta);
                editorStage.draw();
            }
            case LOADING -> {
                loadingStage.act(delta);
                loadingStage.draw();
            }
            case PLAYING -> {
                playStage.act(delta);
                playStage.draw();
            }
        }

        // Send local player position to server (multiplayer only)
        if (isMultiplayerMode && gameClient != null && playerCar != null) {
            PhysicsComponent physics =
                playerCar.getComponent(PhysicsComponent.class);
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
            PhysicsComponent physics =
                    playerCar.getComponent(PhysicsComponent.class);
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
        BodyDef wallDef = new BodyDef();
        wallDef.type = BodyDef.BodyType.StaticBody;
        wallDef.position.set(x, y);

        Body wallBody = world.createBody(wallDef);

        PolygonShape wallShape = new PolygonShape();
        wallShape.setAsBox(halfWidth, halfHeight);

        wallBody.createFixture(wallShape, 0f);
        wallShape.dispose();
    }

    @Override
    public void resize(int width, int height) {
        if (currentState == GameState.EDITING) {
            // In editor mode, dynamically fit the entire grid
            setupEditorCamera();
        } else {
            float aspectRatio = (float) width / height;
            camera.viewportWidth = VIEWPORT_WIDTH_METERS;
            camera.viewportHeight = VIEWPORT_WIDTH_METERS / aspectRatio;
            camera.update();
        }

        if (editorStage != null) {
            editorStage.getViewport().update(width, height, true);
        }
        if (playStage != null) {
            playStage.getViewport().update(width, height, true);
        }
        if (carDebugRenderSystem != null) {
            carDebugRenderSystem.resize(width, height);
        }
        if (loadingStage != null) {
            loadingStage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        renderSystem.dispose();
        if (skidmarkSystem != null) skidmarkSystem.dispose();
        if (carDebugRenderSystem != null) carDebugRenderSystem.dispose();
        trackRenderer.dispose();
        world.dispose();

        if (editorStage != null) editorStage.dispose();
        if (loadingStage != null) loadingStage.dispose();
        if (playStage != null) playStage.dispose();
        if (uiFont != null) uiFont.dispose();
    }
}
