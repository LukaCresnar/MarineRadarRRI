package si.um.feri.project.marineRadar;

import com.badlogic.gdx.graphics.Color;

/**
 * Central configuration for MarineRadar application.
 * Contains all configurable constants, colors, and settings.
 */
public class MarineRadarConfig {
    
    // ==================== PERFORMANCE ====================
    public static final int TARGET_FPS = 60;
    public static final int MAX_SHIPS_DEFAULT = 1000;
    public static final int MAX_LOCATION_HISTORY = 100;
    public static final int MAX_CACHE_TILES = 800;
    public static final int SHIPS_PER_PAGE = 20;
    
    // ==================== SHIP RENDERING ====================
    public static final float SHIP_ICON_SCALE = 1.5f;
    public static final int MIN_ZOOM_FOR_ICONS = 8;
    public static final float SHIP_CLICK_RADIUS = 10f;
    
    // ==================== ANIMATION ====================
    public static final float DIALOG_FADE_DURATION = 0.2f;
    public static final float CAMERA_ANIMATION_SPEED = 0.8f;
    public static final float TRACKING_LERP_SPEED = 3f;
    public static final float ZOOM_LERP_SPEED = 4f;
    public static final float PULSE_SPEED = 3f;
    public static final float PULSE_MIN_SCALE = 0.95f;
    public static final float PULSE_MAX_SCALE = 1.05f;
    
    // ==================== COLOR SCHEMES ====================
    
    public enum ColorScheme {
        LIGHT, DARK, HIGH_CONTRAST
    }
    
    private static ColorScheme currentScheme = ColorScheme.DARK;
    
    // Ship colors - will be updated based on color scheme
    public static Color SHIP_UNKNOWN = new Color(1f, 0.3f, 0.3f, 1f);      // Softer red
    public static Color SHIP_SELECTED = new Color(1f, 0.9f, 0.2f, 1f);     // Brighter yellow
    public static Color SHIP_TRACKED = new Color(0.2f, 1f, 0.4f, 1f);      // Vibrant green
    public static Color SHIP_MOORED = new Color(0.6f, 0.6f, 0.6f, 0.8f);   // Semi-transparent gray
    public static Color SHIP_KNOWN = new Color(0.2f, 0.8f, 0.4f, 1f);      // Green for known types
    
    // UI Colors
    public static Color UI_BACKGROUND = new Color(0.12f, 0.12f, 0.12f, 0.95f);
    public static Color UI_TEXT = new Color(1f, 1f, 1f, 1f);
    public static Color UI_ACCENT = new Color(0.2f, 0.6f, 1f, 1f);
    public static Color UI_SUCCESS = new Color(0.2f, 0.8f, 0.2f, 1f);
    public static Color UI_ERROR = new Color(0.9f, 0.2f, 0.2f, 1f);
    public static Color UI_WARNING = new Color(1f, 0.7f, 0.2f, 1f);
    
    // Connection status colors
    public static Color CONNECTION_CONNECTED = new Color(0.2f, 0.9f, 0.2f, 1f);
    public static Color CONNECTION_DISCONNECTED = new Color(0.9f, 0.2f, 0.2f, 1f);
    public static Color CONNECTION_CONNECTING = new Color(1f, 0.7f, 0.2f, 1f);
    
    // Route colors
    public static Color ROUTE_TRAVELED = new Color(0f, 1f, 0f, 0.6f);
    public static Color ROUTE_PROJECTED = new Color(0f, 1f, 1f, 0.6f);
    public static Color ROUTE_HEADING = new Color(1f, 0.5f, 0f, 0.8f);
    
    // Debug indicator colors
    public static Color DEBUG_MOVEMENT_BEARING = new Color(1f, 0f, 1f, 1f);  // Magenta
    public static Color DEBUG_SEGMENT_BEARING = new Color(0f, 0f, 1f, 1f);   // Blue
    
    // Map colors
    public static Color MAP_BACKGROUND = new Color(0.0039f, 0.6431f, 0.9137f, 1f);  // Ocean blue
    public static Color SKY_3D = new Color(0.4f, 0.6f, 0.9f, 1f);
    
    // Mini-map colors
    public static Color MINIMAP_BACKGROUND = new Color(0.1f, 0.1f, 0.1f, 0.8f);
    public static Color MINIMAP_VIEWPORT = new Color(1f, 1f, 1f, 0.8f);
    public static Color MINIMAP_SHIP = new Color(1f, 0.3f, 0.3f, 1f);
    
    /**
     * Apply a color scheme to all colors
     */
    public static void setColorScheme(ColorScheme scheme) {
        currentScheme = scheme;
        
        switch (scheme) {
            case LIGHT:
                applyLightScheme();
                break;
            case DARK:
                applyDarkScheme();
                break;
            case HIGH_CONTRAST:
                applyHighContrastScheme();
                break;
        }
    }
    
    public static ColorScheme getColorScheme() {
        return currentScheme;
    }
    
    private static void applyLightScheme() {
        // Ship colors - slightly muted for light background
        SHIP_UNKNOWN.set(0.9f, 0.2f, 0.2f, 1f);
        SHIP_SELECTED.set(0.9f, 0.7f, 0.1f, 1f);
        SHIP_TRACKED.set(0.1f, 0.7f, 0.3f, 1f);
        SHIP_MOORED.set(0.5f, 0.5f, 0.5f, 0.9f);
        SHIP_KNOWN.set(0.1f, 0.6f, 0.3f, 1f);
        
        // UI Colors - light theme
        UI_BACKGROUND.set(0.95f, 0.95f, 0.95f, 0.95f);
        UI_TEXT.set(0.1f, 0.1f, 0.1f, 1f);
        UI_ACCENT.set(0.1f, 0.4f, 0.8f, 1f);
        
        // Map colors
        MAP_BACKGROUND.set(0.1f, 0.5f, 0.8f, 1f);
    }
    
    private static void applyDarkScheme() {
        // Ship colors - vibrant for dark background
        SHIP_UNKNOWN.set(1f, 0.3f, 0.3f, 1f);
        SHIP_SELECTED.set(1f, 0.9f, 0.2f, 1f);
        SHIP_TRACKED.set(0.2f, 1f, 0.4f, 1f);
        SHIP_MOORED.set(0.6f, 0.6f, 0.6f, 0.8f);
        SHIP_KNOWN.set(0.2f, 0.8f, 0.4f, 1f);
        
        // UI Colors - dark theme
        UI_BACKGROUND.set(0.12f, 0.12f, 0.12f, 0.95f);
        UI_TEXT.set(1f, 1f, 1f, 1f);
        UI_ACCENT.set(0.2f, 0.6f, 1f, 1f);
        
        // Map colors
        MAP_BACKGROUND.set(0.0039f, 0.6431f, 0.9137f, 1f);
    }
    
    private static void applyHighContrastScheme() {
        // Ship colors - maximum contrast
        SHIP_UNKNOWN.set(1f, 0f, 0f, 1f);          // Pure red
        SHIP_SELECTED.set(1f, 1f, 0f, 1f);          // Pure yellow
        SHIP_TRACKED.set(0f, 1f, 0f, 1f);           // Pure green
        SHIP_MOORED.set(0.7f, 0.7f, 0.7f, 1f);      // Bright gray
        SHIP_KNOWN.set(0f, 1f, 0f, 1f);
        
        // UI Colors - high contrast
        UI_BACKGROUND.set(0f, 0f, 0f, 1f);
        UI_TEXT.set(1f, 1f, 1f, 1f);
        UI_ACCENT.set(0f, 1f, 1f, 1f);              // Cyan accent
        
        // Connection colors - brighter
        CONNECTION_CONNECTED.set(0f, 1f, 0f, 1f);
        CONNECTION_DISCONNECTED.set(1f, 0f, 0f, 1f);
        CONNECTION_CONNECTING.set(1f, 1f, 0f, 1f);
        
        // Map colors - darker for contrast
        MAP_BACKGROUND.set(0f, 0.3f, 0.5f, 1f);
    }
    
    // ==================== KEYBOARD SHORTCUTS ====================
    public static final int KEY_FIND_SHIP = com.badlogic.gdx.Input.Keys.F;
    public static final int KEY_HELP = com.badlogic.gdx.Input.Keys.H;
    public static final int KEY_ESCAPE = com.badlogic.gdx.Input.Keys.ESCAPE;
    public static final int KEY_ZOOM_IN = com.badlogic.gdx.Input.Keys.A;
    public static final int KEY_ZOOM_OUT = com.badlogic.gdx.Input.Keys.S;
    
    // ==================== MINI-MAP ====================
    public static final float MINIMAP_SIZE = 150f;
    public static final float MINIMAP_MARGIN = 10f;
    public static final float MINIMAP_OPACITY = 0.8f;
    
    // ==================== ZOOM INDICATOR ====================
    public static final float ZOOM_INDICATOR_WIDTH = 120f;
    public static final float ZOOM_INDICATOR_HEIGHT = 30f;
}
