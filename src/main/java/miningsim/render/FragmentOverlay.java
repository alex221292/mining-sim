package miningsim.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import miningsim.app.Camera2D;
import miningsim.fragment.RockFragment;

/** Stationary fragments, transformed using the same camera as world cells. */
public final class FragmentOverlay {
    private FragmentOverlay() { }
    public static void render(Graphics2D graphics, Camera2D camera, List<RockFragment> fragments) {
        graphics.setColor(new Color(0x9d7750));
        for (var fragment : fragments) for (var cell : fragment.descriptor().cells()) {
            double x=camera.worldToScreenX(fragment.worldX(cell)), y=camera.worldToScreenY(fragment.worldY(cell));
            graphics.fill(new Rectangle2D.Double(x,y,camera.zoom(),camera.zoom()));
        }
    }
}
