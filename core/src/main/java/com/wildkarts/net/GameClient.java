package com.wildkarts.net;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.wildkarts.net.packets.*;
import java.io.IOException;

/**
 * Wrapper around KryoNet Client that handles UDP reliability.
 */
public class GameClient {

    private final Client client;
    private final UdpReliabilityManager reliabilityManager;
    private boolean connected = false;

    // Callbacks for the UI/Game
    public Runnable onJoinAccepted;
    public Runnable onConnectionFailed;
    public Runnable onDisconnected;
    public java.util.function.Consumer<PlayerPositionPacket> onPlayerPositionReceived;
    public java.util.function.Consumer<Integer> onPlayerDisconnected;
    public java.util.function.Consumer<String> onMapReceived;
    public Runnable onStartGame;

    // Race-authoritative callbacks (server-driven)
    public java.util.function.Consumer<LobbyStatusPacket> onLobbyStatus;
    public java.util.function.Consumer<RaceStateChangedPacket> onRaceStateChanged;
    public java.util.function.Consumer<SectorTimePacket> onSectorTime;
    public java.util.function.Consumer<RacePositionsUpdatePacket> onRacePositionsUpdate;
    public java.util.function.Consumer<GridAssignmentPacket> onGridAssignment;

    private String[] mapChunks;
    private int receivedChunksCount = 0;
    private String completedMapJson;
    private boolean startGamePending = false;

    public int localPlayerId = -1;

    public GameClient() {
        client = new Client();
        reliabilityManager = new UdpReliabilityManager();

        Network.register(client.getKryo());

        reliabilityManager.setOnMaxRetriesExceeded(() -> {
            Gdx.app.log("GameClient", "Connection lost (max retries exceeded).");
            connected = false;
            if (onConnectionFailed != null) {
                Gdx.app.postRunnable(onConnectionFailed);
            }
            client.stop();
        });

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                Gdx.app.log("GameClient", "TCP Connection established. Sending JoinRequest via UDP.");
                connected = true;
                // Once connected via TCP, we initiate our game session via UDP
                sendReliable(new JoinRequest("Player"));
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
                if (onDisconnected != null) {
                    Gdx.app.postRunnable(onDisconnected);
                }
            }

            @Override
            public void received(Connection connection, Object object) {
                // Always immediately ACK any received ReliablePacket
                if (object instanceof ReliablePacket rp) {
                    connection.sendUDP(new AckPacket(rp.sequenceId));
                }

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
                        if (onJoinAccepted != null) {
                            Gdx.app.postRunnable(onJoinAccepted);
                        }
                    }
                    case "MapData" -> {
                        MapData md = (MapData) object;
                        handleMapChunk(md);
                    }
                    case "StartGamePacket" -> {
                        Gdx.app.log("GameClient", "StartGamePacket received.");
                        if (onStartGame != null) {
                            Gdx.app.postRunnable(onStartGame);
                        } else {
                            startGamePending = true;
                        }
                    }
                    case "PlayerPositionPacket" -> {
                        PlayerPositionPacket ppp = (PlayerPositionPacket) object;
                        if (onPlayerPositionReceived != null) {
                            Gdx.app.postRunnable(() -> onPlayerPositionReceived.accept(ppp));
                        }
                    }
                    case "PlayerDisconnectedPacket" -> {
                        PlayerDisconnectedPacket pdp = (PlayerDisconnectedPacket) object;
                        if (onPlayerDisconnected != null) {
                            Gdx.app.postRunnable(() -> onPlayerDisconnected.accept(pdp.playerId));
                        }
                    }
                    case "LobbyStatusPacket" -> {
                        LobbyStatusPacket lsp = (LobbyStatusPacket) object;
                        if (onLobbyStatus != null) {
                            Gdx.app.postRunnable(() -> onLobbyStatus.accept(lsp));
                        }
                    }
                    case "RaceStateChangedPacket" -> {
                        RaceStateChangedPacket rscp = (RaceStateChangedPacket) object;
                        if (onRaceStateChanged != null) {
                            Gdx.app.postRunnable(() -> onRaceStateChanged.accept(rscp));
                        }
                    }
                    case "SectorTimePacket" -> {
                        SectorTimePacket stp = (SectorTimePacket) object;
                        if (onSectorTime != null) {
                            Gdx.app.postRunnable(() -> onSectorTime.accept(stp));
                        }
                    }
                    case "RacePositionsUpdatePacket" -> {
                        RacePositionsUpdatePacket rpp = (RacePositionsUpdatePacket) object;
                        if (onRacePositionsUpdate != null) {
                            Gdx.app.postRunnable(() -> onRacePositionsUpdate.accept(rpp));
                        }
                    }
                    case "GridAssignmentPacket" -> {
                        GridAssignmentPacket gap = (GridAssignmentPacket) object;
                        if (onGridAssignment != null) {
                            Gdx.app.postRunnable(() -> onGridAssignment.accept(gap));
                        }
                    }
                    default -> {}
                }
            }
        });
    }

    public void connect(String ipAddress) {
        client.start();
        new Thread(() -> {
            try {
                // connect() blocks until TCP handshake is complete
                client.connect(5000, ipAddress, Network.TCP_PORT, Network.UDP_PORT);
            } catch (IOException e) {
                Gdx.app.error("GameClient", "Failed to connect", e);
                if (onConnectionFailed != null) {
                    Gdx.app.postRunnable(onConnectionFailed);
                }
            }
        }).start();
    }

    public void sendReliable(ReliablePacket packet) {
        if (connected) {
            reliabilityManager.send(client, packet);
        }
    }

    public void sendUnreliable(UnreliablePacket packet) {
        if (connected) {
            client.sendUDP(packet);
        }
    }

    public void update() {
        if (connected) {
            reliabilityManager.update();
        }
    }

    public void setOnMapReceived(java.util.function.Consumer<String> callback) {
        this.onMapReceived = callback;
        if (onMapReceived != null && completedMapJson != null) {
            Gdx.app.postRunnable(() -> {
                if (onMapReceived != null && completedMapJson != null) {
                    onMapReceived.accept(completedMapJson);
                    completedMapJson = null; // Consume it
                }
            });
        }
    }

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

    public void dispose() {
        client.stop();
    }

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
                
                if (onMapReceived != null) {
                    Gdx.app.postRunnable(() -> onMapReceived.accept(fullJson));
                } else {
                    completedMapJson = fullJson;
                }
                
                mapChunks = null;
                receivedChunksCount = 0;
            }
        }
    }
}
