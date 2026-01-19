package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import si.um.feri.project.marineRadar.map.TileMapRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShipRenderer {

    private final ShapeRenderer shapeRenderer;
    private final TileMapRenderer mapRenderer;
    private SpriteBatch spriteBatch;

    // Ship icons
    private Map<String, Texture> shipIcons = new HashMap<>();
    private static final int MIN_ZOOM_FOR_ICONS = 8; // Show icons at zoom level 8+
    private static final float ICON_ROTATION_ADJUST = 0f; // Adjustment in degrees; use 0 and tweak if needed

    // Colors
    private static final Color SHIP_DEFAULT = new Color(1f, 0f, 0f, 1f);      // Red
    private static final Color SHIP_SELECTED = new Color(1f, 1f, 0f, 1f);     // Yellow
    private static final Color SHIP_TRACKED = new Color(0f, 1f, 0f, 1f);      // Green
    private static final Color SHIP_MOORED = new Color(0.5f, 0.5f, 0.5f, 1f); // Gray
    private static final Color ROUTE_LINE = new Color(0f, 1f, 1f, 0.5f);      // Cyan
    private static final Color HEADING_LINE = new Color(1f, 0.5f, 0f, 0.8f);  // Orange

    public ShipRenderer(ShapeRenderer shapeRenderer, TileMapRenderer mapRenderer) {
        this.shapeRenderer = shapeRenderer;
        this.mapRenderer = mapRenderer;
        this.spriteBatch = new SpriteBatch();
        loadShipIcons();
    }

    private void loadShipIcons() {
        try {
            shipIcons.put("cargo", new Texture(Gdx.files.internal("imgs/cargoShip.png")));
            shipIcons.put("tanker", new Texture(Gdx.files.internal("imgs/tankerShip.png")));
            shipIcons.put("passenger", new Texture(Gdx.files.internal("imgs/cruiseShip.png")));
            shipIcons.put("fishing", new Texture(Gdx.files.internal("imgs/fishingShip.png")));
            shipIcons.put("tug", new Texture(Gdx.files.internal("imgs/tugShip.png")));
            shipIcons.put("military", new Texture(Gdx.files.internal("imgs/militaryShip.png")));
            shipIcons.put("sailing", new Texture(Gdx.files.internal("imgs/sailShip.png")));
            shipIcons.put("fast", new Texture(Gdx.files.internal("imgs/fastShip.png")));
            shipIcons.put("special", new Texture(Gdx.files.internal("imgs/specialShip.png")));
            shipIcons.put("generic", new Texture(Gdx.files.internal("imgs/genericShip.png")));
            Gdx.app.log("ShipRenderer", "Ship icons loaded successfully");
        } catch (Exception e) {
            Gdx.app.error("ShipRenderer", "Failed to load ship icons: " + e.getMessage());
        }
    }

    /**
     * Get icon category based on ship type string
     */
    private String getIconCategory(Ship ship) {
        String type = ship.shipType;
        if (type == null) return "generic";

        // Match based on ship type description
        if (type.contains("Cargo")) return "cargo";
        if (type.contains("Tanker")) return "tanker";
        if (type.contains("Passenger")) return "passenger";
        if (type.contains("Fishing")) return "fishing";
        if (type.contains("Tug") || type.contains("Towing") || type.contains("Pilot") || 
            type.contains("Port Tender") || type.contains("Anti-pollution") || type.contains("Law Enforcement")) return "tug";
        if (type.contains("Military")) return "military";
        if (type.contains("Sailing") || type.contains("Pleasure")) return "sailing";
        if (type.contains("High Speed")) return "fast";
        if (type.contains("Dredging") || type.contains("Diving") || type.contains("Search and Rescue")) return "special";
        
        return "generic";
    }

    public void render(OrthographicCamera camera, List<Ship> ships, Ship selectedShip, Ship trackedShip) {
        int zoomLevel = mapRenderer.getZoomLevel();
        boolean useIcons = zoomLevel >= MIN_ZOOM_FOR_ICONS && !shipIcons.isEmpty();
        
        // Debug: log zoom level occasionally
        if (Gdx.graphics.getFrameId() % 60 == 0) {
            Gdx.app.log("ShipRenderer", "Zoom level: " + zoomLevel + ", useIcons: " + useIcons + ", iconsLoaded: " + shipIcons.size());
        }

        shapeRenderer.setProjectionMatrix(camera.combined);

        // Draw routes first (behind ships)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Ship ship : ships) {
            if (ship == selectedShip || ship == trackedShip) {
                drawRoute(ship, camera.zoom);
            }
        }
        shapeRenderer.end();

        if (useIcons) {
            // Draw ship icons
            spriteBatch.setProjectionMatrix(camera.combined);
            spriteBatch.begin();
            for (Ship ship : ships) {
                drawShipIcon(ship, selectedShip, trackedShip, camera.zoom);
            }
            spriteBatch.end();
        } else {
            // Draw ships as dots
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            for (Ship ship : ships) {
                drawShipDot(ship, selectedShip, trackedShip, camera.zoom);
            }
            shapeRenderer.end();
        }

        // Draw heading indicators
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Ship ship : ships) {
            if (ship.isMoving() && (ship == selectedShip || ship == trackedShip)) {
                drawHeadingIndicator(ship, camera.zoom);
            }
        }
        shapeRenderer.end();
    }

    private void drawShipIcon(Ship ship, Ship selectedShip, Ship trackedShip, float zoom) {
        Vector2 pos = mapRenderer.latLonToPixel(ship.lat, ship.lon);
        
        String iconCategory = getIconCategory(ship);
        Texture icon = shipIcons.get(iconCategory);
        if (icon == null) {
            icon = shipIcons.get("generic");
        }
        if (icon == null) return;

        // Determine size based on selection state (then scale by 1.5x)
        float baseHeight = 24f;
        if (ship == selectedShip) {
            baseHeight = 36f;
            spriteBatch.setColor(1f, 1f, 0.5f, 1f); // Slight yellow tint
        } else if (ship == trackedShip) {
            baseHeight = 32f;
            spriteBatch.setColor(0.5f, 1f, 0.5f, 1f); // Slight green tint
        } else {
            spriteBatch.setColor(1f, 1f, 1f, 1f); // Normal
        }

        // Apply global size multiplier
        final float SIZE_MULTIPLIER = 1.5f;
        baseHeight *= SIZE_MULTIPLIER;

        // Preserve texture aspect ratio
        float texW = icon.getWidth();
        float texH = icon.getHeight();
        float aspect = texW / texH;

        float height = baseHeight;
        float width = baseHeight * aspect;

        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        // Compute icon rotation based on projected route (same as drawRoute)
        // Project 2 hours ahead (same heuristic) and compute bearing to that point
        float drawRotation;
        if (ship.speed > 0.1f) {
            float distance = ship.speed * 2; // same 2-hour projection
            float courseRad = ship.course * MathUtils.degreesToRadians;
            double latOffset = Math.cos(courseRad) * distance / 60.0;
            double lonOffset = Math.sin(courseRad) * distance / 60.0;
            double futureLat = ship.lat + latOffset;
            double futureLon = ship.lon + lonOffset;

            float bearing = computeBearing(ship.lat, ship.lon, futureLat, futureLon);
            drawRotation = bearing - 90f + ICON_ROTATION_ADJUST;
        } else {
            // Fallback to course when stationary or no speed
            drawRotation = ship.course - 90f + ICON_ROTATION_ADJUST;
        }

        // Draw rotated icon (origin at center)
        spriteBatch.draw(icon,
            pos.x - halfWidth, pos.y - halfHeight, // position (bottom-left)
            halfWidth, halfHeight,                 // origin (center)
            width, height,                         // size (preserve aspect)
            1f, 1f,                                 // scale
            drawRotation,                          // rotation (degrees)
            0, 0,                                   // src position
            icon.getWidth(), icon.getHeight(),     // src size
            false, false);                          // flip

        // Reset color to white to avoid tint bleed
        spriteBatch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawShipDot(Ship ship, Ship selectedShip, Ship trackedShip, float zoom) {
        Vector2 pos = mapRenderer.latLonToPixel(ship.lat, ship.lon);

        // Determine color
        Color color;
        float radius;

        if (ship == selectedShip) {
            color = SHIP_SELECTED;
            radius = 10f;
        } else if (ship == trackedShip) {
            color = SHIP_TRACKED;
            radius = 8f;
        } else if (ship.shipType == null || "Unknown".equals(ship.shipType)) {
            // Unknown ship type — render red
            color = SHIP_DEFAULT; // red
            radius = 6f;
        } else {
            // Known type (not selected/tracked) — render green
            color = SHIP_TRACKED;
            radius = 5f;
        }

        shapeRenderer.setColor(color);
        shapeRenderer.circle(pos.x, pos.y, radius);

        // Draw direction triangle if moving
        if (ship.isMoving() && radius > 5f) {
            drawDirectionTriangle(pos, ship.heading, radius + 3f);
        }
    }

    private void drawDirectionTriangle(Vector2 pos, float heading, float size) {
        // Draw small triangle pointing in heading direction
        float angleRad = (heading - 90) * MathUtils.degreesToRadians;

        float x1 = pos.x + MathUtils.cos(angleRad) * size;
        float y1 = pos.y + MathUtils.sin(angleRad) * size;

        float x2 = pos.x + MathUtils.cos(angleRad + 2.4f) * (size * 0.5f);
        float y2 = pos.y + MathUtils.sin(angleRad + 2.4f) * (size * 0.5f);

        float x3 = pos.x + MathUtils.cos(angleRad - 2.4f) * (size * 0.5f);
        float y3 = pos.y + MathUtils.sin(angleRad - 2.4f) * (size * 0.5f);

        shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
    }

    /**
     * Compute compass bearing (degrees, 0 = North) from (lat1,lon1) to (lat2,lon2).
     */
    private float computeBearing(double lat1, double lon1, double lat2, double lon2) {
        double deltaLon = lon2 - lon1;
        double deltaLat = lat2 - lat1;

        // If no significant movement, return 0
        if (Math.abs(deltaLat) < 1e-9 && Math.abs(deltaLon) < 1e-9) return 0f;

        double angleRad = Math.atan2(deltaLon, deltaLat);
        float angleDeg = (float) Math.toDegrees(angleRad);
        if (angleDeg < 0) angleDeg += 360f;
        return angleDeg;
    }

    private void drawRoute(Ship ship, float zoom) {
        if (!ship.isMoving() || ship.destination.equals("Unknown")) return;

        Vector2 pos = mapRenderer.latLonToPixel(ship.lat, ship.lon);

        // Calculate approximate destination point (simplified)
        // In reality, you'd need to decode the destination coordinates
        // For now, draw a projected line based on course and speed

        float distance = ship.speed * 2; // Approximate 2-hour projection
        float courseRad = ship.course * MathUtils.degreesToRadians;

        // Convert nautical miles to approximate lat/lon offset
        double latOffset = Math.cos(courseRad) * distance / 60.0;
        double lonOffset = Math.sin(courseRad) * distance / 60.0;

        Vector2 futurePos = mapRenderer.latLonToPixel(
            ship.lat + latOffset,
            ship.lon + lonOffset
        );

        shapeRenderer.setColor(ROUTE_LINE);
        shapeRenderer.line(pos.x, pos.y, futurePos.x, futurePos.y);

        // Draw dashed line effect
        int segments = 10;
        for (int i = 0; i < segments; i++) {
            if (i % 2 == 0) {
                float t1 = i / (float) segments;
                float t2 = (i + 1) / (float) segments;

                float x1 = pos.x + (futurePos.x - pos.x) * t1;
                float y1 = pos.y + (futurePos.y - pos.y) * t1;
                float x2 = pos.x + (futurePos.x - pos.x) * t2;
                float y2 = pos.y + (futurePos.y - pos.y) * t2;

                shapeRenderer.line(x1, y1, x2, y2);
            }
        }
    }

    private void drawHeadingIndicator(Ship ship, float zoom) {
        Vector2 pos = mapRenderer.latLonToPixel(ship.lat, ship.lon);

        float lineLength = 30f / zoom;
        float headingRad = (ship.heading - 90) * MathUtils.degreesToRadians;

        float endX = pos.x + MathUtils.cos(headingRad) * lineLength;
        float endY = pos.y + MathUtils.sin(headingRad) * lineLength;

        shapeRenderer.setColor(HEADING_LINE);
        shapeRenderer.line(pos.x, pos.y, endX, endY);
    }

    public void renderRadarSweep(OrthographicCamera camera, Ship ship, float angle) {
        Vector2 pos = mapRenderer.latLonToPixel(ship.lat, ship.lon);
        float radius = 50f / camera.zoom;

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        shapeRenderer.setColor(0f, 1f, 0f, 0.5f);
        shapeRenderer.circle(pos.x, pos.y, radius, 32);

        // Draw sweep line
        float rad = angle * MathUtils.degreesToRadians;
        float endX = pos.x + MathUtils.cos(rad) * radius;
        float endY = pos.y + MathUtils.sin(rad) * radius;

        shapeRenderer.setColor(0f, 1f, 0f, 1f);
        shapeRenderer.line(pos.x, pos.y, endX, endY);

        shapeRenderer.end();
    }

    public void dispose() {
        if (spriteBatch != null) {
            spriteBatch.dispose();
        }
        for (Texture texture : shipIcons.values()) {
            if (texture != null) {
                texture.dispose();
            }
        }
        shipIcons.clear();
    }
}
