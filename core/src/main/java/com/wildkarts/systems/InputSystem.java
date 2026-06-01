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
 * Mapuje wejście z klawiatury na wartości {@link InputComponent}.
 *
 * <p>Multiplayer: ten system działa TYLKO na lokalnym kliencie.
 * Na serwerze (lub dla zdalnych graczy) {@link InputComponent} wypełniałby
 * system sieciowy z odebranych pakietów.</p>
 */
public class InputSystem extends IteratingSystem {

    private final ComponentMapper<InputComponent> inputMapper =
            ComponentMapper.getFor(InputComponent.class);
    private final ComponentMapper<RaceComponent> raceMapper =
            ComponentMapper.getFor(RaceComponent.class);

    private ImmutableArray<Entity> raceEntities;

    /**
     * Ustawiane przez {@link com.wildkarts.GameScreen} — blokuje sterowanie,
     * gdy otwarte jest menu nakładki.
     */
    public boolean externalInputBlocked = false;

    /** Tworzy system przetwarzający encje z {@link InputComponent}. */
    public InputSystem() {
        super(Family.all(InputComponent.class).get());
    }

    /** Pobiera encję z {@link RaceComponent} do sprawdzania fazy wyścigu. */
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        raceEntities = engine.getEntitiesFor(Family.all(RaceComponent.class).get());
    }

    /** Sprawdza, czy faza wyścigu blokuje sterowanie (poza RACING i PRACTICE). */
    private boolean isRaceInputBlocked() {
        if (raceEntities == null || raceEntities.size() == 0) return false;
        RaceComponent race = raceMapper.get(raceEntities.first());
        return race != null
                && race.currentState != RaceState.RACING
                && race.currentState != RaceState.PRACTICE;
    }

    /**
     * Odczytuje klawisze WASD i spację, zapisując wynik w komponencie wejścia.
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InputComponent input = inputMapper.get(entity);

        if (isRaceInputBlocked() || externalInputBlocked) {
            input.throttle = 0f;
            input.steering = 0f;
            input.braking = false;
            return;
        }

        input.throttle = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W))
            input.throttle += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S))
            input.throttle -= 1f;

        input.steering = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A))
            input.steering += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D))
            input.steering -= 1f;

        input.braking = Gdx.input.isKeyPressed(Input.Keys.SPACE);
    }
}
