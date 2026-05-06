package com.wildkarts.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Holds a reference to the Box2D body and its dimensions.
 * This component is the bridge between Ashley ECS and Box2D physics world.
 * 
 * For multiplayer snapshots: position, velocity, and angle can be read directly
 * from the Body instance.
 */
public class PhysicsComponent implements Component {

    /** The Box2D dynamic body representing this entity in the physics world. */
    public Body body;

    /** Width of the physics shape in meters (Box2D units). */
    public float widthMeters = 1.0f;

    /** Height (length) of the physics shape in meters (Box2D units). */
    public float heightMeters = 2.0f;
}
