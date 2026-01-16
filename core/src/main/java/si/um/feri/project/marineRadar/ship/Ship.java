package si.um.feri.project.marineRadar;

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

    // Timestamps
    public long timestamp;
    public long firstSeen;

    // UI state
    public boolean isTracked = false;

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
        this.routeInfo = new RouteInfo(lat, lon);
    }

    public void update(double lat, double lon, float speed, float course, float heading, int navStatus) {
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.course = course;
        this.heading = heading;
        this.navigationalStatus = navStatus;
        this.timestamp = System.currentTimeMillis();

        // Update route destination based on course and speed
        routeInfo.updateDestination(lat, lon, course, speed);
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
