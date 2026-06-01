package com.wildkarts.server;

import com.badlogic.gdx.Gdx;
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
import com.wildkarts.net.packets.RaceResultsPacket;
import com.wildkarts.net.packets.RaceStateChangedPacket;
import com.wildkarts.track.TrackGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Autorytatywny menedżer stanu wyścigu na serwerze bez interfejsu graficznego.
 *
 * <p>Automat stanów: WAITING_FOR_PLAYERS → PRACTICE → COUNTDOWN → RACING → FINISHED</p>
 *
 * <p>W fazie PRACTICE gracze jeżdżą swobodnie i zapisują czasy okrążeń.
 * Gdy wszyscy są gotowi, serwer oblicza siatkę startową i przechodzi do COUNTDOWN.</p>
 */
public class ServerRaceManager {

    private static final float COUNTDOWN_SECONDS = 3.0f;
    private static final int POSITIONS_BROADCAST_INTERVAL_TICKS = 5;

    private final Server server;
    private final UdpReliabilityManager reliabilityManager;
    private final TrackGenerator trackGenerator;

    private final RaceLapTracker lapTracker;
    private final RaceGridAssigner gridAssigner;
    private final RacePositionBroadcaster positionBroadcaster;

    private Array<Vector2> trackPoints;

    private final Map<Integer, ServerPlayerState> playersById = new HashMap<>();
    private final Map<Integer, ServerPlayerState> playersByConnId = new HashMap<>();
    private final List<ServerPlayerState> sortBuffer = new ArrayList<>();

    private RaceState raceState = RaceState.WAITING_FOR_PLAYERS;
    private float countdownTimer = COUNTDOWN_SECONDS;
    private float raceTimer = 0f;
    private int broadcastTickCounter = 0;

    /**
     * Tworzy menedżer wyścigu powiązany z serwerem, transmisją i generatorem toru.
     *
     * @param server              instancja serwera KryoNet
     * @param reliabilityManager  menedżer niezawodnej transmisji UDP
     * @param trackGenerator      generator toru z punktami kontrolnymi
     */
    public ServerRaceManager(Server server, UdpReliabilityManager reliabilityManager,
                              TrackGenerator trackGenerator) {
        this.server = server;
        this.reliabilityManager = reliabilityManager;
        this.trackGenerator = trackGenerator;
        this.trackPoints = trackGenerator.getManualPoints();
        this.lapTracker = new RaceLapTracker(reliabilityManager);
        this.gridAssigner = new RaceGridAssigner();
        this.positionBroadcaster = new RacePositionBroadcaster(server);
    }

    /**
     * Odświeża listę punktów toru po załadowaniu nowej mapy.
     */
    public void refreshTrackPoints() {
        this.trackPoints = trackGenerator.getManualPoints();
    }

    /**
     * Rejestruje dołączenie nowego gracza do sesji wyścigu.
     *
     * @param playerId   identyfikator gracza
     * @param connection połączenie sieciowe gracza
     * @param name       wyświetlana nazwa gracza
     */
    public void onPlayerJoined(int playerId, Connection connection, String name) {
        ServerPlayerState state = new ServerPlayerState(playerId, connection);
        state.name = name;
        playersById.put(playerId, state);
        playersByConnId.put(connection.getID(), state);
        Gdx.app.log("ServerRaceManager", "Player " + playerId + " joined the race manager.");
        broadcastLobbyStatus();
    }

    /**
     * Usuwa gracza po rozłączeniu i resetuje lobby gdy pokój jest pusty.
     *
     * @param connection połączenie rozłączonego gracza
     */
    public void onPlayerDisconnected(Connection connection) {
        ServerPlayerState removed = playersByConnId.remove(connection.getID());
        if (removed != null) {
            playersById.remove(removed.playerId);
            Gdx.app.log("ServerRaceManager", "Player " + removed.playerId + " left the race manager.");

            if (playersById.isEmpty())
                resetToLobby();
            else {
                broadcastLobbyStatus();
                if (raceState == RaceState.RACING && allPlayersFinished())
                    transitionTo(RaceState.FINISHED);
            }
        }
    }

    /**
     * Oznacza gracza jako gotowego do jazdy po załadowaniu mapy.
     * Pierwsze załadowanie mapy uruchamia fazę PRACTICE.
     *
     * @param connection połączenie gracza, który załadował mapę
     */
    public void onPlayerMapLoaded(Connection connection) {
        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        state.mapLoaded = true;
        Gdx.app.log("ServerRaceManager", "Player " + state.playerId + " map loaded.");

        if (raceState == RaceState.WAITING_FOR_PLAYERS)
            transitionTo(RaceState.PRACTICE);
    }

    /**
     * Aktualizuje flagę gotowości gracza w fazie treningu.
     *
     * @param connection połączenie gracza
     * @param packet     pakiet ze stanem gotowości
     */
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

    /**
     * Zapisuje ostatnią znaną pozycję gracza na torze.
     *
     * @param connection połączenie gracza
     * @param x          współrzędna X w metrach
     * @param y          współrzędna Y w metrach
     */
    public void onPlayerPosition(Connection connection, float x, float y) {
        ServerPlayerState state = playersByConnId.get(connection.getID());
        if (state == null) return;
        state.lastX = x;
        state.lastY = y;
        state.hasPosition = true;
    }

    /**
     * Weryfikuje i rejestruje minięcie punktu kontrolnego przez gracza.
     *
     * @param connection połączenie gracza
     * @param packet     pakiet z indeksem punktu i pozycją
     */
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

        if (raceState == RaceState.PRACTICE)
            lapTracker.advancePointPractice(state, packet.pointIndex, totalPoints);
        else
            lapTracker.advancePoint(state, packet.pointIndex, totalPoints, raceTimer,
                    () -> { if (allPlayersFinished()) transitionTo(RaceState.FINISHED); });
    }

    /**
     * Wykonuje jeden krok symulacji wyścigu w zależności od aktualnego stanu FSM.
     *
     * @param deltaTime czas od ostatniego ticka w sekundach
     */
    public void update(float deltaTime) {
        switch (raceState) {
            case WAITING_FOR_PLAYERS -> { }
            case PRACTICE -> tickPractice(deltaTime);
            case COUNTDOWN -> tickCountdown(deltaTime);
            case RACING -> tickRacing(deltaTime);
            default -> { }
        }
    }

    /**
     * Obsługuje fazę treningu — nalicza czasy sektorów i sprawdza gotowość graczy.
     *
     * @param deltaTime czas od ostatniego ticka w sekundach
     */
    private void tickPractice(float deltaTime) {
        for (ServerPlayerState state : playersById.values()) {
            if (state.mapLoaded)
                state.currentSectorElapsed += deltaTime;
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

    /**
     * Odlicza czas do startu wyścigu i przechodzi do fazy RACING po zerze.
     *
     * @param deltaTime czas od ostatniego ticka w sekundach
     */
    private void tickCountdown(float deltaTime) {
        countdownTimer -= deltaTime;
        if (countdownTimer <= 0f) {
            countdownTimer = 0f;
            transitionTo(RaceState.RACING);
        }
    }

    /**
     * Nalicza czas wyścigu, pozycje graczy i okresowo wysyła aktualizacje tablicy.
     *
     * @param deltaTime czas od ostatniego ticka w sekundach
     */
    private void tickRacing(float deltaTime) {
        raceTimer += deltaTime;
        for (ServerPlayerState state : playersById.values()) {
            if (state.finished) continue;
            state.currentSectorElapsed += deltaTime;
            positionBroadcaster.updateDistanceToNextPoint(state, trackPoints);
        }
        positionBroadcaster.assignPositions(sortBuffer, playersById.values());
        broadcastTickCounter++;
        if (broadcastTickCounter >= POSITIONS_BROADCAST_INTERVAL_TICKS) {
            broadcastTickCounter = 0;
            positionBroadcaster.broadcastPositions(playersById.values());
        }
    }

    /**
     * Oblicza siatkę startową i wysyła ją wszystkim klientom.
     */
    private void computeAndSendGrid() {
        GridAssignmentPacket packet = gridAssigner.computeGrid(
                sortBuffer, playersById.values(), trackGenerator);
        broadcastReliableToAll(packet);
    }

    /**
     * Wysyła aktualny stan lobby (liczba gotowych graczy) do wszystkich klientów.
     */
    private void broadcastLobbyStatus() {
        int total = playersById.size();
        int ready = 0;
        for (ServerPlayerState state : playersById.values()) {
            if (state.ready) ready++;
        }
        broadcastReliableToAll(new LobbyStatusPacket(ready, total));
    }

    /**
     * Przełącza automat stanów wyścigu i powiadamia klientów o zmianie.
     *
     * @param newState nowy stan wyścigu
     */
    private void transitionTo(RaceState newState) {
        raceState = newState;
        switch (newState) {
            case PRACTICE -> Gdx.app.log("ServerRaceManager", "Race -> PRACTICE (free driving)");
            case COUNTDOWN -> {
                countdownTimer = COUNTDOWN_SECONDS;
                raceTimer = 0f;
                Gdx.app.log("ServerRaceManager", "Race -> COUNTDOWN");
            }
            case RACING -> {
                countdownTimer = 0f;
                Gdx.app.log("ServerRaceManager", "Race -> RACING (GO!)");
            }
            case FINISHED -> Gdx.app.log("ServerRaceManager", "Race -> FINISHED");
            default -> { }
        }
        broadcastReliableToAll(new RaceStateChangedPacket(
                newState.ordinal(), countdownTimer, raceTimer));

        if (newState == RaceState.FINISHED)
            broadcastRaceResults();
    }

    /**
     * Resetuje menedżer do stanu oczekiwania na graczy po opróżnieniu pokoju.
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

    /**
     * Sortuje graczy według pozycji wyścigowej i wysyła końcową tabelę wyników.
     */
    private void broadcastRaceResults() {
        positionBroadcaster.assignPositions(sortBuffer, playersById.values());
        sortBuffer.sort(positionBroadcaster.getRaceOrder());

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

    /**
     * Wysyła pakiet niezawodny do każdego podłączonego gracza (osobna kopia na gracza).
     *
     * @param packet pakiet do wysłania
     */
    private void broadcastReliableToAll(ReliablePacket packet) {
        for (ServerPlayerState state : playersById.values()) {
            ReliablePacket copy = clonePacket(packet);
            reliabilityManager.send(state.connection, copy);
        }
    }

    /**
     * Tworzy kopię pakietu niezawodnego wymaganą przez menedżer retransmisji UDP.
     *
     * @param original oryginalny pakiet
     * @return kopia pakietu gotowa do wysłania
     */
    private ReliablePacket clonePacket(ReliablePacket original) {
        if (original instanceof LobbyStatusPacket l)
            return new LobbyStatusPacket(l.readyPlayers, l.totalPlayers);
        if (original instanceof RaceStateChangedPacket r)
            return new RaceStateChangedPacket(r.newStateOrdinal, r.countdownTimer, r.raceTimer);
        if (original instanceof GridAssignmentPacket)
            return original;
        if (original instanceof RaceResultsPacket)
            return original;
        return original;
    }

    /**
     * Sprawdza, czy wszyscy podłączeni gracze ukończyli wyścig.
     *
     * @return true gdy każdy gracz ma flagę finished
     */
    private boolean allPlayersFinished() {
        if (playersById.isEmpty()) return false;
        for (ServerPlayerState state : playersById.values()) {
            if (!state.finished) return false;
        }
        return true;
    }

    /**
     * Zwraca aktualny stan automatu wyścigu.
     *
     * @return bieżący stan wyścigu
     */
    public RaceState getRaceState() {
        return raceState;
    }

    /**
     * Zwraca liczbę graczy aktualnie podłączonych do sesji wyścigu.
     *
     * @return liczba graczy
     */
    public int getConnectedPlayerCount() {
        return playersById.size();
    }
}
