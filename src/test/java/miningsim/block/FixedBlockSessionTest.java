package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class FixedBlockSessionTest {
    @Test void fastStrokeKeepsTwentyBlocksUntilReleased() {
        var w=new World(200,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,1);
        s.enqueue(()->s.blockStroke(0,8,159,8,false)); s.tick();
        assertEquals(20,s.blocks().size()); assertTrue(s.blocks().blocks().stream().allMatch(b->b.y()==8));
        s.tick(); assertTrue(s.blocks().blocks().stream().allMatch(b->b.y()==8));
        s.enqueue(()->s.blocks().releaseStroke()); s.tick(); assertEquals(20,s.blocks().size());
        int stone=0; for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) if(w.get(x,y)==Material.STONE) stone++;
        assertEquals(20*64,stone);
    }
    @Test void placementIsAtomicAndEraseDoesNotTouchNeighbor() {
        var w=new World(40,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,2);
        w.set(10,10,Material.WATER); long revision=w.mutationRevision();
        assertFalse(s.blocks().place(8,8,false)); assertEquals(revision,w.mutationRevision()); assertEquals(Material.WATER,w.get(10,10));
        assertTrue(s.blocks().place(16,8,false)); assertTrue(s.blocks().place(24,8,false));
        long neighbor=s.blocks().blockAt(24,8).id(); assertTrue(s.blocks().removeAt(16,8));
        assertEquals(neighbor,s.blocks().blockAt(24,8).id()); assertEquals(Material.STONE,w.get(24,8));
        assertEquals(Material.AIR,w.get(16,8));
    }
    @Test void waterContinuesToFallWhileBlockIsObstructed() {
        var w=new World(40,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,3);
        assertTrue(s.blocks().place(8,56,false));
        for(int y=64;y<80;y++) for(int x=0;x<24;x++) w.set(x,y,Material.SOIL);
        w.set(30,1,Material.WATER); s.tick();
        assertEquals(Material.WATER,w.get(30,2)); assertEquals(56,s.blocks().blocks().getFirst().y());
        assertEquals(1,s.cellularTicks());
    }
    @Test void stackFallsAfterBaseRemoval() {
        var w=new World(40,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,1);
        for(int y:new int[]{72,64,56}) assertTrue(s.blocks().place(8,y,false));
        s.tick(); assertTrue(s.blocks().blocks().stream().allMatch(b->b.state()==FixedBlockWorld.State.STATIC));
        assertTrue(s.blocks().removeAt(8,72)); s.tick();
        assertEquals(2,s.blocks().size()); assertTrue(s.blocks().blocks().stream().allMatch(b->b.state()==FixedBlockWorld.State.FALLING));
        s.tick(); assertTrue(s.blocks().blocks().stream().allMatch(b->b.state()==FixedBlockWorld.State.STATIC));
        assertNotNull(s.blocks().blockAt(8,72)); assertNotNull(s.blocks().blockAt(8,64));
        int stone=0; for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) if(w.get(x,y)==Material.STONE) stone++;
        assertEquals(128,stone);
    }
    @Test void denseSandStopsBlockButCellsAndEditsContinue() {
        var w=new World(64,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,1);
        assertTrue(s.blocks().place(8,56,false));
        for(int y=64;y<80;y++) for(int x=0;x<32;x++) w.set(x,y,Material.SAND);
        s.tick(); assertEquals(56,s.blocks().blocks().getFirst().y());
        s.enqueue(()->s.paintCells(50,1,0,Material.SAND)); s.tick(); assertEquals(Material.SAND,w.get(50,2));
        s.enqueue(()->s.blockStroke(8,56,8,56,true)); s.tick(); assertEquals(0,s.blocks().size());
        assertEquals(3,s.cellularTicks());
    }
    @Test void deterministicReplayAndRejectedPlacement() {
        var a=new FixedBlockSession(new World(48,81,World.Mode.BEDROCK_ENABLED),123);
        var b=new FixedBlockSession(new World(48,81,World.Mode.BEDROCK_ENABLED),123);
        for(var s:new FixedBlockSession[]{a,b}) { s.enqueue(()->s.blockStroke(0,16,31,16,false)); s.enqueue(()->s.blocks().releaseStroke()); s.enqueue(()->s.paintCells(40,1,1,Material.WATER)); }
        for(int i=0;i<40;i++) { a.tick(); b.tick(); assertEquals(a.blocks().blocks(),b.blocks().blocks());
            for(int y=0;y<81;y++) for(int x=0;x<48;x++) assertEquals(a.world().get(x,y),b.world().get(x,y)); }
        assertFalse(a.blocks().place(0,80,false)); assertEquals(1,a.blocks().rejected());
    }
}
