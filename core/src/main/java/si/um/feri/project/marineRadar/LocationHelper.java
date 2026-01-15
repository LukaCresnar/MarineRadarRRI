package si.um.feri.project.marineRadar;

public class LocationHelper {

    private static class Location {
        String name;
        double lat;
        double lon;
        double radius;

        Location(String name, double lat, double lon, double radius) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.radius = radius;
        }
    }

    private static final Location[] LOCATIONS = {
        // Major ports and cities
        new Location("North Atlantic Ocean", 40.0, -30.0, 20.0),
        new Location("Mediterranean Sea", 36.0, 15.0, 8.0),
        new Location("English Channel", 50.0, 0.0, 3.0),
        new Location("Gulf of Mexico", 25.0, -90.0, 10.0),
        new Location("Caribbean Sea", 15.0, -75.0, 8.0),
        new Location("North Sea", 56.0, 3.0, 5.0),
        new Location("Baltic Sea", 58.0, 20.0, 5.0),
        new Location("Black Sea", 43.0, 35.0, 4.0),
        new Location("Red Sea", 20.0, 38.0, 8.0),
        new Location("Persian Gulf", 27.0, 51.0, 4.0),
        new Location("Arabian Sea", 15.0, 65.0, 10.0),
        new Location("Bay of Bengal", 15.0, 88.0, 8.0),
        new Location("South China Sea", 12.0, 115.0, 10.0),
        new Location("East China Sea", 30.0, 125.0, 6.0),
        new Location("Sea of Japan", 40.0, 135.0, 6.0),
        new Location("Philippine Sea", 20.0, 130.0, 10.0),
        new Location("Tasman Sea", -40.0, 160.0, 10.0),
        new Location("Coral Sea", -15.0, 150.0, 8.0),
        new Location("Indian Ocean", -20.0, 75.0, 20.0),
        new Location("Pacific Ocean", 0.0, -140.0, 30.0),
        new Location("Atlantic Ocean", 20.0, -40.0, 30.0),

        // Major ports
        new Location("Port of Rotterdam", 51.9, 4.1, 0.5),
        new Location("Port of Singapore", 1.3, 103.8, 0.5),
        new Location("Port of Shanghai", 31.2, 121.5, 0.5),
        new Location("Port of Hamburg", 53.5, 9.9, 0.5),
        new Location("Port of Los Angeles", 33.7, -118.3, 0.5),
        new Location("Port of Antwerp", 51.3, 4.3, 0.5),
        new Location("Port of Hong Kong", 22.3, 114.2, 0.5),
        new Location("Port of Dubai", 25.3, 55.3, 0.5),
        new Location("Suez Canal", 30.5, 32.3, 1.0),
        new Location("Panama Canal", 9.0, -79.5, 0.5),
        new Location("Strait of Gibraltar", 36.0, -5.6, 0.5),
        new Location("Strait of Malacca", 2.5, 101.0, 2.0),
        new Location("Bosphorus Strait", 41.1, 29.1, 0.3),

        // Coastal areas
        new Location("Norwegian Coast", 60.0, 5.0, 5.0),
        new Location("Spanish Coast", 40.0, -3.0, 4.0),
        new Location("Italian Coast", 42.0, 13.0, 4.0),
        new Location("Greek Coast", 38.0, 23.0, 4.0),
        new Location("US East Coast", 35.0, -75.0, 8.0),
        new Location("US West Coast", 37.0, -122.0, 8.0),
        new Location("Japanese Coast", 35.0, 139.0, 5.0),
        new Location("Australian Coast", -33.9, 151.2, 6.0),
        new Location("Brazilian Coast", -23.0, -43.0, 6.0),
    };

    public static String getLocationName(double lat, double lon) {
        // Find nearest location
        Location nearest = null;
        double minDist = Double.MAX_VALUE;

        for (Location loc : LOCATIONS) {
            double dist = distance(lat, lon, loc.lat, loc.lon);
            if (dist < loc.radius && dist < minDist) {
                minDist = dist;
                nearest = loc;
            }
        }

        if (nearest != null) {
            return nearest.name;
        }

        // Return general area based on latitude
        if (lat > 60) return "Arctic Waters";
        if (lat < -60) return "Antarctic Waters";
        if (lat > 30 && lon > -20 && lon < 40) return "European Waters";
        if (lat > 20 && lat < 50 && lon > 100 && lon < 150) return "East Asian Waters";
        if (lat > -40 && lat < 0 && lon > 100 && lon < 180) return "Oceania Waters";
        if (lat > 0 && lat < 30 && lon > -100 && lon < -60) return "Central American Waters";
        if (lat < 0 && lon > -80 && lon < -30) return "South American Waters";
        if (lat > 0 && lat < 40 && lon > -130 && lon < -100) return "North Pacific Waters";

        // Default to ocean based on longitude
        if (lon > -30 && lon < 100) return "Eastern Hemisphere";
        return "Western Hemisphere";
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // Earth radius in km converted to degrees
    }
}
