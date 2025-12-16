package si.um.feri.project.marineRadar;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;

public class MarineRadar extends ApplicationAdapter {

    private VectorMapRenderer map;
    private OrthographicCamera camera;

    float zoomSpeed = 0.05f;
    float moveSpeed = 5f;

    @Override
    public void create() {
        camera = new OrthographicCamera(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );

        map = new VectorMapRenderer(
            camera,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );
    }

    @Override
    public void render() {
        handleInput();

        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        map.render();
    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  camera.position.x -= moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.position.x += moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    camera.position.y += moveSpeed * camera.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  camera.position.y -= moveSpeed * camera.zoom;

        if (Gdx.input.isKeyPressed(Input.Keys.A)) camera.zoom *= (1 + zoomSpeed);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) camera.zoom *= (1 - zoomSpeed);
    }

    @Override
    public void resize(int width, int height) {
        map.viewport.update(width, height);
    }

    @Override
    public void dispose() {
        map.dispose();
    }
}
