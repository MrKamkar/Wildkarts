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
 * Resolves the surface under each car each frame and writes the appropriate
 * physics modifiers (mu, max speed, engine force, rolling resistance, aero drag)
 * onto its CarComponent.
 *
 * Must run BEFORE MovementSystem so the modified values are picked up the
 * same physics step the car crosses a tile boundary.
 *
 * All tuning is driven by fields on CarComponent ({@code grass*Multiplier},
 * {@code surfaceMu*}) so the balance is editable in one place.
 */
public class TerrainSystem extends IteratingSystem {

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

        Vector2 pos = physics.body.getPosition();
        terrain.currentTile = terrain.trackGenerator.getTileAt(pos.x, pos.y);

        if (terrain.currentTile == TrackGenerator.TILE_ROAD) {
            applyRoadSurface(car, physics, terrain);
        } else {
            applyOffRoadSurface(car, physics, terrain);
        }
    }

    /** Restores all physics knobs to their spawn-time defaults. */
    private static void applyRoadSurface(CarComponent car, PhysicsComponent physics, TerrainComponent terrain) {
        car.maxForwardSpeed = terrain.defaultMaxForwardSpeed;
        car.engineForce = terrain.defaultEngineForce;
        car.rollingResistance = terrain.defaultRollingResistance;
        car.aerodynamicDragCoeff = terrain.defaultAeroDragCoeff;
        car.surfaceMu = car.surfaceMuRoad;
        physics.body.setLinearDamping(terrain.defaultLinearDamping);
        physics.body.setAngularDamping(terrain.defaultAngularDamping);
    }

    /**
     * Soft "bog down" profile: reduced top speed and torque so the wheels don't just spin in place,
     * higher rolling/aero/Box2D damping for smooth bleed-off, much higher angular damping so the
     * car can't just keep spinning sideways forever once it leaves the track.
     */
    private static void applyOffRoadSurface(CarComponent car, PhysicsComponent physics, TerrainComponent terrain) {
        car.maxForwardSpeed = terrain.defaultMaxForwardSpeed * car.grassMaxSpeedMultiplier;
        car.engineForce = terrain.defaultEngineForce * car.grassEngineMultiplier;
        car.rollingResistance = terrain.defaultRollingResistance * car.grassRollingResistanceMultiplier;
        car.aerodynamicDragCoeff = terrain.defaultAeroDragCoeff + car.grassAeroDragBonus;
        car.surfaceMu = car.surfaceMuGrass;
        physics.body.setLinearDamping(terrain.defaultLinearDamping * car.grassLinearDampingMultiplier);
        physics.body.setAngularDamping(terrain.defaultAngularDamping * car.grassAngularDampingMultiplier);
    }
}
