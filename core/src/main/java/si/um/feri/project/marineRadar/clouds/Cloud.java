package si.um.feri.project.marineRadar.clouds;

import com.badlogic.gdx.graphics.Texture;

public class Cloud {
    public float x, y;
    public float width, height;
    public float speed;
    public Texture texture;

    public Cloud(float x, float y, float width, float height, float speed, Texture texture) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.speed = speed;
        this.texture = texture;
    }
}
