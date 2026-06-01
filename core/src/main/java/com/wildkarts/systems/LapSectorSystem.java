package com.wildkarts.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.wildkarts.components.LapComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;
import com.wildkarts.net.GameClient;
import com.wildkarts.net.packets.PlayerPassedPointPacket;
import com.wildkarts.track.TrackGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core race-progress system. Each frame it:
 * <ol>
 *   <li>Increments per-racer sector elapsed time.</li>
 *   <li>Checks proximity from each car to its {@code nextTrackPointIndex}
 *       (sourced from {@link TrackGenerator#getManualPoints()}).</li>
 *   <li>When a racer reaches the next point, advances the index; if that
 *       point is a sector boundary, stamps the sector time, computes the
 *       delta against the racer's personal best, and updates the best.</li>
 *   <li>When a racer crosses the finish line (point 0 again), increments
 *       {@code currentLap}; if {@code currentLap > maxLaps}, the racer is
 *       flagged finished and the race FSM is moved to FINISHED.</li>
 *   <li>Sorts all racers and assigns {@code racePosition} (1 = leader).</li>
 * </ol>
 *
 * Detection is position-based (no Box2D sensors), which keeps the system
 * decoupled from physics and trivial to extend to AI / remote players.
 */
public class LapSectorSystem extends EntitySystem {

    private final ComponentMapper<LapComponent> lapMapper =
            ComponentMapper.getFor(LapComponent.class);
    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<RaceComponent> raceMapper =
            ComponentMapper.getFor(RaceComponent.class);

    private final TrackGenerator trackGenerator;
    private final GameClient gameClient; // null in single-player

    private ImmutableArray<Entity> racers;
    private ImmutableArray<Entity> raceEntities;

    private final List<Entity> sortBuffer = new ArrayList<>();

    /**
     * Ordering: higher lap first; then higher next-point index (further along
     * in the current lap); ties broken by smaller distance to the next point.
     */
    private final Comparator<Entity> raceOrder = (a, b) -> {
        LapComponent la = lapMapper.get(a);
        LapComponent lb = lapMapper.get(b);
        if (la.currentLap != lb.currentLap) {
            return Integer.compare(lb.currentLap, la.currentLap);
        }
        if (la.nextTrackPointIndex != lb.nextTrackPointIndex) {
            return Integer.compare(lb.nextTrackPointIndex, la.nextTrackPointIndex);
        }
        return Float.compare(la.distanceToNextPointSq, lb.distanceToNextPointSq);
    };

    public LapSectorSystem(TrackGenerator trackGenerator) {
        this(trackGenerator, null);
    }

    /**
     * Multiplayer constructor. When {@code gameClient} is non-null, the
     * system only DETECTS proximity and forwards each pass to the server
     * via {@link PlayerPassedPointPacket}; it never advances laps locally.
     * The server replies with {@code SectorTimePacket}, and the client's
     * {@code GameScreen} callback writes the authoritative values back
     * into {@link LapComponent}.
     */
    public LapSectorSystem(TrackGenerator trackGenerator, GameClient gameClient) {
        super();
        this.trackGenerator = trackGenerator;
        this.gameClient = gameClient;
    }

    @Override
    public void addedToEngine(Engine engine) {
        racers = engine.getEntitiesFor(
                Family.all(LapComponent.class, PhysicsComponent.class).get());
        raceEntities = engine.getEntitiesFor(Family.all(RaceComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        RaceComponent race = getRaceComponent();
        if (race == null) return;
        if (race.currentState != RaceState.RACING && race.currentState != RaceState.PRACTICE) return;

        Array<Vector2> points = trackGenerator.getManualPoints();
        int totalPoints = points.size;
        if (totalPoints < 3) return;

        for (Entity racer : racers) {
            updateRacer(racer, race, points, totalPoints, deltaTime);
        }

        // Server is authoritative on positions in multiplayer — skip local sort.
        if (!race.serverAuthoritative) {
            assignPositions();
        }
    }

    // ─── Per-racer Update ────────────────────────────────────────────────

    private void updateRacer(Entity racer, RaceComponent race, Array<Vector2> points,
                             int totalPoints, float deltaTime) {
        LapComponent lap = lapMapper.get(racer);
        if (lap.finished) return;

        PhysicsComponent physics = physicsMapper.get(racer);
        if (physics == null || physics.body == null) return;

        lap.currentSectorElapsed += deltaTime;

        int targetIdx = lap.nextTrackPointIndex;
        if (targetIdx < 0 || targetIdx >= totalPoints) {
            targetIdx = 0;
            lap.nextTrackPointIndex = 0;
        }

        Vector2 carPos = physics.body.getPosition();
        Vector2 nextPoint = points.get(targetIdx);
        float dx = nextPoint.x - carPos.x;
        float dy = nextPoint.y - carPos.y;
        float distSq = dx * dx + dy * dy;
        lap.distanceToNextPointSq = distSq;

        if (!trackGenerator.isWithinCheckpointGate(targetIdx, carPos.x, carPos.y)) return;

        if (race.serverAuthoritative) {
            // Forward to server (debounced — one request per point).
            if (gameClient != null && lap.lastRequestedPointIndex != targetIdx) {
                lap.lastRequestedPointIndex = targetIdx;
                gameClient.sendReliable(new PlayerPassedPointPacket(
                        gameClient.localPlayerId, targetIdx, carPos.x, carPos.y));
            }
        } else {
            if (race.currentState == RaceState.PRACTICE) {
                advancePointPractice(lap, race, targetIdx, totalPoints);
            } else {
                advancePoint(lap, race, targetIdx, totalPoints);
            }
        }
    }

    // ─── Point / Sector / Lap Advancement ────────────────────────────────

    private void advancePointPractice(LapComponent lap, RaceComponent race, int passedIdx, int totalPoints) {
        lap.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, race.totalSectors);

        if (passedIdx == 0) {
            if (completedSectorIdx >= 0) {
                recordSectorTime(lap, completedSectorIdx);
            }

            float lapTime = 0f;
            for (float t : lap.currentLapSectorTimes) {
                lapTime += t;
            }
            if (lapTime > 0f) {
                lap.lastPracticeLapTime = lapTime;
                if (lap.bestPracticeLapTime <= 0f || lapTime < lap.bestPracticeLapTime) {
                    lap.bestPracticeLapTime = lapTime;
                }
                Gdx.app.log("Race", String.format(
                        "Practice lap: %.2fs (best %.2fs)", lapTime, lap.bestPracticeLapTime));
            }

            resetForNextPracticeLap(lap);
            race.raceTimer = 0f;
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(lap, completedSectorIdx);
        }
    }

    private static void resetForNextPracticeLap(LapComponent lap) {
        lap.resetForNextPracticeLap();
    }

    private void advancePoint(LapComponent lap, RaceComponent race, int passedIdx, int totalPoints) {
        // The racer just reached point `passedIdx`. Move target to the next one.
        lap.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, race.totalSectors);

        if (passedIdx == 0) {
            // Finish-line crossing closes the final sector AND completes a lap.
            if (completedSectorIdx >= 0) {
                recordSectorTime(lap, completedSectorIdx);
            }
            lap.currentLap++;
            Gdx.app.log("Race", "Lap completed! Now on lap "
                    + lap.currentLap + "/" + race.maxLaps);

            if (lap.currentLap > race.maxLaps) {
                lap.finished = true;
                if (race.currentState != RaceState.FINISHED) {
                    race.currentState = RaceState.FINISHED;
                }
                Gdx.app.log("Race", String.format(
                        "RACE FINISHED! Total time: %.2fs", race.raceTimer));
            }
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(lap, completedSectorIdx);
        }
    }

    /**
     * Returns the index of the sector that ENDS at the given track point,
     * or -1 if this point is not a sector boundary.
     *
     * <p>For totalSectors=3 and totalPoints=N, the boundaries are:
     * <ul>
     *   <li>passedIdx == N/3   → sector 0 just ended</li>
     *   <li>passedIdx == 2N/3  → sector 1 just ended</li>
     *   <li>passedIdx == 0     → sector (totalSectors-1) just ended (finish line)</li>
     * </ul>
     */
    private int sectorEndingAt(int passedIdx, int totalPoints, int totalSectors) {
        if (totalSectors <= 1) return -1;
        int sectorSize = Math.max(1, totalPoints / totalSectors);
        for (int s = 0; s < totalSectors - 1; s++) {
            int boundary = (s + 1) * sectorSize;
            if (passedIdx == boundary) return s;
        }
        if (passedIdx == 0) return totalSectors - 1;
        return -1;
    }

    private void recordSectorTime(LapComponent lap, int sectorIdx) {
        if (sectorIdx < 0 || sectorIdx >= lap.currentLapSectorTimes.length) return;

        float sectorTime = lap.currentSectorElapsed;
        lap.currentLapSectorTimes[sectorIdx] = sectorTime;

        float prevBest = lap.bestSectorTimes[sectorIdx];
        if (prevBest > 0f) {
            lap.lastSectorDelta = sectorTime - prevBest;
        } else {
            lap.lastSectorDelta = 0f;
        }

        if (prevBest <= 0f || sectorTime < prevBest) {
            lap.bestSectorTimes[sectorIdx] = sectorTime;
        }

        lap.currentSectorElapsed = 0f;
        lap.currentSector = (sectorIdx + 1) % lap.currentLapSectorTimes.length;

        Gdx.app.log("Race", String.format(
                "Sector %d: %.2fs (delta %+.2fs)",
                sectorIdx + 1, sectorTime, lap.lastSectorDelta));
    }

    // ─── Position Assignment ─────────────────────────────────────────────

    private void assignPositions() {
        sortBuffer.clear();
        for (Entity racer : racers) {
            sortBuffer.add(racer);
        }
        sortBuffer.sort(raceOrder);
        for (int i = 0; i < sortBuffer.size(); i++) {
            lapMapper.get(sortBuffer.get(i)).racePosition = i + 1;
        }
    }

    // ─── Utilities ───────────────────────────────────────────────────────

    private RaceComponent getRaceComponent() {
        if (raceEntities == null || raceEntities.size() == 0) return null;
        return raceMapper.get(raceEntities.first());
    }
}
