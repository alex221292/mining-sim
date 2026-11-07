package miningsim.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.List;
import miningsim.app.Camera2D;
import miningsim.app.StructuralSimulationController.Mark;

/** Read-only projection of cell-boundary diagnostics. */
public final class CrackOverlay {
    private CrackOverlay() { }
    public static Line2D.Double line(Camera2D camera, Mark b) {
        if (b.y1() == b.y2()) {
            double x = Math.max(b.x1(),b.x2());
            return new Line2D.Double(camera.worldToScreenX(x),camera.worldToScreenY(b.y1()),camera.worldToScreenX(x),camera.worldToScreenY(b.y1()+1));
        }
        double y = Math.max(b.y1(),b.y2());
        return new Line2D.Double(camera.worldToScreenX(b.x1()),camera.worldToScreenY(y),camera.worldToScreenX(b.x1()+1),camera.worldToScreenY(y));
    }
    public static void render(Graphics2D g, Camera2D camera, List<Mark> marks) {
        if (camera.zoom() < 2) return;
        for (Mark mark : marks) {
            g.setColor(mark.broken() ? Color.BLACK : mark.damage() >= 0.6 ? new Color(0xe65339) : new Color(0xf5bf55));
            g.setStroke(new BasicStroke(mark.broken() || mark.damage() >= 0.6 ? 2f : 1f));
            g.draw(line(camera,mark));
        }
    }
}
