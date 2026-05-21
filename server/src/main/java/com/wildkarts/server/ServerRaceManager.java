package com.wildkarts.server;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Server;
import com.wildkarts.components.RaceState;
import com.wildkarts.net.ReliablePacket;
import com.wildkarts.net.UdpReliabilityManager;
import com.wildkarts.net.packets.LobbyStatusPacket;
import com.wildkarts.net.packets.PlayerPassedPointPacket;
import com.wildkarts.net.packets.PlayerReadyPacket;
import com.wildkarts.net.packets.RacePositionsUpdatePacket;
import com.wildkarts.net.packets.RaceStateChangedPacket;
import com.wildkarts.net.packets.SectorTimePacket;
import com.wildkarts.track.TrackGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative race state for the headless server.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Track each connected player's lap, sector, point progress and last
 *       known position.</li>
 *   <li>Drive the race FSM (WAITING_FOR_PLAYERS → COUNTDOWN → RACING →
 *       FINISHED) at the server's tick rate (~30 Hz from
 *       {@code HeadlessApplicationConfiguration.updatesPerSecond}).</li>
 *   <li>Validate {@link PlayerPassedPointPacket} requests against the
 *       server's copy of the track points and the last known position
 *       (cheap anti-cheat) before recording sector / lap progress.</li>
 *   <li>Sort racers every tick and periodically broadcast a
 *       {@link RacePositionsUpdatePacket} leaderboard snapshot.</li>
 * </ul>
 *
 * <p>This class deliberately does NOT depend on libGDX Box2D or Ashley —
 * the server runs headless and only needs the manual control points from
 * {@link TrackGenerator} to validate positions.</p>
 */
public class ServerRaceManager {

    // ─── Configuration ────────────────────────────────────────────────

    private static final int MAX_LAPS = 3;
    private static final int TOTAL_SECTORS = 3;
    private static final float COUNTDOWN_SECONDS = 3.0f;

    /**
     * Maximum allowed distance (meters) between the player's last reported
     * position and the track point they claim to have passed. Cheaper than
     * "did they tunnel through walls?" but catches blatant teleport spoofs.
     */
    private static final float POSITION_VALIDATION_RADIUS = 8f;

    /** Tick interval (in update calls) between leaderboard broadcasts. */
    private static final int POSITIONS_BROADCAST_INTERVAL_TICKS = 5;

    // ─── Per-player Server-side State ─────────────────────────────────

    public static final class ServerPlayerState {
        public final int playerId;
        public final Connection connection;
        public String name = "Player";

        // Lobby
        public boolean ready = false;

        // Position (updated from PlayerPositionPacket relay)
        public float lastX = 0f;
        public float lastY = 0f;
        public boolean hasPosition = false;

        // Lap / sector progress
        public int currentLap = 1;
        public int nextTrackPointIndex = 1;
        public int currentSector = 0;
        public float currentSectorElapsed = 0f;
        public final float[] currentLapSectorTimes = new float[TOTAL_SECTORS];
        public final float[] bestSectorTimes = new float[TOTAL_SECTORS];
        public float lastSectorDelta = 0f;
        public boolean finished = false;

        // Race position (assigned each tick)
        public int racePosition = 1;
        public float distanceToNextPointSq = Float.MAX_VALUE;

        public ServerPlayerState(int playerId, Connection connection) {
            this.playerId = playerId;
            this.connection = connection;
        }
    }

    // ─── Fields ───────────────────────────────────────────────────────

    private final Server server;
    private final UdpReliabilityManager reliabilityManager;

    private final TrackGenerator trackGenerator;
    private Array<Vector2> trackPoints;

    private final Map<Integer, ServerPlayerState> playersById = new HashMap<>();
    private final Map<Integer, ServerPlayerState> playersByConnId = new HashMap<>();

    private RaceState raceState = RaceState.WAITING_FOR_PLAYERS;
    private float countdownTimer = COUNTDOWN_SECONDS;
    private float raceTimer = 0f;

    private int broadcastTickCounter = 0;

    // Reusable sort buffer (avoids per-tick allocation)
    private final List<ServerPlayerState> sortBuffer = new ArrayList<>();

    private final Comparator<ServerPlayerState> raceOrder = (a, b) -> {
        if (a.currentLap != b.currentLap) {
            return Integer.compare(b.currentLap, a.currentLap);
        }
        if (a.nextTrackPointIndex != b.nextTrackPointIndex) {
            return Integer.compare(b.nextTrackPointIndex, a.nextTrackPointIndex);
        }
        return Float.compare(a.distanceToNextPointSq, b.distanceToNextPointSq);
    };

    public ServerRaceManager(Server server, UdpReliabilityManager reliabilityManager,
                              TrackGenerator trackGenerator) {
        this.server = server;
        this.reliabilityManager = reliabilityManager;
        this.trackGenerator = trackGenerator;
        this.trackPoints = trackGenerator.getManualPoints();
    }

    /** Refreshes the cached track points after the server loads/updates a map. */
    public void refreshTrackPoints() {
        this.trackPoints = trackGenerator.getManualPoints();
    }

    // ─── Connection Lifecycle ─────────────────────────────────────────

    public void onPlayerJoined(int playerId, Connection connection, String name) {
        ServerPlayerState state = new ServerPlayerState(playerId, connection);
        state.name = name;
        playersById.put(playerId, state);
        playersByConnId.put(connection.getID(), state);
        Gdx.app.log("ServerRaceManager", "Player " + playerId + " joined the race manager.");
        broadcastLobbyStatus();
    }

    public void onPlayerDisconnected(Connection connection) {
        ServerPlayerState removed = playersByConnId.remove(connection.getID());
        if (removed != null) {
            playersById.remove(removed.playerId);
            Gdx.app.log("ServerRaceManager", "Player " + removed.playerId + " left the race manager.");
            broadcastLobbyStatus();
        }
    }

    // ─── Packet Handlers ──────────────────────────────────────────────

    public void onPlayerReady(Connection connection, PlayerReadyPacket packet) {
        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        if (raceState != RaceState.WAITING_FOR_PLAYERS) return;
        if (state.ready == packet.ready) return;

        state.ready = packet.ready;
        Gdx.app.log("ServerRaceManager", "Player " + state.playerId
                + (state.ready ? " READY" : " UN-READY"));
        broadcastLobbyStatus();
    }

    public void onPlayerPosition(Connection connection, float x, float y) {
        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        state.lastX = x;
        state.lastY = y;
        state.hasPosition = true;
    }

    public void onPlayerPassedPoint(Connection connection, PlayerPassedPointPacket packet) {
        if (raceState != RaceState.RACING) return;

        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        if (state.finished) return;
        if (packet.pointIndex != state.nextTrackPointIndex) {
            // Out-of-order or stale request — ignore.
            return;
        }

        int totalPoints = trackPoints.size;
        if (totalPoints < 3) return;
        if (packet.pointIndex < 0 || packet.pointIndex >= totalPoints) return;

        // Anti-cheat: client must actually be near the claimed point.
        Vector2 expected = trackPoints.get(packet.pointIndex);
        float dx = expected.x - packet.x;
        float dy = expected.y - packet.y;
        if (dx * dx + dy * dy > POSITION_VALIDATION_RADIUS * POSITION_VALIDATION_RADIUS) {
            Gdx.app.log("ServerRaceManager", "Rejected point " + packet.pointIndex
                    + " from player " + state.playerId + " — too far from claimed point.");
            return;
        }

        advancePoint(state, packet.pointIndex, totalPoints);
    }

    // ─── Server Tick ──────────────────────────────────────────────────

    public void update(float deltaTime) {
        switch (raceState) {
            case WAITING_FOR_PLAYERS:
                tickLobby();
                break;
            case COUNTDOWN:
                tickCountdown(deltaTime);
                break;
            case RACING:
                tickRacing(deltaTime);
                break;
            default:
                break;
        }
    }

    private void tickLobby() {
        if (playersById.isEmpty()) return;
        boolean allReady = true;
        for (ServerPlayerState state : playersById.values()) {
            if (!state.ready) {
                allReady = false;
                break;
            }
        }
        if (allReady) {
            transitionTo(RaceState.COUNTDOWN);
        }
    }

    private void tickCountdown(float deltaTime) {
        countdownTimer -= deltaTime;
        if (countdownTimer <= 0f) {
            countdownTimer = 0f;
            transitionTo(RaceState.RACING);
        }
    }

    private void tickRacing(float deltaTime) {
        raceTimer += deltaTime;
        for (ServerPlayerState state : playersById.values()) {
            if (state.finished) continue;
            state.currentSectorElapsed += deltaTime;
            updateDistanceToNextPoint(state);
        }
        assignPositions();
        broadcastTickCounter++;
        if (broadcastTickCounter >= POSITIONS_BROADCAST_INTERVAL_TICKS) {
            broadcastTickCounter = 0;
            broadcastPositions();
        }
    }

    // ─── Lap / Sector Logic (mirror of LapSectorSystem) ───────────────

    private void advancePoint(ServerPlayerState state, int passedIdx, int totalPoints) {
        state.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, TOTAL_SECTORS);

        SectorTimePacket response = new SectorTimePacket();
        response.playerId = state.playerId;
        response.sectorIndex = completedSectorIdx;
        response.bestSectorTime = 0f;
        response.sectorTime = 0f;
        response.delta = 0f;

        if (passedIdx == 0) {
            // Finish line: closes the final sector AND advances the lap.
            if (completedSectorIdx >= 0) {
                recordSectorTime(state, completedSectorIdx, response);
            }
            state.currentLap++;
            Gdx.app.log("ServerRaceManager",
                    "Player " + state.playerId + " completed lap. Now on " + state.currentLap + "/" + MAX_LAPS);

            if (state.currentLap > MAX_LAPS) {
                state.finished = true;
                Gdx.app.log("ServerRaceManager",
                        "Player " + state.playerId + " FINISHED at " + String.format("%.2f", raceTimer) + "s");

                // Optionally transition the global FSM when the first player finishes
                // — for now we keep RACING until all are finished.
                if (allPlayersFinished()) {
                    transitionTo(RaceState.FINISHED);
                }
            }
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(state, completedSectorIdx, response);
        }

        response.currentLap = state.currentLap;
        response.nextTrackPointIndex = state.nextTrackPointIndex;
        response.currentSector = state.currentSector;
        response.finished = state.finished;
        response.raceTimerSnapshot = raceTimer;

        reliabilityManager.send(state.connection, response);
    }

    private void recordSectorTime(ServerPlayerState state, int sectorIdx, SectorTimePacket response) {
        float sectorTime = state.currentSectorElapsed;
        state.currentLapSectorTimes[sectorIdx] = sectorTime;

        float prevBest = state.bestSectorTimes[sectorIdx];
        float delta = (prevBest > 0f) ? sectorTime - prevBest : 0f;
        state.lastSectorDelta = delta;

        if (prevBest <= 0f || sectorTime < prevBest) {
            state.bestSectorTimes[sectorIdx] = sectorTime;
        }

        state.currentSectorElapsed = 0f;
        state.currentSector = (sectorIdx + 1) % TOTAL_SECTORS;

        response.sectorTime = sectorTime;
        response.delta = delta;
        response.bestSectorTime = state.bestSectorTimes[sectorIdx];

        Gdx.app.log("ServerRaceManager", String.format(
                "Player %d sector %d: %.2fs (delta %+.2fs)",
                state.playerId, sectorIdx + 1, sectorTime, delta));
    }

    private static int sectorEndingAt(int passedIdx, int totalPoints, int totalSectors) {
        if (totalSectors <= 1) return -1;
        int sectorSize = Math.max(1, totalPoints / totalSectors);
        for (int s = 0; s < totalSectors - 1; s++) {
            int boundary = (s + 1) * sectorSize;
            if (passedIdx == boundary) return s;
        }
        if (passedIdx == 0) return totalSectors - 1;
        return -1;
    }

    // ─── Positions & Broadcasts ───────────────────────────────────────

    private void updateDistanceToNextPoint(ServerPlayerState state) {
        if (!state.hasPosition) return;
        if (state.nextTrackPointIndex < 0 || state.nextTrackPointIndex >= trackPoints.size) return;
        Vector2 next = trackPoints.get(state.nextTrackPointIndex);
        float dx = next.x - state.lastX;
        float dy = next.y - state.lastY;
        state.distanceToNextPointSq = dx * dx + dy * dy;
    }

    private void assignPositions() {
        sortBuffer.clear();
        sortBuffer.addAll(playersById.values());
        sortBuffer.sort(raceOrder);
        for (int i = 0; i < sortBuffer.size(); i++) {
            sortBuffer.get(i).racePosition = i + 1;
        }
    }

    private void broadcastPositions() {
        int n = playersById.size();
        if (n == 0) return;

        RacePositionsUpdatePacket packet = new RacePositionsUpdatePacket();
        packet.playerIds = new int[n];
        packet.positions = new int[n];
        packet.currentLaps = new int[n];
        packet.nextTrackPointIndices = new int[n];

        int i = 0;
        for (ServerPlayerState state : playersById.values()) {
            packet.playerIds[i] = state.playerId;
            packet.positions[i] = state.racePosition;
            packet.currentLaps[i] = state.currentLap;
            packet.nextTrackPointIndices[i] = state.nextTrackPointIndex;
            i++;
        }

        server.sendToAllUDP(packet);
    }

    private void broadcastLobbyStatus() {
        int total = playersById.size();
        int ready = 0;
        for (ServerPlayerState state : playersById.values()) {
            if (state.ready) ready++;
        }
        broadcastReliableToAll(new LobbyStatusPacket(ready, total));
    }

    private void transitionTo(RaceState newState) {
        raceState = newState;
        if (newState == RaceState.COUNTDOWN) {
            countdownTimer = COUNTDOWN_SECONDS;
            raceTimer = 0f;
            Gdx.app.log("ServerRaceManager", "Race -> COUNTDOWN");
        } else if (newState == RaceState.RACING) {
            countdownTimer = 0f;
            Gdx.app.log("ServerRaceManager", "Race -> RACING (GO!)");
        } else if (newState == RaceState.FINISHED) {
            Gdx.app.log("ServerRaceManager", "Race -> FINISHED");
        }
        broadcastReliableToAll(new RaceStateChangedPacket(
                newState.ordinal(), countdownTimer, raceTimer));
    }

    private void broadcastReliableToAll(ReliablePacket packet) {
        for (ServerPlayerState state : playersById.values()) {
            // Each connection needs its own sequenceId, so send individually.
            // The reliability manager handles ACK / retransmission.
            ReliablePacket copy = clonePacket(packet);
            reliabilityManager.send(state.connection, copy);
        }
    }

    /**
     * KryoNet's reliability manager assigns sequenceId per send, but the
     * same packet instance would be mutated by each call. We create a
     * lightweight copy for each connection. Subclasses are handled by
     * an explicit type dispatch — adequate for the small set used here.
     */
    private ReliablePacket clonePacket(ReliablePacket original) {
        if (original instanceof LobbyStatusPacket l) {
            return new LobbyStatusPacket(l.readyPlayers, l.totalPlayers);
        }
        if (original instanceof RaceStateChangedPacket r) {
            return new RaceStateChangedPacket(r.newStateOrdinal, r.countdownTimer, r.raceTimer);
        }
        return original;
    }

    private boolean allPlayersFinished() {
        if (playersById.isEmpty()) return false;
        for (ServerPlayerState state : playersById.values()) {
            if (!state.finished) return false;
        }
        return true;
    }

    // ─── Getters ──────────────────────────────────────────────────────

    public RaceState getRaceState() {
        return raceState;
    }

    public int getConnectedPlayerCount() {
        return playersById.size();
    }
}
