package com.wildkarts.net;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.wildkarts.net.packets.GridAssignmentPacket;
import com.wildkarts.net.packets.JoinAccepted;
import com.wildkarts.net.packets.JoinRequest;
import com.wildkarts.net.packets.LobbyStatusPacket;
import com.wildkarts.net.packets.MapData;
import com.wildkarts.net.packets.PlayerDisconnectedPacket;
import com.wildkarts.net.packets.PlayerPositionPacket;
import com.wildkarts.net.packets.RacePositionsUpdatePacket;
import com.wildkarts.net.packets.RaceResultsPacket;
import com.wildkarts.net.packets.RaceStateChangedPacket;
import com.wildkarts.net.packets.SectorTimePacket;
import com.wildkarts.net.packets.StartGamePacket;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Opakowanie klienta KryoNet z obsługą niezawodnej transmisji UDP i callbackami dla UI.
 */
public class GameClient {

    private final Client client;
    private final UdpReliabilityManager reliabilityManager;
    private boolean connected = false;

    public Runnable onJoinAccepted;
    public Runnable onConnectionFailed;
    public Runnable onDisconnected;
    public Consumer<PlayerPositionPacket> onPlayerPositionReceived;
    public Consumer<Integer> onPlayerDisconnected;
    public Consumer<String> onMapReceived;
    public Runnable onStartGame;

    public Consumer<LobbyStatusPacket> onLobbyStatus;
    public Consumer<RaceStateChangedPacket> onRaceStateChanged;
    public Consumer<SectorTimePacket> onSectorTime;
    public Consumer<RacePositionsUpdatePacket> onRacePositionsUpdate;
    public Consumer<GridAssignmentPacket> onGridAssignment;
    public Consumer<RaceResultsPacket> onRaceResults;

    private String[] mapChunks;
    private int receivedChunksCount = 0;
    private String completedMapJson;
    private boolean startGamePending = false;

    public int localPlayerId = -1;
    public String playerName = "Player";

    /**
     * Tworzy klienta sieciowego, rejestruje typy pakietów i nasłuchuje zdarzeń połączenia.
     */
    public GameClient() {
        client = new Client();
        reliabilityManager = new UdpReliabilityManager();

        Network.register(client.getKryo());

        reliabilityManager.setOnMaxRetriesExceeded(() -> {
            Gdx.app.log("GameClient", "Connection lost (max retries exceeded).");
            connected = false;
            if (onConnectionFailed != null)
                Gdx.app.postRunnable(onConnectionFailed);
            client.stop();
        });

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                Gdx.app.log("GameClient", "TCP Connection established. Sending JoinRequest via UDP.");
                connected = true;
                sendReliable(new JoinRequest(playerName));
            }

            @Override
            public void disconnected(Connection connection) {
                Gdx.app.log("GameClient", "Disconnected from server.");
                connected = false;
                reliabilityManager.reset();
                mapChunks = null;
                receivedChunksCount = 0;
                completedMapJson = null;
                startGamePending = false;
                if (onDisconnected != null)
                    Gdx.app.postRunnable(onDisconnected);
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof ReliablePacket rp)
                    connection.sendUDP(new AckPacket(rp.sequenceId));

                if (object == null) return;

                switch (object.getClass().getSimpleName()) {
                    case "AckPacket" -> {
                        AckPacket ack = (AckPacket) object;
                        reliabilityManager.onAckReceived(ack.acknowledgedId);
                    }
                    case "JoinAccepted" -> {
                        JoinAccepted ja = (JoinAccepted) object;
                        localPlayerId = ja.playerId;
                        Gdx.app.log("GameClient", "Join accepted. ID: " + localPlayerId);
                        if (onJoinAccepted != null)
                            Gdx.app.postRunnable(onJoinAccepted);
                    }
                    case "MapData" -> handleMapChunk((MapData) object);
                    case "StartGamePacket" -> {
                        Gdx.app.log("GameClient", "StartGamePacket received.");
                        if (onStartGame != null)
                            Gdx.app.postRunnable(onStartGame);
                        else
                            startGamePending = true;
                    }
                    case "PlayerPositionPacket" -> {
                        PlayerPositionPacket ppp = (PlayerPositionPacket) object;
                        if (onPlayerPositionReceived != null)
                            Gdx.app.postRunnable(() -> onPlayerPositionReceived.accept(ppp));
                    }
                    case "PlayerDisconnectedPacket" -> {
                        PlayerDisconnectedPacket pdp = (PlayerDisconnectedPacket) object;
                        if (onPlayerDisconnected != null)
                            Gdx.app.postRunnable(() -> onPlayerDisconnected.accept(pdp.playerId));
                    }
                    case "LobbyStatusPacket" -> {
                        LobbyStatusPacket lsp = (LobbyStatusPacket) object;
                        if (onLobbyStatus != null)
                            Gdx.app.postRunnable(() -> onLobbyStatus.accept(lsp));
                    }
                    case "RaceStateChangedPacket" -> {
                        RaceStateChangedPacket rscp = (RaceStateChangedPacket) object;
                        if (onRaceStateChanged != null)
                            Gdx.app.postRunnable(() -> onRaceStateChanged.accept(rscp));
                    }
                    case "SectorTimePacket" -> {
                        SectorTimePacket stp = (SectorTimePacket) object;
                        if (onSectorTime != null)
                            Gdx.app.postRunnable(() -> onSectorTime.accept(stp));
                    }
                    case "RacePositionsUpdatePacket" -> {
                        RacePositionsUpdatePacket rpp = (RacePositionsUpdatePacket) object;
                        if (onRacePositionsUpdate != null)
                            Gdx.app.postRunnable(() -> onRacePositionsUpdate.accept(rpp));
                    }
                    case "GridAssignmentPacket" -> {
                        GridAssignmentPacket gap = (GridAssignmentPacket) object;
                        if (onGridAssignment != null)
                            Gdx.app.postRunnable(() -> onGridAssignment.accept(gap));
                    }
                    case "RaceResultsPacket" -> {
                        RaceResultsPacket rrp = (RaceResultsPacket) object;
                        if (onRaceResults != null)
                            Gdx.app.postRunnable(() -> onRaceResults.accept(rrp));
                    }
                    default -> { }
                }
            }
        });
    }

    /**
     * Łączy się z serwerem gry w osobnym wątku (TCP + UDP).
     *
     * @param ipAddress adres IP lub nazwa hosta serwera
     */
    public void connect(String ipAddress) {
        client.start();
        Thread connectThread = new Thread(() -> {
            try {
                client.connect(5000, ipAddress, Network.TCP_PORT, Network.UDP_PORT);
            } catch (IOException e) {
                Gdx.app.error("GameClient", "Failed to connect", e);
                if (onConnectionFailed != null)
                    Gdx.app.postRunnable(onConnectionFailed);
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /**
     * Wysyła pakiet niezawodny z potwierdzeniem i retransmisją.
     *
     * @param packet pakiet do wysłania
     */
    public void sendReliable(ReliablePacket packet) {
        if (connected)
            reliabilityManager.send(client, packet);
    }

    /**
     * Wysyła pakiet UDP bez gwarancji dostarczenia (np. pozycja gracza co klatkę).
     *
     * @param packet pakiet do wysłania
     */
    public void sendUnreliable(UnreliablePacket packet) {
        if (connected)
            client.sendUDP(packet);
    }

    /**
     * Aktualizuje menedżer retransmisji — wywoływane co klatkę z pętli gry.
     */
    public void update() {
        if (connected)
            reliabilityManager.update();
    }

    /**
     * Rejestruje callback otrzymania pełnej mapy JSON; dostarcza buforowaną mapę jeśli już przyszła.
     *
     * @param callback funkcja wywoływana z pełnym JSON-em mapy
     */
    public void setOnMapReceived(Consumer<String> callback) {
        this.onMapReceived = callback;
        if (onMapReceived != null && completedMapJson != null) {
            Gdx.app.postRunnable(() -> {
                if (onMapReceived != null && completedMapJson != null) {
                    onMapReceived.accept(completedMapJson);
                    completedMapJson = null;
                }
            });
        }
    }

    /**
     * Rejestruje callback startu gry; wykonuje go natychmiast jeśli pakiet startu już dotarł.
     *
     * @param callback funkcja uruchamiająca rozgrywkę po sygnale serwera
     */
    public void setOnStartGame(Runnable callback) {
        this.onStartGame = callback;
        if (onStartGame != null && startGamePending) {
            Gdx.app.postRunnable(() -> {
                if (onStartGame != null && startGamePending) {
                    onStartGame.run();
                    startGamePending = false;
                }
            });
        }
    }

    /**
     * Zatrzymuje klienta sieciowego i zwalnia połączenie.
     */
    public void dispose() {
        client.stop();
    }

    /**
     * Składa fragmenty mapy w pełny JSON i wywołuje callback po odebraniu wszystkich chunków.
     *
     * @param packet pojedynczy fragment danych mapy
     */
    private void handleMapChunk(MapData packet) {
        if (mapChunks == null || mapChunks.length != packet.totalChunks) {
            mapChunks = new String[packet.totalChunks];
            receivedChunksCount = 0;
        }

        if (mapChunks[packet.chunkIndex] == null) {
            mapChunks[packet.chunkIndex] = packet.data;
            receivedChunksCount++;
            Gdx.app.log("GameClient", "Received map chunk " + (packet.chunkIndex + 1) + "/" + packet.totalChunks);

            if (receivedChunksCount == packet.totalChunks) {
                String fullJson = String.join("", mapChunks);
                Gdx.app.log("GameClient", "Full map JSON received (" + fullJson.length() + " bytes).");

                if (onMapReceived != null)
                    Gdx.app.postRunnable(() -> onMapReceived.accept(fullJson));
                else
                    completedMapJson = fullJson;

                mapChunks = null;
                receivedChunksCount = 0;
            }
        }
    }
}
