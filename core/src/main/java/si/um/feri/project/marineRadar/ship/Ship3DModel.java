package si.um.feri.project.marineRadar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

public class Ship3DModel {
    private Model model;
    private ModelInstance instance;
    private ModelBatch modelBatch;

    private float bobOffset = 0f;

    private static final String OBJ_MODEL_PATH = "objs/boat/12219_boat_v2_L2.obj";
    private static final boolean USE_CUSTOM_MODEL = true;

    public Ship3DModel() {
        modelBatch = new ModelBatch();

        if (USE_CUSTOM_MODEL && OBJ_MODEL_PATH != null) {
            loadObjModel();
        } else {
            createProceduralShipModel();
        }

        Gdx.app.log("Ship3DModel", "Model created successfully");
    }

    private void loadObjModel() {
        try {
            ObjLoader objLoader = new ObjLoader();
            model = objLoader.loadModel(Gdx.files.internal(OBJ_MODEL_PATH));
            instance = new ModelInstance(model);

            // Scale appropriately for visibility
            instance.transform.scl(0.5f, 0.5f, 0.5f);

            Gdx.app.log("Ship3DModel", "Loaded .obj model from: " + OBJ_MODEL_PATH);
        } catch (Exception e) {
            Gdx.app.error("Ship3DModel", "Failed to load .obj: " + e.getMessage());
            createProceduralShipModel();
        }
    }

    private void createProceduralShipModel() {
        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        // Hull - dark blue/gray
        modelBuilder.node().id = "hull";
        modelBuilder.part("hull", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(ColorAttribute.createDiffuse(new Color(0.2f, 0.3f, 0.4f, 1f))))
            .box(0f, 1f, 0f, 12f, 2f, 4f);

        // Superstructure - white
        modelBuilder.node().id = "cabin";
        modelBuilder.part("cabin", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)))
            .box(-2f, 2.5f, 0f, 5f, 2f, 3f);

        // Bridge - light gray
        modelBuilder.node().id = "bridge";
        modelBuilder.part("bridge", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(ColorAttribute.createDiffuse(Color.LIGHT_GRAY)))
            .box(-2f, 4f, 0f, 2.5f, 1f, 2.5f);

        // Smokestack - red/orange
        modelBuilder.node().id = "stack";
        modelBuilder.part("stack", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(ColorAttribute.createDiffuse(new Color(0.8f, 0.3f, 0.1f, 1f))))
            .cylinder(0.6f, 1.5f, 0.6f, 12);

        model = modelBuilder.end();
        instance = new ModelInstance(model);

        Gdx.app.log("Ship3DModel", "Created procedural ship model");
    }

    public void render(PerspectiveCamera camera, Environment environment, Vector3 position, float heading) {
        // Animate bobbing
        bobOffset += Gdx.graphics.getDeltaTime() * 1.5f;
        float bobAmount = (float) Math.sin(bobOffset) * 0.3f;

        // Update transform
        instance.transform.idt();
        instance.transform.setToTranslation(position.x, bobAmount, position.z);
        instance.transform.rotate(Vector3.Y, heading - 90f);

        // Slight roll
        float roll = (float) Math.sin(bobOffset * 1.3f) * 2f;
        instance.transform.rotate(Vector3.Z, roll);

        // Render
        try {
            modelBatch.begin(camera);
            modelBatch.render(instance, environment);
            modelBatch.end();
        } catch (Exception e) {
            Gdx.app.error("Ship3DModel", "Render error: " + e.getMessage());
        }
    }

    public void dispose() {
        if (model != null) {
            model.dispose();
        }
        if (modelBatch != null) {
            modelBatch.dispose();
        }
    }
}
