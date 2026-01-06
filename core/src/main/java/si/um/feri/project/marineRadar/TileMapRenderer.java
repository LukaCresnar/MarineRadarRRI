package si.um.feri.project.marineRadar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class TileMapRenderer {

    private static final int TILE_SIZE = 256;
    private static final int MIN_ZOOM = 2;
    private static final int MAX_ZOOM = 18;
    private static final int MAX_CONCURRENT_DOWNLOADS = 6;

    private final OrthographicCamera camera;
    private final SpriteBatch batch;

    // Simple, stable tile storage - never remove tiles during render
    private final ConcurrentHashMap<String, Texture> tileCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> tileLastUsed = new ConcurrentHashMap<>();
    private final Set<String> loadingTiles = ConcurrentHashMap.newKeySet();
    private final Set<String> failedTiles = ConcurrentHashMap.newKeySet();

    private final ExecutorService downloadExecutor;

    private int zoomLevel = 3;
    private boolean disposed = false;
    private long frameCounter = 0;

    public TileMapRenderer(OrthographicCamera camera) {
        this.camera = camera;
        this.batch = new SpriteBatch();

        downloadExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("TileDownloader-" + System.currentTimeMillis());
            return t;
        });

        // Initialize camera position
        int worldSize = TILE_SIZE * (1 << zoomLevel);
        camera.position.set(worldSize / 2f, worldSize / 2f, 0);
        camera.zoom = 1f;
        camera.update();

        Gdx.app.log("TileMapRenderer", "Initialized - World size: " + worldSize);
    }

    public void render() {
        frameCounter++;
        
        int worldSize = TILE_SIZE * (1 << zoomLevel);
        
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Calculate visible area
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;

        float left = camera.position.x - halfWidth;
        float right = camera.position.x + halfWidth;
        float bottom = camera.position.y - halfHeight;
        float top = camera.position.y + halfHeight;

        // Convert to tile coordinates with generous buffer
        int maxTileCoord = (1 << zoomLevel) - 1;
        float clampedLeft = Math.max(0, left);
        float clampedRight = Math.min(worldSize, right);
        float clampedBottom = Math.max(0, bottom);
        float clampedTop = Math.min(worldSize, top);

        int tileStartX = Math.max(0, Math.min(maxTileCoord, (int)(clampedLeft / TILE_SIZE) - 1));
        int tileEndX = Math.max(0, Math.min(maxTileCoord, (int)(clampedRight / TILE_SIZE) + 1));

        // Convert world coordinates to tile Y accounting for the flip
        int tileBottomFlipped = Math.max(0, Math.min(maxTileCoord, (int)((worldSize - clampedTop) / TILE_SIZE) - 1));
        int tileTopFlipped = Math.max(0, Math.min(maxTileCoord, (int)((worldSize - clampedBottom) / TILE_SIZE) + 1));

        int tileStartY = Math.max(0, Math.min(tileBottomFlipped, tileTopFlipped));
        int tileEndY = Math.min(maxTileCoord, Math.max(tileBottomFlipped, tileTopFlipped));

        int rendered = 0;
        int missing = 0;

        // Render all visible tiles
        for (int tileX = tileStartX; tileX <= tileEndX; tileX++) {
            for (int tileY = tileStartY; tileY <= tileEndY; tileY++) {
                String key = getTileKey(zoomLevel, tileX, tileY);
                Texture texture = tileCache.get(key);

                if (texture != null) {
                    // Mark as recently used
                    tileLastUsed.put(key, frameCounter);

                    // Calculate world position (flip Y for proper map orientation)
                    float worldX = tileX * TILE_SIZE;
                    float worldY = (maxTileCoord - tileY) * TILE_SIZE;

                    // Draw tile
                    batch.draw(texture, worldX, worldY, TILE_SIZE, TILE_SIZE);
                    rendered++;
                } else {
                    // Tile missing - request it
                    if (!loadingTiles.contains(key) && !failedTiles.contains(key)) {
                        requestTile(tileX, tileY, key);
                        missing++;
                    }
                }
            }
        }

        batch.end();

        // Periodic cleanup (every 300 frames = ~5 seconds at 60fps)
        if (frameCounter % 300 == 0) {
            cleanupOldTiles();
        }

        // Periodic status log
        if (frameCounter % 180 == 0) {
            int windowWidth = (int) camera.viewportWidth;
            int windowHeight = (int) camera.viewportHeight;
            Gdx.app.log("TileMapRenderer", String.format(
                "Frame %d | Zoom: %d | Map: %dx%d | Window: %dx%d | Rendered: %d | Missing: %d | Cache: %d | Loading: %d | Failed: %d",
                frameCounter, zoomLevel, worldSize, worldSize, windowWidth, windowHeight, 
                rendered, missing, tileCache.size(), loadingTiles.size(), failedTiles.size()
            ));
        }
    }

    private void requestTile(int x, int y, String key) {
        if (loadingTiles.add(key)) {
            downloadExecutor.submit(() -> downloadTile(x, y, zoomLevel, key));
        }
    }

    private void downloadTile(int x, int y, int zoom, String key) {
        if (disposed) {
            loadingTiles.remove(key);
            return;
        }

        // Check if zoom level changed while waiting
        if (zoom != zoomLevel) {
            loadingTiles.remove(key);
            return;
        }

        // Validate tile coordinates
        int maxCoord = (1 << zoom) - 1;
        if (x < 0 || x > maxCoord || y < 0 || y > maxCoord) {
            Gdx.app.error("TileMapRenderer", "Invalid tile coordinates: zoom=" + zoom + " x=" + x + " y=" + y);
            loadingTiles.remove(key);
            failedTiles.add(key);
            return;
        }

        HttpURLConnection conn = null;
        try {
            String urlStr = String.format(
                "https://tile.openstreetmap.de/tiles/osmde/%d/%d/%d.png",
                zoom, x, y
            );

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "MarineRadar/1.0 LibGDX");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            
            // Small delay 
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            conn.connect();

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                InputStream stream = conn.getInputStream();
                byte[] data = stream.readAllBytes();
                stream.close();

                // Create texture on GL thread
                final byte[] imageData = data;
                Gdx.app.postRunnable(() -> {
                    if (disposed || zoom != zoomLevel) {
                        loadingTiles.remove(key);
                        return;
                    }

                    try {
                        Pixmap pixmap = new Pixmap(imageData, 0, imageData.length);
                        Texture texture = new Texture(pixmap);
                        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                        pixmap.dispose();

                        // Store in cache
                        tileCache.put(key, texture);
                        tileLastUsed.put(key, frameCounter);
                        loadingTiles.remove(key);

                    } catch (Exception e) {
                        Gdx.app.error("TileMapRenderer", "Failed to create texture for " + key + ": " + e.getMessage());
                        loadingTiles.remove(key);
                        failedTiles.add(key);
                    }
                });
            } else if (responseCode == 404) {
                // Tile doesn't exist at this zoom/location
                loadingTiles.remove(key);
                failedTiles.add(key);
            } else {
                Gdx.app.error("TileMapRenderer", "HTTP " + responseCode + " for tile " + key);
                loadingTiles.remove(key);
                // Don't mark as failed for temporary errors - will retry
            }

        } catch (Exception e) {
            Gdx.app.error("TileMapRenderer", "Download error for " + key + ": " + e.getMessage());
            loadingTiles.remove(key);
            // Don't mark as failed for network errors - will retry
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void cleanupOldTiles() {
        if (tileCache.size() < 800) {
            return; // No need to cleanup yet
        }

        // Find tiles that haven't been used in the last 200 frames
        long cutoff = frameCounter - 200;
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, Long> entry : tileLastUsed.entrySet()) {
            if (entry.getValue() < cutoff) {
                toRemove.add(entry.getKey());
            }
        }

        // Remove old tiles
        if (!toRemove.isEmpty()) {
            Gdx.app.log("TileMapRenderer", "Cleaning up " + toRemove.size() + " old tiles");

            for (String key : toRemove) {
                Texture texture = tileCache.remove(key);
                if (texture != null) {
                    texture.dispose();
                }
                tileLastUsed.remove(key);
            }
        }
    }

    public void zoomTowardsCursor(float scrollAmount, int mouseX, int mouseY) {
        // Get world coordinates before zoom
        Vector3 worldBefore = camera.unproject(new Vector3(mouseX, mouseY, 0));

        // Apply zoom 
        camera.zoom *= (1 + scrollAmount * 0.1f);
        camera.update();

        // Get world coordinates after zoom
        Vector3 worldAfter = camera.unproject(new Vector3(mouseX, mouseY, 0));

        // Adjust camera to keep same point under cursor
        camera.position.x += (worldBefore.x - worldAfter.x);
        camera.position.y += (worldBefore.y - worldAfter.y);
        camera.update();

        // Check if zoom level needs to change
        checkZoomChange();
    }

    public void checkZoomChange() {
        int targetZoom = zoomLevel;

        if (camera.zoom < 0.5f && zoomLevel < MAX_ZOOM) {
            targetZoom = zoomLevel + 1;
        } else if (camera.zoom > 2.0f && zoomLevel > MIN_ZOOM) {
            targetZoom = zoomLevel - 1;
        }

        if (targetZoom != zoomLevel) {
            changeZoomLevel(targetZoom);
        }
    }

    private void changeZoomLevel(int newZoomLevel) {
        Gdx.app.log("TileMapRenderer", "Zoom level change: " + zoomLevel + " -> " + newZoomLevel);

        // Calculate position ratio in current world
        int oldWorldSize = TILE_SIZE * (1 << zoomLevel);
        float ratioX = Math.max(0, Math.min(1, camera.position.x / oldWorldSize));
        float ratioY = Math.max(0, Math.min(1, camera.position.y / oldWorldSize));

        int oldZoom = zoomLevel;
        zoomLevel = newZoomLevel;

        // Apply position in new world
        int newWorldSize = TILE_SIZE * (1 << zoomLevel);
        camera.position.x = ratioX * newWorldSize;
        camera.position.y = ratioY * newWorldSize;
        camera.zoom = 1f;

        // Clamp camera position to valid world bounds
        clampCameraPosition();
        camera.update();

        // Clear old zoom level tiles after a delay
        final int oldZoomFinal = oldZoom;
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Wait 1 second for new tiles to load
                clearZoomLevelTiles(oldZoomFinal);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        // Clear failed tiles so they can be retried at new zoom
        failedTiles.clear();
    }

    private void clearZoomLevelTiles(final int zoomToClear) {
        List<String> toRemove = new ArrayList<>();

        // Find all tiles from old zoom level
        for (String key : tileCache.keySet()) {
            if (key.startsWith(zoomToClear + "_")) {
                toRemove.add(key);
            }
        }

        if (toRemove.isEmpty()) {
            return;
        }

        Gdx.app.log("TileMapRenderer", "Clearing " + toRemove.size() + " tiles from zoom " + zoomToClear);

        // Dispose textures on GL thread
        Gdx.app.postRunnable(() -> {
            for (String key : toRemove) {
                Texture texture = tileCache.remove(key);
                if (texture != null) {
                    texture.dispose();
                }
                tileLastUsed.remove(key);
            }
        });

        // Clear loading queue for old zoom
        loadingTiles.removeIf(key -> key.startsWith(zoomToClear + "_"));
    }

    // Web Mercator projection - matches tile coordinate system
    // NOTE: Tiles use flipped Y (Y=0 at top, Y=max at bottom)
    public Vector2 latLonToPixel(double lat, double lon) {
        // Clamp latitude to valid Mercator range
        lat = Math.max(-85.05112878, Math.min(85.05112878, lat));

        // Convert to radians
        double lonRad = Math.toRadians(lon);
        double latRad = Math.toRadians(lat);

        // Standard Mercator projection
        double x = lonRad + Math.PI;
        double y = Math.PI - Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0));

        // Scale to tile coordinates
        double n = Math.pow(2.0, zoomLevel);
        float pixelX = (float)(x * n * TILE_SIZE / (2.0 * Math.PI));
        float pixelY = (float)(y * n * TILE_SIZE / (2.0 * Math.PI));

        // Account for Y-axis flip in tile rendering (maxTileCoord - tileY)
        int maxTileCoord = (1 << zoomLevel) - 1;
        int worldSize = TILE_SIZE * (1 << zoomLevel);
        float flippedY = worldSize - pixelY;

        return new Vector2(pixelX, flippedY);
    }

    public Vector2 pixelToLatLon(float x, float y) {
        double n = Math.pow(2, zoomLevel);
        double lon = x / TILE_SIZE / n * 360.0 - 180.0;

        double latRad = Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * y / TILE_SIZE / n)));
        double lat = Math.toDegrees(latRad);

        return new Vector2((float)lon, (float)lat);
    }

    private void clampCameraPosition() {
        int worldSize = TILE_SIZE * (1 << zoomLevel);
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;

        // Clamp to prevent showing areas outside the world
        // Handle both width and height independently
        if (worldSize > 0) {
            // Clamp X (width)
            if (2 * halfWidth >= worldSize) {
                // Viewport is wider than world - center it
                camera.position.x = worldSize / 2f;
            } else {
                // Clamp to valid bounds
                camera.position.x = Math.max(halfWidth, Math.min(worldSize - halfWidth, camera.position.x));
            }

            // Clamp Y (height)
            if (2 * halfHeight >= worldSize) {
                // Viewport is taller than world - center it
                camera.position.y = worldSize / 2f;
            } else {
                // Clamp to valid bounds
                camera.position.y = Math.max(halfHeight, Math.min(worldSize - halfHeight, camera.position.y));
            }
        }
    }

    private String getTileKey(int zoom, int x, int y) {
        return zoom + "_" + x + "_" + y;
    }

    public int getWorldSize() {
        return TILE_SIZE * (1 << zoomLevel);
    }

    public int getZoomLevel() {
        return zoomLevel;
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    public void dispose() {
        disposed = true;

        downloadExecutor.shutdownNow();

        try {
            downloadExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Dispose all textures
        for (Texture texture : tileCache.values()) {
            if (texture != null) {
                texture.dispose();
            }
        }

        tileCache.clear();
        tileLastUsed.clear();
        loadingTiles.clear();
        failedTiles.clear();

        batch.dispose();

        Gdx.app.log("TileMapRenderer", "Disposed");
    }
}
