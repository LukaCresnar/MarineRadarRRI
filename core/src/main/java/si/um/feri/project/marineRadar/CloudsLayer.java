package si.um.feri.project.marineRadar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Animated clouds layer for zoomed-out view (zoom levels 1-4)
 * Provides parallax depth effect with slowly moving cloud images
 */
public class CloudsLayer {
    private static final int TILE_SIZE = 256;
    private static final int NUM_CLOUDS = 12;
    private static final float CLOUD_SPEED = 15f;
    private static final float CLOUD_OPACITY = 0.7f;
    
    private List<Cloud> clouds;
    private Texture cloudTexture;
    private Random random;
    private int lastZoomLevel = -1;
    private boolean cloudsVisible = true;
    
    public CloudsLayer() {
        this.random = new Random(42);
        this.clouds = new ArrayList<>();
        
        // Load cloud texture from assets
        try {
            cloudTexture = new Texture(Gdx.files.internal("imgs/clouds.png"));
            cloudTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            Gdx.app.log("CloudsLayer", "Cloud texture loaded successfully");
            Gdx.app.log("CloudsLayer", "Cloud texture size: " + cloudTexture.getWidth() + "x" + cloudTexture.getHeight());
        } catch (Exception e) {
            Gdx.app.error("CloudsLayer", "Failed to load imgs/clouds.png: " + e.getMessage());
            cloudTexture = null;
        }
    }
    
    private void generateClouds(int zoomLevel) {
        clouds.clear();
        
        // Calculate world size based on current zoom level
        int worldSize = TILE_SIZE * (1 << zoomLevel);
        float centerX = worldSize / 2f;
        float centerY = worldSize / 2f;
        float spread = worldSize * 0.4f; // Spread clouds over 80% of world width
        
        for (int i = 0; i < NUM_CLOUDS * 3; i++) {
            float x = centerX + (random.nextFloat() - 0.5f) * spread * 2;
            float y = centerY + (random.nextFloat() - 0.5f) * spread * 2;
            float width = 100 + random.nextFloat() * 200;
            float height = 50 + random.nextFloat() * 100;
            float speed = CLOUD_SPEED + random.nextFloat() * 20f;
            
            clouds.add(new Cloud(x, y, width, height, speed, cloudTexture));
        }
        Gdx.app.log("CloudsLayer", "Generated " + clouds.size() + " clouds for zoom level " + zoomLevel + " (world size: " + worldSize + ")");
    }
    
    public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch batch, int zoomLevel, int worldSize) {
        // Only show clouds at zoom levels 1-4 and if visibility is enabled
        if (zoomLevel > 4 || !cloudsVisible) {
            return;
        }
        
        if (cloudTexture == null) {
            return;
        }
        
        // Regenerate clouds if zoom level changed
        if (lastZoomLevel != zoomLevel) {
            lastZoomLevel = zoomLevel;
            generateClouds(zoomLevel);
        }
        
        // Update cloud positions (continuous scrolling)
        for (Cloud cloud : clouds) {
            cloud.x += cloud.speed * Gdx.graphics.getDeltaTime();
            
            // Wrap around
            if (cloud.x > worldSize * 3) {
                cloud.x = -cloud.width;
            }
        }
        
        // Render clouds with texture and opacity
        batch.setColor(1f, 1f, 1f, CLOUD_OPACITY);
        
        for (Cloud cloud : clouds) {
            batch.draw(cloudTexture, cloud.x, cloud.y, cloud.width, cloud.height);
        }
        
        batch.setColor(1f, 1f, 1f, 1f); // Reset color
    }
    
    public void dispose() {
        if (cloudTexture != null) {
            cloudTexture.dispose();
        }
    }
    
    public void setVisible(boolean visible) {
        this.cloudsVisible = visible;
    }
    
    public boolean isVisible() {
        return cloudsVisible;
    }
}
