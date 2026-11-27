package miningsim.render;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import miningsim.block.Mining;
import miningsim.visual.BlockDamage;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class BlockDamageTest {
    @Test void exactBoundariesAndOverflow() {
        for(var rock:new Material[]{Material.STONE,Material.GRANITE,Material.LIMESTONE,Material.MARBLE}) for(int size:new int[]{4,8}) {
            int required=Mining.requiredWork(rock,size);
            assertEquals(0,BlockDamage.stage(0,required));
            assertEquals(1,BlockDamage.stage(1,required));
            for(int n=2;n<=10;n++) {
                int boundary=required*n/10;
                assertEquals(n-1,BlockDamage.stage(boundary-1,required));
                assertEquals(n,BlockDamage.stage(boundary,required));
                if(n<10) assertEquals(n,BlockDamage.stage(boundary+1,required));
            }
        }
        assertEquals(9,BlockDamage.stage(Integer.MAX_VALUE-1,Integer.MAX_VALUE));
        assertEquals(10,BlockDamage.stage(Integer.MAX_VALUE,Integer.MAX_VALUE));
    }
    @Test void geometryAndMasks() {
        var segments=BlockDamage.segments();
        assertSame(segments,BlockDamage.segments());
        for(int stage=1;stage<=10;stage++) {
            int current=stage;
            assertTrue(segments.stream().anyMatch(s->s.stage()==current));
        }
        for(var s:segments) for(int c:new int[]{s.x1(),s.y1(),s.x2(),s.y2()}) assertTrue(c>=0 && c<=100);
        var masks=new BlockDamageMasks();
        for(int stage=0;stage<=10;stage++) {
            var image=new BufferedImage(160,160,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics(); masks.draw(g,stage,16,16,128); g.dispose();
            int pixels=0;
            for(int y=0;y<160;y++) for(int x=0;x<160;x++) if((image.getRGB(x,y)>>>24)!=0) {
                assertTrue(x>=16 && x<144 && y>=16 && y<144); pixels++;
            }
            if(stage==0) assertEquals(0,pixels); else assertTrue(pixels>0);
        }
    }
}
