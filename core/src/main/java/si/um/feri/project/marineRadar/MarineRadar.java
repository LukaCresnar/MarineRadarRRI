package si.um.feri.project.marineRadar;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import si.um.feri.project.marineRadar.map.TileMapRenderer;
import si.um.feri.project.marineRadar.ship.Ship;
import si.um.feri.project.marineRadar.ship.Ship3DRenderer;
import si.um.feri.project.marineRadar.ship.ShipDataFetcher;
import si.um.feri.project.marineRadar.ship.ShipDetailsDialog;
import si.um.feri.project.marineRadar.ship.ShipSearchPanel;
import si.um.feri.project.marineRadar.ship.ShipRenderer;

import java.util.ArrayList;
import java.util.List;

public class MarineRadar extends ApplicationAdapter {

    private TileMapRenderer map;
    private OrthographicCamera camera;
    private ShapeRenderer shapeRenderer;

    private float lastMouseX, lastMouseY;
    private boolean dragging = false;

    private Ship3DRenderer ship3DRenderer;
    private ShipRenderer shipRenderer;
    private boolean show3DMode = false;

    private Stage uiStage;
    private Skin skin;
    // Configurable skin file path (system property: 'marineRadar.skin')
    private String skinFile;
    public static final String SKIN_PROPERTY = "marineRadar.skin";
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
    
    private ShipDetailsDialog currentShipDialog = null; // Track currently open ship dialog
    
    // 3D mode UI elements
    private Table mode3DUI;
    private Label ship3DInfoLabel;
    private ShipDetailsDialog ship3DInfoDialog = null; // Ship info in 3D mode (compact, no buttons)
    
    // Main UI table (to hide in 3D mode)
    private Table mainUITable;

    private InputMultiplexer inputMultiplexer;
    private MapInputProcessor mapInputProcessor;

    private float radarAngle = 0f;
    private boolean showRadarSweep = true;
    private boolean showRoutes = true;

    private boolean animatingToShip = false;
    private Vector2 cameraTarget = new Vector2();
    private float targetZoom = 1f;
    private float animationProgress = 0f;
    private long lastSimUpdate = 0; // Timestamp of last simulation update (ms)
    
    private boolean trackingShip = false; // Continuous tracking mode
    private Ship trackedShip = null; // Ship being tracked
    
    private boolean animatingZoomOut = false; // Zooming out to center
    private int startMapZoomLevel = 3; // Starting map zoom level for animation
    private int targetMapZoomLevel = 3; // Target map zoom level for animation
    private float lastZoomTransitionCheck = 0f; // For gradual zoom transitions

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
        shipRenderer = new ShipRenderer(shapeRenderer, map);

        setupUI();

        mapInputProcessor = new MapInputProcessor();
        inputMultiplexer = new InputMultiplexer();
        // UI stage must be first to receive button clicks before other processors
        inputMultiplexer.addProcessor(uiStage);
        inputMultiplexer.addProcessor(mapInputProcessor);
        Gdx.input.setInputProcessor(inputMultiplexer);

        Gdx.app.log("MarineRadar", "Initialization complete");
    }

    private void setupUI() {
        uiStage = new Stage(new ScreenViewport());
        // Use configurable skin file; default to skin/metal-ui.json
        skinFile = System.getProperty(SKIN_PROPERTY, "skin/metal-ui.json");
        Gdx.app.log("MarineRadar", "Using skin file: " + skinFile);
        try {
            if (Gdx.files.internal(skinFile).exists()) {
                skin = new Skin(Gdx.files.internal(skinFile));
                Gdx.app.log("MarineRadar", "Loaded skin: " + skinFile);
            } else {
                Gdx.app.log("MarineRadar", "Skin file not found: " + skinFile + ". Creating minimal skin.");
                skin = new Skin();
                BitmapFont font = new BitmapFont();
                Label.LabelStyle labelStyle = new Label.LabelStyle(font, com.badlogic.gdx.graphics.Color.WHITE);
                skin.add("default", labelStyle);
                TextButton.TextButtonStyle tbs = new TextButton.TextButtonStyle();
                tbs.font = font;
                skin.add("default", tbs);
            }
        } catch (Exception e) {
            Gdx.app.log("MarineRadar", "Failed to load skin '" + skinFile + "': " + e.getMessage() + ". Creating minimal skin.");
            skin = new Skin();
            BitmapFont font = new BitmapFont();
            Label.LabelStyle labelStyle = new Label.LabelStyle(font, com.badlogic.gdx.graphics.Color.WHITE);
            skin.add("default", labelStyle);
            TextButton.TextButtonStyle tbs = new TextButton.TextButtonStyle();
            tbs.font = font;
            skin.add("default", tbs);
        }

        // Ensure 'default-rect' drawable exists (many UI tables expect it). If missing, add a simple colored drawable.
        if (!skin.has("default-rect", Drawable.class)) {
            Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
            pm.setColor(0.12f, 0.12f, 0.12f, 1f);
            pm.fill();
            Texture tex = new Texture(pm);
            pm.dispose();
            skin.add("default-rect", new TextureRegionDrawable(new TextureRegion(tex)));
            Gdx.app.log("MarineRadar", "Added fallback drawable 'default-rect' to skin.");
        }

        // Resolve drawables to use directly (safer than relying on skin name lookup at setBackground time)
        Drawable defaultRectDrawable;
        if (skin.has("default-rect", Drawable.class)) {
            defaultRectDrawable = skin.getDrawable("default-rect");
        } else {
            Pixmap pm2 = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
            pm2.setColor(0.12f, 0.12f, 0.12f, 1f);
            pm2.fill();
            Texture tex2 = new Texture(pm2);
            pm2.dispose();
            defaultRectDrawable = new TextureRegionDrawable(new TextureRegion(tex2));
        }

        // Prefer a white background for info panels; fall back to 'rect' or create a white texture
        Drawable whiteDrawable;
        if (skin.has("white", Drawable.class)) {
            whiteDrawable = skin.getDrawable("white");
        } else if (skin.has("rect", Drawable.class)) {
            whiteDrawable = skin.getDrawable("rect");
        } else {
            Pixmap pm3 = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
            pm3.setColor(1f, 1f, 1f, 1f);
            pm3.fill();
            Texture tex3 = new Texture(pm3);
            pm3.dispose();
            whiteDrawable = new TextureRegionDrawable(new TextureRegion(tex3));
        }

        mainUITable = new Table();
        mainUITable.setFillParent(true);
        mainUITable.top().left();
        mainUITable.pad(10);

        Table infoPanel = new Table(skin);
        infoPanel.setBackground(whiteDrawable);
        infoPanel.pad(10);

        positionLabel = new Label("Position: 0.0, 0.0", skin);
        zoomLabel = new Label("Zoom: 1.0", skin);
        shipCountLabel = new Label("Ships: 0", skin);
        connectionLabel = new Label("Status: Connecting...", skin);

        infoPanel.add(positionLabel).left().row();
        infoPanel.add(zoomLabel).left().row();
        infoPanel.add(shipCountLabel).left().row();
        infoPanel.add(connectionLabel).left().row();

        mainUITable.add(infoPanel).left().row();

        TextButton settingsButton = new TextButton("Settings", skin);
        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showSettingsDialog();
            }
        });

        mainUITable.add(settingsButton).left().padTop(10).row();
        
        TextButton centerButton = new TextButton("Center Map", skin);
        centerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                centerCamera();
            }
        });
        
        mainUITable.add(centerButton).left().padTop(10).row();
        
        TextButton findShipButton = new TextButton("Find Ship", skin);
        findShipButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                boolean show = !shipSearchPanel.isVisible();
                shipSearchPanel.setVisible(show);
                shipSearchPanel.setTouchable(show ? Touchable.enabled : Touchable.disabled);
                // If showing, give keyboard focus to the search field; if hiding, clear scroll/keyboard focus so map scroll works
                if (show) {
                    uiStage.setKeyboardFocus(shipSearchPanel);
                    uiStage.setScrollFocus(shipSearchPanel);
                } else {
                    uiStage.setKeyboardFocus(null);
                    uiStage.setScrollFocus(null);
                }
            }
        });
        
        mainUITable.add(findShipButton).left().padTop(10).row();

        shipSearchPanel = new ShipSearchPanel(ships, skin, new ShipSearchPanel.ShipSelectionListener() {
            @Override
            public void onShipSelected(Ship ship) {
                // Remember selected in the search panel
                selectedSearchShip = ship;
                shipSearchPanel.setSelectedShip(ship);

                // Center camera on the ship's position
                Vector2 pos = map.latLonToPixel(ship.lat, ship.lon);
                camera.position.set(pos.x, pos.y, 0);
                camera.update();

                // Mark selection
                if (selectedShip != null) {
                    selectedShip.isSelected = false;
                }
                selectedShip = ship;
                selectedShip.isSelected = true;
            }

            @Override
            public void onShipDetails(Ship ship) {
                // Open ship details dialog after centering
                showShipDetails(ship);
            }

            
            @Override
            public void onClose() {
                shipSearchPanel.setVisible(false);
                shipSearchPanel.setTouchable(Touchable.disabled);
                uiStage.setKeyboardFocus(null);
                uiStage.setScrollFocus(null);
            }
        });
        shipSearchPanel.setVisible(false);
        mainUITable.add(shipSearchPanel).left().padTop(10).row();

        uiStage.addActor(mainUITable);
        
        // Create 3D mode UI (separate table that's visible only in 3D mode)
        mode3DUI = new Table();
        mode3DUI.setFillParent(true);
        mode3DUI.top().left();
        mode3DUI.pad(10);
        
        Table info3DPanel = new Table(skin);
        // Use white background for the 3D info/controls panel for better contrast
        info3DPanel.setBackground(whiteDrawable);
        info3DPanel.pad(10);
        
        ship3DInfoLabel = new Label("3D View Mode", skin);
        ship3DInfoLabel.setColor(0.2f, 0.8f, 1f, 1f);
        info3DPanel.add(ship3DInfoLabel).left().row();
        
        Label controls3DLabel = new Label("Controls:\nA/D or Arrows: Rotate camera\nW/S: Zoom in/out\nQ/E: Camera height\n1-4: Camera modes\nESC: Exit 3D view", skin);
        controls3DLabel.setFontScale(0.9f);
        info3DPanel.add(controls3DLabel).left().padTop(10).row();
        
        mode3DUI.add(info3DPanel).left().row();
        
        TextButton exit3DButton = new TextButton("Exit 3D View", skin);
        exit3DButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                exit3DMode();
            }
        });
        mode3DUI.add(exit3DButton).left().padTop(10).row();
        
        mode3DUI.setVisible(false);
        uiStage.addActor(mode3DUI);

        createSettingsDialog();
    }

    private void createSettingsDialog() {
        settingsDialog = new Dialog("Settings", skin);

        Table contentTable = new Table();
        contentTable.pad(20);

        // Max Ships Slider
        Label maxShipsLabel = new Label("Max Ships: ", skin);
        maxShipsSlider = new Slider(0, 2000, 50, false, skin);
        maxShipsSlider.setValue(1000);
        maxShipsValueLabel = new Label("1000", skin);

        maxShipsSlider.addListener(event -> {
            if (event instanceof ChangeListener.ChangeEvent) {
                int value = (int) maxShipsSlider.getValue();
                maxShipsValueLabel.setText(String.valueOf(value));
                shipDataFetcher.setMaxShips(value);
                
                // Remove excess ships if slider value is below current ship count
                while (ships.size() > value) {
                    int randomIndex = (int) (Math.random() * ships.size());
                    Ship removedShip = ships.remove(randomIndex);
                    
                    // Clear selection if we removed the selected or tracked ship
                    if (selectedShip == removedShip) {
                        selectedShip = null;
                    }
                    if (trackedShip == removedShip) {
                        trackedShip = null;
                        trackingShip = false;
                    }
                }
                
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
        buttonTable.add(toggleCloudsButton).pad(5);
        buttonTable.add(toggleRoutesButton).pad(5);

        contentTable.add(buttonTable).row();

        settingsDialog.getContentTable().add(contentTable);

        TextButton closeButton = new TextButton("Close", skin);
        settingsDialog.button(closeButton);
        
        // Make dialog modal to block interaction with elements behind it
        settingsDialog.setModal(true);
    }

    private void showSettingsDialog() {
        settingsDialog.show(uiStage);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // --- SIMULATION: move ships every 2 seconds using course and speed ---
        long now = System.currentTimeMillis();
        if (now - lastSimUpdate >= Ship.UPDATE_INTERVAL_MS) {
            for (Ship ship : ships) {
                ship.simulateMovement(Ship.UPDATE_INTERVAL_MS);
            }
            lastSimUpdate = now;
        }

        // Handle 3D mode rendering (full screen 3D scene)
        if (show3DMode && ship3DRenderer.isActive()) {
            // Show 3D UI, hide 2D UI
            mode3DUI.setVisible(true);
            
            // Update 3D info label
            Ship currentShip = ship3DRenderer.getCurrentShip();
            if (currentShip != null) {
                ship3DInfoLabel.setText(String.format("3D View: %s\nSpeed: %.1f kn | Heading: %.0f°",
                    currentShip.name, currentShip.speed, currentShip.heading));
            }
            
            // 3D mode - render ocean scene with ship
            Gdx.gl.glClearColor(0.4f, 0.6f, 0.9f, 1f); // Sky blue
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            
            // Check for escape key to exit 3D mode
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                exit3DMode();
            }
            
            // Render 3D scene
            ship3DRenderer.render(delta);
            
            // Draw UI overlay (including exit button)
            uiStage.act(delta);
            uiStage.draw();
            return;
        }
        
        // Hide 3D UI when not in 3D mode
        mode3DUI.setVisible(false);

        // Regular 2D map mode
        handleInput();
        updateRadar(delta);
        updateCameraAnimation(delta);
        updateUI();

        if ((long) (Gdx.graphics.getFrameId()) % 30 == 0) {
            shipSearchPanel.refreshShips();
        }

        // --- DEBUG: Log zoom level and camera zoom ---
        if (Gdx.graphics.getFrameId() % 60 == 0) {
            Gdx.app.log("MarineRadar", "Zoom label: " + map.getZoomLevel() + ", camera.zoom: " + camera.zoom);
        }
        // --- END DEBUG ---

        Gdx.gl.glClearColor(0.0039f, 0.6431f, 0.9137f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        map.render();

        // Render ships as 2D icons
        shipRenderer.render(camera, ships, selectedShip, trackedShip);

        if (showRadarSweep && selectedShip != null) {
            renderRadarSweep();
        }

        uiStage.act(delta);
        uiStage.draw();
    }

    private void handleInput() {
        // Manual input disables tracking mode
        boolean manualInput = false;
        
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.position.x -= moveSpeed * camera.zoom;
            manualInput = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.position.x += moveSpeed * camera.zoom;
            manualInput = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.position.y += moveSpeed * camera.zoom;
            manualInput = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.position.y -= moveSpeed * camera.zoom;
            manualInput = true;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.zoom *= (1 + zoomSpeed);
            camera.zoom = MathUtils.clamp(camera.zoom, 0.3f, 5f);
            manualInput = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.zoom *= (1 - zoomSpeed);
            camera.zoom = MathUtils.clamp(camera.zoom, 0.3f, 5f);
            manualInput = true;
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
                manualInput = true;
            }
        } else {
            dragging = false;
        }
        
        // Disable tracking if user manually moves camera
        if (manualInput) {
            trackingShip = false;
            trackedShip = null;
            animatingZoomOut = false;
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
        // Handle zoom out animation
        if (animatingZoomOut) {
            animationProgress += delta * 0.6f; // Smooth gradual zoom out
            
            if (animationProgress >= 1f) {
                animatingZoomOut = false;
                animationProgress = 1f;
                
                // Ensure final zoom level and position
                if (map.getZoomLevel() != targetMapZoomLevel) {
                    map.setZoomLevel(targetMapZoomLevel);
                }
                int worldSize = map.getWorldSize();
                camera.position.set(worldSize / 2f, worldSize / 2f, 0);
                camera.zoom = 1f;
                camera.update();
            } else {
                // Smooth exponential easing
                float t = 1f - (float) Math.pow(1f - animationProgress, 3);
                
                // Gradually change map zoom level based on progress
                int currentMapZoom = map.getZoomLevel();
                float zoomProgress = MathUtils.lerp(startMapZoomLevel, targetMapZoomLevel, t);
                int desiredMapZoom = Math.round(zoomProgress);
                
                if (desiredMapZoom != currentMapZoom && desiredMapZoom >= targetMapZoomLevel && desiredMapZoom <= startMapZoomLevel) {
                    map.setZoomLevel(desiredMapZoom);
                    // Recalculate target position for new zoom level
                    int worldSize = map.getWorldSize();
                    cameraTarget.set(worldSize / 2f, worldSize / 2f);
                }
                
                // Smooth camera movement - higher lerp for fluid motion
                camera.position.x = MathUtils.lerp(camera.position.x, cameraTarget.x, delta * 3f);
                camera.position.y = MathUtils.lerp(camera.position.y, cameraTarget.y, delta * 3f);
                
                // Smooth continuous zoom using camera zoom for in-between values
                float targetCameraZoom = (float) Math.pow(2, currentMapZoom - zoomProgress);
                camera.zoom = MathUtils.lerp(camera.zoom, targetCameraZoom, delta * 5f);
            }
            return;
        }
        
        // Continuous tracking mode - keep camera centered on ship
        if (trackingShip && trackedShip != null) {
            // Smoothly zoom in using both map zoom and camera zoom
            int currentMapZoom = map.getZoomLevel();
            if (currentMapZoom < targetMapZoomLevel) {
                lastZoomTransitionCheck += delta;
                if (lastZoomTransitionCheck >= 0.6f) {
                    lastZoomTransitionCheck = 0f;
                    map.setZoomLevel(currentMapZoom + 1);
                }
            }
            
            Vector2 shipPos = map.latLonToPixel(trackedShip.lat, trackedShip.lon);
            // Smoothly follow the ship
            camera.position.x = MathUtils.lerp(camera.position.x, shipPos.x, delta * 3f);
            camera.position.y = MathUtils.lerp(camera.position.y, shipPos.y, delta * 3f);
            
            // Smooth zoom transition
            float targetCameraZoom = currentMapZoom < targetMapZoomLevel ? 0.5f : 1f;
            camera.zoom = MathUtils.lerp(camera.zoom, targetCameraZoom, delta * 4f);
            clampCamera();
            return;
        }
        
        if (!animatingToShip) return;

        animationProgress += delta * 0.8f; // Smooth animation speed
        
        if (animationProgress >= 1f) {
            animatingToShip = false;
            animationProgress = 1f;
            
            // Ensure final zoom level
            if (map.getZoomLevel() != targetMapZoomLevel) {
                map.setZoomLevel(targetMapZoomLevel);
                if (selectedShip != null) {
                    Vector2 pos = map.latLonToPixel(selectedShip.lat, selectedShip.lon);
                    camera.position.set(pos.x, pos.y, 0);
                }
            }
            camera.zoom = 1f;

            if (selectedShip != null) {
                show3DMode = true;
                ship3DRenderer.activate(selectedShip);
            }
        } else {
            // Smooth exponential easing for zoom in
            float t = (float) Math.pow(animationProgress, 2);
            
            // Gradually change map zoom level based on progress
            int currentMapZoom = map.getZoomLevel();
            float zoomProgress = MathUtils.lerp(startMapZoomLevel, targetMapZoomLevel, t);
            int desiredMapZoom = Math.round(zoomProgress);
            
            if (desiredMapZoom != currentMapZoom && desiredMapZoom >= startMapZoomLevel && desiredMapZoom <= targetMapZoomLevel) {
                map.setZoomLevel(desiredMapZoom);
                // Recalculate target position for new zoom level
                if (selectedShip != null) {
                    Vector2 pos = map.latLonToPixel(selectedShip.lat, selectedShip.lon);
                    cameraTarget.set(pos);
                }
            }
            
            // Smooth camera movement
            camera.position.x = MathUtils.lerp(camera.position.x, cameraTarget.x, delta * 4f);
            camera.position.y = MathUtils.lerp(camera.position.y, cameraTarget.y, delta * 4f);
            
            // Smooth continuous zoom - use camera zoom for decimal precision
            float targetCameraZoom = (float) Math.pow(2, currentMapZoom - zoomProgress);
            camera.zoom = MathUtils.lerp(camera.zoom, targetCameraZoom, delta * 6f);
        }
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
        animatingZoomOut = false;
        trackingShip = false;
        trackedShip = null;
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
        // Close any existing ship dialog to prevent stacking
        if (currentShipDialog != null) {
            currentShipDialog.hide();
            currentShipDialog.remove();
            currentShipDialog = null;
        }
        
        ShipDetailsDialog dialog = new ShipDetailsDialog(ship, skin, new ShipDetailsDialog.ShipSelectionListener() {
            @Override
            public void onTrack(Ship ship) {
                trackShip(ship);
            }

            @Override
            public void onFocusShip(Ship ship) {
                focusOnShip(ship);
            }
        });
        
        currentShipDialog = dialog;
        dialog.show(uiStage);
        
        // Position dialog to the right side of the screen
        float dialogX = Gdx.graphics.getWidth() - dialog.getWidth() - 20;
        float dialogY = (Gdx.graphics.getHeight() - dialog.getHeight()) / 2;
        dialog.setPosition(dialogX, dialogY);
    }

    private void trackShip(Ship ship) {
        selectedShip = ship;
        trackedShip = ship;
        trackingShip = true;
        animatingToShip = false;
        animatingZoomOut = false;
        show3DMode = false;
        ship3DRenderer.deactivate();
        
        // Set target zoom level - animation will gradually transition
        startMapZoomLevel = map.getZoomLevel();
        targetMapZoomLevel = 8;
        lastZoomTransitionCheck = 0f;
        
        Gdx.app.log("MarineRadar", "Tracking ship: " + ship.name + " - will zoom to level 8");
    }
    
    private void focusOnShip(Ship ship) {
        selectedShip = ship;
        trackedShip = ship;
        trackingShip = true;
        animatingZoomOut = false;
        animatingToShip = false;
        
        // Hide main UI and show 3D UI
        mainUITable.setVisible(false);
        mode3DUI.setVisible(true);
        
        // Close any existing dialogs
        if (currentShipDialog != null) {
            currentShipDialog.hide();
            currentShipDialog.remove();
            currentShipDialog = null;
        }
        
        // Show compact ship info dialog for 3D mode
        if (ship3DInfoDialog != null) {
            ship3DInfoDialog.hide();
            ship3DInfoDialog.remove();
        }
        ship3DInfoDialog = new ShipDetailsDialog(ship, skin, true); // compact mode
        ship3DInfoDialog.show(uiStage);
        // Position in top-right corner
        ship3DInfoDialog.setPosition(
            Gdx.graphics.getWidth() - ship3DInfoDialog.getWidth() - 20,
            Gdx.graphics.getHeight() - ship3DInfoDialog.getHeight() - 20
        );
        
        // Activate 3D mode immediately
        show3DMode = true;
        ship3DRenderer.activate(ship);

        Gdx.app.log("MarineRadar", "Entering 3D view for ship: " + ship.name);
    }
    
    private void exit3DMode() {
        show3DMode = false;
        ship3DRenderer.deactivate();
        
        // Show main UI and hide 3D UI
        mainUITable.setVisible(true);
        mode3DUI.setVisible(false);
        
        // Hide 3D mode ship info dialog
        if (ship3DInfoDialog != null) {
            ship3DInfoDialog.hide();
            ship3DInfoDialog.remove();
            ship3DInfoDialog = null;
        }
        
        // If we were tracking a ship, continue tracking in 2D
        if (trackedShip != null) {
            // Center camera on tracked ship
            Vector2 pos = map.latLonToPixel(trackedShip.lat, trackedShip.lon);
            camera.position.set(pos.x, pos.y, 0);
            
            // Set appropriate zoom level
            startMapZoomLevel = map.getZoomLevel();
            targetMapZoomLevel = 8;
            lastZoomTransitionCheck = 0f;
            trackingShip = true;
        }
        
        Gdx.app.log("MarineRadar", "Exiting 3D view");
    }
    
    private void zoomOutToCenter() {
        // Stop tracking and 3D mode
        trackingShip = false;
        trackedShip = null;
        selectedShip = null;
        show3DMode = false;
        ship3DRenderer.deactivate();
        
        // Start zoom out animation
        animatingToShip = false;
        animatingZoomOut = true;
        animationProgress = 0f;
        lastZoomTransitionCheck = 0f;
        
        // Set zoom transition parameters
        startMapZoomLevel = map.getZoomLevel();
        targetMapZoomLevel = 3;
        
        // Set target to center of current map (will update as zoom changes)
        int currentWorldSize = map.getWorldSize();
        cameraTarget.set(currentWorldSize / 2f, currentWorldSize / 2f);
        targetZoom = 1f;
        
        Gdx.app.log("MarineRadar", "Zooming out to center - from level " + startMapZoomLevel + " to level 3");
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        uiStage.getViewport().update(width, height, true);
        ship3DRenderer.resize(width, height);
    }

    @Override
    public void dispose() {
        map.dispose();
        shapeRenderer.dispose();
        uiStage.dispose();
        skin.dispose();
        ship3DRenderer.dispose();
        shipDataFetcher.stop();
        if (shipRenderer != null) shipRenderer.dispose();
    }

    /**
     * Set the skin file path to be used when the application is created.
     * Example: System property 'marineRadar.skin' or call this method before starting the app.
     */
    public void setSkinFile(String skinFile) {
        this.skinFile = skinFile;
        Gdx.app.log("MarineRadar", "skinFile set to " + skinFile);
    }

    private class MapInputProcessor extends InputAdapter {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            map.zoomTowardsCursor(amountY, Gdx.input.getX(), Gdx.input.getY());
            // Disable tracking and animations on manual zoom
            trackingShip = false;
            trackedShip = null;
            animatingZoomOut = false;
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.LEFT) {
                Vector3 worldPos = camera.unproject(new Vector3(screenX, screenY, 0));
                Ship clicked = findShipAt(worldPos.x, worldPos.y);

                if (clicked != null) {
                    // Left click: select the ship, center the camera, and open the details dialog
                    if (button == Input.Buttons.LEFT) {
                        Vector2 pos = map.latLonToPixel(clicked.lat, clicked.lon);
                        camera.position.set(pos.x, pos.y, 0);
                        camera.update();

                        if (selectedShip != null) {
                            selectedShip.isSelected = false;
                        }
                        selectedShip = clicked;
                        selectedShip.isSelected = true;

                        showShipDetails(clicked);
                        return true;
                    }

                    // Right click: also open ship details dialog (alternative access)
                    if (button == Input.Buttons.RIGHT) {
                        if (selectedShip != null) {
                            selectedShip.isSelected = false;
                        }
                        selectedShip = clicked;
                        selectedShip.isSelected = true;
                        showShipDetails(clicked);
                        return true;
                    }
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
