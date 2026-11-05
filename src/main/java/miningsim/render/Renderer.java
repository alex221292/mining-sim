package miningsim.render;

import java.awt.Graphics2D;
import miningsim.app.Camera2D;
import miningsim.world.World;

public interface Renderer {
    void render(Graphics2D graphics, World world, Camera2D camera, int width, int height);
}
