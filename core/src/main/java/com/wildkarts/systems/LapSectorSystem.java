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
 * System postępu wyścigu — śledzi sektory, okrążenia i pozycje kierowców.
 *
 * <p>Każda klatka: nalicza czas sektora, sprawdza bliskość punktów kontrolnych,
 * rejestruje minięcie sektorów/okrążeń i sortuje pozycje wyścigowe.</p>
 */
public class LapSectorSystem extends EntitySystem {

    private final ComponentMapper<LapComponent> lapMapper =
            ComponentMapper.getFor(LapComponent.class);
    private final ComponentMapper<PhysicsComponent> physicsMapper =
            ComponentMapper.getFor(PhysicsComponent.class);
    private final ComponentMapper<RaceComponent> raceMapper =
            ComponentMapper.getFor(RaceComponent.class);

    private final TrackGenerator trackGenerator;
    private final GameClient gameClient;

    private ImmutableArray<Entity> racers;
    private ImmutableArray<Entity> raceEntities;

    private final List<Entity> sortBuffer = new ArrayList<>();

    private final Comparator<Entity> raceOrder = (a, b) -> {
        LapComponent la = lapMapper.get(a);
        LapComponent lb = lapMapper.get(b);
        if (la.currentLap != lb.currentLap)
            return Integer.compare(lb.currentLap, la.currentLap);
        if (la.nextTrackPointIndex != lb.nextTrackPointIndex)
            return Integer.compare(lb.nextTrackPointIndex, la.nextTrackPointIndex);
        return Float.compare(la.distanceToNextPointSq, lb.distanceToNextPointSq);
    };

    /**
     * Tworzy system dla trybu jednoosobowego (bez klienta sieciowego).
     *
     * @param trackGenerator generator toru z punktami kontrolnymi
     */
    public LapSectorSystem(TrackGenerator trackGenerator) {
        this(trackGenerator, null);
    }

    /**
     * Tworzy system z opcjonalnym klientem sieciowym.
     * W trybie wieloosobowym wykrywa minięcia punktów i wysyła je na serwer;
     * serwer odpowiada pakietem SectorTimePacket z autorytatywnymi danymi.
     *
     * @param trackGenerator generator toru
     * @param gameClient     klient sieciowy lub null w trybie solo
     */
    public LapSectorSystem(TrackGenerator trackGenerator, GameClient gameClient) {
        super();
        this.trackGenerator = trackGenerator;
        this.gameClient = gameClient;
    }

    /**
     * Pobiera encje wyścigowe po dodaniu systemu do silnika.
     *
     * @param engine silnik ECS
     */
    @Override
    public void addedToEngine(Engine engine) {
        racers = engine.getEntitiesFor(
                Family.all(LapComponent.class, PhysicsComponent.class).get());
        raceEntities = engine.getEntitiesFor(Family.all(RaceComponent.class).get());
    }

    /**
     * Aktualizuje postęp wszystkich kierowców i przypisuje pozycje (tryb solo).
     *
     * @param deltaTime czas od ostatniej klatki w sekundach
     */
    @Override
    public void update(float deltaTime) {
        RaceComponent race = getRaceComponent();
        if (race == null) return;
        if (race.currentState != RaceState.RACING && race.currentState != RaceState.PRACTICE) return;

        Array<Vector2> points = trackGenerator.getManualPoints();
        int totalPoints = points.size;
        if (totalPoints < 3) return;

        for (Entity racer : racers)
            updateRacer(racer, race, points, totalPoints, deltaTime);

        if (!race.serverAuthoritative)
            assignPositions();
    }

    /**
     * Aktualizuje pojedynczego kierowcę — czas sektora i wykrywanie punktów kontrolnych.
     */
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
        lap.distanceToNextPointSq = dx * dx + dy * dy;

        if (!trackGenerator.isWithinCheckpointGate(targetIdx, carPos.x, carPos.y)) return;

        if (race.serverAuthoritative) {
            if (gameClient != null && lap.lastRequestedPointIndex != targetIdx) {
                lap.lastRequestedPointIndex = targetIdx;
                gameClient.sendReliable(new PlayerPassedPointPacket(
                        gameClient.localPlayerId, targetIdx, carPos.x, carPos.y));
            }
        } else if (race.currentState == RaceState.PRACTICE) {
            advancePointPractice(lap, race, targetIdx, totalPoints);
        } else {
            advancePoint(lap, race, targetIdx, totalPoints);
        }
    }

    /**
     * Rejestruje minięcie punktu w fazie treningu (solo).
     */
    private void advancePointPractice(LapComponent lap, RaceComponent race, int passedIdx, int totalPoints) {
        lap.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, race.totalSectors);

        if (passedIdx == 0) {
            if (completedSectorIdx >= 0)
                recordSectorTime(lap, completedSectorIdx);

            float lapTime = 0f;
            for (float t : lap.currentLapSectorTimes)
                lapTime += t;

            if (lapTime > 0f) {
                lap.lastPracticeLapTime = lapTime;
                if (lap.bestPracticeLapTime <= 0f || lapTime < lap.bestPracticeLapTime)
                    lap.bestPracticeLapTime = lapTime;
                Gdx.app.log("Race", String.format(
                        "Practice lap: %.2fs (best %.2fs)", lapTime, lap.bestPracticeLapTime));
            }

            lap.resetForNextPracticeLap();
            race.raceTimer = 0f;
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(lap, completedSectorIdx);
        }
    }

    /**
     * Rejestruje minięcie punktu w fazie wyścigu (solo).
     */
    private void advancePoint(LapComponent lap, RaceComponent race, int passedIdx, int totalPoints) {
        lap.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, race.totalSectors);

        if (passedIdx == 0) {
            if (completedSectorIdx >= 0)
                recordSectorTime(lap, completedSectorIdx);
            lap.currentLap++;
            Gdx.app.log("Race", "Lap completed! Now on lap "
                    + lap.currentLap + "/" + race.maxLaps);

            if (lap.currentLap > race.maxLaps) {
                lap.finished = true;
                if (race.currentState != RaceState.FINISHED)
                    race.currentState = RaceState.FINISHED;
                Gdx.app.log("Race", String.format(
                        "RACE FINISHED! Total time: %.2fs", race.raceTimer));
            }
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(lap, completedSectorIdx);
        }
    }

    /**
     * Zwraca indeks sektora kończącego się na danym punkcie toru, lub -1.
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

    /**
     * Zapisuje czas ukończonego sektora i oblicza deltę względem rekordu.
     */
    private void recordSectorTime(LapComponent lap, int sectorIdx) {
        if (sectorIdx < 0 || sectorIdx >= lap.currentLapSectorTimes.length) return;

        float sectorTime = lap.currentSectorElapsed;
        lap.currentLapSectorTimes[sectorIdx] = sectorTime;

        float prevBest = lap.bestSectorTimes[sectorIdx];
        if (prevBest > 0f)
            lap.lastSectorDelta = sectorTime - prevBest;
        else
            lap.lastSectorDelta = 0f;

        if (prevBest <= 0f || sectorTime < prevBest)
            lap.bestSectorTimes[sectorIdx] = sectorTime;

        lap.currentSectorElapsed = 0f;
        lap.currentSector = (sectorIdx + 1) % lap.currentLapSectorTimes.length;

        Gdx.app.log("Race", String.format(
                "Sector %d: %.2fs (delta %+.2fs)",
                sectorIdx + 1, sectorTime, lap.lastSectorDelta));
    }

    /**
     * Sortuje kierowców i przypisuje im pozycje wyścigowe 1…N.
     */
    private void assignPositions() {
        sortBuffer.clear();
        for (Entity racer : racers)
            sortBuffer.add(racer);
        sortBuffer.sort(raceOrder);
        for (int i = 0; i < sortBuffer.size(); i++)
            lapMapper.get(sortBuffer.get(i)).racePosition = i + 1;
    }

    /**
     * Zwraca komponent wyścigu z encji menedżera wyścigu.
     */
    private RaceComponent getRaceComponent() {
        if (raceEntities == null || raceEntities.size() == 0) return null;
        return raceMapper.get(raceEntities.first());
    }
}
