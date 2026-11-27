package miningsim.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import miningsim.visual.BlockDamage;

/** Java2D-only cache: rasterized once, shared by all blocks and zoom levels. */
public final class BlockDamageMasks {
    private static final int SIZE = 128;
    private final BufferedImage[] masks = new BufferedImage[11];
    public BlockDamageMasks() {
        for (int stage=0;stage<masks.length;stage++) {
            var image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            for (var segment:BlockDamage.segments()) if(segment.stage()<=stage) {
                var line=new Line2D.Double(segment.x1()*SIZE/100.0,segment.y1()*SIZE/100.0,segment.x2()*SIZE/100.0,segment.y2()*SIZE/100.0);
                g.setColor(new Color(245,235,215,150)); g.setStroke(new BasicStroke(3.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); g.draw(line);
                g.setColor(new Color(20,16,14,235)); g.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); g.draw(line);
            }
            g.dispose(); masks[stage]=image;
        }
    }
    public void draw(Graphics2D g,int stage,int x,int y,int size) {
        if(stage>0 && size>0) g.drawImage(masks[stage],x,y,size,size,null);
    }
}
