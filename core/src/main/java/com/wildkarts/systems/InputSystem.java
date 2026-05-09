package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.wildkarts.components.InputComponent;

/**
 * Maps keyboard input to InputComponent values.
 * 
 * Multiplayer note: In a networked game, this system runs ONLY on the local client.
 * On the server (or for remote players), a NetworkInputSystem would populate
 * InputComponent from received network packets instead.
 */
public class InputSystem extends IteratingSystem {

    private final ComponentMapper<InputComponent> inputMapper =
            ComponentMapper.getFor(InputComponent.class);

    public InputSystem() {
        super(Family.all(InputComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InputComponent input = inputMapper.get(entity);

        // Throttle: W = forward, S = reverse
        input.throttle = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            input.throttle += 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            input.throttle -= 1f;
        }

        // Steering: A = left, D = right
        input.steering = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            input.steering -= 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            input.steering += 1f;
        }

        // Handbrake / drift: Space
        input.braking = Gdx.input.isKeyPressed(Input.Keys.SPACE);
    }
}
