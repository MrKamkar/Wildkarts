package com.wildkarts.server;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Server;
import com.wildkarts.components.RaceState;
import com.wildkarts.net.ReliablePacket;
import com.wildkarts.net.UdpReliabilityManager;
import com.wildkarts.net.packets.GridAssignmentPacket;
import com.wildkarts.net.packets.LobbyStatusPacket;
import com.wildkarts.net.packets.PlayerPassedPointPacket;
import com.wildkarts.net.packets.PlayerReadyPacket;
import com.wildkarts.net.packets.RacePositionsUpdatePacket;
import com.wildkarts.net.packets.RaceResultsPacket;
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
 * <p>FSM: WAITING_FOR_PLAYERS -> PRACTICE -> COUNTDOWN -> RACING -> FINISHED</p>
 *
 * <p>During PRACTICE players drive freely and record lap times. When all
 * players signal ready, the server computes grid positions based on best
 * practice lap times and transitions to COUNTDOWN.</p>
 */
public class ServerRaceManager {

    // ─── Configuration ────────────────────────────────────────────────

    private static final int MAX_LAPS = 3;
    private static final int TOTAL_SECTORS = 3;
    private static final float COUNTDOWN_SECONDS = 3.0f;

    /** Tick interval (in update calls) between leaderboard broadcasts. */
    private static final int POSITIONS_BROADCAST_INTERVAL_TICKS = 5;

    /** Spacing in meters between grid slots along the road direction. */
    private static final float GRID_SLOT_SPACING = 3.0f;

    /** Offset behind the start line for the first grid slot. */
    private static final float GRID_START_OFFSET = 2.0f;

    // ─── Per-player Server-side State ─────────────────────────────────

    public static final class ServerPlayerState {
        public final int playerId;
        public final Connection connection;
        public String name = "Player";

        // Lobby / practice
        public boolean ready = false;
        public boolean mapLoaded = false;

        // Position (updated from PlayerPositionPacket relay)
        public float lastX = 0f;
        public float lastY = 0f;
        public boolean hasPosition = false;

        // Practice best lap time (0 = no completed lap)
        public float bestPracticeLapTime = 0f;

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
        public float finishTime = 0f;

        public ServerPlayerState(int playerId, Connection connection) {
            this.playerId = playerId;
            this.connection = connection;
        }

        /** Resets all race progress but preserves bestPracticeLapTime and connection info. */
        public void resetForRace() {
            currentLap = 1;
            nextTrackPointIndex = 1;
            currentSector = 0;
            currentSectorElapsed = 0f;
            for (int i = 0; i < TOTAL_SECTORS; i++) {
                currentLapSectorTimes[i] = 0f;
                bestSectorTimes[i] = 0f;
            }
            lastSectorDelta = 0f;
            finished = false;
            finishTime = 0f;
            racePosition = 1;
            distanceToNextPointSq = Float.MAX_VALUE;
        }

        /** Resets practice lap progress (called after each practice lap completion). */
        public void resetForNextPracticeLap() {
            currentLap = 1;
            nextTrackPointIndex = 1;
            currentSector = 0;
            currentSectorElapsed = 0f;
            for (int i = 0; i < TOTAL_SECTORS; i++) {
                currentLapSectorTimes[i] = 0f;
            }
            lastSectorDelta = 0f;
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

    private final Comparator<ServerPlayerState> gridOrder = (a, b) -> {
        if (a.bestPracticeLapTime <= 0f && b.bestPracticeLapTime <= 0f) return 0;
        if (a.bestPracticeLapTime <= 0f) return 1;
        if (b.bestPracticeLapTime <= 0f) return -1;
        return Float.compare(a.bestPracticeLapTime, b.bestPracticeLapTime);
    };

    public ServerRaceManager(Server server, UdpReliabilityManager reliabilityManager,
                              TrackGenerator trackGenerator) {
        this.server = server;
        this.reliabilityManager = reliabilityManager;
        this.trackGenerator = trackGenerator;
        this.trackPoints = trackGenerator.getManualPoints();
    }

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

            if (playersById.isEmpty()) {
                resetToLobby();
            } else {
                broadcastLobbyStatus();
                // If race is running and all remaining players finished, end it
                if (raceState == RaceState.RACING && allPlayersFinished()) {
                    transitionTo(RaceState.FINISHED);
                }
            }
        }
    }

    /**
     * Called when a client has fully loaded the map and is ready to spawn a car.
     * Transitions to PRACTICE when the first player loads.
     */
    public void onPlayerMapLoaded(Connection connection) {
        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        state.mapLoaded = true;
        Gdx.app.log("ServerRaceManager", "Player " + state.playerId + " map loaded.");

        if (raceState == RaceState.WAITING_FOR_PLAYERS) {
            transitionTo(RaceState.PRACTICE);
        }
    }

    // ─── Packet Handlers ──────────────────────────────────────────────

    public void onPlayerReady(Connection connection, PlayerReadyPacket packet) {
        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        if (raceState != RaceState.PRACTICE) return;
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
        if (raceState != RaceState.RACING && raceState != RaceState.PRACTICE) return;

        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        if (state.finished) return;
        if (packet.pointIndex != state.nextTrackPointIndex) return;

        int totalPoints = trackPoints.size;
        if (totalPoints < 3) return;
        if (packet.pointIndex < 0 || packet.pointIndex >= totalPoints) return;

        if (!trackGenerator.isWithinCheckpointGate(packet.pointIndex, packet.x, packet.y)) {
            Gdx.app.log("ServerRaceManager", "Rejected point " + packet.pointIndex
                    + " from player " + state.playerId + " — outside checkpoint gate.");
            return;
        }

        if (raceState == RaceState.PRACTICE) {
            advancePointPractice(state, packet.pointIndex, totalPoints);
        } else {
            advancePoint(state, packet.pointIndex, totalPoints);
        }
    }

    // ─── Server Tick ──────────────────────────────────────────────────

    public void update(float deltaTime) {
        switch (raceState) {
            case WAITING_FOR_PLAYERS:
                break;
            case PRACTICE:
                tickPractice(deltaTime);
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

    private void tickPractice(float deltaTime) {
        for (ServerPlayerState state : playersById.values()) {
            if (state.mapLoaded) {
                state.currentSectorElapsed += deltaTime;
            }
        }

        if (playersById.isEmpty()) return;
        boolean allReady = true;
        for (ServerPlayerState state : playersById.values()) {
            if (!state.ready) {
                allReady = false;
                break;
            }
        }
        if (allReady) {
            computeAndSendGrid();
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

    // ─── Practice Lap Logic ───────────────────────────────────────────

    private void advancePointPractice(ServerPlayerState state, int passedIdx, int totalPoints) {
        state.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, TOTAL_SECTORS);

        SectorTimePacket response = new SectorTimePacket();
        response.playerId = state.playerId;
        response.sectorIndex = completedSectorIdx;
        response.bestSectorTime = 0f;
        response.sectorTime = 0f;
        response.delta = 0f;
        response.lastLapTime = 0f;
        response.bestPracticeLapTime = state.bestPracticeLapTime;

        if (passedIdx == 0) {
            if (completedSectorIdx >= 0) {
                recordSectorTime(state, completedSectorIdx, response);
            }

            float lapTime = 0f;
            for (float t : state.currentLapSectorTimes) {
                lapTime += t;
            }

            if (lapTime > 0f) {
                response.lastLapTime = lapTime;
                if (state.bestPracticeLapTime <= 0f || lapTime < state.bestPracticeLapTime) {
                    state.bestPracticeLapTime = lapTime;
                    Gdx.app.log("ServerRaceManager", String.format(
                            "Player %d NEW BEST practice lap: %.2fs", state.playerId, lapTime));
                }
                response.bestPracticeLapTime = state.bestPracticeLapTime;
            }

            state.resetForNextPracticeLap();
        } else if (completedSectorIdx >= 0) {
            recordSectorTime(state, completedSectorIdx, response);
        }

        response.currentLap = state.currentLap;
        response.nextTrackPointIndex = state.nextTrackPointIndex;
        response.currentSector = state.currentSector;
        response.finished = false;
        response.raceTimerSnapshot = 0f;

        reliabilityManager.send(state.connection, response);
    }

    // ─── Race Lap / Sector Logic ──────────────────────────────────────

    private void advancePoint(ServerPlayerState state, int passedIdx, int totalPoints) {
        state.nextTrackPointIndex = (passedIdx + 1) % totalPoints;

        int completedSectorIdx = sectorEndingAt(passedIdx, totalPoints, TOTAL_SECTORS);

        SectorTimePacket response = new SectorTimePacket();
        response.playerId = state.playerId;
        response.sectorIndex = completedSectorIdx;
        response.bestSectorTime = 0f;
        response.sectorTime = 0f;
        response.delta = 0f;
        response.lastLapTime = 0f;
        response.bestPracticeLapTime = state.bestPracticeLapTime;

        if (passedIdx == 0) {
            if (completedSectorIdx >= 0) {
                recordSectorTime(state, completedSectorIdx, response);
            }
            state.currentLap++;
            Gdx.app.log("ServerRaceManager",
                    "Player " + state.playerId + " completed lap. Now on " + state.currentLap + "/" + MAX_LAPS);

            if (state.currentLap > MAX_LAPS) {
                state.finished = true;
                state.finishTime = raceTimer;
                Gdx.app.log("ServerRaceManager",
                        "Player " + state.playerId + " FINISHED at " + String.format("%.2f", raceTimer) + "s");

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

    // ─── Grid Computation ─────────────────────────────────────────────

    /**
     * Sorts players by best practice lap time (fastest = pole), computes
     * world-space grid coordinates, sends {@link GridAssignmentPacket} to
     * all clients, and resets player race state.
     */
    private void computeAndSendGrid() {
        sortBuffer.clear();
        sortBuffer.addAll(playersById.values());
        sortBuffer.sort(gridOrder);

        Vector2 startPos = trackGenerator.getStartPosition();
        float startAngle = trackGenerator.getStartAngle();

        float roadDirAngle = startAngle + MathUtils.HALF_PI;
        float rdx = MathUtils.cos(roadDirAngle);
        float rdy = MathUtils.sin(roadDirAngle);

        int n = sortBuffer.size();
        GridAssignmentPacket packet = new GridAssignmentPacket();
        packet.playerIds = new int[n];
        packet.gridPositions = new int[n];
        packet.xs = new float[n];
        packet.ys = new float[n];
        packet.angles = new float[n];
        packet.bestLapTimes = new float[n];

        for (int i = 0; i < n; i++) {
            ServerPlayerState state = sortBuffer.get(i);
            float offset = GRID_START_OFFSET + i * GRID_SLOT_SPACING;
            float gx = startPos.x - rdx * offset;
            float gy = startPos.y - rdy * offset;

            packet.playerIds[i] = state.playerId;
            packet.gridPositions[i] = i + 1;
            packet.xs[i] = gx;
            packet.ys[i] = gy;
            packet.angles[i] = startAngle;
            packet.bestLapTimes[i] = state.bestPracticeLapTime;

            state.resetForRace();

            Gdx.app.log("ServerRaceManager", String.format(
                    "Grid P%d: player %d (%s) — best: %.2fs — pos: (%.1f, %.1f)",
                    i + 1, state.playerId, state.name,
                    state.bestPracticeLapTime, gx, gy));
        }

        broadcastReliableToAll(packet);
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
        if (newState == RaceState.PRACTICE) {
            Gdx.app.log("ServerRaceManager", "Race -> PRACTICE (free driving)");
        } else if (newState == RaceState.COUNTDOWN) {
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

        if (newState == RaceState.FINISHED) {
            broadcastRaceResults();
        }
    }

    /**
     * Fully resets the race manager to WAITING_FOR_PLAYERS.
     * Called when the room becomes empty so the next session starts fresh.
     */
    public void resetToLobby() {
        Gdx.app.log("ServerRaceManager", "Resetting to WAITING_FOR_PLAYERS (room empty or race ended).");
        raceState = RaceState.WAITING_FOR_PLAYERS;
        countdownTimer = COUNTDOWN_SECONDS;
        raceTimer = 0f;
        broadcastTickCounter = 0;

        for (ServerPlayerState state : playersById.values()) {
            state.ready = false;
            state.bestPracticeLapTime = 0f;
            state.resetForRace();
        }
    }

    private void broadcastRaceResults() {
        assignPositions();
        sortBuffer.clear();
        sortBuffer.addAll(playersById.values());
        sortBuffer.sort(raceOrder);

        int n = sortBuffer.size();
        RaceResultsPacket packet = new RaceResultsPacket();
        packet.playerIds = new int[n];
        packet.playerNames = new String[n];
        packet.finishTimes = new float[n];

        for (int i = 0; i < n; i++) {
            ServerPlayerState state = sortBuffer.get(i);
            packet.playerIds[i] = state.playerId;
            packet.playerNames[i] = state.name;
            packet.finishTimes[i] = state.finishTime;
        }

        broadcastReliableToAll(packet);
    }

    private void broadcastReliableToAll(ReliablePacket packet) {
        for (ServerPlayerState state : playersById.values()) {
            ReliablePacket copy = clonePacket(packet);
            reliabilityManager.send(state.connection, copy);
        }
    }

    private ReliablePacket clonePacket(ReliablePacket original) {
        if (original instanceof LobbyStatusPacket l) {
            return new LobbyStatusPacket(l.readyPlayers, l.totalPlayers);
        }
        if (original instanceof RaceStateChangedPacket r) {
            return new RaceStateChangedPacket(r.newStateOrdinal, r.countdownTimer, r.raceTimer);
        }
        if (original instanceof GridAssignmentPacket) {
            return original;
        }
        if (original instanceof RaceResultsPacket) {
            return original;
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
