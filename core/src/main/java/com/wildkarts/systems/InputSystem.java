package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.wildkarts.components.InputComponent;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;

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
    private final ComponentMapper<RaceComponent> raceMapper =
            ComponentMapper.getFor(RaceComponent.class);

    private ImmutableArray<Entity> raceEntities;

    /** Set by GameScreen to block all kart input when an overlay menu is open. */
    public boolean externalInputBlocked = false;

    public InputSystem() {
        super(Family.all(InputComponent.class).get());
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        raceEntities = engine.getEntitiesFor(Family.all(RaceComponent.class).get());
    }

    private boolean isRaceInputBlocked() {
        if (raceEntities == null || raceEntities.size() == 0) return false;
        RaceComponent race = raceMapper.get(raceEntities.first());
        return race != null
                && race.currentState != RaceState.RACING
                && race.currentState != RaceState.PRACTICE;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InputComponent input = inputMapper.get(entity);

        if (isRaceInputBlocked() || externalInputBlocked) {
            input.throttle = 0f;
            input.steering = 0f;
            input.braking = false;
            return;
        }

        // Throttle: W = forward, S = reverse
        input.throttle = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            input.throttle += 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            input.throttle -= 1f;
        }

        // Steering: A = left (positive CCW), D = right (negative CW)
        input.steering = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            input.steering += 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            input.steering -= 1f;
        }

        // Handbrake / drift: Space
        input.braking = Gdx.input.isKeyPressed(Input.Keys.SPACE);
    }
}
