package miningsim.render;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import miningsim.world.World;

public final class Java2DRenderer implements Renderer {
    private final BufferedImage image;
    private final int[] pixels;
    private static final int[] COLORS = { 0xff161b22, 0xff808890, 0xffe6c66e, 0xff397bd2, 0xff855631 };
    public Java2DRenderer(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }
    public void render(Graphics2D graphics, World world, int width, int height) {
        for (int y = 0; y < world.height(); y++)
            for (int x = 0; x < world.width(); x++)
                pixels[y * world.width() + x] = COLORS[world.get(x, y).ordinal()];
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(image, 0, 0, width, height, null);
    }
}
