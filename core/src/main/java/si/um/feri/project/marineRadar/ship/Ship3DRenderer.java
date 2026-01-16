package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import si.um.feri.project.marineRadar.map.TileMapRenderer;

public class Ship3DRenderer {
    private Ship3DModel shipModel;
    private PerspectiveCamera camera3D;
    private Environment environment;
    private TileMapRenderer mapRenderer;

    private boolean active = false;
    private Ship currentShip = null;

    public Ship3DRenderer(TileMapRenderer mapRenderer) {
        this.mapRenderer = mapRenderer;

        // Create 3D camera
        camera3D = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera3D.near = 1f;
        camera3D.far = 300f;

        // Create lighting environment
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f));
        environment.add(new DirectionalLight().set(0.9f, 0.9f, 0.9f, -0.5f, -1f, -0.2f));

        // Create ship 3D model
        shipModel = new Ship3DModel();

        Gdx.app.log("Ship3DRenderer", "3D renderer initialized");
    }

    public void activate(Ship ship) {
        this.currentShip = ship;
        this.active = true;
        Gdx.app.log("Ship3DRenderer", "3D view activated for " + ship.name);
    }

    public void deactivate() {
        this.active = false;
        this.currentShip = null;
        Gdx.app.log("Ship3DRenderer", "3D view deactivated");
    }

    public boolean isActive() {
        return active;
    }

    public void render(float delta) {
        if (!active || currentShip == null) {
            return;
        }

        // Get ship world position (using 2D map coordinates)
        Vector2 shipPos2D = mapRenderer.latLonToPixel(currentShip.lat, currentShip.lon);

        // Camera positioning - close side view
        float cameraDistance = 50f;
        float cameraHeight = 15f;
        float sideAngle = 130f;

        float headingRad = (float) Math.toRadians(currentShip.heading + sideAngle);
        float offsetX = (float) Math.sin(headingRad) * cameraDistance;
        float offsetZ = (float) Math.cos(headingRad) * cameraDistance;

        camera3D.position.set(offsetX, cameraHeight, offsetZ);
        camera3D.lookAt(0, 3f, 0);
        camera3D.up.set(0, 1, 0);
        camera3D.update();

        // Render ship at origin (0,0,0) with proper orientation
        Vector3 shipPos3D = new Vector3(0, 0, 0);

        shipModel.render(camera3D, environment, shipPos3D, currentShip.heading);
    }

    public void dispose() {
        shipModel.dispose();
    }
}
