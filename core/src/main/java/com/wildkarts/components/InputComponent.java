package com.wildkarts.components;

import com.badlogic.ashley.core.Component;

/**
 * Stores normalized player input values.
 * Decoupled from physics — key design for multiplayer readiness.
 * 
 * In single-player: populated by InputSystem from keyboard.
 * In multiplayer: populated by NetworkInputSystem from server packets.
 */
public class InputComponent implements Component {

    /** Forward/backward input. Range: -1 (full reverse) to 1 (full throttle). */
    public float throttle = 0f;

    /** Left/right steering input. Range: -1 (full left) to 1 (full right). */
    public float steering = 0f;

    /** Handbrake / drift trigger. Reduces lateral friction when active. */
    public boolean braking = false;
}
