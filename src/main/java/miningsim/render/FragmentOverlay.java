package miningsim.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import miningsim.app.Camera2D;
import miningsim.fragment.RockFragment;
import miningsim.physics.RigidBody;

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
    /** Draws actual local cell geometry in the physics center-of-mass frame. */
    public static void renderBodies(Graphics2D graphics, Camera2D camera, List<RigidBody> bodies) {
        for(int i=1;i<bodies.size();i++) if(bodies.get(i-1).id()>bodies.get(i).id()) {
            var ordered=new java.util.ArrayList<>(bodies); ordered.sort(java.util.Comparator.comparingLong(RigidBody::id)); bodies=ordered; break;
        }
        Graphics2D g=(Graphics2D)graphics.create();
        g.setColor(new Color(0x808890));
        var rectangle=new Rectangle2D.Double(0,0,1,1);
        for(var body:bodies) {
            var transform=g.getTransform();
            g.translate(camera.worldToScreenX(body.xCells()),camera.worldToScreenY(body.yCells()));
            g.scale(camera.zoom(),camera.zoom());
            g.rotate(body.angleRadians());
            g.translate(-body.geometry().localCenterX(),-body.geometry().localCenterY());
            for(var cell:body.fragment().descriptor().cells()) {
                rectangle.x=cell.x(); rectangle.y=cell.y(); g.fill(rectangle);
            }
            g.setTransform(transform);
        }
        g.dispose();
    }
}
