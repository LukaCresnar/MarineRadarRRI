package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import si.um.feri.project.marineRadar.map.TileMapRenderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete 3D scene for rendering a ship on the ocean with map tiles as ground.
 * Includes dynamic water overlay, sky dome, and atmospheric effects.
 */
public class Ship3DScene {
    
    // Scene models
    private Model waterOverlayModel;
    private Model skyModel;
    private Model horizonModel;
    private Model mapGroundModel;
    private ModelInstance waterOverlayInstance;
    private ModelInstance skyInstance;
    private ModelInstance horizonInstance;
    private ModelInstance mapGroundInstance;
    
    // Map tile rendering
    private TileMapRenderer mapRenderer;
    private Map<String, Texture> tileTextures = new ConcurrentHashMap<>();
    private Map<String, Model> tileModels = new ConcurrentHashMap<>();
    private Map<String, ModelInstance> tileInstances = new ConcurrentHashMap<>();
    
    // Current ship position for tile loading
    private double shipLat = 0;
    private double shipLon = 0;
    private int mapZoom = 17; // High zoom for detailed view (17 is more reliable than 18)
    
    // Scene size
    private static final float SCENE_SIZE = 500f;
    private static final float TILE_SIZE_3D = 80f; // Size of each tile in 3D space
    private static final int TILES_AROUND = 3; // Load tiles in a 7x7 grid around ship
    
    // Water animation
    private float waterTime = 0f;
    
    // Materials
    private Material waterMaterial;
    private Material skyMaterial;
    
    // Wave parameters
    private float[] waveOffsets;
    private float[] waveSpeeds;
    private float[] waveAmplitudes;
    
    public Ship3DScene() {
        createSkyDome();
        createHorizon();
        createWaterOverlay();
        initializeWaveParameters();
        
        Gdx.app.log("Ship3DScene", "3D scene initialized with map support");
    }
    
    /**
     * Set the map renderer reference for tile loading
     */
    public void setMapRenderer(TileMapRenderer mapRenderer) {
        this.mapRenderer = mapRenderer;
    }
    
    /**
     * Update ship position for tile loading
     */
    public void setShipPosition(double lat, double lon) {
        this.shipLat = lat;
        this.shipLon = lon;
        loadTilesAroundShip();
    }
    
    private void initializeWaveParameters() {
        waveOffsets = new float[4];
        waveSpeeds = new float[]{0.5f, 0.8f, 1.2f, 0.3f};
        waveAmplitudes = new float[]{0.4f, 0.25f, 0.18f, 0.5f};
    }
    
    private void createWaterOverlay() {
        ModelBuilder modelBuilder = new ModelBuilder();
        
        // Semi-transparent water overlay on top of map
        waterMaterial = new Material();
        waterMaterial.set(ColorAttribute.createDiffuse(new Color(0.0f, 0.4f, 0.6f, 0.3f)));
        waterMaterial.set(ColorAttribute.createSpecular(0.9f, 0.9f, 0.9f, 1f));
        waterMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA));
        
        // Water overlay slightly above the ground
        waterOverlayModel = modelBuilder.createRect(
            -SCENE_SIZE, 0.1f, -SCENE_SIZE,
            -SCENE_SIZE, 0.1f, SCENE_SIZE,
            SCENE_SIZE, 0.1f, SCENE_SIZE,
            SCENE_SIZE, 0.1f, -SCENE_SIZE,
            0, 1, 0,
            waterMaterial,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        
        waterOverlayInstance = new ModelInstance(waterOverlayModel);
    }
    
    private void createSkyDome() {
        ModelBuilder modelBuilder = new ModelBuilder();
        
        // Create gradient sky material - nice blue
        skyMaterial = new Material();
        skyMaterial.set(ColorAttribute.createDiffuse(new Color(0.5f, 0.7f, 0.95f, 1f)));
        
        // Create a large sphere for the sky
        skyModel = modelBuilder.createSphere(
            2000f, 1000f, 2000f, 32, 32,
            skyMaterial,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        
        skyInstance = new ModelInstance(skyModel);
        skyInstance.transform.setToTranslation(0, -50f, 0);
    }
    
    private void createHorizon() {
        ModelBuilder modelBuilder = new ModelBuilder();
        
        // Create horizon fog/mist effect
        Material horizonMaterial = new Material();
        horizonMaterial.set(ColorAttribute.createDiffuse(new Color(0.8f, 0.85f, 0.95f, 0.4f)));
        horizonMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA));
        
        horizonModel = modelBuilder.createCylinder(
            1500f, 30f, 1500f, 64,
            horizonMaterial,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        
        horizonInstance = new ModelInstance(horizonModel);
        horizonInstance.transform.setToTranslation(0, 2f, 0);
    }
    
    /**
     * Load map tiles around the ship's position
     */
    private void loadTilesAroundShip() {
        if (shipLat == 0 && shipLon == 0) return;
        
        // Calculate tile coordinates at zoom level
        int centerTileX = lonToTileX(shipLon, mapZoom);
        int centerTileY = latToTileY(shipLat, mapZoom);
        
        // Load tiles in a grid around the center
        for (int dx = -TILES_AROUND; dx <= TILES_AROUND; dx++) {
            for (int dz = -TILES_AROUND; dz <= TILES_AROUND; dz++) {
                int tileX = centerTileX + dx;
                int tileY = centerTileY + dz;
                
                String tileKey = mapZoom + "/" + tileX + "/" + tileY;
                
                if (!tileTextures.containsKey(tileKey)) {
                    loadTileAsync(tileX, tileY, mapZoom, dx, dz);
                }
            }
        }
    }
    
    /**
     * Load a single tile asynchronously with fallback zoom levels
     */
    private void loadTileAsync(int tileX, int tileY, int zoom, int offsetX, int offsetZ) {
        String tileKey = zoom + "/" + tileX + "/" + tileY;
        
        new Thread(() -> {
            byte[] data = null;
            int usedZoom = zoom;
            int usedTileX = tileX;
            int usedTileY = tileY;
            
            // Try loading at requested zoom, fall back to lower zooms if needed
            for (int tryZoom = zoom; tryZoom >= 14 && data == null; tryZoom--) {
                try {
                    // Recalculate tile coords for different zoom
                    if (tryZoom != zoom) {
                        int zoomDiff = zoom - tryZoom;
                        usedTileX = tileX >> zoomDiff;
                        usedTileY = tileY >> zoomDiff;
                        usedZoom = tryZoom;
                    }
                    
                    String urlStr = String.format("https://tile.openstreetmap.org/%d/%d/%d.png", usedZoom, usedTileX, usedTileY);
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "MarineRadar/1.0");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    if (conn.getResponseCode() == 200) {
                        InputStream is = conn.getInputStream();
                        // Java 8 compatible way to read all bytes
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(chunk)) != -1) {
                            buffer.write(chunk, 0, bytesRead);
                        }
                        data = buffer.toByteArray();
                        is.close();
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    // Try next zoom level
                }
            }
            
            if (data != null) {
                final byte[] finalData = data;
                final int fOffsetX = offsetX;
                final int fOffsetZ = offsetZ;
                    
                // Create texture on main thread
                Gdx.app.postRunnable(() -> {
                    try {
                        Pixmap pixmap = new Pixmap(finalData, 0, finalData.length);
                        Texture texture = new Texture(pixmap, true); // Generate mipmaps
                        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                        texture.setAnisotropicFilter(4f); // Sharper at angles
                        pixmap.dispose();
                        
                        tileTextures.put(tileKey, texture);
                        createTileModel(tileKey, texture, fOffsetX, fOffsetZ);
                    } catch (Exception e) {
                        Gdx.app.error("Ship3DScene", "Texture create failed: " + e.getMessage());
                    }
                });
            }
        }).start();
    }
    
    /**
     * Create a 3D model for a tile
     */
    private void createTileModel(String tileKey, Texture texture, int offsetX, int offsetZ) {
        ModelBuilder modelBuilder = new ModelBuilder();
        
        // Use unlit material to preserve original tile colors
        Material tileMaterial = new Material();
        tileMaterial.set(TextureAttribute.createDiffuse(texture));
        // No color attribute - let the texture colors show through accurately
        
        float x = offsetX * TILE_SIZE_3D;
        float z = offsetZ * TILE_SIZE_3D;
        float halfSize = TILE_SIZE_3D / 2f;
        
        // Create a textured quad for the tile
        Model tileModel = modelBuilder.createRect(
            x - halfSize, 0, z - halfSize,
            x - halfSize, 0, z + halfSize,
            x + halfSize, 0, z + halfSize,
            x + halfSize, 0, z - halfSize,
            0, 1, 0,
            tileMaterial,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates
        );
        
        tileModels.put(tileKey, tileModel);
        tileInstances.put(tileKey, new ModelInstance(tileModel));
    }
    
    // Tile coordinate conversion
    private int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
    }
    
    private int latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom));
    }
    
    /**
     * Update scene animations
     */
    public void update(float delta) {
        waterTime += delta;
        
        // Update wave offsets
        for (int i = 0; i < waveOffsets.length; i++) {
            waveOffsets[i] += waveSpeeds[i] * delta;
        }
        
        // Animate water overlay color
        float alpha = 0.25f + 0.05f * MathUtils.sin(waterTime * 0.5f);
        ((ColorAttribute) waterMaterial.get(ColorAttribute.Diffuse)).color.set(
            0.0f, 0.35f + 0.05f * MathUtils.sin(waterTime * 0.3f), 0.55f, alpha
        );
    }
    
    /**
     * Calculate wave height at a given position
     */
    public float getWaveHeight(float x, float z) {
        float height = 0;
        
        // Sum multiple wave layers for realistic ocean motion
        height += waveAmplitudes[0] * MathUtils.sin(x * 0.03f + waveOffsets[0] * 2f);
        height += waveAmplitudes[1] * MathUtils.sin(z * 0.04f + waveOffsets[1] * 3f);
        height += waveAmplitudes[2] * MathUtils.sin((x + z) * 0.02f + waveOffsets[2]);
        height += waveAmplitudes[3] * MathUtils.sin((x - z) * 0.015f + waveOffsets[3] * 0.5f);
        
        return height;
    }
    
    /**
     * Calculate wave normal at a given position (for ship tilting)
     */
    public Vector3 getWaveNormal(float x, float z) {
        float delta = 0.5f;
        float heightCenter = getWaveHeight(x, z);
        float heightX = getWaveHeight(x + delta, z);
        float heightZ = getWaveHeight(x, z + delta);
        
        Vector3 normal = new Vector3(
            heightCenter - heightX,
            delta,
            heightCenter - heightZ
        ).nor();
        
        return normal;
    }
    
    /**
     * Get wave parameters for ship animation
     */
    public WaveData getWaveData(float x, float z) {
        WaveData data = new WaveData();
        data.height = getWaveHeight(x, z);
        
        // Calculate pitch and roll from wave normal
        Vector3 normal = getWaveNormal(x, z);
        data.pitch = MathUtils.atan2(normal.z, normal.y) * MathUtils.radiansToDegrees * 0.5f;
        data.roll = MathUtils.atan2(normal.x, normal.y) * MathUtils.radiansToDegrees * 0.5f;
        
        return data;
    }
    
    /**
     * Render the scene (sky, map tiles - no water overlay for clear map visibility)
     */
    public void render(ModelBatch modelBatch, Environment environment) {
        // Render sky first (background)
        modelBatch.render(skyInstance);
        
        // Render map tiles as ground
        for (ModelInstance tileInstance : tileInstances.values()) {
            modelBatch.render(tileInstance, environment);
        }
        
        // Render horizon mist for atmosphere
        modelBatch.render(horizonInstance);
    }
    
    /**
     * Create environment with proper lighting
     */
    public Environment createEnvironment() {
        Environment environment = new Environment();
        
        // Moderate ambient light to preserve natural colors
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f));
        
        // Sun - main directional light (slightly reduced to avoid oversaturation)
        DirectionalLight sun = new DirectionalLight();
        sun.set(0.8f, 0.78f, 0.75f, -0.3f, -0.8f, -0.3f);
        environment.add(sun);
        
        // Fill light from the front (reduced)
        DirectionalLight fill = new DirectionalLight();
        fill.set(0.3f, 0.32f, 0.35f, 0.4f, -0.3f, 0.6f);
        environment.add(fill);
        
        return environment;
    }
    
    /**
     * Clear loaded tiles (call when ship moves significantly)
     */
    public void clearTiles() {
        for (Texture tex : tileTextures.values()) {
            tex.dispose();
        }
        for (Model model : tileModels.values()) {
            model.dispose();
        }
        tileTextures.clear();
        tileModels.clear();
        tileInstances.clear();
    }
    
    public void dispose() {
        if (waterOverlayModel != null) waterOverlayModel.dispose();
        if (skyModel != null) skyModel.dispose();
        if (horizonModel != null) horizonModel.dispose();
        clearTiles();
    }
    
    /**
     * Data class for wave information at a point
     */
    public static class WaveData {
        public float height;
        public float pitch;
        public float roll;
    }
}
