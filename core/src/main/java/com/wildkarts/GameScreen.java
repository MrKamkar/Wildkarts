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
public class GameScreen extends ScreenAdapter {

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

        renderSystem = new RenderSystem(camera, world);
        renderSystem.priority = 3;

        engine.addSystem(inputSystem);
        engine.addSystem(movementSystem);
        engine.addSystem(physicsSystem);
        engine.addSystem(renderSystem);

        // --- Create player car entity ---
        CarFactory carFactory = new CarFactory(world, engine);
        playerCar = carFactory.createCar(0, 0, 0);

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
