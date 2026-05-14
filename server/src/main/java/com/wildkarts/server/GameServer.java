package com.wildkarts.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.wildkarts.net.Network;
import com.wildkarts.net.ReliablePacket;
import com.wildkarts.net.UdpReliabilityManager;
import com.wildkarts.net.AckPacket;
import com.wildkarts.net.packets.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless authoritative server application.
 */
public class GameServer extends ApplicationAdapter {

    private Server server;
    private UdpReliabilityManager reliabilityManager;
    private AtomicInteger playerIdGenerator = new AtomicInteger(1);
    
    // Tracks which connections have already joined to prevent duplicate spawns
    private java.util.Map<Integer, Integer> playerConnectionMap = new java.util.HashMap<>();

    @Override
    public void create() {
        server = new Server();
        reliabilityManager = new UdpReliabilityManager();

        Network.register(server.getKryo());

        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                Gdx.app.log("GameServer", "Client connected: " + connection.getRemoteAddressTCP());
            }

            @Override
            public void disconnected(Connection connection) {
                Gdx.app.log("GameServer", "Client disconnected: " + connection.getRemoteAddressTCP());
                Integer playerId = playerConnectionMap.remove(connection.getID());
                if (playerId != null) {
                    server.sendToAllExceptTCP(connection.getID(), new PlayerDisconnectedPacket(playerId));
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
                } else if (object instanceof JoinRequest) {
                    handleJoinRequest(connection, (JoinRequest) object);
                } else if (object instanceof PlayerPositionPacket) {
                    // Broadcast to other players
                    server.sendToAllExceptUDP(connection.getID(), object);
                }
            }
        });

        try {
            String bindIp = System.getProperty("bindIp", "0.0.0.0");
            if (bindIp.equals("0.0.0.0")) {
                server.bind(Network.TCP_PORT, Network.UDP_PORT);
            } else {
                server.bind(new java.net.InetSocketAddress(bindIp, Network.TCP_PORT),
                            new java.net.InetSocketAddress(bindIp, Network.UDP_PORT));
            }
            server.start();
            Gdx.app.log("GameServer", "Server started on " + bindIp + " TCP " + Network.TCP_PORT + " and UDP " + Network.UDP_PORT);
        } catch (IOException e) {
            Gdx.app.error("GameServer", "Failed to start server", e);
            Gdx.app.exit();
        }
    }

    private void handleJoinRequest(Connection connection, JoinRequest request) {
        if (playerConnectionMap.containsKey(connection.getID())) {
            // We already processed this client's join request. 
            // The client is retransmitting because our AckPacket or JoinAccepted was lost/blocked.
            return;
        }

        int playerId = playerIdGenerator.getAndIncrement();
        playerConnectionMap.put(connection.getID(), playerId);
        Gdx.app.log("GameServer", "Player joined: " + request.playerName + " (ID: " + playerId + ")");

        // 1. Send JoinAccepted
        reliabilityManager.send(connection, new JoinAccepted(playerId));

        // 2. Send MapData (dummy map for now)
        float[] mapX = {0f, 10f, 10f, 0f};
        float[] mapY = {0f, 0f, 10f, 10f};
        reliabilityManager.send(connection, new MapData(mapX, mapY));
    }

    @Override
    public void render() {
        // Update reliability manager to handle retransmissions
        reliabilityManager.update();
    }

    @Override
    public void dispose() {
        server.stop();
    }
}
