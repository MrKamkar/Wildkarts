package com.wildkarts.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.wildkarts.net.AckPacket;
import com.wildkarts.net.Network;
import com.wildkarts.net.ReliablePacket;
import com.wildkarts.net.UdpReliabilityManager;
import com.wildkarts.net.packets.JoinAccepted;
import com.wildkarts.net.packets.JoinRequest;
import com.wildkarts.net.packets.MapData;
import com.wildkarts.net.packets.MapReadyPacket;
import com.wildkarts.net.packets.PlayerDisconnectedPacket;
import com.wildkarts.net.packets.PlayerPassedPointPacket;
import com.wildkarts.net.packets.PlayerPositionPacket;
import com.wildkarts.net.packets.PlayerReadyPacket;
import com.wildkarts.net.packets.StartGamePacket;
import com.wildkarts.track.TrackGenerator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bezgraficzna aplikacja serwera autorytatywnego.
 * Obsługuje połączenia KryoNet, dystrybucję mapy i deleguje logikę wyścigu do {@link ServerRaceManager}.
 */
public class GameServer extends ApplicationAdapter {

    private Server server;
    private UdpReliabilityManager reliabilityManager;
    private final AtomicInteger playerIdGenerator = new AtomicInteger(1);

    /** Mapuje ID połączenia KryoNet na przypisany identyfikator gracza. */
    private final Map<Integer, Integer> playerConnectionMap = new HashMap<>();

    private String mapJson;
    private static final int CHUNK_SIZE = 8192;

    private TrackGenerator trackGenerator;
    private ServerRaceManager raceManager;

    /** Uruchamia serwer KryoNet, ładuje mapę i rejestruje listener pakietów. */
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
                if (playerId != null)
                    server.sendToAllExceptTCP(connection.getID(), new PlayerDisconnectedPacket(playerId));
                raceManager.onPlayerDisconnected(connection);
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

    /**
     * Rejestruje nowego gracza, wysyła potwierdzenie dołączenia i fragmenty mapy.
     * Ignoruje powtórne {@link JoinRequest} z tego samego połączenia (retransmisja UDP).
     */
    private void handleJoinRequest(Connection connection, JoinRequest request) {
        if (playerConnectionMap.containsKey(connection.getID()))
            return;

        int playerId = playerIdGenerator.getAndIncrement();
        playerConnectionMap.put(connection.getID(), playerId);
        Gdx.app.log("GameServer", "Player joined: " + request.playerName + " (ID: " + playerId + ")");

        reliabilityManager.send(connection, new JoinAccepted(playerId));

        raceManager.onPlayerJoined(playerId, connection, request.playerName);

        sendMapData(connection);
    }

    /**
     * Ładuje mapę serwera z pliku lub tworzy domyślny tor kwadratowy.
     */
    private void initializeMap() {
        trackGenerator = new TrackGenerator();
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

    /**
     * Wysyła dane mapy w fragmentach po {@link #CHUNK_SIZE} bajtów.
     *
     * @param connection połączenie docelowego klienta
     */
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

    /** Aktualizuje retransmisje UDP i logikę wyścigu (~30 Hz). */
    @Override
    public void render() {
        reliabilityManager.update();

        if (raceManager != null)
            raceManager.update(Gdx.graphics.getDeltaTime());
    }

    /** Zatrzymuje serwer KryoNet. */
    @Override
    public void dispose() {
        server.stop();
    }
}
