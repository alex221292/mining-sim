package miningsim.render;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import miningsim.world.World;
import miningsim.app.Camera2D;

public final class Java2DRenderer implements Renderer {
    private final BufferedImage image;
    private final int[] pixels;
    private static final int[] COLORS = { 0xff161b22, 0xff808890, 0xffe6c66e, 0xff397bd2, 0xff855631, 0xff667482, 0xff62442e };
    public Java2DRenderer(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }
    public void render(Graphics2D graphics, World world, Camera2D camera, int width, int height) {
        Camera2D.CellRect visible = camera.visibleCells(width, height, world.width(), world.height());
        for (int y = visible.top(); y < visible.bottom(); y++)
            for (int x = visible.left(); x < visible.right(); x++)
                pixels[y * world.width() + x] = COLORS[Byte.toUnsignedInt(world.get(x, y).id())];
        graphics.setColor(new Color(0x0c1016));
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        AffineTransform transform = new AffineTransform();
        transform.translate(camera.worldToScreenX(0), camera.worldToScreenY(0));
        transform.scale(camera.zoom(), camera.zoom());
        graphics.drawImage(image, transform, null);
    }
}
