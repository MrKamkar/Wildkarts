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
import com.wildkarts.track.TrackGenerator;
import com.wildkarts.track.TrackData;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

/**
 * Headless authoritative server application.
 */
public class GameServer extends ApplicationAdapter {

    private Server server;
    private UdpReliabilityManager reliabilityManager;
    private AtomicInteger playerIdGenerator = new AtomicInteger(1);
    
    // Tracks which connections have already joined to prevent duplicate spawns
    private java.util.Map<Integer, Integer> playerConnectionMap = new java.util.HashMap<>();
    
    private String mapJson;
    private static final int CHUNK_SIZE = 8192; // 8KB chunks

    @Override
    public void create() {
        server = new Server();
        reliabilityManager = new UdpReliabilityManager();

        Network.register(server.getKryo());

        initializeMap();

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
                } else if (object instanceof MapReadyPacket) {
                    Gdx.app.log("GameServer", "Client " + connection.getID() + " is READY. Starting game for them.");
                    reliabilityManager.send(connection, new StartGamePacket());
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

        // 2. Send MapData in chunks
        sendMapData(connection);
    }

    private void initializeMap() {
        TrackGenerator generator = new TrackGenerator();
        // The server looks for this specific file in its working directory (usually root or server folder)
        String serverMapFile = "Maps/server_map.json";
        
        if (!generator.loadMap(serverMapFile)) {
            Gdx.app.log("GameServer", "No saved map found at " + serverMapFile + ". Creating default track.");
            generator.addPoint(-20, -20);
            generator.addPoint(20, -20);
            generator.addPoint(20, 20);
            generator.addPoint(-20, 20);
        }
        
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        mapJson = json.toJson(generator.exportData());
        Gdx.app.log("GameServer", "Map initialized. JSON size: " + mapJson.length() + " bytes.");
    }

    private void sendMapData(Connection connection) {
        int totalChunks = (int) Math.ceil((double) mapJson.length() / CHUNK_SIZE);
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(mapJson.length(), (i + 1) * CHUNK_SIZE);
            String chunk = mapJson.substring(start, end);
            reliabilityManager.send(connection, new MapData(totalChunks, i, chunk));
        }
        Gdx.app.log("GameServer", "Sent " + totalChunks + " chunks of map data to connection " + connection.getID());
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
