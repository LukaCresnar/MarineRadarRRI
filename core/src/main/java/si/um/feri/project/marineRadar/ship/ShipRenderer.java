package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import si.um.feri.project.marineRadar.map.TileMapRenderer;

import java.util.List;

public class ShipRenderer {

    private final ShapeRenderer shapeRenderer;
    private final TileMapRenderer mapRenderer;

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
    }

    public void render(OrthographicCamera camera, List<Ship> ships, Ship selectedShip, Ship trackedShip) {
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Draw routes first (behind ships)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Ship ship : ships) {
            if (ship == selectedShip || ship == trackedShip) {
                drawRoute(ship, camera.zoom);
            }
        }
        shapeRenderer.end();

        // Draw ships
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Ship ship : ships) {
            drawShip(ship, selectedShip, trackedShip, camera.zoom);
        }
        shapeRenderer.end();

        // Draw heading indicators
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Ship ship : ships) {
            if (ship.isMoving() && (ship == selectedShip || ship == trackedShip)) {
                drawHeadingIndicator(ship, camera.zoom);
            }
        }
        shapeRenderer.end();
    }

    private void drawShip(Ship ship, Ship selectedShip, Ship trackedShip, float zoom) {
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
        } else if (ship.navigationalStatus == 1 || ship.navigationalStatus == 5) {
            color = SHIP_MOORED; // Anchored or moored
            radius = 5f;
        } else {
            color = SHIP_DEFAULT;
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
}
