package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import miningsim.app.SimulationControl;
import miningsim.persistence.FixedSessionIO;
import miningsim.resource.OreComposition;
import miningsim.resource.OreId;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.visual.BlockDamage;
import org.junit.jupiter.api.Test;

class MiningTest {
    private FixedBlockSession session(int size) { return new FixedBlockSession(new World(32,33,World.Mode.BEDROCK_ENABLED),42,size); }
    @Test void resistanceAndExactTicks() {
        Material[] materials={Material.LIMESTONE,Material.STONE,Material.MARBLE,Material.GRANITE};
        int[] bases={60,120,180,240};
        for(int size:new int[]{4,8}) for(int i=0;i<materials.length;i++) {
            var s=session(size); int y=32-size;
            assertTrue(s.blocks().place(0,y,false,materials[i]));
            int required=bases[i]*size*size/16;
            assertEquals(required,Mining.requiredWork(materials[i],size));
            s.mining().select(true,0,y);
            for(int t=1;t<required/2;t++) { s.tick(); assertFalse(s.mining().progressAt(0,y).ready()); }
            s.tick(); assertTrue(s.mining().progressAt(0,y).ready());
            s.tick(); assertEquals(required,s.mining().progressAt(0,y).accumulatedWork());
        }
        for(var m:new Material[]{Material.AIR,Material.BEDROCK,Material.WATER,Material.SAND,Material.GRAVEL,Material.SOIL,Material.WOOD})
            assertThrows(IllegalArgumentException.class,()->Mining.requiredWork(m,4));
    }
    @Test void switchingStoppingRemovalAndPause() {
        var s=session(4); var c=new SimulationControl(s::tick);
        assertTrue(s.blocks().place(0,28,false)); assertTrue(s.blocks().place(4,28,false));
        s.mining().select(true,0,28); c.tick(); s.mining().stop(); c.tick();
        assertEquals(2,s.mining().progressAt(0,28).accumulatedWork());
        assertEquals(1,stage(s,0,28));
        assertEquals(0,stage(s,4,28));
        s.mining().select(true,4,28); c.tick();
        s.mining().select(true,0,28); c.togglePause(); assertFalse(c.tick());
        assertTrue(c.singleStep()); assertEquals(4,s.mining().progressAt(0,28).accumulatedWork());
        assertEquals(2,s.mining().progressAt(4,28).accumulatedWork());
        assertEquals(1,stage(s,0,28)); assertEquals(1,stage(s,4,28));
        long id=s.blocks().blockAt(0,28).id(); assertTrue(s.blocks().removeAt(0,28));
        assertTrue(s.blocks().place(0,28,false)); assertNotEquals(id,s.blocks().blockAt(0,28).id());
        assertEquals(0,s.mining().progressAt(0,28).accumulatedWork());
        assertEquals(0,stage(s,0,28));
        assertNull(session(4).mining().progressAt(0,28));
    }
    @Test void rejectsPinnedWaitingFallingAndOtherCells() {
        var s=session(4); assertTrue(s.blocks().place(0,0,true));
        s.mining().select(true,0,0); s.tick(); assertEquals(0,s.mining().progressAt(0,0).accumulatedWork());
        s.blocks().releaseStroke(); s.tick(); assertEquals(0,s.mining().progressAt(0,0).accumulatedWork());
        for(int i=0;i<10;i++) s.tick();
        var b=s.blocks().blocks().getFirst(); assertEquals(FixedBlockWorld.State.FALLING,b.state());
        s.mining().select(true,b.x(),b.y()); s.tick();
        var moved=s.blocks().blocks().getFirst(); assertEquals(0,s.mining().progressAt(moved.x(),moved.y()).accumulatedWork());
        assertNull(s.mining().progressAt(31,32));
    }
    @Test void nonRockTargetsAndFocusLossDoNotAccumulate() {
        var s=session(4);
        var materials=new Material[]{Material.AIR,Material.WATER,Material.SAND,Material.GRAVEL,Material.SOIL,Material.WOOD};
        for(int x=0;x<materials.length;x++) {
            s.world().set(x,0,materials[x]); s.mining().select(true,x,0); s.tick();
            assertNull(s.mining().progressAt(x,0));
        }
        s.mining().select(true,0,32); s.tick(); assertNull(s.mining().progressAt(0,32));
        assertTrue(s.blocks().place(8,28,false));
        s.mining().select(true,8,28); s.tick();
        s.mining().stop(); // Same headless input contract used on focus loss.
        s.tick(); assertEquals(2,s.mining().progressAt(8,28).accumulatedWork());
    }
    @Test void persistenceAndPhysicalParity() throws Exception {
        for(int ticks:new int[]{4,60}) {
            var a=session(4); var b=session(4);
            var ores=OreComposition.of(new OreComposition.Component(OreId.IRON,100),new OreComposition.Component(OreId.GOLD,1));
            for(var s:new FixedBlockSession[]{a,b}) assertTrue(s.blocks().place(0,28,false,Material.STONE,ores));
            a.mining().select(true,0,28);
            for(int i=0;i<ticks;i++) { a.tick(); b.tick(); }
            assertEquals(b.blocks().saveState(),a.blocks().saveState());
            var out=new ByteArrayOutputStream(); FixedSessionIO.write(a,out);
            var loaded=FixedSessionIO.read(new ByteArrayInputStream(out.toByteArray())).fixed();
            assertEquals(a.blocks().saveState(),loaded.blocks().saveState());
            assertEquals(0,loaded.mining().progressAt(0,28).accumulatedWork());
            assertEquals(0,stage(loaded,0,28));
            for(int y=0;y<33;y++) for(int x=0;x<32;x++) assertEquals(b.world().get(x,y),a.world().get(x,y));
        }
    }
    @Test void workFollowsBlockIntoWaterWithoutAffectingPhysics() {
        var a=session(4); var b=session(4);
        for(var s:new FixedBlockSession[]{a,b}) {
            assertTrue(s.blocks().place(0,24,false,Material.MARBLE));
            assertTrue(s.blocks().place(0,28,false));
        }
        a.mining().select(true,0,24); a.tick(); b.tick(); a.mining().stop();
        long id=a.blocks().blockAt(0,24).id();
        for(var s:new FixedBlockSession[]{a,b}) {
            assertTrue(s.blocks().removeAt(0,28));
            for(int y=28;y<32;y++) for(int x=0;x<4;x++) s.world().set(x,y,Material.WATER);
        }
        for(int t=0;t<20;t++) { a.tick(); b.tick(); }
        var moved=a.blocks().blocks().getFirst(); assertEquals(id,moved.id()); assertEquals(28,moved.y());
        assertEquals(2,a.mining().progressAt(moved.x(),moved.y()).accumulatedWork());
        assertEquals(1,stage(a,moved.x(),moved.y()));
        assertEquals(b.blocks().saveState(),a.blocks().saveState());
        for(int y=0;y<33;y++) for(int x=0;x<32;x++) assertEquals(b.world().get(x,y),a.world().get(x,y));
    }
    private static int stage(FixedBlockSession s,int x,int y) {
        var p=s.mining().progressAt(x,y);
        return BlockDamage.stage(p.accumulatedWork(),p.requiredWork());
    }
}
