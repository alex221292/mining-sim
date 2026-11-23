package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class OverflowRegressionTest {
    @Test void overfilledOpenPoolSpillsWithoutLosingWater() {
        var w=new World(56,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,123);
        for(int y=48;y<=72;y+=8) { assertTrue(s.blocks().place(8,y,true)); assertTrue(s.blocks().place(32,y,true)); }
        for(int y=56;y<80;y++) for(int x=16;x<32;x++) w.set(x,y,Material.WATER);
        int before=count(w); int placed=0;
        for(int n=0;n<3;n++) {
            if(s.blocks().place(16,8,false)) placed++;
            for(int i=0;i<60;i++) { s.tick(); assertEquals(before,count(w)); }
        }
        assertEquals(3,placed); boolean outside=false;
        for(int y=0;y<80;y++) for(int x=0;x<56;x++) if((x<8 || x>=40) && w.get(x,y)==Material.WATER) outside=true;
        assertTrue(outside,"water never spilled over the basin wall");
    }
    private int count(World w) { int n=0; for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) if(w.get(x,y)==Material.WATER) n++; return n; }
}
