package si.um.feri.project.marineRadar;

public class Ship {
    public String mmsi;
    public String name;
    public String type;
    public double lat;
    public double lon;
    public float speed;      // Speed in knots
    public float course;     // Course over ground in degrees
    public float heading;    // True heading in degrees
    public long timestamp;   // Last update timestamp

    public Ship(String mmsi, double lat, double lon) {
        this.mmsi = mmsi;
        this.lat = lat;
        this.lon = lon;
        this.name = "Unknown";
        this.type = "Unknown";
        this.speed = 0f;
        this.course = 0f;
        this.heading = 0f;
        this.timestamp = System.currentTimeMillis();
    }

    public void update(double lat, double lon, float speed, float course, float heading) {
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.course = course;
        this.heading = heading;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isStale() {
        // Consider ship data stale after 5 minutes
        return System.currentTimeMillis() - timestamp > 300000;
    }

    @Override
    public String toString() {
        return String.format("Ship[%s] %s at (%.4f, %.4f)", mmsi, name, lat, lon);
    }
}
