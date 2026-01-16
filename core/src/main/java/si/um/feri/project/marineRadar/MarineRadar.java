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
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import si.um.feri.project.marineRadar.map.TileMapRenderer;
import si.um.feri.project.marineRadar.ship.Ship;
import si.um.feri.project.marineRadar.ship.Ship3DRenderer;
import si.um.feri.project.marineRadar.ship.ShipDataFetcher;
import si.um.feri.project.marineRadar.ship.ShipSearchPanel;

import java.util.ArrayList;
import java.util.List;

public class MarineRadar extends ApplicationAdapter {

    private TileMapRenderer map;
    private OrthographicCamera camera;
    private ShapeRenderer shapeRenderer;

    private float lastMouseX, lastMouseY;
    private boolean dragging = false;

    private Ship3DRenderer ship3DRenderer;
    private boolean show3DMode = false;

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

    private List<Ship> ships;
    private Ship selectedShip = null;
    private ShipDataFetcher shipDataFetcher;

    private Dialog settingsDialog;
    private Slider maxShipsSlider;
    private Label maxShipsValueLabel;

    private InputMultiplexer inputMultiplexer;
    private MapInputProcessor mapInputProcessor;

    private float radarAngle = 0f;
    private boolean showRadarSweep = true;
    private boolean showRoutes = true;

    private boolean animatingToShip = false;
    private Vector2 cameraTarget = new Vector2();
    private float targetZoom = 1f;
    private float animationProgress = 0f;

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_INFO);

        camera = new OrthographicCamera(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );

        map = new TileMapRenderer(camera);
        shapeRenderer = new ShapeRenderer();
        ships = new ArrayList<>();

        Gdx.app.log("MarineRadar", "Starting ship data fetcher...");
        shipDataFetcher = new ShipDataFetcher(ships);
        shipDataFetcher.startFetching();

        ship3DRenderer = new Ship3DRenderer(map);

        setupUI();

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

        Table mainTable = new Table();
        mainTable.setFillParent(true);
        mainTable.top().left();
        mainTable.pad(10);

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

        mainTable.add(infoPanel).left().row();

        TextButton settingsButton = new TextButton("Settings", skin);
        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showSettingsDialog();
            }
        });

        mainTable.add(settingsButton).left().padTop(10).row();

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

        createSettingsDialog();
    }

    private void createSettingsDialog() {
        settingsDialog = new Dialog("Settings", skin);

        Table contentTable = new Table();
        contentTable.pad(20);

        // Max Ships Slider
        Label maxShipsLabel = new Label("Max Ships: ", skin);
        maxShipsSlider = new Slider(100, 2000, 50, false, skin);
        maxShipsSlider.setValue(1000);
        maxShipsValueLabel = new Label("1000", skin);

        maxShipsSlider.addListener(event -> {
            if (event instanceof ChangeListener.ChangeEvent) {
                int value = (int) maxShipsSlider.getValue();
                maxShipsValueLabel.setText(String.valueOf(value));
                shipDataFetcher.setMaxShips(value);
                return true;
            }
            return false;
        });

        Table sliderTable = new Table();
        sliderTable.add(maxShipsLabel);
        sliderTable.add(maxShipsSlider).width(200).padLeft(10);
        sliderTable.add(maxShipsValueLabel).padLeft(10);

        contentTable.add(sliderTable).row();
        contentTable.padBottom(20);

        // Buttons
        TextButton helpButton = new TextButton("Help", skin);
        helpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                settingsDialog.hide();
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
                settingsDialog.hide();
                centerCamera();
            }
        });

        TextButton toggleSearchButton = new TextButton("Find Ship", skin);
        toggleSearchButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                shipSearchPanel.setVisible(!shipSearchPanel.isVisible());
            }
        });

        TextButton toggleCloudsButton = new TextButton("Toggle Clouds", skin);
        toggleCloudsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                map.toggleClouds();
            }
        });

        TextButton toggleRoutesButton = new TextButton("Toggle Routes", skin);
        toggleRoutesButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showRoutes = !showRoutes;
            }
        });

        Table buttonTable = new Table();
        buttonTable.add(helpButton).pad(5);
        buttonTable.add(radarToggle).pad(5).row();
        buttonTable.add(centerButton).pad(5);
        buttonTable.add(toggleSearchButton).pad(5).row();
        buttonTable.add(toggleCloudsButton).pad(5);
        buttonTable.add(toggleRoutesButton).pad(5);

        contentTable.add(buttonTable).row();

        settingsDialog.getContentTable().add(contentTable);

        TextButton closeButton = new TextButton("Close", skin);
        settingsDialog.button(closeButton);
    }

    private void showSettingsDialog() {
        settingsDialog.show(uiStage);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handleInput();
        updateRadar(delta);
        updateCameraAnimation(delta);
        updateUI();

        if ((long) (Gdx.graphics.getFrameId()) % 30 == 0) {
            shipSearchPanel.refreshShips();
        }

        Gdx.gl.glClearColor(0.0039f, 0.6431f, 0.9137f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        map.render();

        if (!show3DMode) {
            renderShipsEnhanced();
        }

        if (show3DMode && ship3DRenderer.isActive()) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            ship3DRenderer.render(delta);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        }

        if (showRadarSweep && selectedShip != null && !show3DMode) {
            renderRadarSweep();
        }

        uiStage.act(delta);
        uiStage.draw();
    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  camera.position.x -= moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.position.x += moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    camera.position.y += moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  camera.position.y -= moveSpeed * camera.zoom;

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.zoom *= (1 + zoomSpeed);
            camera.zoom = MathUtils.clamp(camera.zoom, 0.3f, 5f);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.zoom *= (1 - zoomSpeed);
            camera.zoom = MathUtils.clamp(camera.zoom, 0.3f, 5f);
        }

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
        radarAngle += 60f * delta;
        if (radarAngle >= 360f) {
            radarAngle -= 360f;
        }
    }

    private void updateCameraAnimation(float delta) {
        if (!animatingToShip) return;

        animationProgress += delta * 2f;

        if (animationProgress >= 1f) {
            animatingToShip = false;
            animationProgress = 1f;

            if (selectedShip != null) {
                show3DMode = true;
                ship3DRenderer.activate(selectedShip);
            }
        }

        float t = animationProgress * animationProgress * (3f - 2f * animationProgress);

        camera.position.x = MathUtils.lerp(camera.position.x, cameraTarget.x, t);
        camera.position.y = MathUtils.lerp(camera.position.y, cameraTarget.y, t);
        camera.zoom = MathUtils.lerp(camera.zoom, targetZoom, t);
    }

    private void updateUI() {
        Vector2 latLon = map.pixelToLatLon(camera.position.x, camera.position.y);
        positionLabel.setText(String.format("Position: %.4f, %.4f", latLon.y, latLon.x));
        zoomLabel.setText(String.format("Zoom Level: %d (%.2fx)", map.getZoomLevel(), camera.zoom));
        shipCountLabel.setText(String.format("Ships: %d", ships.size()));

        if (shipDataFetcher.isConnected()) {
            connectionLabel.setText("Status: Connected");
            connectionLabel.setColor(0f, 1f, 0f, 1f);
        } else {
            connectionLabel.setText("Status: Disconnected");
            connectionLabel.setColor(1f, 0f, 0f, 1f);
        }
    }

    private void renderShipsEnhanced() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Draw routes first
        if (showRoutes) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            for (Ship ship : ships) {
                if (ship == selectedShip && ship.isMoving()) {
                    drawShipRoute(ship);
                }
            }
            shapeRenderer.end();
        }

        // Draw ships
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Ship ship : ships) {
            Vector2 pos = map.latLonToPixel(ship.lat, ship.lon);

            if (ship == selectedShip) {
                shapeRenderer.setColor(1f, 1f, 0f, 1f);
                shapeRenderer.circle(pos.x, pos.y, 8f);
            } else if (ship.navigationalStatus == 1 || ship.navigationalStatus == 5) {
                shapeRenderer.setColor(0.6f, 0.6f, 0.6f, 1f);
                shapeRenderer.circle(pos.x, pos.y, 5f);
            } else {
                shapeRenderer.setColor(1f, 0f, 0f, 1f);
                shapeRenderer.circle(pos.x, pos.y, 5f);
            }
        }
        shapeRenderer.end();

        // Draw heading indicators
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Ship ship : ships) {
            if (ship.isMoving() && (ship == selectedShip || ship.speed > 5f)) {
                drawHeadingIndicator(ship);
            }
        }
        shapeRenderer.end();
    }

    private void drawShipRoute(Ship ship) {
        if (ship.routeInfo == null) return;

        Vector2 startPos = map.latLonToPixel(ship.routeInfo.startLat, ship.routeInfo.startLon);
        Vector2 currentPos = map.latLonToPixel(ship.lat, ship.lon);
        Vector2 destPos = map.latLonToPixel(ship.routeInfo.destLat, ship.routeInfo.destLon);

        // Draw line from start to current (green)
        shapeRenderer.setColor(0f, 1f, 0f, 0.6f);
        shapeRenderer.line(startPos.x, startPos.y, currentPos.x, currentPos.y);

        // Draw line from current to destination (cyan, dashed)
        shapeRenderer.setColor(0f, 1f, 1f, 0.6f);
        int segments = 15;
        for (int i = 0; i < segments; i += 2) {
            float t1 = i / (float) segments;
            float t2 = Math.min((i + 1) / (float) segments, 1f);

            float x1 = currentPos.x + (destPos.x - currentPos.x) * t1;
            float y1 = currentPos.y + (destPos.y - currentPos.y) * t1;
            float x2 = currentPos.x + (destPos.x - currentPos.x) * t2;
            float y2 = currentPos.y + (destPos.y - currentPos.y) * t2;

            shapeRenderer.line(x1, y1, x2, y2);
        }

        // Draw markers
        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 1f, 0f, 1f);
        shapeRenderer.circle(startPos.x, startPos.y, 4f);
        shapeRenderer.setColor(1f, 0.5f, 0f, 1f);
        shapeRenderer.circle(destPos.x, destPos.y, 4f);
        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
    }

    private void drawHeadingIndicator(Ship ship) {
        Vector2 pos = map.latLonToPixel(ship.lat, ship.lon);
        float lineLength = 25f;
        float headingRad = (ship.heading - 90) * MathUtils.degreesToRadians;
        float endX = pos.x + MathUtils.cos(headingRad) * lineLength;
        float endY = pos.y + MathUtils.sin(headingRad) * lineLength;
        shapeRenderer.setColor(1f, 0.6f, 0f, 1f);
        shapeRenderer.line(pos.x, pos.y, endX, endY);
    }

    private void renderRadarSweep() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        Vector2 pos = map.latLonToPixel(selectedShip.lat, selectedShip.lon);
        float radius = 50f / camera.zoom;

        shapeRenderer.setColor(0f, 1f, 0f, 0.5f);
        shapeRenderer.circle(pos.x, pos.y, radius, 32);

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
        animatingToShip = false;
        show3DMode = false;
        ship3DRenderer.deactivate();
    }

    private void centerOnSelectedShip() {
        if (selectedSearchShip != null) {
            Vector2 pos = map.latLonToPixel(selectedSearchShip.lat, selectedSearchShip.lon);
            camera.position.set(pos.x, pos.y, 0);
            camera.update();
        }
    }

    private void showHelpScreen() {
        Dialog dialog = new Dialog("Controls", skin);
        dialog.text(
            "NAVIGATION:\n" +
                "• Mouse drag - move map\n" +
                "• Scroll wheel - zoom\n" +
                "• Arrow keys - move\n" +
                "• A / S - zoom\n\n" +
                "SHIPS:\n" +
                "• Click ship - details\n" +
                "• Routes show origin → destination\n" +
                "• Green line: traveled\n" +
                "• Cyan line: projected route\n\n" +
                "FEATURES:\n" +
                "• Real-time AIS data\n" +
                "• 3D ship view\n" +
                "• Limited to 100 ships"
        );
        dialog.button("Close");
        dialog.show(uiStage);
    }

    private void showShipDetails(Ship ship) {
        Dialog dialog = new Dialog("Vessel Information", skin) {
            @Override
            protected void result(Object object) {
                if (object != null && object.equals("track")) {
                    selectedShip = ship;
                } else if (object != null && object.equals("focus")) {
                    focusOnShip(ship);
                }
            }
        };

        StringBuilder details = new StringBuilder();

        details.append("=== VESSEL IDENTITY ===\n");
        details.append("Name: ").append(ship.name).append("\n");
        details.append("MMSI: ").append(ship.mmsi).append("\n");
        if (!ship.callSign.isEmpty()) {
            details.append("Call Sign: ").append(ship.callSign).append("\n");
        }
        if (ship.imoNumber > 0) {
            details.append("IMO: ").append(ship.imoNumber).append("\n");
        }
        details.append("Type: ").append(ship.shipType).append("\n");

        details.append("\n=== ROUTE ===\n");
        if (ship.routeInfo != null) {
            details.append("From: ").append(ship.routeInfo.startLocation).append("\n");
            details.append("Heading To: ").append(ship.routeInfo.destLocation).append("\n");
        }

        details.append("\n=== CURRENT STATUS ===\n");
        details.append(String.format("Position: %.4f, %.4f\n", ship.lat, ship.lon));
        details.append(String.format("Speed: %.1f knots\n", ship.speed));
        details.append(String.format("Course: %.1f°\n", ship.course));
        details.append(String.format("Heading: %.1f°\n", ship.heading));
        details.append("Status: ").append(ship.getNavigationalStatusText()).append("\n");

        if (ship.shipLength > 0 || ship.draught > 0) {
            details.append("\n=== DIMENSIONS ===\n");
            if (ship.shipLength > 0 && ship.shipWidth > 0) {
                details.append("Size: ").append(ship.getFormattedSize()).append("\n");
            }
            if (ship.draught > 0) {
                details.append(String.format("Draught: %.1f m\n", ship.draught));
            }
        }

        if (!ship.destination.equals("Unknown")) {
            details.append("\n=== VOYAGE ===\n");
            details.append("Destination: ").append(ship.destination).append("\n");
            if (ship.eta != null && ship.eta.isValid()) {
                details.append("ETA: ").append(ship.eta.toString()).append("\n");
            }
        }

        dialog.text(details.toString());
        dialog.button("Track", "track");
        dialog.button("Focus & 3D", "focus");
        dialog.button("Close", "close");
        dialog.show(uiStage);
    }

    private void focusOnShip(Ship ship) {
        selectedShip = ship;
        Vector2 pos = map.latLonToPixel(ship.lat, ship.lon);

        cameraTarget.set(pos);
        targetZoom = 0.15f;
        animationProgress = 0f;
        animatingToShip = true;

        Gdx.app.log("MarineRadar", "Focusing on ship: " + ship.name);
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
        ship3DRenderer.dispose();
        shipDataFetcher.stop();
    }

    private class MapInputProcessor extends InputAdapter {
        @Override
        public boolean scrolled(float amountX, float amountY) {
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
