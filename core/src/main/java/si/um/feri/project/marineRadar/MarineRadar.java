package si.um.feri.project.marineRadar;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.util.ArrayList;
import java.util.List;

public class MarineRadar extends ApplicationAdapter {

    private TileMapRenderer map;
    private OrthographicCamera camera;
    private ShapeRenderer shapeRenderer;

    private float lastMouseX, lastMouseY;
    private boolean dragging = false;

    private Stage uiStage;
    private Skin skin;
    private Label positionLabel;
    private Label zoomLabel;
    private Label shipCountLabel;
    private Label connectionLabel;
    private ShipSearchPanel shipSearchPanel;
    private Ship selectedSearchShip = null;

    private float zoomSpeed = 0.05f;
    private float moveSpeed = 5f;

    // Ship management
    private List<Ship> ships;
    private Ship selectedShip = null;
    private ShipDataFetcher shipDataFetcher;

    // Input handling
    private InputMultiplexer inputMultiplexer;
    private MapInputProcessor mapInputProcessor;

    // Radar sweep effect
    private float radarAngle = 0f;
    private boolean showRadarSweep = true;

    @Override
    public void create() {
        // Enable debug logging
        Gdx.app.setLogLevel(com.badlogic.gdx.Application.LOG_DEBUG);

        camera = new OrthographicCamera(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );

        map = new TileMapRenderer(camera);
        shapeRenderer = new ShapeRenderer();
        ships = new ArrayList<>();

        // Initialize ship data fetcher with real AISStream.io data
        Gdx.app.log("MarineRadar", "Starting ship data fetcher...");
        shipDataFetcher = new ShipDataFetcher(ships);
        shipDataFetcher.startFetching();

        // Setup UI
        setupUI();

        // Setup input handling
        mapInputProcessor = new MapInputProcessor();
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(mapInputProcessor);
        inputMultiplexer.addProcessor(uiStage);
        Gdx.input.setInputProcessor(inputMultiplexer);

        Gdx.app.log("MarineRadar", "Initialization complete");
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
        shipCountLabel = new Label("Ships: 0", skin);
        connectionLabel = new Label("Status: Connecting...", skin);

        infoPanel.add(positionLabel).left().row();
        infoPanel.add(zoomLabel).left().row();
        infoPanel.add(shipCountLabel).left().row();
        infoPanel.add(connectionLabel).left().row();

        // Control buttons
        Table buttonTable = new Table();

        TextButton helpButton = new TextButton("Help", skin);
        helpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showHelpScreen();
            }
        });

        TextButton radarToggle = new TextButton("Toggle Radar", skin);
        radarToggle.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showRadarSweep = !showRadarSweep;
            }
        });

        TextButton centerButton = new TextButton("Center Map", skin);
        centerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                centerCamera();
            }
        });
        
        TextButton toggleSearchButton = new TextButton("Find Ship", skin);
        toggleSearchButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (shipSearchPanel.isVisible()) {
                    shipSearchPanel.setVisible(false);
                } else {
                    shipSearchPanel.setVisible(true);
                }
            }
        });
        
        TextButton toggleCloudsButton = new TextButton("Toggle Clouds", skin);
        toggleCloudsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                map.toggleClouds();
            }
        });

        buttonTable.add(helpButton).pad(5);
        buttonTable.add(radarToggle).pad(5);
        buttonTable.add(centerButton).pad(5);
        buttonTable.add(toggleSearchButton).pad(5);
        buttonTable.add(toggleCloudsButton).pad(5);

        mainTable.add(infoPanel).left().row();
        mainTable.add(buttonTable).left().padTop(10).row();
        
        shipSearchPanel = new ShipSearchPanel(ships, skin, new ShipSearchPanel.ShipSelectionListener() {
            @Override
            public void onShipSelected(Ship ship) {
                selectedSearchShip = ship;
            }
            
            @Override
            public void onShipDoubleClicked(Ship ship) {
                selectedSearchShip = ship;
                centerOnSelectedShip();
            }
        });
        shipSearchPanel.setVisible(false);
        mainTable.add(shipSearchPanel).left().padTop(10).row();

        uiStage.addActor(mainTable);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handleInput();
        updateRadar(delta);
        updateUI();
        
        // Refresh ship list periodically
        if ((long) (Gdx.graphics.getFrameId()) % 30 == 0) {
            shipSearchPanel.refreshShips();
        }

        Gdx.gl.glClearColor(0.0039f, 0.6431f, 0.9137f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();

        // Render map
        map.render();

        // Render ships
        renderShips();

        // Render radar sweep
        if (showRadarSweep && selectedShip != null) {
            renderRadarSweep();
        }

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

    private void updateRadar(float delta) {
        radarAngle += 60f * delta; // 60 degrees per second
        if (radarAngle >= 360f) {
            radarAngle -= 360f;
        }
    }

    private void updateUI() {
        Vector2 latLon = map.pixelToLatLon(camera.position.x, camera.position.y);
        positionLabel.setText(String.format("Position: %.4f, %.4f", latLon.y, latLon.x));
        zoomLabel.setText(String.format("Zoom Level: %d (%.2fx)", map.getZoomLevel(), camera.zoom));
        shipCountLabel.setText(String.format("Ships: %d", ships.size()));

        // Update connection status
        if (shipDataFetcher.isConnected()) {
            connectionLabel.setText("Status: Connected");
            connectionLabel.setColor(0f, 1f, 0f, 1f); // Green
        } else {
            connectionLabel.setText("Status: Disconnected");
            connectionLabel.setColor(1f, 0f, 0f, 1f); // Red
        }
    }

    private void renderShips() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (Ship ship : ships) {
            Vector2 pos = map.latLonToPixel(ship.lat, ship.lon);

            if (ship == selectedShip) {
                shapeRenderer.setColor(1f, 1f, 0f, 1f); // Yellow for selected
                shapeRenderer.circle(pos.x, pos.y, 8f);
            } else {
                shapeRenderer.setColor(1f, 0f, 0f, 1f); // Red for ships
                shapeRenderer.circle(pos.x, pos.y, 5f);
            }
        }

        shapeRenderer.end();
    }

    private void renderRadarSweep() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        Vector2 pos = map.latLonToPixel(selectedShip.lat, selectedShip.lon);
        float radius = 50f / camera.zoom;

        shapeRenderer.setColor(0f, 1f, 0f, 0.5f);
        shapeRenderer.circle(pos.x, pos.y, radius, 32);

        // Draw sweep line
        float rad = radarAngle * MathUtils.degreesToRadians;
        float endX = pos.x + MathUtils.cos(rad) * radius;
        float endY = pos.y + MathUtils.sin(rad) * radius;

        shapeRenderer.setColor(0f, 1f, 0f, 1f);
        shapeRenderer.line(pos.x, pos.y, endX, endY);

        shapeRenderer.end();
    }

    private void centerCamera() {
        int worldSize = map.getWorldSize();
        camera.position.set(worldSize / 2f, worldSize / 2f, 0);
        camera.zoom = 1f;
    }

    private void centerOnSelectedShip() {
        if (selectedSearchShip != null) {
            Vector2 pos = map.latLonToPixel(selectedSearchShip.lat, selectedSearchShip.lon);
            camera.position.set(pos.x, pos.y, 0);
            camera.update();
        } else {
            Gdx.app.log("MarineRadar", "No ship selected");
        }
    }

    private void showHelpScreen() {
        Dialog dialog = new Dialog("Controls", skin);
        dialog.text(
            "NAVIGATION:\n" +
                "• Mouse drag - move map\n" +
                "• Scroll wheel - zoom in/out\n" +
                "• Arrow keys - move map\n" +
                "• A / S - zoom in/out\n\n" +
                "SHIPS:\n" +
                "• Click on ship - view details\n" +
                "• Red dots - vessels\n" +
                "• Yellow dot - selected vessel\n\n" +
                "FEATURES:\n" +
                "• Real-time AIS data from AISStream.io\n" +
                "• Toggle radar sweep effect\n" +
                "• Center map button"
        );
        dialog.button("Close");
        dialog.show(uiStage);
    }

    private void showShipDetails(Ship ship) {
        Dialog dialog = new Dialog("Vessel Details", skin) {
            @Override
            protected void result(Object object) {
                if (object != null && object.equals(true)) {
                    selectedShip = ship;
                }
            }
        };

        String details = String.format(
            "Name: %s\n" +
                "MMSI: %s\n" +
                "Type: %s\n" +
                "Position: %.4f, %.4f\n" +
                "Speed: %.1f knots\n" +
                "Course: %.1f°\n" +
                "Heading: %.1f°",
            ship.name != null ? ship.name : "Unknown",
            ship.mmsi != null ? ship.mmsi : "N/A",
            ship.type != null ? ship.type : "Unknown",
            ship.lat, ship.lon,
            ship.speed,
            ship.course,
            ship.heading
        );

        dialog.text(details);
        dialog.button("Track", true);
        dialog.button("Close", false);
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
        shapeRenderer.dispose();
        uiStage.dispose();
        skin.dispose();
        shipDataFetcher.stop();
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
            if (button == Input.Buttons.LEFT) {
                Vector3 worldPos = camera.unproject(new Vector3(screenX, screenY, 0));
                Ship clicked = findShipAt(worldPos.x, worldPos.y);

                if (clicked != null) {
                    selectedShip = clicked;
                    showShipDetails(clicked);
                    return true;
                }
            }
            return false;
        }

        private Ship findShipAt(float worldX, float worldY) {
            float clickRadius = 10f * camera.zoom;

            for (Ship ship : ships) {
                Vector2 pos = map.latLonToPixel(ship.lat, ship.lon);
                float dist = Vector2.dst(worldX, worldY, pos.x, pos.y);

                if (dist < clickRadius) {
                    return ship;
                }
            }
            return null;
        }
    }
}
