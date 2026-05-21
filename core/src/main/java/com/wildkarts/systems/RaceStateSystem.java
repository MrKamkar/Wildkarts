package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;

/**
 * Drives the race lifecycle finite state machine:
 * <pre>
 *   WAITING_FOR_PLAYERS  --(all ready)-->  COUNTDOWN
 *   COUNTDOWN            --(timer == 0)--> RACING
 *   RACING               --(lap > max)--> FINISHED   (set by LapSectorSystem)
 * </pre>
 * Processes the singleton entity carrying {@link RaceComponent}.
 */
public class RaceStateSystem extends IteratingSystem {

    private final ComponentMapper<RaceComponent> raceMapper =
            ComponentMapper.getFor(RaceComponent.class);

    public RaceStateSystem() {
        super(Family.all(RaceComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        RaceComponent race = raceMapper.get(entity);

        if (race.serverAuthoritative) {
            tickAuthoritativeMirror(race, deltaTime);
            return;
        }

        switch (race.currentState) {
            case WAITING_FOR_PLAYERS:
                if (race.requiredPlayers > 0 && race.readyPlayers >= race.requiredPlayers) {
                    race.currentState = RaceState.COUNTDOWN;
                    race.countdownTimer = 3.0f;
                    Gdx.app.log("Race", "All players ready — countdown!");
                }
                break;

            case COUNTDOWN:
                race.countdownTimer -= deltaTime;
                if (race.countdownTimer <= 0f) {
                    race.countdownTimer = 0f;
                    race.currentState = RaceState.RACING;
                    Gdx.app.log("Race", "GO!");
                }
                break;

            case RACING:
                race.raceTimer += deltaTime;
                break;

            default:
                // FINISHED — no automatic transitions out
                break;
        }
    }

    /**
     * In server-authoritative (multiplayer) mode we only tick timers so the
     * HUD shows smooth values between server packets. State transitions are
     * driven exclusively by RaceStateChangedPacket.
     */
    private void tickAuthoritativeMirror(RaceComponent race, float deltaTime) {
        switch (race.currentState) {
            case COUNTDOWN:
                race.countdownTimer = Math.max(0f, race.countdownTimer - deltaTime);
                break;
            case RACING:
                race.raceTimer += deltaTime;
                break;
            default:
                break;
        }
    }
}
