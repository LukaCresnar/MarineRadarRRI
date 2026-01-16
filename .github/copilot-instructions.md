# MarineRadar AI Coding Guidelines

## Architecture Overview
MarineRadar is a LibGDX-based desktop application for real-time maritime traffic visualization. It uses a multi-module Gradle structure with `core` (shared logic) and `lwjgl3` (desktop launcher). Key components:
- **TileMapRenderer**: Handles online tile-based map rendering with caching
- **Ship rendering**: Dual 2D/3D modes using icons and OBJ models
- **AIS integration**: WebSocket connection to aisstream.io for live ship data
- **UI system**: Scene2D-based panels with maritime-themed skin

## Core Patterns
- **Layered rendering pipeline**: Map tiles → Cloud parallax → Ship routes → Ship icons → UI overlay (see `MarineRadar.render()`)
- **Ship data management**: Ships stored in `List<Ship>`, updated via WebSocket with position interpolation
- **Camera control**: OrthographicCamera with smooth animation transitions (e.g., `animateToShip()` in MarineRadar.java)
- **Input multiplexing**: Separate processors for map panning/zoom and UI interactions
- **3D ship rendering**: Uses ModelBatch with PerspectiveCamera, activated on ship selection (see Ship3DRenderer.java)

## Development Workflow
- **Build & Run**: Use `./gradlew lwjgl3:run` for desktop execution; assets auto-copied from `assets/` to resources
- **Debugging**: Enable LibGDX logging with `Gdx.app.setLogLevel(Application.LOG_INFO)`; check WebSocket connection status in console
- **Asset management**: Place files in `assets/` folder; `generateAssetList` task creates `assets.txt` for internal loading
- **Dependencies**: Core uses gdx, json-org; lwjgl3 adds WebSocket client and native backends

## Code Conventions
- **Ship models**: Prefer OBJ loading with fallback to procedural generation (see Ship3DModel.java)
- **Error handling**: Silent WebSocket parsing errors; log render failures but continue execution
- **Performance**: Limit ships to 1000 max; use delta time for smooth animations
- **UI layout**: Table-based with padding; toggle visibility instead of removing actors
- **Camera clamping**: Always clamp to world bounds to prevent invalid positions

## Key Files
- `MarineRadar.java`: Main game loop, rendering pipeline, input handling
- `ShipDataFetcher.java`: WebSocket AIS data integration with reconnection logic
- `TileMapRenderer.java`: Map tile loading and caching system
- `Ship3DRenderer.java`: 3D ship visualization with ModelBatch
- `assets/uiskin.json`: Scene2D UI theme definitions</content>
<parameter name="filePath">.github/copilot-instructions.md