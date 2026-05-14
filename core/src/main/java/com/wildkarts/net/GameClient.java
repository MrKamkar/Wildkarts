package com.wildkarts.net;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.wildkarts.net.packets.*;
import com.wildkarts.net.AckPacket;
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
                if (onDisconnected != null) {
                    Gdx.app.postRunnable(onDisconnected);
                }
            }

            @Override
            public void received(Connection connection, Object object) {
                // Always immediately ACK any received ReliablePacket
                if (object instanceof ReliablePacket) {
                    connection.sendUDP(new AckPacket(((ReliablePacket) object).sequenceId));
                }

                if (object instanceof AckPacket) {
                    reliabilityManager.onAckReceived(((AckPacket) object).acknowledgedId);
                } else if (object instanceof JoinAccepted) {
                    localPlayerId = ((JoinAccepted) object).playerId;
                    Gdx.app.log("GameClient", "Join accepted. ID: " + localPlayerId);
                    if (onJoinAccepted != null) {
                        Gdx.app.postRunnable(onJoinAccepted);
                    }
                } else if (object instanceof MapData) {
                    Gdx.app.log("GameClient", "MapData received. Points: " + ((MapData) object).pointsX.length);
                    // Handle map data here
                } else if (object instanceof PlayerPositionPacket) {
                    if (onPlayerPositionReceived != null) {
                        PlayerPositionPacket packet = (PlayerPositionPacket) object;
                        Gdx.app.postRunnable(() -> onPlayerPositionReceived.accept(packet));
                    }
                } else if (object instanceof PlayerDisconnectedPacket) {
                    if (onPlayerDisconnected != null) {
                        int id = ((PlayerDisconnectedPacket) object).playerId;
                        Gdx.app.postRunnable(() -> onPlayerDisconnected.accept(id));
                    }
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

    public void dispose() {
        client.stop();
    }
}
