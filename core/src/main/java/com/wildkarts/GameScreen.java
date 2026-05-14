package com.wildkarts;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.wildkarts.factory.CarFactory;
import com.wildkarts.systems.InputSystem;
import com.wildkarts.systems.MovementSystem;
import com.wildkarts.systems.PhysicsSystem;
import com.wildkarts.systems.RenderSystem;

/**
 * Main game screen — initializes Box2D world, Ashley engine, and game entities.
 * 
 * System execution order:
 * 1. InputSystem      — reads keyboard → InputComponent
 * 2. MovementSystem   — InputComponent → Box2D forces
 * 3. PhysicsSystem    — world.step() with fixed timestep
 * 4. RenderSystem     — draws entities
 * 
 * This ordering ensures input is processed before physics, and rendering
 * happens after physics has settled — critical for deterministic behavior.
 */
import java.util.HashMap;
import java.util.Map;
import com.wildkarts.systems.NetworkSyncSystem;
import com.wildkarts.net.packets.PlayerPositionPacket;

public class GameScreen extends ScreenAdapter {

    private final WildKartsGame game;
    private final com.wildkarts.net.GameClient gameClient;
    
    // Map to track remote players
    private final Map<Integer, com.badlogic.ashley.core.Entity> remotePlayers = new HashMap<>();

    public GameScreen(WildKartsGame game) {
        this.game = game;
        this.gameClient = game.getGameClient();
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

    /** Pixels-per-meter ratio for camera. */
    private static final float VIEWPORT_WIDTH_METERS = 40f;

    @Override
    public void show() {
        // --- Camera setup ---
        float aspectRatio = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        camera = new OrthographicCamera(VIEWPORT_WIDTH_METERS, VIEWPORT_WIDTH_METERS / aspectRatio);
        camera.position.set(0, 0, 0);
        camera.update();

        // --- Box2D world (no gravity — top-down!) ---
        world = new World(new Vector2(0, 0), true);

        // --- Ashley ECS engine ---
        engine = new Engine();

        // --- Register systems in execution order ---
        // Priority values define order: lower = runs first
        InputSystem inputSystem = new InputSystem();
        inputSystem.priority = 0;

        MovementSystem movementSystem = new MovementSystem();
        movementSystem.priority = 1;

        physicsSystem = new PhysicsSystem(world);
        physicsSystem.priority = 2;

        NetworkSyncSystem syncSystem = new NetworkSyncSystem();
        syncSystem.priority = 3;

        renderSystem = new RenderSystem(camera, world, physicsSystem);
        renderSystem.priority = 4;

        engine.addSystem(inputSystem);
        engine.addSystem(movementSystem);
        engine.addSystem(physicsSystem);
        engine.addSystem(syncSystem);
        engine.addSystem(renderSystem);

        // --- Create player car entity ---
        CarFactory carFactory = new CarFactory(world, engine);
        playerCar = carFactory.createCar(0, 0, 0);

        // --- Handle incoming remote player positions ---
        if (gameClient != null) {
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

        // --- Create some boundary walls for testing ---
        createBoundaryWalls();
    }

    @Override
    public void render(float delta) {
        // Clear screen — dark asphalt color
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.18f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Toggle debug draw with F1
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            renderSystem.toggleDebugDraw();
        }

        // Follow player car with camera
        updateCamera();

        // Update all ECS systems
        engine.update(delta);

        // Send local player position to server
        if (gameClient != null && playerCar != null) {
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
    }

    @Override
    public void dispose() {
        renderSystem.dispose();
        world.dispose();
    }
}
