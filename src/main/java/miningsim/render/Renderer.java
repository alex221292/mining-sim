package miningsim.render;

import java.awt.Graphics2D;
import miningsim.world.World;

public interface Renderer {
    void render(Graphics2D graphics, World world, int width, int height);
}
