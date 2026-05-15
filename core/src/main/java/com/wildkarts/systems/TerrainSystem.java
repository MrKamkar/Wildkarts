package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.components.TerrainComponent;
import com.wildkarts.track.TrackGenerator;

/**
 * Checks what terrain tile each entity is standing on and adjusts
 * CarComponent speed parameters accordingly.
 *
 * Must run BEFORE MovementSystem so that speed limits are current
 * when drive force is applied.
 *
 * On GRASS: maxForwardSpeed and driveForce are drastically reduced,
 * simulating off-track penalty.
 * On ROAD: values are restored to defaults.
 */
public class TerrainSystem extends IteratingSystem {

    /** Speed limit on grass (m/s). Normal road speed is ~80. */
    private static final float GRASS_MAX_SPEED = 15f;

    /** Drive force on grass (N). Normal road force is ~60. */
    private static final float GRASS_DRIVE_FORCE = 20f;

    private final ComponentMapper<TerrainComponent> terrainMapper =
            ComponentMapper.getFor(TerrainComponent.class);
    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);

    public TerrainSystem() {
        super(Family.all(TerrainComponent.class, PhysicsComponent.class, CarComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TerrainComponent terrain = terrainMapper.get(entity);
        PhysicsComponent physics = physicsMapper.get(entity);
        CarComponent car = carMapper.get(entity);

        if (physics.body == null || terrain.trackGenerator == null) return;

        // Look up the tile under the car
        Vector2 pos = physics.body.getPosition();
        terrain.currentTile = terrain.trackGenerator.getTileAt(pos.x, pos.y);

        // Adjust speed based on terrain
        if (terrain.currentTile == TrackGenerator.TILE_ROAD) {
            car.maxForwardSpeed = terrain.defaultMaxForwardSpeed;
            car.driveForce = terrain.defaultDriveForce;
        } else {
            car.maxForwardSpeed = GRASS_MAX_SPEED;
            car.driveForce = GRASS_DRIVE_FORCE;
        }
    }
}
