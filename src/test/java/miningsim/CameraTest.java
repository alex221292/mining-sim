package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import miningsim.app.Brush;
import miningsim.app.Camera2D;
import miningsim.render.Java2DRenderer;
import miningsim.world.Material;
import miningsim.world.World;

class CameraTest {
    @Test void conversionsAndPanAreInverse() {
        Camera2D c = new Camera2D(); c.pan(-3.5, 7.25);
        assertEquals(-1.5, c.screenToWorldX(4), 1e-9);
        assertEquals(9.25, c.screenToWorldY(4), 1e-9);
        assertEquals(4, c.worldToScreenX(-1.5), 1e-9);
        assertEquals(4, c.worldToScreenY(9.25), 1e-9);
        c.reset(); assertEquals(0, c.x()); assertEquals(0, c.y()); assertEquals(2, c.zoom());
    }
    @Test void zoomPreservesCursorAndClamps() {
        Camera2D c = new Camera2D(); c.pan(15, -2);
        double x = c.screenToWorldX(143), y = c.screenToWorldY(77);
        c.zoomAt(4, 143, 77);
        assertEquals(x, c.screenToWorldX(143), 1e-9); assertEquals(y, c.screenToWorldY(77), 1e-9);
        c.zoomAt(100, 143, 77); assertEquals(Camera2D.MAX_ZOOM, c.zoom());
        assertEquals(x, c.screenToWorldX(143), 1e-9); assertEquals(y, c.screenToWorldY(77), 1e-9);
        c.zoomAt(.01, 143, 77); assertEquals(Camera2D.MIN_ZOOM, c.zoom());
        assertEquals(x, c.screenToWorldX(143), 1e-9); assertThrows(IllegalArgumentException.class, () -> c.zoomAt(Double.NaN, 0, 0));
    }
    @Test void visibleRectangleIsHalfOpenAndClipped() {
        Camera2D c = new Camera2D(); c.pan(-1.25, 2.2);
        assertEquals(new Camera2D.CellRect(0, 2, 4, 6), c.visibleCells(10, 6, 20, 20));
        c.pan(100, 0); assertEquals(new Camera2D.CellRect(20, 2, 20, 6), c.visibleCells(10, 6, 20, 20));
        c.pan(-200, 0); assertEquals(new Camera2D.CellRect(0, 2, 0, 6), c.visibleCells(10, 6, 20, 20));
        assertEquals(new Camera2D.CellRect(0, 0, 0, 0), c.visibleCells(0, 6, 20, 20));
    }
    @Test void paintingAndErasingUseWorldCoordinatesAndIgnoreOutsidePointer() {
        World w = new World(10, 10); Camera2D c = new Camera2D(); c.pan(3, 4); c.zoomAt(4, 0, 0);
        Brush.paint(w, c, 5, 9, 0, Material.SAND);
        assertEquals(Material.SAND, w.get(4, 6));
        Brush.paint(w, c, 5, 9, 0, Material.AIR); assertEquals(Material.AIR, w.get(4, 6));
        c.reset(); c.pan(-.25, 0);
        Brush.paint(w, c, 0, 0, 3, Material.STONE); assertEquals(0, w.occupiedCount());
        Brush.paint(w, c, 1, 0, 1, Material.WOOD); assertEquals(Material.WOOD, w.get(0, 0));
        assertEquals(Material.WOOD, w.get(1, 0)); assertEquals(Material.WOOD, w.get(0, 1));
    }
    @Test void rendererUsesSameTransformAndClipsWorld() {
        World w = new World(2, 1); w.set(0, 0, Material.SAND); w.set(1, 0, Material.WATER);
        Camera2D camera = new Camera2D(); camera.pan(-1, 0);
        BufferedImage out = new BufferedImage(8, 2, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics(); new Java2DRenderer(2, 1).render(g, w, camera, 8, 2); g.dispose();
        assertEquals(0x0c1016, out.getRGB(0, 0) & 0xffffff);
        assertEquals(0xe6c66e, out.getRGB(2, 0) & 0xffffff);
        assertEquals(0x397bd2, out.getRGB(4, 0) & 0xffffff);
        assertEquals(0x0c1016, out.getRGB(6, 0) & 0xffffff);
    }
}
