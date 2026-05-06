package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.wildkarts.components.CarComponent;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.PhysicsComponent;

/**
 * Core driving system — translates InputComponent values into Box2D forces.
 * 
 * Physics model based on iforce2d's top-down car tutorial:
 * 1. Cancel lateral velocity (with drift threshold)
 * 2. Apply forward drag
 * 3. Calculate speed-dependent steering angle
 * 4. Apply drive force
 * 
 * This system does NOT call world.step() — that's PhysicsSystem's job.
 */
public class MovementSystem extends IteratingSystem {

    private final ComponentMapper<InputComponent> inputMapper =
            ComponentMapper.getFor(InputComponent.class);
    private final ComponentMapper<CarComponent> carMapper =
            ComponentMapper.getFor(CarComponent.class);
    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);

    // Reusable vectors to avoid GC pressure
    private final Vector2 forwardDir = new Vector2();
    private final Vector2 lateralDir = new Vector2();
    private final Vector2 lateralVelocity = new Vector2();
    private final Vector2 impulse = new Vector2();
    private final Vector2 forceVec = new Vector2();
    private final Vector2 currentVelocity = new Vector2();

    public MovementSystem() {
        super(Family.all(InputComponent.class, CarComponent.class, PhysicsComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InputComponent input = inputMapper.get(entity);
        CarComponent car = carMapper.get(entity);
        PhysicsComponent physics = physicsMapper.get(entity);
        Body body = physics.body;

        if (body == null) return;

        float angle = body.getAngle();

        // --- Calculate local direction vectors ---
        // Forward = along the body's "up" direction (Y-axis in local space)
        forwardDir.set(MathUtils.cos(angle + MathUtils.HALF_PI),
                       MathUtils.sin(angle + MathUtils.HALF_PI));
        // Lateral = perpendicular to forward (X-axis in local space)
        lateralDir.set(MathUtils.cos(angle), MathUtils.sin(angle));

        // Current velocity
        currentVelocity.set(body.getLinearVelocity());

        // Forward speed (positive = moving forward, negative = moving backward)
        float forwardSpeed = currentVelocity.dot(forwardDir);

        // --- 1. LATERAL FRICTION (drift control) ---
        applyLateralFriction(body, car, input.braking);

        // --- 2. FORWARD DRAG ---
        applyForwardDrag(body, forwardSpeed, car);

        // --- 3. STEERING ---
        applySteering(body, car, input.steering, forwardSpeed, deltaTime);

        // --- 4. DRIVE FORCE ---
        applyDriveForce(body, car, input.throttle, forwardSpeed);
    }

    /**
     * Cancels lateral (sideways) velocity to prevent sliding.
     * The impulse is clamped to maxLateralImpulse — exceeding this = drift!
     * Braking reduces lateral friction for easier drifting.
     */
    private void applyLateralFriction(Body body, CarComponent car, boolean braking) {
        float lateralSpeed = body.getLinearVelocity().dot(lateralDir);
        lateralVelocity.set(lateralDir).scl(lateralSpeed);

        // Impulse to fully cancel lateral velocity
        impulse.set(lateralVelocity).scl(-body.getMass());

        // Apply friction coefficient
        float friction = braking ? car.lateralFriction * 0.5f : car.lateralFriction;
        impulse.scl(friction);

        // Clamp impulse magnitude — this is what creates drift
        float impulseMagnitude = impulse.len();
        if (impulseMagnitude > car.maxLateralImpulse) {
            impulse.scl(car.maxLateralImpulse / impulseMagnitude);
        }

        body.applyLinearImpulse(impulse, body.getWorldCenter(), true);
    }

    /**
     * Applies subtle forward drag to naturally slow the car.
     * This works alongside Box2D's linearDamping for a natural feel.
     */
    private void applyForwardDrag(Body body, float forwardSpeed, CarComponent car) {
        float dragMagnitude = -forwardSpeed * 0.5f; // Subtle extra drag
        forceVec.set(forwardDir).scl(dragMagnitude);
        body.applyForceToCenter(forceVec, true);
    }

    /**
     * Applies steering rotation based on current speed.
     * At low speed: wide steering angle for tight turns.
     * At high speed: narrow angle for stability.
     * The angle interpolates smoothly for natural feel.
     */
    private void applySteering(Body body, CarComponent car, float steeringInput,
                               float forwardSpeed, float deltaTime) {
        float absSpeed = Math.abs(forwardSpeed);
        float maxSpeed = car.maxForwardSpeed;

        // Interpolate steering angle based on speed ratio
        float speedFraction = MathUtils.clamp(absSpeed / maxSpeed, 0f, 1f);
        float targetAngleDeg = MathUtils.lerp(car.maxSteeringAngle, car.minSteeringAngle, speedFraction);

        // Smooth steering interpolation
        float desiredAngle = steeringInput * targetAngleDeg;
        car.currentSteeringAngle = MathUtils.lerp(car.currentSteeringAngle, desiredAngle,
                car.steeringSpeed * deltaTime);

        // Only apply steering when moving (prevents spinning in place)
        if (absSpeed > 0.5f) {
            float angularVelocity = car.currentSteeringAngle * MathUtils.degreesToRadians;
            // Reverse steering direction when moving backward
            if (forwardSpeed < 0) {
                angularVelocity = -angularVelocity;
            }
            body.setAngularVelocity(angularVelocity * (absSpeed / maxSpeed + 0.3f) * 3f);
        } else {
            body.setAngularVelocity(0);
        }
    }

    /**
     * Applies forward/backward drive force based on throttle input.
     * Respects max speed limits in both directions.
     */
    private void applyDriveForce(Body body, CarComponent car, float throttle, float forwardSpeed) {
        if (throttle == 0f) return;

        float force;
        if (throttle > 0f) {
            // Forward — check max speed
            if (forwardSpeed < car.maxForwardSpeed) {
                force = car.driveForce * throttle;
            } else {
                return; // Already at max speed
            }
        } else {
            // Reverse / braking
            if (forwardSpeed > 0.5f) {
                // Moving forward + pressing S = braking (stronger force)
                force = -car.brakeForce;
            } else if (forwardSpeed > -car.maxBackwardSpeed) {
                // Already stopped or reversing — apply reverse drive
                force = car.driveForce * throttle;
            } else {
                return; // At max reverse speed
            }
        }

        forceVec.set(forwardDir).scl(force);
        body.applyForceToCenter(forceVec, true);
    }
}
