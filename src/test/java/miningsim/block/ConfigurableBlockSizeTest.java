package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.app.Camera2D;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurableBlockSizeTest {
    private int count(World w,Material m) { int n=0; for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) if(w.get(x,y)==m) n++; return n; }
    private void audit(FixedBlockSession s,int size) {
        assertEquals(s.blocks().size()*size*size,count(s.world(),Material.STONE));
        for(var b:s.blocks().blocks()) for(int y=b.y();y<b.y()+size;y++) for(int x=b.x();x<b.x()+size;x++) assertEquals(b.id(),s.blocks().blockAt(x,y).id());
    }
    @Test void invalidConfigurationIsRejected() {
        var w=new World(40,41,World.Mode.BEDROCK_ENABLED);
        for(int size:new int[]{0,1,5,16}) assertThrows(IllegalArgumentException.class,()->new FixedBlockSession(w,1,size));
        assertEquals(8,new FixedBlockSession(w,1).blocks().rockBlockSizeCells());
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void strokesEraseAndCamera(int size) {
        var w=new World(24*size,24*size+1,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,2,size);
        var camera=new Camera2D(); camera.zoomAt(2.0,0,0); camera.pan(3,5);
        int x=(int)Math.floor(camera.screenToWorldX(camera.worldToScreenX(size+0.25)));
        int y=(int)Math.floor(camera.screenToWorldY(camera.worldToScreenY(size+0.25)));
        s.blockStroke(x,y,x+19*size,y,false); s.blockStroke(x,y,x+19*size,y,false);
        assertEquals(20,s.blocks().size()); s.tick(); assertEquals(20,s.blocks().size()); audit(s,size);
        long neighbor=s.blocks().blockAt(x+2*size,y).id(); assertTrue(s.blocks().removeAt(x+size,y));
        assertEquals(neighbor,s.blocks().blockAt(x+2*size,y).id()); audit(s,size);
        var diagonal=new FixedBlockSession(new World(24*size,24*size+1,World.Mode.BEDROCK_ENABLED),3,size);
        diagonal.blockStroke(0,0,19*size,19*size,false);
        for(int i=0;i<20;i++) assertNotNull(diagonal.blocks().blockAt(i*size,i*size));
        assertEquals(20,diagonal.blocks().size()); audit(diagonal,size);
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void stackAndRestoredSupport(int size) {
        var w=new World(8*size,10*size+1,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,4,size);
        int base=9*size; for(int i=0;i<3;i++) assertTrue(s.blocks().place(size,base-i*size,false));
        s.tick(); assertTrue(s.blocks().removeAt(size,base));
        for(int i=0;i<4;i++) s.tick(); assertTrue(s.blocks().place(size,base,false)); s.tick();
        assertTrue(s.blocks().removeAt(size,base));
        for(int i=0;i<FixedBlockWorld.FALL_DELAY_TICKS;i++) { s.tick(); assertNotNull(s.blocks().blockAt(size,base-size)); }
        for(int i=0;i<40;i++) s.tick();
        assertNotNull(s.blocks().blockAt(size,base)); assertNotNull(s.blocks().blockAt(size,base-size)); audit(s,size);
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void fiveBlocksInDeepOpenWater(int size) {
        var w=new World(3*size,15*size+1,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,5,size);
        for(int y=0;y<15*size;y+=size) { assertTrue(s.blocks().place(0,y,true)); assertTrue(s.blocks().place(2*size,y,true)); }
        for(int y=7*size;y<15*size;y++) for(int x=size;x<2*size;x++) w.set(x,y,Material.WATER);
        int water=count(w,Material.WATER);
        for(int n=0;n<5;n++) {
            assertTrue(s.blocks().place(size,size,false));
            for(int tick=0;tick<40;tick++) { s.blocks().step(); assertEquals(water,count(w,Material.WATER)); }
            assertNotNull(s.blocks().blockAt(size,(14-n)*size),s.blocks().diagnostics().toString());
        }
        audit(s,size);
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void closedPoolRefusalAndReopening(int size) {
        var w=new World(6*size,9*size+1,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,6,size);
        for(int y=2*size;y<9*size;y++) for(int x=0;x<3*size;x++) w.set(x,y,Material.WATER);
        for(int y=2*size;y<9*size;y++) w.set(3*size,y,Material.WOOD);
        for(int x=0;x<=3*size;x++) w.set(x,2*size-1,Material.WOOD);
        for(int y=2*size;y<3*size;y++) for(int x=size;x<2*size;x++) w.set(x,y,Material.AIR);
        assertTrue(s.blocks().place(size,2*size,false)); int water=count(w,Material.WATER);
        assertTrue(s.blocks().place(4*size,size,false));
        for(int i=0;i<FixedBlockWorld.FALL_DELAY_TICKS+1;i++) s.blocks().step();
        assertTrue(s.blocks().obstructed()>0); assertNotNull(s.blocks().blockAt(size,2*size)); assertNotNull(s.blocks().blockAt(4*size,2*size));
        s.enqueue(()->s.paintCells(5*size,1,0,Material.WOOD)); s.tick(); assertEquals(Material.WOOD,w.get(5*size,1));
        s.enqueue(()->s.paintCells(5*size,1,0,Material.AIR)); s.tick(); assertEquals(Material.AIR,w.get(5*size,1));
        assertEquals(water,count(w,Material.WATER)); w.set(size,2*size-1,Material.AIR); s.blocks().step();
        assertNotNull(s.blocks().blockAt(size,3*size)); assertEquals(water,count(w,Material.WATER)); audit(s,size);
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void overflowingPoolConservesWater(int size) {
        var w=new World(7*size,10*size+1,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,123,size);
        for(int y=6*size;y<10*size;y+=size) { assertTrue(s.blocks().place(size,y,true)); assertTrue(s.blocks().place(4*size,y,true)); }
        for(int y=7*size;y<10*size;y++) for(int x=2*size;x<4*size;x++) w.set(x,y,Material.WATER);
        int water=count(w,Material.WATER);
        for(int n=0;n<3;n++) { assertTrue(s.blocks().place(2*size,size,false)); for(int i=0;i<60;i++) { s.tick(); assertEquals(water,count(w,Material.WATER)); } }
        boolean outside=false; for(int y=0;y<10*size;y++) for(int x=0;x<7*size;x++) if((x<size || x>=5*size) && w.get(x,y)==Material.WATER) outside=true;
        assertTrue(outside,"water did not spill over walls"); audit(s,size);
    }
    @Test void defaultEightMatchesExplicitEightReplay() {
        var a=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),99);
        var b=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),99,8);
        for(var s:List.of(a,b)) { s.blocks().place(8,8,false); s.world().set(8,24,Material.WATER); s.world().set(40,1,Material.SAND); }
        for(int t=0;t<300;t++) { a.tick(); b.tick(); assertEquals(a.blocks().blocks(),b.blocks().blocks()); assertEquals(a.blocks().diagnostics(),b.blocks().diagnostics());
            for(int y=0;y<65;y++) for(int x=0;x<64;x++) assertEquals(a.world().get(x,y),b.world().get(x,y)); }
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void boundariesChunksAndGranular(int size) {
        var w=new World(64,82,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,7,size);
        assertTrue(s.blocks().place(31,31,false)); var block=s.blocks().blockAt(31,31); assertNotNull(block);
        assertTrue(block.y()<32 && block.y()+size>32,"fixture crosses chunk seam");
        for(int y=block.y();y<block.y()+size;y++) for(int x=block.x();x<block.x()+size;x++) assertEquals(block.id(),s.blocks().blockAt(x,y).id());
        assertFalse(s.blocks().place(0,81,false));
        for(var m:List.of(Material.SAND,Material.GRAVEL,Material.SOIL)) {
            var t=new FixedBlockSession(new World(32,33,World.Mode.BEDROCK_ENABLED),8,size);
            assertTrue(t.blocks().place(size,size,false)); t.world().set(size,2*size,m); t.blocks().step();
            assertNotNull(t.blocks().blockAt(size,size)); assertEquals(m,t.world().get(size,2*size));
            t.tick(); assertEquals(1,count(t.world(),m)); assertEquals(1,t.cellularTicks());
        }
        audit(s,size);
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void failedWaterPlanPublishesNothing(int size) {
        var w=new World(3*size,9*size+1,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,8,size);
        for(int y=2*size;y<9*size;y++) for(int x=0;x<3*size;x++) w.set(x,y,Material.WATER);
        for(int x=0;x<3*size;x++) w.set(x,2*size-1,Material.WOOD);
        for(int y=2*size;y<3*size;y++) for(int x=size;x<2*size;x++) w.set(x,y,Material.AIR);
        assertTrue(s.blocks().place(size,2*size,false)); var before=s.blocks().blocks(); long revision=w.mutationRevision();
        int water=count(w,Material.WATER);
        for(int i=0;i<=FixedBlockWorld.FALL_DELAY_TICKS;i++) s.blocks().step();
        assertTrue(s.blocks().obstructed()>0); assertEquals(revision,w.mutationRevision()); assertEquals(before,s.blocks().blocks());
        assertEquals(water,count(w,Material.WATER)); audit(s,size);
    }
    @ParameterizedTest @ValueSource(ints={8,4}) void stressReplayTenThousandTicks(int size) {
        var a=new FixedBlockSession(new World(48,49,World.Mode.BEDROCK_ENABLED),11,size);
        var b=new FixedBlockSession(new World(48,49,World.Mode.BEDROCK_ENABLED),11,size);
        for(var s:List.of(a,b)) { assertTrue(s.blocks().place(0,0,false)); s.world().set(40,1,Material.WATER); s.world().set(42,1,Material.SAND); }
        for(int tick=0;tick<10000;tick++) {
            int t=tick;
            for(var s:List.of(a,b)) { if(t%300==0) s.enqueue(()->s.blocks().place(16,0,false)); if(t%300==150) s.enqueue(()->s.blocks().removeAt(16,48-size)); s.tick(); }
            assertEquals(tick+1,a.cellularTicks()); assertEquals(a.blocks().blocks(),b.blocks().blocks());
            if(tick%100==0 || tick==9999) { audit(a,size); assertEquals(1,count(a.world(),Material.WATER)); assertEquals(1,count(a.world(),Material.SAND));
                for(int y=0;y<49;y++) for(int x=0;x<48;x++) assertEquals(a.world().get(x,y),b.world().get(x,y)); }
        }
    }
}
