package si.um.feri.project.marineRadar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class VectorMapRenderer {

    private float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

    public final ShapeRenderer shapeRenderer;
    private final List<float[]> polygons = new ArrayList<>();

    public OrthographicCamera camera;
    public Viewport viewport;

    public VectorMapRenderer(OrthographicCamera camera, float screenWidth, float screenHeight) {
        this.camera = camera;

        shapeRenderer = new ShapeRenderer();
        viewport = new FitViewport(screenWidth, screenHeight, camera);
        viewport.apply();

        loadGeoJSON("worldMap.geo.json");

        // Center camera on world
        camera.position.set(
            (minX + maxX) / 2f,
            (minY + maxY) / 2f,
            0
        );

        // Zoom out to world size
        camera.zoom = (maxX - minX) / screenWidth;
        camera.update();
    }

    private void loadGeoJSON(String filename) {
        try {
            InputStream is = Gdx.files.internal(filename).read();
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            JSONObject geojson = new JSONObject(text);
            JSONArray features = geojson.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                JSONObject geometry = features.getJSONObject(i).getJSONObject("geometry");
                String type = geometry.getString("type");

                if (type.equals("Polygon")) {
                    polygons.add(parseRing(geometry.getJSONArray("coordinates").getJSONArray(0)));
                }
                else if (type.equals("MultiPolygon")) {
                    JSONArray polys = geometry.getJSONArray("coordinates");
                    for (int p = 0; p < polys.length(); p++) {
                        polygons.add(parseRing(polys.getJSONArray(p).getJSONArray(0)));
                    }
                }
            }

            System.out.println("Loaded polygons: " + polygons.size());
            System.out.println("Bounds X: " + minX + " → " + maxX);
            System.out.println("Bounds Y: " + minY + " → " + maxY);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private float[] parseRing(JSONArray coords) {
        float[] verts = new float[coords.length() * 2];

        for (int i = 0; i < coords.length(); i++) {
            JSONArray c = coords.getJSONArray(i);
            Vector2 p = lonLatToMeters(c.getDouble(0), c.getDouble(1));

            verts[i * 2] = p.x;
            verts[i * 2 + 1] = p.y;

            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        return verts;
    }

    // Web Mercator (clamped)
    public static Vector2 lonLatToMeters(double lon, double lat) {
        lat = Math.max(-85.05112878, Math.min(85.05112878, lat));

        double x = lon * 20037508.34 / 180.0;
        double y = Math.log(Math.tan((90 + lat) * Math.PI / 360)) / (Math.PI / 180);
        y = y * 20037508.34 / 180.0;

        return new Vector2((float) x, (float) y);
    }

    public void render() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.BLACK);

        for (float[] verts : polygons) {
            for (int i = 0; i < verts.length - 2; i += 2) {
                shapeRenderer.line(
                    verts[i], verts[i + 1],
                    verts[i + 2], verts[i + 3]
                );
            }
        }

        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
