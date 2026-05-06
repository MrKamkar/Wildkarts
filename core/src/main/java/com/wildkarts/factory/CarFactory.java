package com.wildkarts.factory;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Factory for creating car entities with all required components and Box2D body.
 * 
 * Centralizes entity creation — easy to extend for different car types,
 * AI opponents, or multiplayer remote players.
 */
public class CarFactory {

    private final World world;
    private final Engine engine;

    public CarFactory(World world, Engine engine) {
        this.world = world;
        this.engine = engine;
    }

    /**
     * Creates a complete car entity at the given position.
     *
     * @param x      Starting X position in world meters
     * @param y      Starting Y position in world meters
     * @param angle  Starting angle in radians
     * @return The created entity (already added to Engine)
     */
    public Entity createCar(float x, float y, float angle) {
        Entity entity = new Entity();

        // --- Components ---
        InputComponent input = new InputComponent();
        CarComponent car = new CarComponent();
        PhysicsComponent physics = new PhysicsComponent();

        // --- Create Box2D body ---
        physics.body = createCarBody(x, y, angle, physics.widthMeters, physics.heightMeters, car);

        // Store entity reference in body's user data (useful for collision callbacks)
        physics.body.setUserData(entity);

        // --- Assemble entity ---
        entity.add(input);
        entity.add(car);
        entity.add(physics);

        engine.addEntity(entity);
        return entity;
    }

    /**
     * Creates a car with custom tuning parameters.
     * Useful for different vehicle classes (light, heavy, drift-focused, etc.)
     */
    public Entity createCar(float x, float y, float angle, CarComponent customCar) {
        Entity entity = new Entity();

        InputComponent input = new InputComponent();
        PhysicsComponent physics = new PhysicsComponent();

        physics.body = createCarBody(x, y, angle, physics.widthMeters, physics.heightMeters, customCar);
        physics.body.setUserData(entity);

        entity.add(input);
        entity.add(customCar);
        entity.add(physics);

        engine.addEntity(entity);
        return entity;
    }

    /**
     * Creates the Box2D dynamic body for a car.
     * 
     * Body configuration:
     * - Dynamic type (responds to forces)
     * - Rectangle shape (PolygonShape)
     * - Linear/angular damping from CarComponent (controls drag)
     * - Moderate density for realistic mass
     * - Low restitution (cars don't bounce much)
     */
    private Body createCarBody(float x, float y, float angle,
                               float width, float height, CarComponent car) {
        // Body definition
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.angle = angle;
        bodyDef.linearDamping = car.linearDamping;
        bodyDef.angularDamping = car.angularDamping;
        bodyDef.bullet = true; // Prevents tunneling at high speeds

        Body body = world.createBody(bodyDef);

        // Shape — rectangle centered on body
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2f, height / 2f);

        // Fixture definition
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1.0f;
        fixtureDef.friction = 0.3f;
        fixtureDef.restitution = 0.1f;

        body.createFixture(fixtureDef);
        shape.dispose();

        return body;
    }
}
