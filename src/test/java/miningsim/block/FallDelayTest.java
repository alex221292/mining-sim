package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class FallDelayTest {
    @Test void supportLossWaitsExactlyEightTicksThenFallsContinuously() {
        var world=new World(48,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(world,1);
        assertTrue(s.blocks().place(8,64,false)); assertTrue(s.blocks().place(8,56,false));
        s.tick(); assertTrue(s.blocks().removeAt(8,64)); world.set(32,1,Material.WATER);
        for(int i=0;i<FixedBlockWorld.FALL_DELAY_TICKS;i++) {
            s.tick(); assertNotNull(s.blocks().blockAt(8,56)); assertEquals(i+2,s.cellularTicks());
        }
        assertEquals(Material.AIR,world.get(32,1)); s.tick(); assertNotNull(s.blocks().blockAt(8,64));
        s.tick(); assertNotNull(s.blocks().blockAt(8,72));
    }
    @Test void restoredSupportResetsOnlyItsOwnTimer() {
        var world=new World(48,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(world,2);
        assertTrue(s.blocks().place(8,32,false)); assertTrue(s.blocks().place(24,32,false));
        for(int i=0;i<4;i++) s.tick();
        assertTrue(s.blocks().place(8,40,false)); s.tick(); assertTrue(s.blocks().removeAt(8,40));
        for(int i=0;i<4;i++) s.tick();
        assertNotNull(s.blocks().blockAt(8,32)); assertNotNull(s.blocks().blockAt(24,40));
        for(int i=0;i<5;i++) s.tick(); assertNotNull(s.blocks().blockAt(8,40));
    }
}
