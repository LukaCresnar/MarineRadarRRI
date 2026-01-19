package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.Gdx;
import si.um.feri.project.marineRadar.LocationHelper;

import java.util.ArrayList;
import java.util.List;

public class Ship {
    // Basic identification
    public String mmsi;
    public String name;
    public String callSign;
    public int imoNumber;

    // Position and movement
    public double lat;
    public double lon;
    public float speed;
    public float course;
    public float heading;
    public int navigationalStatus;

    // Ship characteristics
    public String shipType;
    public String cargo;
    public int shipLength;
    public int shipWidth;
    public float draught;

    // Voyage information
    public String destination;
    public String origin;
    public ETA eta;

    // Route information
    public RouteInfo routeInfo;

    // Location history (limited to 100 locations)
    public static final int MAX_LOCATION_HISTORY = 100;
    public static final long UPDATE_INTERVAL_MS = 2000; // 2 seconds
    public List<double[]> locationHistory = new ArrayList<>();
    public float rotation = 0f; // Calculated rotation in degrees
    private long lastLocationUpdateTime = 0;
    private long lastApiUpdateTime = 0; // Last time we received data from API

    // Timestamps
    public long timestamp;
    public long firstSeen;

    // UI state
    public boolean isTracked = false;
    public boolean isSelected = false;

    public Ship(String mmsi, double lat, double lon) {
        this.mmsi = mmsi;
        this.lat = lat;
        this.lon = lon;
        this.name = "Unknown";
        this.callSign = "";
        this.imoNumber = 0;
        this.shipType = "Unknown";
        this.cargo = "Unknown";
        this.destination = "Unknown";
        this.origin = "Unknown";
        this.speed = 0f;
        this.course = 0f;
        this.heading = 0f;
        this.navigationalStatus = 0;
        this.shipLength = 0;
        this.shipWidth = 0;
        this.draught = 0f;
        this.timestamp = System.currentTimeMillis();
        this.firstSeen = System.currentTimeMillis();
        this.lastLocationUpdateTime = System.currentTimeMillis();
        this.routeInfo = new RouteInfo(lat, lon);
        // Add initial location to history
        locationHistory.add(new double[]{lat, lon});
    }

    public void update(double lat, double lon, float speed, float course, float heading, int navStatus) {
        long currentTime = System.currentTimeMillis();
        
        // Only update location history every 2 seconds
        if (currentTime - lastLocationUpdateTime >= UPDATE_INTERVAL_MS) {
            // Calculate rotation based on previous and current location
            if (!locationHistory.isEmpty()) {
                double[] lastLocation = locationHistory.get(locationHistory.size() - 1);
                this.rotation = calculateRotation(lastLocation[0], lastLocation[1], lat, lon);
            }
            
            // Add current location to history
            locationHistory.add(new double[]{lat, lon});
            
            // Limit history to MAX_LOCATION_HISTORY entries
            while (locationHistory.size() > MAX_LOCATION_HISTORY) {
                locationHistory.remove(0);
            }
            
            // Debug log for selected ship (clicked)
            if (isSelected) {
                Gdx.app.log("Ship", name + " [" + mmsi + "] - History: " + locationHistory.size() + 
                    " locations, Rotation: " + String.format("%.1f", rotation) + "°" +
                    ", Pos: (" + String.format("%.4f", lat) + ", " + String.format("%.4f", lon) + ")");
            }
            
            lastLocationUpdateTime = currentTime;
        }
        
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.course = course;
        this.heading = heading;
        this.navigationalStatus = navStatus;
        this.timestamp = currentTime;
        this.lastApiUpdateTime = currentTime;

        // Update route destination based on course and speed
        routeInfo.updateDestination(lat, lon, course, speed);
    }

    // Simulated movement: advance position by deltaMs based on speed (knots) and course (degrees)
    // This is used to visually move ships between PositionReports when requested by the renderer.
    public void simulateMovement(long deltaMs) {
        if (speed <= 0.0f) return; // stationary

        double hours = deltaMs / 3600000.0; // ms -> hours
        double distanceNm = speed * hours; // nautical miles
        if (distanceNm <= 0.0) return;

        // Convert nautical miles to degrees (approx): 1 nm ≈ 1/60 degree latitude
        double distanceDegLat = distanceNm / 60.0;

        double courseRad = Math.toRadians(course);

        // Compute new position taking latitude into account for longitude scaling
        double newLat = lat + distanceDegLat * Math.cos(courseRad);
        double newLon = lon + (distanceDegLat * Math.sin(courseRad)) / Math.cos(Math.toRadians(lat));

        // Keep previous for rotation calculation
        double prevLat = lat;
        double prevLon = lon;

        // Update position and timestamp
        this.lat = newLat;
        this.lon = newLon;
        this.timestamp = System.currentTimeMillis();

        // Update rotation based on movement
        this.rotation = calculateRotation(prevLat, prevLon, newLat, newLon);

        // Add to history and clamp size
        locationHistory.add(new double[]{newLat, newLon});
        while (locationHistory.size() > MAX_LOCATION_HISTORY) {
            locationHistory.remove(0);
        }

        // Update route destination based on current course/speed
        routeInfo.updateDestination(lat, lon, course, speed);

        // Debug log for selected ship
        if (isSelected) {
            Gdx.app.log("Ship", name + " [" + mmsi + "] - SIMULATED - History: " + locationHistory.size() +
                " locations, Rotation: " + String.format("%.1f", rotation) + "°" +
                ", Pos: (" + String.format("%.4f", lat) + ", " + String.format("%.4f", lon) + ")" +
                ", Speed: " + String.format("%.1f", speed) + " kn, Course: " + String.format("%.1f", course));
        }
    }


    /**
     * Calculate rotation angle (in degrees) from previous location to current location.
     * 0 degrees = North, 90 = East, 180 = South, 270 = West
     */
    private float calculateRotation(double prevLat, double prevLon, double currLat, double currLon) {
        double deltaLon = currLon - prevLon;
        double deltaLat = currLat - prevLat;
        
        // If no significant movement, keep previous rotation
        if (Math.abs(deltaLat) < 0.00001 && Math.abs(deltaLon) < 0.00001) {
            return this.rotation;
        }
        
        // Calculate bearing using atan2
        // atan2 returns angle from -PI to PI, where 0 is East
        // We need to convert to compass bearing where 0 is North
        double angleRad = Math.atan2(deltaLon, deltaLat);
        float angleDeg = (float) Math.toDegrees(angleRad);
        
        // Normalize to 0-360 range
        if (angleDeg < 0) {
            angleDeg += 360;
        }
        
        return angleDeg;
    }

    public void updateStaticData(String name, String callSign, int imoNumber, String shipType,
                                 String destination, int length, int width, float draught, ETA eta) {
        if (name != null && !name.isEmpty()) this.name = name;
        if (callSign != null && !callSign.isEmpty()) this.callSign = callSign;
        if (imoNumber > 0) this.imoNumber = imoNumber;
        if (shipType != null && !shipType.isEmpty()) this.shipType = shipType;
        if (destination != null && !destination.isEmpty()) this.destination = destination;
        this.shipLength = length;
        this.shipWidth = width;
        this.draught = draught;
        this.eta = eta;
    }

    public boolean isStale() {
        return System.currentTimeMillis() - timestamp > 600000;
    }

    public boolean isMoving() {
        return speed > 0.5f;
    }

    public String getNavigationalStatusText() {
        switch (navigationalStatus) {
            case 0: return "Under way using engine";
            case 1: return "At anchor";
            case 2: return "Not under command";
            case 3: return "Restricted manoeuvrability";
            case 4: return "Constrained by draught";
            case 5: return "Moored";
            case 6: return "Aground";
            case 7: return "Engaged in fishing";
            case 8: return "Under way sailing";
            default: return "Unknown";
        }
    }

    public String getShipTypeDescription() {
        return shipType != null && !shipType.equals("Unknown") ? shipType : "Vessel";
    }

    public String getFormattedSize() {
        if (shipLength > 0 && shipWidth > 0) {
            return String.format("%dm × %dm", shipLength, shipWidth);
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", name, mmsi);
    }

    public static class ETA {
        public int month;
        public int day;
        public int hour;
        public int minute;

        public ETA(int month, int day, int hour, int minute) {
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
        }

        public boolean isValid() {
            return month > 0 && month <= 12 && day > 0 && day <= 31;
        }

        @Override
        public String toString() {
            if (!isValid()) return "N/A";
            return String.format("%02d/%02d %02d:%02d", month, day, hour, minute);
        }
    }

    public static class RouteInfo {
        public double startLat;
        public double startLon;
        public double destLat;
        public double destLon;
        public String startLocation;
        public String destLocation;

        public RouteInfo(double startLat, double startLon) {
            this.startLat = startLat;
            this.startLon = startLon;
            this.startLocation = LocationHelper.getLocationName(startLat, startLon);
            updateDestination(startLat, startLon, 0, 0);
        }

        public void updateDestination(double currentLat, double currentLon, float course, float speed) {
            if (speed < 0.5f) {
                destLat = currentLat;
                destLon = currentLon;
            } else {
                // Project 12 hours ahead
                double distance = speed * 12.0 / 60.0; // Convert to degrees (rough)
                double courseRad = Math.toRadians(course);
                destLat = currentLat + Math.cos(courseRad) * distance;
                destLon = currentLon + Math.sin(courseRad) * distance;
            }
            destLocation = LocationHelper.getLocationName(destLat, destLon);
        }
    }
}
