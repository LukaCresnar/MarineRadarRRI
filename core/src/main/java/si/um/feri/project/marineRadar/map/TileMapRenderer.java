package si.um.feri.project.marineRadar.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import si.um.feri.project.marineRadar.clouds.CloudsLayer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;

/**
 * Professional-grade tile map renderer with smooth zoom transitions,
 * multi-level fallback rendering, and optimized caching.
 * 
 * Uses Web Mercator projection (EPSG:3857) matching OpenStreetMap tiles.
 */
public class TileMapRenderer {

    // Tile constants
    private static final int TILE_SIZE = 256;
    private static final int MIN_ZOOM = 2;
    private static final int MAX_ZOOM = 18;
    
    // Performance tuning
    private static final int MAX_CONCURRENT_DOWNLOADS = 8;
    private static final int MAX_CACHE_SIZE = 800;
    private static final int FALLBACK_ZOOM_LEVELS = 4;
    private static final long TILE_EXPIRE_FRAMES = 600;
    
    // Core rendering
    private final OrthographicCamera camera;
    private final SpriteBatch batch;
    private final CloudsLayer cloudsLayer;
    
    // Tile storage with thread-safe access
    private final ConcurrentHashMap<String, TileData> tileCache = new ConcurrentHashMap<>();
    private final Set<String> loadingTiles = ConcurrentHashMap.newKeySet();
    private final Set<String> failedTiles = ConcurrentHashMap.newKeySet();
    private final PriorityBlockingQueue<TileRequest> downloadQueue = new PriorityBlockingQueue<>();
    
    // Download management
    private final ExecutorService downloadExecutor;
    private volatile boolean disposed = false;
    
    // State tracking
    private int zoomLevel = 3;
    private long frameCounter = 0;
    private Set<String> currentFrameVisibleTiles = new HashSet<>();
    
    /**
     * Holds tile texture and metadata for smart caching
     */
    private static class TileData {
        final Texture texture;
        final int zoom;
        volatile long lastUsedFrame;
        volatile boolean essential;
        
        TileData(Texture texture, int zoom, long frame) {
            this.texture = texture;
            this.zoom = zoom;
            this.lastUsedFrame = frame;
            this.essential = false;
        }
    }
    
    /**
     * Priority-based tile download request
     */
    private static class TileRequest implements Comparable<TileRequest> {
        final int x, y, zoom;
        final String key;
        final int priority;
        final long requestTime;
        
        TileRequest(int x, int y, int zoom, String key, int priority) {
            this.x = x;
            this.y = y;
            this.zoom = zoom;
            this.key = key;
            this.priority = priority;
            this.requestTime = System.currentTimeMillis();
        }
        
        @Override
        public int compareTo(TileRequest other) {
            int priorityCompare = Integer.compare(this.priority, other.priority);
            if (priorityCompare != 0) return priorityCompare;
            return Long.compare(this.requestTime, other.requestTime);
        }
    }

    public TileMapRenderer(OrthographicCamera camera) {
        this.camera = camera;
        this.batch = new SpriteBatch();
        this.cloudsLayer = new CloudsLayer();

        // Create download thread pool with priority queue processing
        downloadExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("TileDownloader");
            return t;
        });
        
        // Start download workers
        for (int i = 0; i < MAX_CONCURRENT_DOWNLOADS; i++) {
            downloadExecutor.submit(this::downloadWorker);
        }

        // Initialize camera position at world center
        int worldSize = getWorldSize();
        camera.position.set(worldSize / 2f, worldSize / 2f, 0);
        camera.zoom = 1f;
        camera.update();
    }

    /**
     * Main render method - renders visible tiles with fallback support
     */
    public void render() {
        frameCounter++;
        currentFrameVisibleTiles.clear();

        int worldSize = getWorldSize();
        int maxTileCoord = (1 << zoomLevel) - 1;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Calculate visible tile range with buffer for smooth scrolling
        TileRange visibleRange = calculateVisibleTileRange(worldSize, maxTileCoord);
        
        // First pass: Identify all visible tiles and request missing ones
        List<TileRenderInfo> tilesToRender = new ArrayList<>();
        
        for (int tileX = visibleRange.startX; tileX <= visibleRange.endX; tileX++) {
            for (int tileY = visibleRange.startY; tileY <= visibleRange.endY; tileY++) {
                TileRenderInfo info = prepareTileForRender(tileX, tileY, maxTileCoord);
                tilesToRender.add(info);
                currentFrameVisibleTiles.add(getTileKey(zoomLevel, tileX, tileY));
            }
        }
        
        // Second pass: Render tiles (fallbacks first, then actual tiles on top)
        
        // Render fallback tiles first (lower quality, will be covered by high quality)
        for (TileRenderInfo info : tilesToRender) {
            if (info.fallbackTexture != null && info.actualTexture == null) {
                renderFallbackTile(info);
            }
        }
        
        // Render actual tiles on top
        for (TileRenderInfo info : tilesToRender) {
            if (info.actualTexture != null) {
                renderActualTile(info);
            }
        }

        batch.end();

        // Render clouds layer
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        cloudsLayer.render(batch, zoomLevel, worldSize);
        batch.end();

        // Periodic maintenance
        if (frameCounter % 120 == 0) {
            performCacheMaintenance();
        }
    }
    
    /**
     * Calculates the range of tiles visible on screen
     */
    private TileRange calculateVisibleTileRange(int worldSize, int maxTileCoord) {
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;

        float left = camera.position.x - halfWidth;
        float right = camera.position.x + halfWidth;
        float bottom = camera.position.y - halfHeight;
        float top = camera.position.y + halfHeight;

        // Clamp to world bounds
        float clampedLeft = Math.max(0, left);
        float clampedRight = Math.min(worldSize, right);
        float clampedBottom = Math.max(0, bottom);
        float clampedTop = Math.min(worldSize, top);

        // Calculate tile coordinates with 1 tile buffer for smooth scrolling
        int tileStartX = Math.max(0, (int)(clampedLeft / TILE_SIZE) - 1);
        int tileEndX = Math.min(maxTileCoord, (int)(clampedRight / TILE_SIZE) + 1);

        // Y is flipped: worldY = (maxTileCoord - tileY) * TILE_SIZE
        // So tileY = maxTileCoord - worldY / TILE_SIZE
        int tileStartY = Math.max(0, maxTileCoord - (int)(clampedTop / TILE_SIZE) - 1);
        int tileEndY = Math.min(maxTileCoord, maxTileCoord - (int)(clampedBottom / TILE_SIZE) + 1);

        // Ensure start <= end
        if (tileStartY > tileEndY) {
            int temp = tileStartY;
            tileStartY = tileEndY;
            tileEndY = temp;
        }

        return new TileRange(tileStartX, tileEndX, tileStartY, tileEndY);
    }
    
    private static class TileRange {
        final int startX, endX, startY, endY;
        TileRange(int startX, int endX, int startY, int endY) {
            this.startX = startX;
            this.endX = endX;
            this.startY = startY;
            this.endY = endY;
        }
    }
    
    /**
     * Holds information about a tile to be rendered
     */
    private static class TileRenderInfo {
        final int tileX, tileY;
        final float worldX, worldY;
        Texture actualTexture;
        Texture fallbackTexture;
        int fallbackZoom;
        int fallbackSubX, fallbackSubY, fallbackScale;
        
        TileRenderInfo(int tileX, int tileY, float worldX, float worldY) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.worldX = worldX;
            this.worldY = worldY;
        }
    }
    
    /**
     * Prepares a tile for rendering, finding actual or fallback textures
     */
    private TileRenderInfo prepareTileForRender(int tileX, int tileY, int maxTileCoord) {
        float worldX = tileX * TILE_SIZE;
        float worldY = (maxTileCoord - tileY) * TILE_SIZE;
        
        TileRenderInfo info = new TileRenderInfo(tileX, tileY, worldX, worldY);
        
        // Try to get the actual tile
        String key = getTileKey(zoomLevel, tileX, tileY);
        TileData tileData = tileCache.get(key);
        
        if (tileData != null) {
            tileData.lastUsedFrame = frameCounter;
            tileData.essential = true;
            info.actualTexture = tileData.texture;
        } else {
            // Request the tile if not already loading
            if (!loadingTiles.contains(key) && !failedTiles.contains(key)) {
                requestTile(tileX, tileY, zoomLevel, 0);
            }
            
            // Find a fallback tile from parent zoom levels
            findFallbackTile(info, tileX, tileY);
        }
        
        return info;
    }
    
    /**
     * Searches for a fallback tile from parent (lower) zoom levels
     */
    private void findFallbackTile(TileRenderInfo info, int tileX, int tileY) {
        for (int parentZoom = zoomLevel - 1; parentZoom >= Math.max(MIN_ZOOM, zoomLevel - FALLBACK_ZOOM_LEVELS); parentZoom--) {
            int zoomDiff = zoomLevel - parentZoom;
            int scale = 1 << zoomDiff;
            int parentX = tileX / scale;
            int parentY = tileY / scale;
            
            String parentKey = getTileKey(parentZoom, parentX, parentY);
            TileData parentData = tileCache.get(parentKey);
            
            if (parentData != null) {
                parentData.lastUsedFrame = frameCounter;
                info.fallbackTexture = parentData.texture;
                info.fallbackZoom = parentZoom;
                info.fallbackSubX = tileX - (parentX * scale);
                info.fallbackSubY = tileY - (parentY * scale);
                info.fallbackScale = scale;
                return;
            }
        }
    }
    
    /**
     * Renders a fallback tile (scaled portion of parent tile)
     */
    private void renderFallbackTile(TileRenderInfo info) {
        if (info.fallbackTexture == null) return;
        
        int srcSize = TILE_SIZE / info.fallbackScale;
        int srcX = info.fallbackSubX * srcSize;
        int srcY = info.fallbackSubY * srcSize;
        
        // Draw scaled portion of parent tile
        batch.draw(info.fallbackTexture,
            info.worldX, info.worldY, TILE_SIZE, TILE_SIZE,
            srcX, srcY, srcSize, srcSize,
            false, false);
    }
    
    /**
     * Renders the actual tile at full quality
     */
    private void renderActualTile(TileRenderInfo info) {
        batch.draw(info.actualTexture, info.worldX, info.worldY, TILE_SIZE, TILE_SIZE);
    }
    
    /**
     * Requests a tile to be downloaded with priority
     */
    private void requestTile(int x, int y, int zoom, int priority) {
        String key = getTileKey(zoom, x, y);
        if (loadingTiles.add(key)) {
            downloadQueue.offer(new TileRequest(x, y, zoom, key, priority));
        }
    }
    
    /**
     * Worker thread that processes the download queue
     */
    private void downloadWorker() {
        while (!disposed) {
            try {
                TileRequest request = downloadQueue.poll(500, TimeUnit.MILLISECONDS);
                if (request != null) {
                    // Skip if request is stale (different zoom level now)
                    if (Math.abs(request.zoom - zoomLevel) > 2) {
                        loadingTiles.remove(request.key);
                        continue;
                    }
                    downloadTile(request);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Downloads a single tile from the tile server
     */
    private void downloadTile(TileRequest request) {
        if (disposed) {
            loadingTiles.remove(request.key);
            return;
        }

        // Validate tile coordinates
        int maxCoord = (1 << request.zoom) - 1;
        if (request.x < 0 || request.x > maxCoord || request.y < 0 || request.y > maxCoord) {
            loadingTiles.remove(request.key);
            failedTiles.add(request.key);
            return;
        }

        HttpURLConnection conn = null;
        try {
            // Use OpenStreetMap tile server
            String urlStr = String.format(
                "https://tile.openstreetmap.org/%d/%d/%d.png",
                request.zoom, request.x, request.y
            );

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "MarineRadar/1.0 (LibGDX; Educational Project)");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                InputStream stream = conn.getInputStream();
                byte[] data = readAllBytesFromStream(stream);
                stream.close();

                // Create texture on GL thread
                final byte[] imageData = data;
                final int tileZoom = request.zoom;
                final String tileKey = request.key;
                
                Gdx.app.postRunnable(() -> {
                    if (disposed) {
                        loadingTiles.remove(tileKey);
                        return;
                    }

                    try {
                        Pixmap pixmap = new Pixmap(imageData, 0, imageData.length);
                        Texture texture = new Texture(pixmap);
                        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                        pixmap.dispose();

                        // Store in cache
                        tileCache.put(tileKey, new TileData(texture, tileZoom, frameCounter));
                        loadingTiles.remove(tileKey);

                    } catch (Exception e) {
                        Gdx.app.error("TileMapRenderer", "Failed to create texture: " + e.getMessage());
                        loadingTiles.remove(tileKey);
                        failedTiles.add(tileKey);
                    }
                });
            } else if (responseCode == 404) {
                loadingTiles.remove(request.key);
                failedTiles.add(request.key);
            } else {
                loadingTiles.remove(request.key);
            }

        } catch (Exception e) {
            loadingTiles.remove(request.key);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Performs cache maintenance - removes old unused tiles
     */
    private void performCacheMaintenance() {
        if (tileCache.size() < MAX_CACHE_SIZE) {
            return;
        }

        // Find tiles to remove (LRU eviction)
        List<Map.Entry<String, TileData>> entries = new ArrayList<>(tileCache.entrySet());
        
        // Sort by last used frame (oldest first)
        entries.sort(Comparator.comparingLong(e -> e.getValue().lastUsedFrame));
        
        // Calculate how many to remove
        int toRemove = tileCache.size() - (MAX_CACHE_SIZE * 3 / 4);
        
        List<String> keysToRemove = new ArrayList<>();
        for (int i = 0; i < Math.min(toRemove, entries.size()); i++) {
            Map.Entry<String, TileData> entry = entries.get(i);
            TileData data = entry.getValue();
            
            // Don't remove essential tiles (currently visible) or recent tiles
            if (!data.essential && frameCounter - data.lastUsedFrame > TILE_EXPIRE_FRAMES) {
                if (data.zoom != zoomLevel || frameCounter - data.lastUsedFrame > TILE_EXPIRE_FRAMES * 2) {
                    keysToRemove.add(entry.getKey());
                }
            }
        }
        
        // Remove tiles on GL thread
        if (!keysToRemove.isEmpty()) {
            Gdx.app.postRunnable(() -> {
                for (String key : keysToRemove) {
                    TileData removed = tileCache.remove(key);
                    if (removed != null && removed.texture != null) {
                        removed.texture.dispose();
                    }
                }
            });
        }
        
        // Reset essential flags
        for (TileData data : tileCache.values()) {
            data.essential = false;
        }
        
        // Clear old failed tiles so they can be retried
        failedTiles.clear();
    }

    /**
     * Handles zoom towards cursor position
     */
    public void zoomTowardsCursor(float scrollAmount, int mouseX, int mouseY) {
        // Get world coordinates before zoom
        Vector3 worldBefore = camera.unproject(new Vector3(mouseX, mouseY, 0));

        // Apply smooth zoom
        float zoomFactor = 1 + scrollAmount * 0.1f;
        camera.zoom *= zoomFactor;
        camera.zoom = Math.max(0.25f, Math.min(4f, camera.zoom));
        camera.update();

        // Get world coordinates after zoom
        Vector3 worldAfter = camera.unproject(new Vector3(mouseX, mouseY, 0));

        // Adjust camera to keep same point under cursor
        camera.position.x += (worldBefore.x - worldAfter.x);
        camera.position.y += (worldBefore.y - worldAfter.y);
        
        clampCameraPosition();
        camera.update();

        // Check if zoom level needs to change
        checkZoomChange();
    }

    /**
     * Checks if the zoom level should change based on camera zoom
     */
    public void checkZoomChange() {
        int targetZoom = zoomLevel;

        if (camera.zoom < 0.5f && zoomLevel < MAX_ZOOM) {
            targetZoom = zoomLevel + 1;
        } else if (camera.zoom > 2.0f && zoomLevel > MIN_ZOOM && zoomLevel > 4) {
            targetZoom = zoomLevel - 1;
        }

        if (targetZoom != zoomLevel) {
            changeZoomLevel(targetZoom);
        }
    }

    /**
     * Changes the zoom level with smooth transition
     */
    private void changeZoomLevel(int newZoomLevel) {
        // Calculate position ratio in current world
        int oldWorldSize = getWorldSize();
        float ratioX = camera.position.x / oldWorldSize;
        float ratioY = camera.position.y / oldWorldSize;

        int oldZoom = zoomLevel;
        zoomLevel = newZoomLevel;

        // Apply position in new world
        int newWorldSize = getWorldSize();
        camera.position.x = ratioX * newWorldSize;
        camera.position.y = ratioY * newWorldSize;
        camera.zoom = 1f;

        clampCameraPosition();
        camera.update();

        // Pre-load tiles at new zoom level for visible area
        preloadVisibleTiles();

        // Clear failed tiles so they can be retried at new zoom
        failedTiles.removeIf(key -> key.startsWith(oldZoom + "_"));
    }
    
    /**
     * Pre-loads tiles for the current visible area
     */
    private void preloadVisibleTiles() {
        int worldSize = getWorldSize();
        int maxTileCoord = (1 << zoomLevel) - 1;
        TileRange range = calculateVisibleTileRange(worldSize, maxTileCoord);
        
        for (int tileX = range.startX; tileX <= range.endX; tileX++) {
            for (int tileY = range.startY; tileY <= range.endY; tileY++) {
                String key = getTileKey(zoomLevel, tileX, tileY);
                if (!tileCache.containsKey(key) && !loadingTiles.contains(key) && !failedTiles.contains(key)) {
                    requestTile(tileX, tileY, zoomLevel, 0);
                }
            }
        }
    }

    /**
     * Web Mercator projection: Lat/Lon to pixel coordinates
     * Matches OpenStreetMap tile coordinate system exactly
     */
    public Vector2 latLonToPixel(double lat, double lon) {
        // Clamp latitude to valid Mercator range
        lat = Math.max(-85.05112878, Math.min(85.05112878, lat));

        // Number of tiles at this zoom level
        double n = 1 << zoomLevel;
        
        // X coordinate: simple linear mapping of longitude
        double x = (lon + 180.0) / 360.0 * n;
        
        // Y coordinate: Mercator projection
        double latRad = Math.toRadians(lat);
        double y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;
        
        // Convert to pixels
        float pixelX = (float)(x * TILE_SIZE);
        float pixelY = (float)(y * TILE_SIZE);
        
        // Flip Y to match our rendering (0 at bottom)
        int worldSize = getWorldSize();
        float flippedY = worldSize - pixelY;

        return new Vector2(pixelX, flippedY);
    }

    /**
     * Pixel coordinates to Lat/Lon
     */
    public Vector2 pixelToLatLon(float pixelX, float pixelY) {
        int worldSize = getWorldSize();
        double n = 1 << zoomLevel;
        
        // Unflip Y
        float unflippedY = worldSize - pixelY;
        
        // Convert from pixels to tile coordinates
        double x = pixelX / TILE_SIZE;
        double y = unflippedY / TILE_SIZE;
        
        // Convert to lat/lon
        double lon = x / n * 360.0 - 180.0;
        double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n)));
        double lat = Math.toDegrees(latRad);

        return new Vector2((float)lon, (float)lat);
    }

    /**
     * Clamps camera position to valid world bounds
     */
    private void clampCameraPosition() {
        int worldSize = getWorldSize();
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;

        if (2 * halfWidth >= worldSize) {
            camera.position.x = worldSize / 2f;
        } else {
            camera.position.x = Math.max(halfWidth, Math.min(worldSize - halfWidth, camera.position.x));
        }

        if (2 * halfHeight >= worldSize) {
            camera.position.y = worldSize / 2f;
        } else {
            camera.position.y = Math.max(halfHeight, Math.min(worldSize - halfHeight, camera.position.y));
        }
    }

    private byte[] readAllBytesFromStream(InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
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
    
    public void setZoomLevel(int newZoomLevel) {
        if (newZoomLevel >= MIN_ZOOM && newZoomLevel <= MAX_ZOOM && newZoomLevel != zoomLevel) {
            changeZoomLevel(newZoomLevel);
        }
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
            downloadExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Dispose all textures
        for (TileData data : tileCache.values()) {
            if (data.texture != null) {
                data.texture.dispose();
            }
        }

        tileCache.clear();
        loadingTiles.clear();
        failedTiles.clear();
        downloadQueue.clear();

        cloudsLayer.dispose();
        batch.dispose();
    }

    public void toggleClouds() {
        cloudsLayer.setVisible(!cloudsLayer.isVisible());
    }
}
