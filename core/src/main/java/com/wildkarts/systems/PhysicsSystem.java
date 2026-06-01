package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.physics.box2d.World;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Box2D ze stałym krokiem czasu i nakładaniem sił pojazdu w każdym podkroku.
 */
public class PhysicsSystem extends EntitySystem {

    /** Stały krok symulacji fizyki (1/60 s). */
    public static final float FIXED_TIME_STEP = 1f / 60f;

    private static final float MAX_FRAME_TIME = 0.25f;
    private static final int VELOCITY_ITERATIONS = 8;
    private static final int POSITION_ITERATIONS = 3;

    private final ComponentMapper<CarComponent> carMapper = ComponentMapper.getFor(CarComponent.class);
    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<InputComponent> inputMapper =
            ComponentMapper.getFor(InputComponent.class);

    private final World world;
    private float accumulator = 0f;
    private ImmutableArray<Entity> physicsEntities;
    private ImmutableArray<Entity> drivenCars;

    /**
     * Tworzy system fizyki powiązany ze światem Box2D.
     *
     * @param world świat Box2D
     */
    public PhysicsSystem(World world) {
        super();
        this.world = world;
    }

    /**
     * Buforuje listy encji z komponentami fizyki i sterowanych aut.
     */
    @Override
    public void addedToEngine(Engine engine) {
        physicsEntities = engine.getEntitiesFor(Family.all(PhysicsComponent.class).get());
        drivenCars = engine.getEntitiesFor(
                Family.all(InputComponent.class, CarComponent.class, PhysicsComponent.class).get());
    }

    /**
     * Wykonuje stałokrokową symulację Box2D z akumulatorem czasu klatki.
     */
    @Override
    public void update(float deltaTime) {
        float frameTime = Math.min(deltaTime, MAX_FRAME_TIME);
        accumulator += frameTime;

        while (accumulator >= FIXED_TIME_STEP) {
            snapshotPreviousTransforms();
            simulateDrivenVehicles();
            world.step(FIXED_TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            accumulator -= FIXED_TIME_STEP;
        }
    }

    /** Zapisuje poprzednią pozycję i kąt przed krokiem fizyki (do interpolacji renderu). */
    private void snapshotPreviousTransforms() {
        if (physicsEntities == null) return;
        for (Entity entity : physicsEntities) {
            PhysicsComponent phys = physicsMapper.get(entity);
            if (phys != null && phys.body != null) {
                phys.prevPosition.set(phys.body.getPosition());
                phys.prevAngle = phys.body.getAngle();
            }
        }
    }

    /** Symuluje jeden podkrok ruchu dla aut sterowanych przez gracza. */
    private void simulateDrivenVehicles() {
        if (drivenCars == null) return;
        for (Entity entity : drivenCars) {
            CarComponent car = carMapper.get(entity);
            PhysicsComponent physics = physicsMapper.get(entity);
            InputComponent input = inputMapper.get(entity);
            if (car == null || physics == null || physics.body == null || input == null) continue;
            MovementSystem.simulatePhysicsStep(physics.body, car, input, physics);
        }
    }

    /**
     * Zwraca świat Box2D używany przez ten system.
     *
     * @return instancja {@link World}
     */
    public World getWorld() {
        return world;
    }

    /**
     * Współczynnik interpolacji między poprzednią a bieżącą pozycją (0…1).
     *
     * @return alpha do renderowania płynnego ruchu między krokami fizyki
     */
    public float getInterpolationAlpha() {
        return accumulator / FIXED_TIME_STEP;
    }
}
