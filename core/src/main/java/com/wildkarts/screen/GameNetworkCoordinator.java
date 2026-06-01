package com.wildkarts.screen;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.wildkarts.components.LapComponent;
import com.wildkarts.components.NetworkSyncComponent;
import com.wildkarts.components.PhysicsComponent;
import com.wildkarts.components.RaceComponent;
import com.wildkarts.components.RaceState;
import com.wildkarts.net.packets.MapReadyPacket;
import com.wildkarts.track.TrackData;

/**
 * Rejestruje callbacki sieciowe klienta gry i synchronizuje stan zdalnych graczy z ECS.
 */
public class GameNetworkCoordinator {

    private final GameScreenContext ctx;
    private final GameSessionController sessionController;
    private final PlayUiController playUiController;

    /**
     * Tworzy koordynator sieci powiązany z kontekstem i kontrolerami sesji/UI.
     *
     * @param ctx               współdzielony kontekst ekranu gry
     * @param sessionController kontroler stanu wyścigu i gracza lokalnego
     * @param playUiController  kontroler UI wyników i lobby
     */
    public GameNetworkCoordinator(GameScreenContext ctx,
                                  GameSessionController sessionController,
                                  PlayUiController playUiController) {
        this.ctx = ctx;
        this.sessionController = sessionController;
        this.playUiController = playUiController;
    }

    /**
     * Podpina wszystkie handlery pakietów sieciowych do klienta gry.
     * Handlery wykonują się na wątku głównym LibGDX.
     *
     * @param onStartGame callback wywoływany gdy serwer sygnalizuje start gry
     */
    public void registerCallbacks(Runnable onStartGame) {
        if (ctx.gameClient == null) return;

        ctx.gameClient.setOnMapReceived(jsonStr -> {
            Gdx.app.log("GameScreen", "Building track from received JSON...");
            Json json = new Json();
            TrackData data = json.fromJson(TrackData.class, jsonStr);
            ctx.trackGenerator.importData(data);

            Gdx.app.log("GameScreen", "Track built. Sending MapReady.");
            ctx.loadingLabel.setText("Map Ready! Waiting for server...");
            ctx.gameClient.sendReliable(new MapReadyPacket());
        });

        ctx.gameClient.setOnStartGame(onStartGame);

        ctx.gameClient.onPlayerPositionReceived = packet -> {
            if (packet.playerId == ctx.gameClient.localPlayerId) return;

            Entity remoteCar = ctx.remotePlayers.get(packet.playerId);
            if (remoteCar == null) {
                remoteCar = ctx.carFactory.createRemoteCar(packet.x, packet.y, packet.angle);
                remoteCar.add(new LapComponent());
                ctx.remotePlayers.put(packet.playerId, remoteCar);
            }

            NetworkSyncComponent sync = remoteCar.getComponent(NetworkSyncComponent.class);
            if (sync != null) {
                NetworkSyncComponent.Snapshot snap = new NetworkSyncComponent.Snapshot();
                snap.timestamp = System.currentTimeMillis();
                snap.position.set(packet.x, packet.y);
                snap.angle = packet.angle;
                snap.velocity.set(packet.velocityX, packet.velocityY);

                // Przybliżona prędkość kątowa — pakiet jej nie zawiera.
                snap.angularVelocity = 0;
                if (!sync.snapshots.isEmpty()) {
                    NetworkSyncComponent.Snapshot last = sync.snapshots.get(sync.snapshots.size() - 1);
                    float dt = (snap.timestamp - last.timestamp) / 1000f;
                    if (dt > 0) {
                        float diff = (snap.angle - last.angle) % ((float) Math.PI * 2);
                        if (diff > Math.PI) diff -= Math.PI * 2;
                        else if (diff < -Math.PI) diff += Math.PI * 2;
                        snap.angularVelocity = diff / dt;
                    }
                }

                sync.snapshots.add(snap);
                if (sync.snapshots.size() > 20)
                    sync.snapshots.remove(0);
            }
        };

        ctx.gameClient.onPlayerDisconnected = id -> {
            Entity remoteCar = ctx.remotePlayers.remove(id);
            if (remoteCar != null) {
                PhysicsComponent phys = remoteCar.getComponent(PhysicsComponent.class);
                if (phys != null && phys.body != null)
                    ctx.world.destroyBody(phys.body);
                ctx.engine.removeEntity(remoteCar);
                Gdx.app.log("GameScreen", "Remote player removed: " + id);
            }
        };

        wireRaceNetworkCallbacks();
    }

    /**
     * Podpina handlery pakietów związanych z wyścigiem (lobby, sektory, siatka, wyniki).
     */
    private void wireRaceNetworkCallbacks() {
        if (ctx.gameClient == null) return;

        ctx.gameClient.onLobbyStatus = packet -> {
            RaceComponent race = sessionController.getRaceComponent();
            if (race != null) {
                race.readyPlayers = packet.readyPlayers;
                race.requiredPlayers = packet.totalPlayers;
            }
            if (ctx.lobbyStatusLabel != null) {
                ctx.lobbyStatusLabel.setText("Players ready: " + packet.readyPlayers
                        + " / " + packet.totalPlayers);
            }
        };

        ctx.gameClient.onRaceStateChanged = packet -> {
            RaceComponent race = sessionController.getRaceComponent();
            if (race == null) return;
            RaceState[] values = RaceState.values();
            if (packet.newStateOrdinal < 0 || packet.newStateOrdinal >= values.length) return;
            race.currentState = values[packet.newStateOrdinal];
            race.countdownTimer = packet.countdownTimer;
            race.raceTimer = packet.raceTimer;
            Gdx.app.log("GameScreen", "Server race state: " + race.currentState);
        };

        ctx.gameClient.onSectorTime = packet -> {
            Entity carEntity = findCarEntityByPlayerId(packet.playerId);
            if (carEntity == null) return;
            LapComponent lap = carEntity.getComponent(LapComponent.class);
            if (lap == null) return;

            lap.currentLap = packet.currentLap;
            lap.nextTrackPointIndex = packet.nextTrackPointIndex;
            lap.currentSector = packet.currentSector;
            lap.finished = packet.finished;
            lap.lastRequestedPointIndex = -1;

            if (packet.sectorIndex >= 0 && packet.sectorIndex < lap.currentLapSectorTimes.length) {
                lap.currentLapSectorTimes[packet.sectorIndex] = packet.sectorTime;
                lap.bestSectorTimes[packet.sectorIndex] = packet.bestSectorTime;
                lap.lastSectorDelta = packet.delta;
                lap.currentSectorElapsed = 0f;
            }

            if (packet.lastLapTime > 0f) {
                lap.lastPracticeLapTime = packet.lastLapTime;
                lap.resetCurrentLapTiming();
            }
            if (packet.bestPracticeLapTime > 0f)
                lap.bestPracticeLapTime = packet.bestPracticeLapTime;

            RaceComponent race = sessionController.getRaceComponent();
            if (race != null && packet.raceTimerSnapshot > 0f)
                race.raceTimer = packet.raceTimerSnapshot;
        };

        ctx.gameClient.onRacePositionsUpdate = packet -> {
            if (packet.playerIds == null) return;
            for (int i = 0; i < packet.playerIds.length; i++) {
                int pid = packet.playerIds[i];
                Entity carEntity = findCarEntityByPlayerId(pid);
                if (carEntity == null) continue;
                LapComponent lap = carEntity.getComponent(LapComponent.class);
                if (lap == null) continue;
                lap.racePosition = packet.positions[i];
                lap.currentLap = packet.currentLaps[i];
                if (pid != ctx.gameClient.localPlayerId)
                    lap.nextTrackPointIndex = packet.nextTrackPointIndices[i];
            }
        };

        ctx.gameClient.onGridAssignment = packet -> {
            if (packet.playerIds == null) return;
            Gdx.app.log("GameScreen", "Grid assignment received — teleporting cars.");
            for (int i = 0; i < packet.playerIds.length; i++) {
                int pid = packet.playerIds[i];
                Entity carEntity = findCarEntityByPlayerId(pid);
                if (carEntity == null) continue;
                PhysicsComponent phys = carEntity.getComponent(PhysicsComponent.class);
                if (phys == null || phys.body == null) continue;
                phys.body.setTransform(packet.xs[i], packet.ys[i], packet.angles[i]);
                phys.body.setLinearVelocity(0, 0);
                phys.body.setAngularVelocity(0);
                phys.prevPosition.set(packet.xs[i], packet.ys[i]);
                phys.prevAngle = packet.angles[i];

                LapComponent lap = carEntity.getComponent(LapComponent.class);
                if (lap != null) {
                    lap.resetForNextPracticeLap();
                    lap.finished = false;
                    lap.lastRequestedPointIndex = -1;
                }
            }
        };

        ctx.gameClient.onRaceResults = packet -> {
            if (packet.playerIds == null) return;
            Gdx.app.log("GameScreen", "Race results received — showing leaderboard.");
            playUiController.showRaceResults(packet.playerIds, packet.playerNames, packet.finishTimes);
        };
    }

    /**
     * Znajduje encję samochodu gracza po identyfikatorze sieciowym.
     *
     * @param playerId identyfikator gracza z serwera
     * @return encja samochodu lokalnego lub zdalnego, albo null
     */
    private Entity findCarEntityByPlayerId(int playerId) {
        if (ctx.gameClient != null && playerId == ctx.gameClient.localPlayerId)
            return ctx.playerCar;
        return ctx.remotePlayers.get(playerId);
    }
}
