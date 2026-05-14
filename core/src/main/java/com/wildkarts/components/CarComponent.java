package com.wildkarts.components;

import com.badlogic.ashley.core.Component;

/**
 * Car tuning parameters — defines the "feel" of driving.
 * All values are easily tweakable for balancing.
 * 
 * Simcade defaults: responsive steering, light drift, satisfying acceleration.
 */
public class CarComponent implements Component {

    // --- Drive parameters ---

    /** Maximum forward speed in m/s. */
    public float maxForwardSpeed = 80f;

    /** Maximum reverse speed in m/s. */
    public float maxBackwardSpeed = 30f;

    /** Forward driving force in Newtons. */
    public float driveForce = 60f;

    /** Braking force multiplier (applied as reverse force). */
    public float brakeForce = 40f;

    // --- Steering parameters ---

    /** Max steering angle (degrees) at very low speed — tight turns. */
    public float maxSteeringAngle = 45f;

    /** Min steering angle (degrees) at max speed — stability at high speed. */
    public float minSteeringAngle = 12f;

    /** How fast the steering angle interpolates (higher = snappier). */
    public float steeringSpeed = 4.5f;

    // --- Friction and drift ---

    /**
     * Lateral friction coefficient. Controls how much side-slip is canceled.
     * 1.0 = no drift (perfect grip), 0.0 = ice (full slide).
     * Simcade sweet spot: 0.85–0.95
     */
    public float lateralFriction = 0.92f;

    /**
     * Maximum lateral impulse before tires "break loose".
     * Lower value = easier to start drifting.
     * Acts as a clamp on the lateral correction impulse.
     */
    public float maxLateralImpulse = 20.0f;

    /** Linear damping — forward drag. Car slows down when not accelerating. */
    public float linearDamping = 2.0f;

    /** Angular damping — rotation drag. Prevents indefinite spinning. */
    public float angularDamping = 6.0f;

    // --- Runtime state (used for interpolation / multiplayer snapshots) ---

    /** Current interpolated steering angle in degrees. */
    public float currentSteeringAngle = 0f;
}
