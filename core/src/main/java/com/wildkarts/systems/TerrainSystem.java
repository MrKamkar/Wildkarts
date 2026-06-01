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
 * Rozpoznaje powierzchnię pod każdym autem co klatkę i zapisuje odpowiednie
 * modyfikatory fizyki (mu, max prędkość, siła silnika, opór toczenia, opór aerodynamiczny)
 * w {@link CarComponent}.
 *
 * <p>Musi działać PRZED {@link MovementSystem}, aby zmiany zostały uwzględnione
 * w tym samym kroku fizyki, w którym auto przekracza granicę kafelka.</p>
 *
 * <p>Balans sterowany polami {@link CarComponent} ({@code grass*Multiplier}, {@code surfaceMu*})
 * — edytowalny w jednym miejscu.</p>
 */
public class TerrainSystem extends IteratingSystem {

    private final ComponentMapper<TerrainComponent> terrainMapper =
            ComponentMapper.getFor(TerrainComponent.class);
    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);

    /** Tworzy system dla encji z terenem, fizyką i komponentem auta. */
    public TerrainSystem() {
        super(Family.all(TerrainComponent.class, PhysicsComponent.class, CarComponent.class).get());
    }

    /**
     * Sprawdza kafelek pod autem i stosuje parametry drogi lub trawy.
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TerrainComponent terrain = terrainMapper.get(entity);
        PhysicsComponent physics = physicsMapper.get(entity);
        CarComponent car = carMapper.get(entity);

        if (physics.body == null || terrain.trackGenerator == null) return;

        Vector2 pos = physics.body.getPosition();
        terrain.currentTile = terrain.trackGenerator.getTileAt(pos.x, pos.y);

        if (terrain.currentTile == TrackGenerator.TILE_ROAD)
            applyRoadSurface(car, physics, terrain);
        else
            applyOffRoadSurface(car, physics, terrain);
    }

    /** Przywraca domyślne parametry fizyki z momentu spawnu (droga). */
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
     * Profil „zatonienia” poza drogą: niższa prędkość i moment obrotowy,
     * wyższe tłumienie liniowe/kątowe Box2D — auto nie może wiecznie jeździć bokiem po trawie.
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
