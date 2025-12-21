package si.um.feri.project.marineRadar;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class MarineRadar extends ApplicationAdapter {

    private TileMapRenderer map;
    private OrthographicCamera camera;

    private float lastMouseX, lastMouseY;
    private boolean dragging = false;

    private Stage uiStage;
    private Skin skin;
    private Label positionLabel;
    private Label zoomLabel;

    private float zoomSpeed = 0.05f;
    private float moveSpeed = 5f;

    // Input handling
    private InputMultiplexer inputMultiplexer;
    private MapInputProcessor mapInputProcessor;

    @Override
    public void create() {
        camera = new OrthographicCamera(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );

        map = new TileMapRenderer(camera);

        // Setup UI
        setupUI();

        // Setup input handling
        mapInputProcessor = new MapInputProcessor();
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(uiStage);
        inputMultiplexer.addProcessor(mapInputProcessor);
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    private void setupUI() {
        uiStage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        // Main UI container
        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.top().left();
        mainTable.pad(10);

        // Info panel
        Table infoPanel = new Table(skin);
        infoPanel.setBackground("default-rect");
        infoPanel.pad(10);

        positionLabel = new Label("Position: 0.0, 0.0", skin);
        zoomLabel = new Label("Zoom: 1.0", skin);

        infoPanel.add(positionLabel).left().row();
        infoPanel.add(zoomLabel).left().row();

        // Control buttons
        Table buttonTable = new Table();

        TextButton helpButton = new TextButton("Help", skin);
        helpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showHelpScreen();
            }
        });

        TextButton centerButton = new TextButton("Center Map", skin);
        centerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                centerCamera();
            }
        });

        buttonTable.add(helpButton).pad(5);
        buttonTable.add(centerButton).pad(5);

        mainTable.add(infoPanel).left().row();
        mainTable.add(buttonTable).left().padTop(10);

        uiStage.addActor(mainTable);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handleInput();
        updateUI();

        Gdx.gl.glClearColor(0.0039f, 0.6431f, 0.9137f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();

        // Render map
        map.render();

        // Render UI
        uiStage.act(delta);
        uiStage.draw();
    }

    private void handleInput() {
        // Keyboard movement
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  camera.position.x -= moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.position.x += moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    camera.position.y += moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  camera.position.y -= moveSpeed * camera.zoom;

        // Keyboard zoom
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.zoom *= (1 + zoomSpeed);
            camera.zoom = MathUtils.clamp(camera.zoom, 0.3f, 5f);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.zoom *= (1 - zoomSpeed);
            camera.zoom = MathUtils.clamp(camera.zoom, 0.3f, 5f);
        }

        // Mouse dragging
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && uiStage.hit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY(), true) == null) {
            if (!dragging) {
                dragging = true;
                lastMouseX = Gdx.input.getX();
                lastMouseY = Gdx.input.getY();
            } else {
                float dx = Gdx.input.getX() - lastMouseX;
                float dy = Gdx.input.getY() - lastMouseY;

                camera.position.x -= dx * camera.zoom;
                camera.position.y += dy * camera.zoom;

                lastMouseX = Gdx.input.getX();
                lastMouseY = Gdx.input.getY();
            }
        } else {
            dragging = false;
        }

        // Keep camera in bounds
        clampCamera();
    }

    private void clampCamera() {
        int worldSize = map.getWorldSize();
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;

        camera.position.x = MathUtils.clamp(camera.position.x, halfWidth, worldSize - halfWidth);
        camera.position.y = MathUtils.clamp(camera.position.y, halfHeight, worldSize - halfHeight);
    }

    private void updateUI() {
        Vector2 latLon = map.pixelToLatLon(camera.position.x, camera.position.y);
        positionLabel.setText(String.format("Position: %.4f, %.4f", latLon.y, latLon.x));
        zoomLabel.setText(String.format("Zoom Level: %d (%.2fx)", map.getZoomLevel(), camera.zoom));
    }

    private void centerCamera() {
        int worldSize = map.getWorldSize();
        camera.position.set(worldSize / 2f, worldSize / 2f, 0);
        camera.zoom = 1f;
    }

    private void showHelpScreen() {
        Dialog dialog = new Dialog("Controls", skin);
        dialog.text(
            "NAVIGATION:\n" +
                "• Mouse drag - move map\n" +
                "• Scroll wheel - zoom in/out\n" +
                "• Arrow keys - move map\n" +
                "• A / S - zoom in/out\n\n" +
                "FEATURES:\n" +
                "• Real-time tile loading\n" +
                "• Zoom towards cursor\n" +
                "• Center map button"
        );
        dialog.button("Close");
        dialog.show(uiStage);
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        uiStage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        map.dispose();
        uiStage.dispose();
        skin.dispose();
    }

    // Input processor for map interactions
    private class MapInputProcessor extends InputAdapter {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            // Zoom towards cursor position
            map.zoomTowardsCursor(amountY, Gdx.input.getX(), Gdx.input.getY());
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            // Future: handle map clicks for selecting ships
            return false;
        }
    }
}
