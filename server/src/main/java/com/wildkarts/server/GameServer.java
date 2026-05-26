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
import com.wildkarts.track.TrackGenerator;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless authoritative server application.
 */
public class GameServer extends ApplicationAdapter {

    private Server server;
    private UdpReliabilityManager reliabilityManager;
    private final AtomicInteger playerIdGenerator = new AtomicInteger(1);
    
    // Tracks which connections have already joined to prevent duplicate spawns
    private final Map<Integer, Integer> playerConnectionMap = new HashMap<>();
    
    private String mapJson;
    private static final int CHUNK_SIZE = 8192; // 8KB chunks

    // Authoritative race state (FSM, sector validation, leaderboard)
    private TrackGenerator trackGenerator;
    private ServerRaceManager raceManager;

    @Override
    public void create() {
        server = new Server();
        reliabilityManager = new UdpReliabilityManager();

        Network.register(server.getKryo());

        initializeMap();

        raceManager = new ServerRaceManager(server, reliabilityManager, trackGenerator);

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
                raceManager.onPlayerDisconnected(connection);
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
                    case "JoinRequest" -> {
                        JoinRequest jr = (JoinRequest) object;
                        handleJoinRequest(connection, jr);
                    }
                    case "MapReadyPacket" -> {
                        Gdx.app.log("GameServer", "Client " + connection.getID() + " loaded map.");
                        reliabilityManager.send(connection, new StartGamePacket());
                        raceManager.onPlayerMapLoaded(connection);
                    }
                    case "PlayerPositionPacket" -> {
                        // Relay to peers AND feed the race manager so it can validate point passes.
                        PlayerPositionPacket ppp = (PlayerPositionPacket) object;
                        raceManager.onPlayerPosition(connection, ppp.x, ppp.y);
                        server.sendToAllExceptUDP(connection.getID(), object);
                    }
                    case "PlayerReadyPacket" -> {
                        raceManager.onPlayerReady(connection, (PlayerReadyPacket) object);
                    }
                    case "PlayerPassedPointPacket" -> {
                        raceManager.onPlayerPassedPoint(connection, (PlayerPassedPointPacket) object);
                    }
                    default -> {}
                }
            }
        });

        try {
            String bindIp = System.getProperty("bindIp", "0.0.0.0");
            if (bindIp.equals("0.0.0.0")) {
                server.bind(Network.TCP_PORT, Network.UDP_PORT);
            } else {
                server.bind(new InetSocketAddress(bindIp, Network.TCP_PORT),
                            new InetSocketAddress(bindIp, Network.UDP_PORT));
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

        // 2. Register with race manager (lobby tracking)
        raceManager.onPlayerJoined(playerId, connection, request.playerName);

        // 3. Send MapData in chunks
        sendMapData(connection);
    }

    private void initializeMap() {
        trackGenerator = new TrackGenerator();
        // The server looks for this specific file in its working directory (usually root or server folder)
        String serverMapFile = "Maps/server_map.json";
        
        if (!trackGenerator.loadMap(serverMapFile)) {
            Gdx.app.log("GameServer", "No saved map found at " + serverMapFile + ". Creating default track.");
            trackGenerator.addPoint(-20, -20);
            trackGenerator.addPoint(20, -20);
            trackGenerator.addPoint(20, 20);
            trackGenerator.addPoint(-20, 20);
        }
        
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        mapJson = json.toJson(trackGenerator.exportData());
        Gdx.app.log("GameServer", "Map initialized. JSON size: " + mapJson.length()
                + " bytes. Track points: " + trackGenerator.getManualPoints().size);
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

        // Drive the authoritative race FSM (~30 Hz via HeadlessApplication)
        if (raceManager != null) {
            raceManager.update(Gdx.graphics.getDeltaTime());
        }
    }

    @Override
    public void dispose() {
        server.stop();
    }
}
