package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.Gdx;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class ShipDataFetcher {

    private final List<Ship> ships;
    private final Map<String, Ship> shipMap = new ConcurrentHashMap<>();
    private WebSocketClient wsClient;
    private boolean running = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private int maxShips = 1000; // Limit to prevent lag
    private boolean maxShipsReached = false;

    private static final String API_KEY = "fd72c19f82a5c62bda5c4cc17866c4e3577920a5";
    private static final String WS_URL = "wss://stream.aisstream.io/v0/stream";

    private Random random = new Random();

    public ShipDataFetcher(List<Ship> ships) {
        this.ships = ships;
    }

    public void setMaxShips(int maxShips) {
        this.maxShips = maxShips;
        if (ships.size() < maxShips) {
            maxShipsReached = false;
        }
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
                    Gdx.app.log("ShipDataFetcher", "Connected to AISStream.io");
                    reconnectAttempts = 0;
                    maxShipsReached = false;

                    // Subscribe to worldwide data
                    String subscribeMsg = "{\"APIKey\":\"" + API_KEY +
                        "\",\"BoundingBoxes\":[[[-90,-180],[90,180]]]}";

                    send(subscribeMsg);
                    Gdx.app.log("ShipDataFetcher", "Subscription active");
                }

                @Override
                public void onMessage(ByteBuffer message) {
                    if (maxShipsReached) return;
                    try {
                        String jsonString = StandardCharsets.UTF_8.decode(message).toString();
                        parseAISMessage(jsonString);
                    } catch (Exception e) {
                        // Silently ignore
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (maxShipsReached) return;
                    try {
                        parseAISMessage(message);
                    } catch (Exception e) {
                        // Silently ignore
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Gdx.app.log("ShipDataFetcher", "Connection closed: " + code);

                    if (running && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && code != 1008) {
                        reconnectAttempts++;
                        int delay = Math.min(30, 5 * reconnectAttempts);

                        new Thread(() -> {
                            try {
                                Thread.sleep(delay * 1000);
                                if (running) connectWebSocket();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Gdx.app.error("ShipDataFetcher", "Error: " + e.getMessage());
                }
            };

            wsClient.setConnectionLostTimeout(30);
            wsClient.connect();

        } catch (Exception e) {
            Gdx.app.error("ShipDataFetcher", "Connection failed: " + e.getMessage());
        }
    }

    private void parseAISMessage(String json) {
        try {
            if (ships.size() >= maxShips) {
                if (!maxShipsReached) {
                    maxShipsReached = true;
                    Gdx.app.log("ShipDataFetcher", "Max ships reached (" + maxShips + "), pausing updates");
                }
                return;
            }

            JSONObject root = new JSONObject(json);

            if (!root.has("MessageType")) return;
            String messageType = root.getString("MessageType");

            if (messageType.equals("PositionReport")) {
                parsePositionReport(root);
            } else if (messageType.equals("ShipStaticData")) {
                parseStaticData(root);
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private void parsePositionReport(JSONObject root) {
        try {
            JSONObject message = root.getJSONObject("Message");
            JSONObject posReport = message.getJSONObject("PositionReport");

            if (!posReport.has("UserID")) return;

            String mmsi = String.valueOf(posReport.getInt("UserID"));
            double lat = posReport.getDouble("Latitude");
            double lon = posReport.getDouble("Longitude");

            if (lat == 0.0 && lon == 0.0) return;
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return;

            float speed = (float) posReport.optDouble("Sog", 0.0);
            float course = (float) posReport.optDouble("Cog", 0.0);
            float heading = (float) posReport.optDouble("TrueHeading", course);
            int navStatus = posReport.optInt("NavigationalStatus", 15);

            String shipName = "Unknown";
            if (root.has("MetaData")) {
                JSONObject metadata = root.getJSONObject("MetaData");
                shipName = metadata.optString("ShipName", generateShipName()).trim();
            } else {
                shipName = generateShipName();
            }

            final String fMmsi = mmsi;
            final double fLat = lat;
            final double fLon = lon;
            final float fSpeed = speed;
            final float fCourse = course;
            final float fHeading = heading;
            final int fNavStatus = navStatus;
            final String fShipName = shipName;

            Gdx.app.postRunnable(() -> {
                Ship ship = shipMap.get(fMmsi);

                if (ship == null && ships.size() < maxShips) {
                    ship = new Ship(fMmsi, fLat, fLon);
                    ship.name = fShipName;
                    enhanceShipData(ship);
                    // Initialize movement/state from first position report
                    ship.update(fLat, fLon, fSpeed, fCourse, fHeading, fNavStatus);
                    ships.add(ship);
                    shipMap.put(fMmsi, ship);
                } else if (ship != null) {
                    ship.update(fLat, fLon, fSpeed, fCourse, fHeading, fNavStatus);
                }
            });

        } catch (Exception e) {
            // Ignore
        }
    }

    private void parseStaticData(JSONObject root) {
        try {
            JSONObject message = root.getJSONObject("Message");
            JSONObject staticData = message.getJSONObject("ShipStaticData");

            if (!staticData.has("UserID")) return;

            String mmsi = String.valueOf(staticData.getInt("UserID"));
            String name = staticData.optString("Name", "").trim();
            String callSign = staticData.optString("CallSign", "").trim();
            int imoNumber = staticData.optInt("ImoNumber", 0);
            int shipType = staticData.optInt("Type", 0);
            String destination = staticData.optString("Destination", "").trim();
            float draught = (float) staticData.optDouble("MaximumStaticDraught", 0.0);

            JSONObject dimension = staticData.optJSONObject("Dimension");
            int length = 0, width = 0;
            if (dimension != null) {
                int a = dimension.optInt("A", 0);
                int b = dimension.optInt("B", 0);
                int c = dimension.optInt("C", 0);
                int d = dimension.optInt("D", 0);
                length = a + b;
                width = c + d;
            }

            Ship.ETA eta = null;
            if (staticData.has("Eta")) {
                JSONObject etaObj = staticData.getJSONObject("Eta");
                int month = etaObj.optInt("Month", 0);
                int day = etaObj.optInt("Day", 0);
                int hour = etaObj.optInt("Hour", 0);
                int minute = etaObj.optInt("Minute", 0);
                eta = new Ship.ETA(month, day, hour, minute);
            }

            String shipTypeStr = decodeShipType(shipType);

            final String fMmsi = mmsi;
            final String fName = name.isEmpty() ? generateShipName() : name;
            final String fCallSign = callSign.isEmpty() ? generateCallSign() : callSign;
            final int fImo = imoNumber > 0 ? imoNumber : generateIMO();
            final String fType = shipTypeStr;
            final String fDest = destination;
            final int fLength = length > 0 ? length : random.nextInt(150) + 50;
            final int fWidth = width > 0 ? width : random.nextInt(30) + 10;
            final float fDraught = draught > 0 ? draught : random.nextFloat() * 10 + 5;
            final Ship.ETA fEta = eta;

            Gdx.app.postRunnable(() -> {
                Ship ship = shipMap.get(fMmsi);

                if (ship != null) {
                    ship.updateStaticData(fName, fCallSign, fImo, fType, fDest,
                        fLength, fWidth, fDraught, fEta);
                }
            });

        } catch (Exception e) {
            // Ignore
        }
    }

    private void enhanceShipData(Ship ship) {
        // Fill in missing data with plausible values
        if (ship.callSign.isEmpty()) {
            ship.callSign = generateCallSign();
        }
        if (ship.imoNumber == 0) {
            ship.imoNumber = generateIMO();
        }
        if (ship.shipLength == 0) {
            ship.shipLength = random.nextInt(150) + 50;
            ship.shipWidth = random.nextInt(30) + 10;
        }
        if (ship.draught == 0) {
            ship.draught = random.nextFloat() * 10 + 5;
        }
        if (ship.cargo.equals("Unknown")) {
            ship.cargo = generateCargo();
        }
    }

    private String generateShipName() {
        String[] prefixes = {"MV", "SS", "MSC", "COSCO", "MAERSK", "CMA CGM"};
        String[] names = {"AURORA", "PACIFIC", "ATLANTIC", "HORIZON", "NAVIGATOR",
            "EXPLORER", "VOYAGER", "PIONEER", "DISCOVERY", "ENDEAVOR"};
        return prefixes[random.nextInt(prefixes.length)] + " " +
            names[random.nextInt(names.length)];
    }

    private String generateCallSign() {
        char[] letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        return "" + letters[random.nextInt(26)] + letters[random.nextInt(26)] +
            letters[random.nextInt(26)] + (1000 + random.nextInt(9000));
    }

    private int generateIMO() {
        return 7000000 + random.nextInt(3000000);
    }

    private String generateCargo() {
        String[] cargos = {"Container", "Bulk Cargo", "Oil Tanker", "General Cargo",
            "Vehicles", "Refrigerated Goods", "Chemicals", "LNG"};
        return cargos[random.nextInt(cargos.length)];
    }

    private String decodeShipType(int code) {
        if (code >= 20 && code <= 29) return "Wing in ground (WIG)";
        if (code == 30) return "Fishing Vessel";
        if (code == 31 || code == 32) return "Towing Vessel";
        if (code == 33) return "Dredging Vessel";
        if (code == 34) return "Diving Operations";
        if (code == 35) return "Military Vessel";
        if (code == 36) return "Sailing Vessel";
        if (code == 37) return "Pleasure Craft";
        if (code >= 40 && code <= 49) return "High Speed Craft";
        if (code == 50) return "Pilot Vessel";
        if (code == 51) return "Search and Rescue";
        if (code == 52) return "Tug Boat";
        if (code == 53) return "Port Tender";
        if (code == 54) return "Anti-pollution Vessel";
        if (code == 55) return "Law Enforcement";
        if (code >= 60 && code <= 69) return "Passenger Ship";
        if (code >= 70 && code <= 79) return "Cargo Ship";
        if (code >= 80 && code <= 89) return "Tanker";
        if (code >= 90 && code <= 99) return "Other Vessel";
        return "Cargo Ship";
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
        Gdx.app.log("ShipDataFetcher", "Stopped");
    }

    public int getShipCount() {
        return ships.size();
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }
}
