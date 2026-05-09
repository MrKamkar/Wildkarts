package com.wildkarts.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;

/**
 * Manages Box2D world stepping with fixed time step (accumulator pattern).
 * 
 * This ensures deterministic physics regardless of frame rate — critical for:
 * - Consistent gameplay across different hardware
 * - Future multiplayer synchronization (server and client run same physics)
 * - Reproducible physics behavior
 */
public class PhysicsSystem extends EntitySystem {

    /** Fixed physics step — 60 Hz. */
    private static final float FIXED_TIME_STEP = 1 / 60f;

    /** Max accumulated time to prevent spiral of death on lag spikes. */
    private static final float MAX_FRAME_TIME = 0.25f;

    /** Box2D velocity solver iterations — higher = more accurate but slower. */
    private static final int VELOCITY_ITERATIONS = 8;

    /** Box2D position solver iterations. */
    private static final int POSITION_ITERATIONS = 3;

    private final World world;
    private float accumulator = 0f;

    public PhysicsSystem(World world) {
        super();
        this.world = world;
    }

    @Override
    public void update(float deltaTime) {
        // Clamp frame time to prevent physics explosion on lag
        float frameTime = Math.min(deltaTime, MAX_FRAME_TIME);
        accumulator += frameTime;

        // Step physics in fixed increments
        while (accumulator >= FIXED_TIME_STEP) {
            world.step(FIXED_TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            accumulator -= FIXED_TIME_STEP;
        }
    }

    /** Returns the Box2D world for external access (e.g., debug rendering). */
    public World getWorld() {
        return world;
    }

    /**
     * Returns the interpolation alpha for rendering between physics steps.
     * Useful for smooth rendering when frame rate differs from physics rate.
     * 
     * Future use: interpolation system for multiplayer state smoothing.
     */
    public float getInterpolationAlpha() {
        return accumulator / FIXED_TIME_STEP;
    }
}
