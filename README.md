# MarineRadar – Interactive Maritime Tracking System

MarineRadar is an interactive maritime traffic visualization application built with LibGDX.
It displays real-time ship positions on a global map using online AIS data, offering smooth navigation, ship tracking, and a clean, maritime-themed UI.

Developed by Team BlueSignal  
Authors: Dragan Stojanović & Luka Črešnar

---

## Project Overview

The goal of MarineRadar is to provide a clear, dynamic, and visually coherent overview of global maritime traffic.
The application combines real-world ship data, online map tiles, and custom visual effects to create an informative and immersive experience.

Key focus areas:
- Real-time ship tracking
- Smooth map navigation and zooming
- Clean maritime-themed visuals
- Modular and scalable architecture

---

## Map System

- Uses online tile-based maps (e.g. OpenStreetMap or similar providers)
- Tiles are loaded dynamically based on:
    - Camera position
    - Zoom level
- Supports smooth:
    - Panning
    - Zooming
- Efficient memory usage via tile streaming and optional caching

Benefits:
- High visual quality at all zoom levels
- Professional, modern map rendering
- Scalable for large datasets

---

## Visual Effects

- Animated cloud layer visible at lower zoom levels
- Implemented as a parallax layer
- Adds depth, motion, and atmosphere without affecting map performance

---

## Ship Rendering & Interaction

- Ships appear when zoomed in sufficiently
- Each ship:
    - Is represented by an animated icon
    - Moves smoothly using interpolated position updates
- Clicking a ship:
    - Focuses the camera on it
    - Displays its movement trail
    - Opens an information panel

---

## AIS Data Integration

MarineRadar connects to online AIS / maritime APIs to fetch live ship data, including:
- Latitude and longitude
- Speed and heading
- Ship name and identifier
- Vessel type and dimensions
- Destination and ETA
- Optional ship images (if supported by the API)

Data is refreshed periodically to ensure accuracy and smooth motion.

---

## Ship Information Panel

Clicking a ship opens a pop-up UI displaying:
- Ship name
- Photo (if available)
- Vessel type and dimensions
- Speed and direction
- Current position
- Route (origin → destination)
- Additional metadata from the API

The UI is designed to be clean, readable, and maritime-themed.

---

## Follow Mode

- Users can enable Follow Mode for any ship
- The camera smoothly tracks the selected vessel in real time
- The ship’s trajectory remains clearly visible
- Transitions are fluid and non-disorienting

---

## Technical Stack

- **Framework**: LibGDX (cross-platform game development)
- **Graphics**: OrthographicCamera (2D) & PerspectiveCamera (3D)
- **Map Rendering**: Online tile fetching (OpenStreetMap-compatible)
- **Tile Management**: Multi-level caching with fallback rendering
- **AIS Integration**: WebSocket connection to aisstream.io
- **Ship Icons**: Type-specific PNG assets (9 categories)
- **3D Models**: OBJ/G3DB support with procedural fallback
- **UI System**: Scene2D with custom maritime-themed skin
- **Layered Rendering Pipeline**:
    1. Map tiles (zoom-aware, smooth transitions)
    2. Cloud parallax layer (atmospheric effects)
    3. Ship routes and history trails
    4. Type-specific ship icons (rotation based on movement)
    5. UI overlay (info panels, dialogs, search)
- **Performance**: 60 FPS cap, smart tile caching (800 tiles max)
- **Ship Tracking**: Location history (100 points), smooth interpolation

---

## Key Features

✓ **Real-time AIS Data**: Live ship positions from global maritime network  
✓ **Smart Search**: Filter by name, MMSI, or speed (>5 knots)  
✓ **Dual Tracking Modes**: 2D continuous follow or 3D immersive view  
✓ **Type-Specific Icons**: 9 ship categories (cargo, tanker, passenger, fishing, etc.)  
✓ **Movement Visualization**: History trails, bearing indicators, smooth rotation  
✓ **Professional UI**: Maritime-themed skin, clean information panels  
✓ **Configurable Settings**: Max ships limit, radar sweep, clouds, routes  
✓ **Smooth Navigation**: Zoom levels 2-18, pan/drag support  
✓ **Performance Optimized**: Efficient tile caching, FPS limiting  

---

## Rendering Pipeline

```
Map Tiles (zoom-aware, cached)
    ↓
Cloud / Parallax Layer (atmospheric)
    ↓
Ship History Trails (movement visualization)
    ↓
Ship Icons (type-specific, rotation-aware)
    ↓
Debug Indicators (bearing lines)
    ↓
UI Layer (panels, dialogs, search)
```

---

## Project Outcome

MarineRadar successfully demonstrates:
- **Real-time Data Integration**: Seamless WebSocket connection to global AIS network
- **Advanced Map Rendering**: Multi-level tile system with smooth zoom transitions
- **Interactive 3D Graphics**: Dual 2D/3D rendering modes with ship models
- **Professional UI/UX**: Intuitive search, filtering, and ship tracking interfaces
- **Performance Optimization**: Smart caching, FPS limiting, efficient rendering pipeline
- **Clean Architecture**: Modular design with clear separation of concerns

**Technologies Showcased**:
- LibGDX game framework
- WebSocket real-time communication
- 3D graphics and camera systems
- Tile-based map rendering
- UI/UX design patterns
- Performance optimization techniques

The result is a **professional, responsive, and informative maritime tracking application** suitable for educational purposes, maritime monitoring, or as a foundation for more advanced navigation systems.

---

## How to Run

```bash
# Build and run the desktop application
./gradlew lwjgl3:run

# Or on Windows
gradlew.bat lwjgl3:run
```

**System Requirements**:
- Java 8 or higher
- Internet connection (for map tiles and AIS data)
- OpenGL-compatible graphics

---

## Controls

**Navigation**:
- Mouse drag: Pan map
- Scroll wheel: Zoom in/out
- Arrow keys: Pan map
- A/S keys: Zoom in/out

**Ship Interaction**:
- Click ship: View details
- Find Ship button: Open search panel
- Track button (in dialog): Follow ship continuously
- Focus button (in dialog): Enter 3D view

**3D View** (when focused on a ship):
- A/D or Arrows: Rotate camera
- W/S: Zoom in/out
- Q/E: Camera height
- 1-4: Camera modes
- ESC: Exit 3D view

---

## Credits

**Team BlueSignal**  
- Dragan Stojanović - Lead Developer
- Luka Črešnar - Developer

**Technologies**:
- LibGDX Framework
- OpenStreetMap (map tiles)
- aisstream.io (AIS data)

---

*Developed for educational purposes as part of game development coursework.*
