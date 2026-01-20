package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;

/**
 * Professional 3D ship model with realistic animations.
 * Uses G3DB format for optimal performance, with OBJ fallback.
 */
public class Ship3DModel {
    
    // Ship model
    private Model shipModel;
    private ModelInstance shipInstance;
    
    // Wake effect models
    private Model wakeModel;
    private ModelInstance[] wakeInstances;
    private static final int WAKE_SEGMENTS = 8;
    
    // Animation state
    private float animationTime = 0f;
    private float currentBob = 0f;
    private float currentPitch = 0f;
    private float currentRoll = 0f;
    private float targetPitch = 0f;
    private float targetRoll = 0f;
    
    // Ship movement
    private Vector3 position = new Vector3();
    private float heading = 0f;
    private float speed = 0f;
    
    // Smooth animation interpolation
    private static final float BOB_SMOOTHING = 3f;
    private static final float TILT_SMOOTHING = 2f;
    
    // Model paths
    private static final String G3DB_MODEL_PATH = "objs/boat/conv-12219_boat_v2_L2.g3db";
    private static final String OBJ_MODEL_PATH = "objs/boat/12219_boat_v2_L2.obj";
    
    // Transform helpers
    private final Matrix4 tempMatrix = new Matrix4();
    private final Quaternion tempQuat = new Quaternion();
    private final Vector3 tempVec = new Vector3();
    
    // Model scale and offset corrections
    private float modelScale = 0.015f;
    private Vector3 modelOffset = new Vector3(0, 0, 0);
    private float modelRotationOffset = 90f; // Degrees to rotate model to face forward
    
    public Ship3DModel() {
        loadShipModel();
        createWakeEffect();
        
        Gdx.app.log("Ship3DModel", "Ship model initialized");
    }
    
    private void loadShipModel() {
        // Use procedural model for reliable rendering with proper colors
        // The external models (G3DB/OBJ) don't have embedded textures
        createProceduralShip();
    }
    
    private boolean tryLoadG3DB() {
        try {
            G3dModelLoader loader = new G3dModelLoader(new JsonReader());
            shipModel = loader.loadModel(Gdx.files.internal(G3DB_MODEL_PATH));
            shipInstance = new ModelInstance(shipModel);
            
            // Apply initial scale
            shipInstance.transform.scl(modelScale);
            
            Gdx.app.log("Ship3DModel", "Loaded G3DB model: " + G3DB_MODEL_PATH);
            return true;
        } catch (Exception e) {
            Gdx.app.log("Ship3DModel", "G3DB load failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean tryLoadOBJ() {
        try {
            ObjLoader loader = new ObjLoader();
            shipModel = loader.loadModel(Gdx.files.internal(OBJ_MODEL_PATH));
            shipInstance = new ModelInstance(shipModel);
            
            // Apply initial scale
            shipInstance.transform.scl(modelScale);
            
            Gdx.app.log("Ship3DModel", "Loaded OBJ model: " + OBJ_MODEL_PATH);
            return true;
        } catch (Exception e) {
            Gdx.app.log("Ship3DModel", "OBJ load failed: " + e.getMessage());
            return false;
        }
    }
    
    private void createProceduralShip() {
        Gdx.app.log("Ship3DModel", "Creating procedural ship model");
        
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        
        // Hull - main body (dark blue-gray)
        Material hullMaterial = new Material();
        hullMaterial.set(ColorAttribute.createDiffuse(new Color(0.15f, 0.2f, 0.3f, 1f)));
        hullMaterial.set(ColorAttribute.createSpecular(0.3f, 0.3f, 0.3f, 1f));
        
        builder.node().id = "hull";
        builder.part("hull", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            hullMaterial)
            .box(0, 0.5f, 0, 8f, 1.5f, 2.5f);
        
        // Hull bottom (tapered)
        builder.node().id = "hull_bottom";
        builder.part("hull_bottom", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            hullMaterial)
            .box(0, -0.3f, 0, 7f, 0.8f, 2f);
        
        // Bow (front of ship)
        Material bowMaterial = new Material();
        bowMaterial.set(ColorAttribute.createDiffuse(new Color(0.18f, 0.22f, 0.32f, 1f)));
        
        builder.node().id = "bow";
        builder.part("bow", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            bowMaterial)
            .box(5f, 0.3f, 0, 2f, 1f, 1.8f);
        
        // Superstructure - white cabin
        Material superstructureMaterial = new Material();
        superstructureMaterial.set(ColorAttribute.createDiffuse(Color.WHITE));
        superstructureMaterial.set(ColorAttribute.createSpecular(0.5f, 0.5f, 0.5f, 1f));
        
        builder.node().id = "superstructure";
        builder.part("superstructure", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            superstructureMaterial)
            .box(-1f, 2f, 0, 4f, 1.5f, 2f);
        
        // Bridge - control room
        Material bridgeMaterial = new Material();
        bridgeMaterial.set(ColorAttribute.createDiffuse(new Color(0.9f, 0.9f, 0.95f, 1f)));
        
        builder.node().id = "bridge";
        builder.part("bridge", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            bridgeMaterial)
            .box(-1f, 3.2f, 0, 2.5f, 0.8f, 1.8f);
        
        // Windows (dark glass effect)
        Material windowMaterial = new Material();
        windowMaterial.set(ColorAttribute.createDiffuse(new Color(0.1f, 0.15f, 0.2f, 0.8f)));
        windowMaterial.set(ColorAttribute.createSpecular(0.8f, 0.8f, 0.8f, 1f));
        
        builder.node().id = "windows";
        builder.part("windows", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            windowMaterial)
            .box(-0.5f, 3.2f, 0.95f, 1.8f, 0.5f, 0.1f);
        
        // Funnel/Smokestack
        Material funnelMaterial = new Material();
        funnelMaterial.set(ColorAttribute.createDiffuse(new Color(0.8f, 0.25f, 0.15f, 1f)));
        
        builder.node().id = "funnel";
        builder.part("funnel", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            funnelMaterial)
            .box(-2.5f, 3f, 0, 0.8f, 1.2f, 0.6f);
        
        // Mast
        Material mastMaterial = new Material();
        mastMaterial.set(ColorAttribute.createDiffuse(Color.LIGHT_GRAY));
        
        builder.node().id = "mast";
        builder.part("mast", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            mastMaterial)
            .box(0f, 4.5f, 0, 0.15f, 2f, 0.15f);
        
        // Radar dome
        builder.node().id = "radar";
        builder.part("radar", GL20.GL_TRIANGLES,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
            superstructureMaterial)
            .sphere(0.4f, 0.3f, 0.4f, 12, 8);
        
        shipModel = builder.end();
        shipInstance = new ModelInstance(shipModel);
        
        // Position radar dome
        shipInstance.getNode("radar").translation.set(0f, 5.5f, 0f);
        shipInstance.calculateTransforms();
        
        modelScale = 1f; // Procedural model is already at correct scale
    }
    
    private void createWakeEffect() {
        ModelBuilder builder = new ModelBuilder();
        
        // Wake material - white foam with transparency
        Material wakeMaterial = new Material();
        wakeMaterial.set(ColorAttribute.createDiffuse(new Color(1f, 1f, 1f, 0.6f)));
        wakeMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA));
        
        wakeModel = builder.createRect(
            -0.3f, 0.02f, -1f,
            -0.3f, 0.02f, 1f,
            0.3f, 0.02f, 1f,
            0.3f, 0.02f, -1f,
            0, 1, 0,
            wakeMaterial,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        
        wakeInstances = new ModelInstance[WAKE_SEGMENTS];
        for (int i = 0; i < WAKE_SEGMENTS; i++) {
            wakeInstances[i] = new ModelInstance(wakeModel);
        }
    }
    
    /**
     * Update ship state from Ship data
     */
    public void updateFromShip(Ship ship, Ship3DScene.WaveData waveData) {
        this.heading = ship.heading;
        this.speed = ship.speed;
        
        // Apply wave effects
        targetPitch = waveData.pitch + getSpeedPitch();
        targetRoll = waveData.roll + getTurnRoll();
    }
    
    /**
     * Get additional pitch from ship speed (bow rises at speed)
     */
    private float getSpeedPitch() {
        return -speed * 0.1f; // Slight bow-up at speed
    }
    
    /**
     * Get roll from turning (heel into turn)
     */
    private float getTurnRoll() {
        // Could be enhanced with actual turn rate detection
        return 0f;
    }
    
    /**
     * Update animations each frame
     */
    public void update(float delta, Ship3DScene.WaveData waveData) {
        animationTime += delta;
        
        // Smooth interpolation for bob (vertical movement)
        float targetBob = waveData.height;
        currentBob += (targetBob - currentBob) * BOB_SMOOTHING * delta;
        
        // Smooth interpolation for pitch and roll
        currentPitch += (targetPitch - currentPitch) * TILT_SMOOTHING * delta;
        currentRoll += (targetRoll - currentRoll) * TILT_SMOOTHING * delta;
        
        // Enhanced wobble effect - multiple frequencies for realism
        float wobbleTime1 = animationTime * 1.2f;
        float wobbleTime2 = animationTime * 0.8f;
        float wobbleTime3 = animationTime * 2.1f;
        
        // Pitch wobble (front-back tilt)
        float microPitch = 1.5f * (float) Math.sin(wobbleTime1) 
                         + 0.8f * (float) Math.sin(wobbleTime2 * 1.7f)
                         + 0.4f * (float) Math.sin(wobbleTime3);
        
        // Roll wobble (side-side tilt) - slightly different frequency
        float microRoll = 2.0f * (float) Math.sin(wobbleTime1 * 0.9f + 0.5f) 
                        + 1.0f * (float) Math.sin(wobbleTime2 * 1.3f)
                        + 0.5f * (float) Math.sin(wobbleTime3 * 0.7f);
        
        // Vertical bob - gentle up/down motion
        float microBob = 0.15f * (float) Math.sin(wobbleTime1 * 0.6f)
                       + 0.08f * (float) Math.sin(wobbleTime2 * 1.1f);
        
        // Build transform matrix
        shipInstance.transform.idt();
        
        // Position (including bob) - ship sits on the water
        shipInstance.transform.translate(position.x, position.y + currentBob + microBob + 0.5f, position.z);
        
        // Rotation order: Yaw (heading) -> Pitch -> Roll
        // The ship model faces +X, so rotate to face the heading direction
        shipInstance.transform.rotate(Vector3.Y, -heading + modelRotationOffset);
        shipInstance.transform.rotate(Vector3.X, currentPitch + microPitch);
        shipInstance.transform.rotate(Vector3.Z, currentRoll + microRoll);
        
        // Apply scale
        shipInstance.transform.scale(modelScale, modelScale, modelScale);
        
        // Apply model offset if needed
        if (modelOffset.len2() > 0) {
            shipInstance.transform.translate(modelOffset);
        }
        
        // Update wake effect
        updateWake(delta);
    }
    
    private void updateWake(float delta) {
        if (speed < 0.5f) {
            // Hide wake when stationary
            for (ModelInstance wake : wakeInstances) {
                wake.transform.setToScaling(0, 0, 0);
            }
            return;
        }
        
        float wakeSpacing = 2f;
        float headingRad = heading * (float) Math.PI / 180f;
        float dirX = -(float) Math.sin(headingRad);
        float dirZ = -(float) Math.cos(headingRad);
        
        for (int i = 0; i < WAKE_SEGMENTS; i++) {
            float distance = (i + 1) * wakeSpacing;
            float scale = 1f + i * 0.3f; // Wake expands behind ship
            float alpha = 1f - (i / (float) WAKE_SEGMENTS);
            
            // Animate wake
            float waveOffset = (float) Math.sin(animationTime * 3f + i * 0.5f) * 0.1f;
            
            wakeInstances[i].transform.idt();
            wakeInstances[i].transform.translate(
                position.x + dirX * distance,
                position.y + currentBob - 0.1f + waveOffset,
                position.z + dirZ * distance
            );
            wakeInstances[i].transform.rotate(Vector3.Y, heading);
            wakeInstances[i].transform.scale(scale * (speed / 10f), 1f, scale * 0.5f);
        }
    }
    
    /**
     * Set ship world position
     */
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }
    
    public Vector3 getPosition() {
        return position;
    }
    
    /**
     * Render the ship model
     */
    public void render(ModelBatch modelBatch, Environment environment) {
        modelBatch.render(shipInstance, environment);
        
        // Render wake
        if (speed >= 0.5f) {
            for (ModelInstance wake : wakeInstances) {
                modelBatch.render(wake, environment);
            }
        }
    }
    
    /**
     * Get ship bounding radius for camera calculations
     */
    public float getBoundingRadius() {
        return 10f * modelScale;
    }
    
    public void dispose() {
        if (shipModel != null) shipModel.dispose();
        if (wakeModel != null) wakeModel.dispose();
    }
}
