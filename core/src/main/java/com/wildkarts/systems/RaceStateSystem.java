package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;

/**
 * Steruje maszyną stanów wyścigu:
 * <pre>
 *   WAITING_FOR_PLAYERS  --(wszyscy gotowi)-->  COUNTDOWN
 *   PRACTICE             --(wszyscy gotowi)-->  COUNTDOWN
 *   COUNTDOWN            --(timer == 0)-->       RACING
 *   RACING               --(lap > max)-->       FINISHED   (ustawiane przez LapSectorSystem)
 * </pre>
 * Przetwarza pojedynczą encję z {@link RaceComponent}.
 */
public class RaceStateSystem extends IteratingSystem {

    private final ComponentMapper<RaceComponent> raceMapper =
            ComponentMapper.getFor(RaceComponent.class);

    /** Tworzy system dla encji menedżera wyścigu. */
    public RaceStateSystem() {
        super(Family.all(RaceComponent.class).get());
    }

    /**
     * Aktualizuje fazę wyścigu i timery (lokalnie lub jako lustro serwera).
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        RaceComponent race = raceMapper.get(entity);

        if (race.serverAuthoritative) {
            tickAuthoritativeMirror(race, deltaTime);
            return;
        }

        switch (race.currentState) {
            case WAITING_FOR_PLAYERS:
            case PRACTICE:
                if (race.requiredPlayers > 0 && race.readyPlayers >= race.requiredPlayers) {
                    race.currentState = RaceState.COUNTDOWN;
                    race.countdownTimer = 3.0f;
                    race.raceTimer = 0f;
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
                break;
        }
    }

    /**
     * W trybie autorytetu serwera tylko płynnie odlicza timery między pakietami.
     * Przejścia stanów pochodzą wyłącznie z {@link com.wildkarts.net.packets.RaceStateChangedPacket}.
     */
    private void tickAuthoritativeMirror(RaceComponent race, float deltaTime) {
        switch (race.currentState) {
            case PRACTICE:
                break;
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
