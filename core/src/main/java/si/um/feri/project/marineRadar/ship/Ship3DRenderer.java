package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import si.um.feri.project.marineRadar.map.TileMapRenderer;

/**
 * Professional 3D ship renderer with cinematic camera and ocean scene.
 * 
 * Features:
 * - Smooth camera following with multiple view modes
 * - Dynamic ocean with waves
 * - Ship bobbing and tilting realistically
 * - Wake effects behind moving ships
 * - Smooth transitions when activating/deactivating
 */
public class Ship3DRenderer {
    
    // Core rendering
    private ModelBatch modelBatch;
    private PerspectiveCamera camera;
    private Environment environment;
    
    // Scene components
    private Ship3DScene scene;
    private Ship3DModel shipModel;
    
    // Reference to map (for coordinate conversion)
    private TileMapRenderer mapRenderer;
    
    // State
    private boolean active = false;
    private Ship currentShip = null;
    
    // Camera configuration
    private CameraMode cameraMode = CameraMode.CHASE;
    private float cameraDistance = 40f;
    private float cameraHeight = 15f;
    private float cameraAngle = 0f; // Orbit angle around ship
    private float targetCameraAngle = 0f;
    
    // Camera position smoothing
    private Vector3 cameraPosition = new Vector3();
    private Vector3 cameraTarget = new Vector3();
    private Vector3 smoothCameraPosition = new Vector3();
    private Vector3 smoothCameraTarget = new Vector3();
    
    // Transition animation
    private float transitionProgress = 0f;
    private boolean transitioning = false;
    private static final float TRANSITION_DURATION = 1.5f;
    
    // Camera smoothing factors
    private static final float CAMERA_POSITION_SMOOTHING = 3f;
    private static final float CAMERA_TARGET_SMOOTHING = 5f;
    private static final float CAMERA_ANGLE_SMOOTHING = 2f;
    
    // View modes
    public enum CameraMode {
        CHASE,      // Behind ship, following heading
        ORBIT,      // Orbiting around ship
        SIDE,       // Side view
        FRONT,      // Looking at front of ship
        CINEMATIC   // Smooth cinematic panning
    }
    
    public Ship3DRenderer(TileMapRenderer mapRenderer) {
        this.mapRenderer = mapRenderer;
        
        // Initialize ModelBatch
        modelBatch = new ModelBatch();
        
        // Initialize camera
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1f;
        camera.far = 5000f;
        camera.position.set(0, 50, 50);
        camera.lookAt(0, 0, 0);
        camera.update();
        
        // Initialize scene
        scene = new Ship3DScene();
        environment = scene.createEnvironment();
        
        // Initialize ship model
        shipModel = new Ship3DModel();
        
        Gdx.app.log("Ship3DRenderer", "3D renderer initialized with full scene");
    }
    
    /**
     * Activate 3D view for a ship with smooth transition
     */
    public void activate(Ship ship) {
        if (ship == null) return;
        
        this.currentShip = ship;
        this.active = true;
        this.transitioning = true;
        this.transitionProgress = 0f;
        
        // Initialize camera position behind ship
        updateCameraForShip(ship, 0f);
        smoothCameraPosition.set(cameraPosition);
        smoothCameraTarget.set(cameraTarget);
        
        // Reset camera angle to chase position
        cameraAngle = ship.heading + 180f;
        targetCameraAngle = cameraAngle;
        
        Gdx.app.log("Ship3DRenderer", "3D view activated for: " + ship.name);
    }
    
    /**
     * Deactivate 3D view with smooth transition
     */
    public void deactivate() {
        this.active = false;
        this.currentShip = null;
        this.transitioning = false;
        
        Gdx.app.log("Ship3DRenderer", "3D view deactivated");
    }
    
    public boolean isActive() {
        return active;
    }
    
    public Ship getCurrentShip() {
        return currentShip;
    }
    
    /**
     * Switch camera viewing mode
     */
    public void setCameraMode(CameraMode mode) {
        this.cameraMode = mode;
        
        switch (mode) {
            case CHASE:
                cameraDistance = 40f;
                cameraHeight = 15f;
                break;
            case SIDE:
                cameraDistance = 35f;
                cameraHeight = 10f;
                targetCameraAngle = currentShip != null ? currentShip.heading + 90f : 0f;
                break;
            case FRONT:
                cameraDistance = 50f;
                cameraHeight = 12f;
                targetCameraAngle = currentShip != null ? currentShip.heading : 0f;
                break;
            case ORBIT:
                cameraDistance = 45f;
                cameraHeight = 20f;
                break;
            case CINEMATIC:
                cameraDistance = 60f;
                cameraHeight = 25f;
                break;
        }
    }
    
    /**
     * Handle input for camera control
     */
    public void handleInput() {
        if (!active || currentShip == null) return;
        
        // Orbit camera with arrow keys or A/D
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            targetCameraAngle -= 60f * Gdx.graphics.getDeltaTime();
            cameraMode = CameraMode.ORBIT;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            targetCameraAngle += 60f * Gdx.graphics.getDeltaTime();
            cameraMode = CameraMode.ORBIT;
        }
        
        // Zoom with W/S or Up/Down
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            cameraDistance = Math.max(15f, cameraDistance - 30f * Gdx.graphics.getDeltaTime());
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            cameraDistance = Math.min(100f, cameraDistance + 30f * Gdx.graphics.getDeltaTime());
        }
        
        // Height adjustment with Q/E
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            cameraHeight = Math.max(5f, cameraHeight - 20f * Gdx.graphics.getDeltaTime());
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            cameraHeight = Math.min(50f, cameraHeight + 20f * Gdx.graphics.getDeltaTime());
        }
        
        // Camera mode switching with number keys
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            setCameraMode(CameraMode.CHASE);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            setCameraMode(CameraMode.SIDE);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            setCameraMode(CameraMode.FRONT);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
            setCameraMode(CameraMode.CINEMATIC);
        }
    }
    
    /**
     * Main render method - call this each frame when active
     */
    public void render(float delta) {
        if (!active || currentShip == null) {
            return;
        }
        
        // Handle camera input
        handleInput();
        
        // Update transition
        if (transitioning) {
            transitionProgress += delta / TRANSITION_DURATION;
            if (transitionProgress >= 1f) {
                transitionProgress = 1f;
                transitioning = false;
            }
        }
        
        // Update ship position for map tile loading
        scene.setShipPosition(currentShip.lat, currentShip.lon);
        
        // Update scene animations
        scene.update(delta);
        
        // Get wave data at ship position
        Ship3DScene.WaveData waveData = scene.getWaveData(0, 0);
        
        // Update ship model
        shipModel.setPosition(0, 0, 0); // Ship at origin, camera moves around it
        shipModel.updateFromShip(currentShip, waveData);
        shipModel.update(delta, waveData);
        
        // Update camera
        updateCameraForShip(currentShip, delta);
        
        // Smooth camera interpolation
        float positionLerp = CAMERA_POSITION_SMOOTHING * delta;
        float targetLerp = CAMERA_TARGET_SMOOTHING * delta;
        
        smoothCameraPosition.lerp(cameraPosition, Math.min(1f, positionLerp));
        smoothCameraTarget.lerp(cameraTarget, Math.min(1f, targetLerp));
        
        // Apply to camera
        camera.position.set(smoothCameraPosition);
        camera.lookAt(smoothCameraTarget);
        camera.up.set(0, 1, 0);
        camera.update();
        
        // Render scene
        renderScene();
    }
    
    /**
     * Update camera position based on ship and camera mode
     */
    private void updateCameraForShip(Ship ship, float delta) {
        // Smoothly interpolate camera angle
        if (cameraMode == CameraMode.CHASE) {
            // Chase mode follows ship heading
            float targetAngle = ship.heading + 180f;
            float angleDiff = ((targetAngle - cameraAngle + 540f) % 360f) - 180f;
            cameraAngle += angleDiff * CAMERA_ANGLE_SMOOTHING * delta;
        } else if (cameraMode == CameraMode.CINEMATIC) {
            // Cinematic mode slowly orbits
            targetCameraAngle += 5f * delta;
            cameraAngle = MathUtils.lerp(cameraAngle, targetCameraAngle, CAMERA_ANGLE_SMOOTHING * delta);
        } else if (cameraMode == CameraMode.ORBIT) {
            // Orbit uses target angle
            float angleDiff = ((targetCameraAngle - cameraAngle + 540f) % 360f) - 180f;
            cameraAngle += angleDiff * CAMERA_ANGLE_SMOOTHING * delta;
        }
        
        // Normalize angle
        cameraAngle = ((cameraAngle % 360f) + 360f) % 360f;
        
        // Calculate camera position
        float angleRad = cameraAngle * MathUtils.degreesToRadians;
        
        float camX = MathUtils.sin(angleRad) * cameraDistance;
        float camZ = MathUtils.cos(angleRad) * cameraDistance;
        float camY = cameraHeight;
        
        // Add subtle camera bob for realism
        float cameraBob = 0.3f * MathUtils.sin(scene.getWaveData(camX, camZ).height * 0.5f);
        
        cameraPosition.set(camX, camY + cameraBob, camZ);
        
        // Look at ship with slight upward offset
        float lookHeight = 5f;
        cameraTarget.set(0, lookHeight, 0);
    }
    
    /**
     * Render the complete 3D scene
     */
    private void renderScene() {
        // Clear depth buffer (color buffer cleared by main app)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        
        // Enable depth testing
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        
        // Enable blending for transparent elements
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        
        // Begin model batch
        modelBatch.begin(camera);
        
        // Render scene (sky, water)
        scene.render(modelBatch, environment);
        
        // Render ship
        shipModel.render(modelBatch, environment);
        
        modelBatch.end();
        
        // Restore OpenGL state
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
    
    /**
     * Resize handler
     */
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }
    
    /**
     * Get camera distance for external control
     */
    public float getCameraDistance() {
        return cameraDistance;
    }
    
    /**
     * Set camera distance
     */
    public void setCameraDistance(float distance) {
        this.cameraDistance = MathUtils.clamp(distance, 15f, 150f);
    }
    
    /**
     * Get current camera mode
     */
    public CameraMode getCameraMode() {
        return cameraMode;
    }
    
    public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (scene != null) scene.dispose();
        if (shipModel != null) shipModel.dispose();
    }
}
