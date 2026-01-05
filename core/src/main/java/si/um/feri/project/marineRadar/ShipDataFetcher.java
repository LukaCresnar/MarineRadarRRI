package si.um.feri.project.marineRadar;

import com.badlogic.gdx.Gdx;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches real-time ship data from AISStream.io via WebSocket
 * Documentation: https://aisstream.io/documentation
 */
public class ShipDataFetcher {

    private final List<Ship> ships;
    private final Map<String, Ship> shipMap = new ConcurrentHashMap<>();
    private WebSocketClient wsClient;
    private boolean running = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private static final String API_KEY = "fd72c19f82a5c62bda5c4cc17866c4e3577920a5";
    private static final String WS_URL = "wss://stream.aisstream.io/v0/stream";

    public ShipDataFetcher(List<Ship> ships) {
        this.ships = ships;
    }

    public void startFetching() {
        if (running) return;
        running = true;
        reconnectAttempts = 0;

        connectWebSocket();
    }

    private void connectWebSocket() {
        try {
            wsClient = new WebSocketClient(new URI(WS_URL)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Gdx.app.log("ShipDataFetcher", "=== WebSocket Connected ===");
                    Gdx.app.log("ShipDataFetcher", "HTTP Status: " + handshake.getHttpStatus());
                    Gdx.app.log("ShipDataFetcher", "HTTP Status Message: " + handshake.getHttpStatusMessage());
                    reconnectAttempts = 0;

                    // EXACT format from the working example - no spaces, no formatting
                    String subscribeMsg = "{\"APIKey\":\"fd72c19f82a5c62bda5c4cc17866c4e3577920a5\",\"BoundingBoxes\":[[[-90,-180],[90,180]]]}";

                    Gdx.app.log("ShipDataFetcher", "Sending subscription...");
                    Gdx.app.log("ShipDataFetcher", "Message: " + subscribeMsg);
                    Gdx.app.log("ShipDataFetcher", "Message length: " + subscribeMsg.length());

                    try {
                        send(subscribeMsg);
                        Gdx.app.log("ShipDataFetcher", "Subscription sent successfully!");
                    } catch (Exception e) {
                        Gdx.app.error("ShipDataFetcher", "Failed to send subscription: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(ByteBuffer message) {
                    // AISStream.io sends messages as ByteBuffers, not Strings!
                    try {
                        String jsonString = StandardCharsets.UTF_8.decode(message).toString();
                        Gdx.app.log("ShipDataFetcher", "=== Received Message ===");
                        Gdx.app.log("ShipDataFetcher", "Message preview: " + jsonString.substring(0, Math.min(300, jsonString.length())));
                        parseAISMessage(jsonString);
                    } catch (Exception e) {
                        Gdx.app.error("ShipDataFetcher", "Error parsing ByteBuffer message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // This method is unused as aisstream.io returns messages as byte buffers
                    Gdx.app.log("ShipDataFetcher", "=== Received String Message (Unexpected) ===");
                    Gdx.app.log("ShipDataFetcher", message.substring(0, Math.min(300, message.length())));

                    // Try to parse it anyway
                    try {
                        parseAISMessage(message);
                    } catch (Exception e) {
                        Gdx.app.error("ShipDataFetcher", "Error parsing string message: " + e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    String closeReason = reason != null && !reason.isEmpty() ? reason : "No reason provided";
                    Gdx.app.log("ShipDataFetcher", "=== Connection Closed ===");
                    Gdx.app.log("ShipDataFetcher", String.format(
                        "Closed by: %s | Code: %d | Reason: %s",
                        remote ? "REMOTE PEER" : "US", code, closeReason
                    ));

                    // Common WebSocket close codes:
                    // 1000 = Normal closure
                    // 1001 = Going away
                    // 1002 = Protocol error
                    // 1003 = Unsupported data
                    // 1006 = Abnormal closure
                    // 1008 = Policy violation
                    // 1011 = Server error

                    if (code == 1008) {
                        Gdx.app.error("ShipDataFetcher", "Policy violation - Check your API key!");
                    } else if (code == 1002) {
                        Gdx.app.error("ShipDataFetcher", "Protocol error - Subscription format may be wrong!");
                    }

                    // Attempt reconnect if still running and haven't exceeded max attempts
                    if (running && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && code != 1008) {
                        reconnectAttempts++;
                        int delay = Math.min(30, 5 * reconnectAttempts);

                        Gdx.app.log("ShipDataFetcher", "Reconnect attempt " + reconnectAttempts + " in " + delay + " seconds...");

                        new Thread(() -> {
                            try {
                                Thread.sleep(delay * 1000);
                                if (running) {
                                    connectWebSocket();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    } else if (code == 1008) {
                        Gdx.app.error("ShipDataFetcher", "NOT RECONNECTING - API key rejected. Please verify your API key at aisstream.io");
                        running = false;
                    } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                        Gdx.app.error("ShipDataFetcher", "Max reconnect attempts reached.");
                    }
                }

                @Override
                public void onError(Exception e) {
                    Gdx.app.error("ShipDataFetcher", "WebSocket error: " + e.getMessage());
                    e.printStackTrace();
                }
            };

            // Add connection timeout
            wsClient.setConnectionLostTimeout(30);
            wsClient.connect();

        } catch (Exception e) {
            Gdx.app.error("ShipDataFetcher", "Failed to create WebSocket: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseAISMessage(String json) {
        try {
            JSONObject root = new JSONObject(json);

            // Check message type
            if (!root.has("MessageType")) {
                return;
            }

            String messageType = root.getString("MessageType");

            // We only care about PositionReport messages
            if (!messageType.equals("PositionReport")) {
                return;
            }

            // Get the Message object
            if (!root.has("Message")) {
                return;
            }

            JSONObject message = root.getJSONObject("Message");

            // Get PositionReport object
            if (!message.has("PositionReport")) {
                return;
            }

            JSONObject positionReport = message.getJSONObject("PositionReport");

            // Extract UserID (MMSI) from PositionReport, not Message
            if (!positionReport.has("UserID")) {
                return;
            }

            String mmsi = String.valueOf(positionReport.getInt("UserID"));

            // Extract coordinates
            if (!positionReport.has("Latitude") || !positionReport.has("Longitude")) {
                return;
            }

            double lat = positionReport.getDouble("Latitude");
            double lon = positionReport.getDouble("Longitude");

            // Validate coordinates
            if (lat == 0.0 && lon == 0.0) return;
            if (Double.isNaN(lat) || Double.isNaN(lon)) return;
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return;

            // Extract navigation data
            float speed = (float) positionReport.optDouble("Sog", 0.0);
            float course = (float) positionReport.optDouble("Cog", 0.0);
            float heading = (float) positionReport.optDouble("TrueHeading", course);

            // Get ship name from MetaData if available
            String shipName = "Unknown";
            String shipType = "Unknown";

            if (root.has("MetaData")) {
                try {
                    JSONObject metadata = root.getJSONObject("MetaData");
                    String name = metadata.optString("ShipName", "").trim();

                    if (!name.isEmpty() && !name.equals("Unknown")) {
                        shipName = name;
                    }

                    // Note: ShipType is usually in static data, not metadata
                } catch (Exception e) {
                    // Ignore metadata errors
                }
            }

            // Final values for the lambda
            final String finalMmsi = mmsi;
            final double finalLat = lat;
            final double finalLon = lon;
            final float finalSpeed = speed;
            final float finalCourse = course;
            final float finalHeading = heading;
            final String finalShipName = shipName;
            final String finalShipType = shipType;

            // Update on GL thread
            Gdx.app.postRunnable(() -> {
                Ship ship = shipMap.get(finalMmsi);

                if (ship == null) {
                    // New ship discovered
                    ship = new Ship(finalMmsi, finalLat, finalLon);
                    ship.name = finalShipName;
                    ship.type = finalShipType;
                    ship.speed = finalSpeed;
                    ship.course = finalCourse;
                    ship.heading = finalHeading;

                    ships.add(ship);
                    shipMap.put(finalMmsi, ship);

                    Gdx.app.log("ShipDataFetcher", String.format(
                        "NEW SHIP #%d: %s (%s) at (%.4f, %.4f) - Speed: %.1f knots",
                        ships.size(), ship.name, finalMmsi, finalLat, finalLon, finalSpeed
                    ));
                } else {
                    // Update existing ship
                    ship.update(finalLat, finalLon, finalSpeed, finalCourse, finalHeading);
                }
            });

        } catch (Exception e) {
            Gdx.app.error("ShipDataFetcher", "Error parsing AIS message: " + e.getMessage());
            if (Gdx.app.getLogLevel() == com.badlogic.gdx.Application.LOG_DEBUG) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;

        if (wsClient != null) {
            try {
                wsClient.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Gdx.app.log("ShipDataFetcher", "Stopped - Total ships: " + ships.size());
    }

    public int getShipCount() {
        return ships.size();
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }
}
