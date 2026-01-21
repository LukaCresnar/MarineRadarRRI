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
    private static final float ICON_DRAW_OFFSET = -90f; // Screen-space offset (degrees) if icon graphic baseline needs shift

    // Colors
    private static final Color SHIP_DEFAULT = new Color(1f, 0f, 0f, 1f);      // Red
    private static final Color SHIP_SELECTED = new Color(1f, 1f, 0f, 1f);     // Yellow
    private static final Color SHIP_TRACKED = new Color(0f, 1f, 0f, 1f);      // Green
    private static final Color SHIP_MOORED = new Color(0.5f, 0.5f, 0.5f, 1f); // Gray
    private static final Color ROUTE_LINE = new Color(0f, 1f, 1f, 0.5f);      // Cyan
    private static final Color HEADING_LINE = new Color(1f, 0.5f, 0f, 0.8f);  // Orange
    private static final Color PATH_COLOR = new Color(0f, 0f, 0f, 1f);       // Black path for selected ship

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
         
        // Draw path (location history) for selected ship
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Ship ship : ships) {
            if (ship.isSelected && ship.locationHistory.size() > 1) {
                shapeRenderer.setColor(PATH_COLOR);
                List<double[]> hist = ship.locationHistory;
                for (int i = 1; i < hist.size(); i++) {
                    double[] prev = hist.get(i - 1);
                    double[] cur = hist.get(i);
                    Vector2 p0 = mapRenderer.latLonToPixel(prev[0], prev[1]);
                    Vector2 p1 = mapRenderer.latLonToPixel(cur[0], cur[1]);
                    shapeRenderer.line(p0.x, p0.y, p1.x, p1.y);
                }

                /* 
                // Debug overlays: show movement-based rotation and recent segment bearing
                // Compute last segment
                double[] last = hist.get(hist.size() - 1);
                double[] prev = hist.get(hist.size() - 2);
                Vector2 lastP = mapRenderer.latLonToPixel(last[0], last[1]);

                // Movement-smoothed rotation (compass degrees) -> screen angle
                float movementBearing = ship.rotation; // 0 = North
                float screenAngMovement = (movementBearing - 90f) * MathUtils.degreesToRadians;
                float len = 40f / Math.max(0.5f, 1f); // fixed length in pixels
                float mx = lastP.x + MathUtils.cos(screenAngMovement) * len;
                float my = lastP.y + MathUtils.sin(screenAngMovement) * len;

                // Last-segment immediate bearing
                float segBearing = computeBearing(prev[0], prev[1], last[0], last[1]);
                float screenAngSeg = (segBearing - 90f) * MathUtils.degreesToRadians;
                float sx = lastP.x + MathUtils.cos(screenAngSeg) * len;
                float sy = lastP.y + MathUtils.sin(screenAngSeg) * len;

                // Course-based direction
                float courseScreenAng = (ship.course - 90f) * MathUtils.degreesToRadians;
                float cx = lastP.x + MathUtils.cos(courseScreenAng) * len;
                float cy = lastP.y + MathUtils.sin(courseScreenAng) * len;

                
                // Draw movement bearing in magenta
                shapeRenderer.setColor(1f, 0f, 1f, 1f);
                shapeRenderer.line(lastP.x, lastP.y, mx, my);

                // Draw last-segment bearing in blue
                shapeRenderer.setColor(0f, 0f, 1f, 1f);
                shapeRenderer.line(lastP.x, lastP.y, sx, sy);
                */
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

        // Compute screen-space rotation by averaging recent locationHistory segments in pixel space
        float drawRotation = ship.course - 90f + ICON_ROTATION_ADJUST; // default
        if (ship.locationHistory.size() >= 2) {
            List<double[]> hist = ship.locationHistory;
            int n = hist.size();
            int pairs = Math.min(3, n - 1);
            float sumDx = 0f, sumDy = 0f;
            for (int k = 1; k <= pairs; k++) {
                double[] prev = hist.get(n - 1 - k);
                double[] curr = hist.get(n - k);
                Vector2 p0 = mapRenderer.latLonToPixel(prev[0], prev[1]);
                Vector2 p1 = mapRenderer.latLonToPixel(curr[0], curr[1]);
                sumDx += (p1.x - p0.x);
                sumDy += (p1.y - p0.y);
            }
            if (Math.abs(sumDx) > 1e-6 || Math.abs(sumDy) > 1e-6) {
                float angleRad = (float)Math.atan2(sumDy, sumDx);
                float angleDeg = angleRad * MathUtils.radiansToDegrees;
                drawRotation = angleDeg + ICON_DRAW_OFFSET;
            }
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
     * Uses longitude scaling by mean latitude to approximate true bearing on Mercator map.
     */
    private float computeBearing(double lat1, double lon1, double lat2, double lon2) {
        double deltaLon = lon2 - lon1;
        double deltaLat = lat2 - lat1;
        if (Math.abs(deltaLat) < 1e-12 && Math.abs(deltaLon) < 1e-12) return 0f;

        double meanLatRad = Math.toRadians((lat1 + lat2) / 2.0);
        double scaledDeltaLon = deltaLon * Math.cos(meanLatRad);
        double angleRad = Math.atan2(scaledDeltaLon, deltaLat);
        float angleDeg = (float) Math.toDegrees(angleRad);
        if (angleDeg < 0) angleDeg += 360f;
        return angleDeg;
    }


    private void drawRoute(Ship ship, float zoom) {
        if (!ship.isMoving() || ship.destination.equals("Unknown")) return;

        Vector2 pos = mapRenderer.latLonToPixel(ship.lat, ship.lon);

        // Use the ship's RouteInfo destination (keeps route and icon math consistent)
        if (ship.routeInfo == null) return;
        Vector2 futurePos = mapRenderer.latLonToPixel(ship.routeInfo.destLat, ship.routeInfo.destLon);

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
