package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class WaterDisplacementTest {
    private int count(World w,Material m) { int n=0; for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) if(w.get(x,y)==m) n++; return n; }
    private void pool(World w,int fromY,int toY) { for(int y=fromY;y<toY;y++) for(int x=8;x<24;x++) w.set(x,y,Material.WATER); }
    private void assertOwnership(FixedBlockSession s) {
        assertEquals(s.blocks().size()*64,count(s.world(),Material.STONE));
        for(var b:s.blocks().blocks()) for(int y=b.y();y<b.y()+8;y++) for(int x=b.x();x<b.x()+8;x++) assertEquals(Material.STONE,s.world().get(x,y));
    }
    @Test void shallowAndDeepPoolReachBedrockWithoutLosingWater() {
        for(int top:List.of(48,32)) {
            var w=new World(48,65,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,17); pool(w,top,64);
            assertTrue(s.blocks().place(8,8,false)); int water=count(w,Material.WATER); int moved=0;
            for(int i=0;i<24;i++) { int previous=s.blocks().blocks().getFirst().y(); s.blocks().step(); if(s.blocks().blocks().getFirst().y()>previous) moved++;
                assertEquals(water,count(w,Material.WATER)); assertOwnership(s); }
            assertEquals(56,s.blocks().blocks().getFirst().y()); assertTrue(moved>=6);
        }
    }
    @Test void multipleBlocksAndWallRemainConservative() {
        var w=new World(64,65,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,18);
        for(int y=16;y<64;y++) w.set(7,y,Material.WOOD); pool(w,32,64);
        assertTrue(s.blocks().place(8,16,false)); assertTrue(s.blocks().place(16,16,false));
        int water=count(w,Material.WATER);
        for(int i=0;i<50;i++) { s.tick(); assertEquals(water,count(w,Material.WATER)); assertOwnership(s); }
        assertEquals(List.of(56,56),s.blocks().blocks().stream().map(FixedBlockWorld.Block::y).toList(),"obstructed="+s.blocks().obstructed());
        for(int y=16;y<64;y++) assertEquals(Material.WOOD,w.get(7,y));
    }
    @Test void sealedTankAndRejectedPublicationAreLocal() {
        var w=new World(48,65,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,19);
        for(int y=24;y<64;y++) for(int x=0;x<24;x++) w.set(x,y,Material.WATER);
        for(int y=24;y<64;y++) w.set(24,y,Material.WOOD);
        for(int x=0;x<=24;x++) w.set(x,23,Material.WOOD);
        for(int y=24;y<32;y++) for(int x=8;x<16;x++) w.set(x,y,Material.AIR);
        assertTrue(s.blocks().place(8,24,false)); int water=count(w,Material.WATER); var before=s.blocks().blocks(); long revision=w.mutationRevision();
        for(int i=0;i<=FixedBlockWorld.FALL_DELAY_TICKS;i++) s.blocks().step(); assertEquals(before,s.blocks().blocks()); assertEquals(revision,w.mutationRevision()); assertEquals(water,count(w,Material.WATER)); assertTrue(s.blocks().obstructed()>0);
        assertTrue(s.blocks().place(32,8,false)); for(int i=0;i<=FixedBlockWorld.FALL_DELAY_TICKS;i++) s.blocks().step();
        assertEquals(24,s.blocks().blockAt(8,24).y()); assertEquals(16,s.blocks().blockAt(32,16).y()); assertEquals(water,count(w,Material.WATER));
        var other=new World(48,65,World.Mode.BEDROCK_ENABLED); var t=new FixedBlockSession(other,20); assertTrue(t.blocks().place(8,8,false));
        var state=t.blocks().blocks(); long rev=other.mutationRevision();
        other.setChangeSink(new World.ChangeSink() { public void changed(int x,int y,byte from,byte to) { fail("callback must not run"); } public void invalidated() { } });
        for(int i=0;i<=FixedBlockWorld.FALL_DELAY_TICKS;i++) t.blocks().step(); assertEquals(state,t.blocks().blocks()); assertEquals(rev,other.mutationRevision()); assertEquals(1,t.blocks().obstructed());
        assertEquals(FixedBlockWorld.Reason.INVALID_OR_STALE_PLAN,t.blocks().diagnostics().getFirst().reason());
    }
    @Test void competingBlocksResolveInStableOrder() {
        var a=new FixedBlockSession(new World(48,65,World.Mode.BEDROCK_ENABLED),29);
        var b=new FixedBlockSession(new World(48,65,World.Mode.BEDROCK_ENABLED),29);
        for(var s:List.of(a,b)) {
            pool(s.world(),32,64); assertTrue(s.blocks().place(8,24,false)); assertTrue(s.blocks().place(16,24,false));
        }
        int water=count(a.world(),Material.WATER);
        for(int i=0;i<12;i++) {
            a.tick(); b.tick(); assertEquals(a.blocks().blocks(),b.blocks().blocks());
            assertEquals(water,count(a.world(),Material.WATER)); assertOwnership(a);
            for(int y=0;y<65;y++) for(int x=0;x<48;x++) assertEquals(a.world().get(x,y),b.world().get(x,y));
        }
    }
    @Test void tenThousandMixedTicksReplayAfterSubmersion() {
        var a=new FixedBlockSession(new World(48,65,World.Mode.BEDROCK_ENABLED),77);
        var b=new FixedBlockSession(new World(48,65,World.Mode.BEDROCK_ENABLED),77);
        for(var s:List.of(a,b)) {
            pool(s.world(),32,64); assertTrue(s.blocks().place(8,8,false)); assertTrue(s.blocks().place(16,8,false));
            s.world().set(34,1,Material.SAND); s.world().set(36,1,Material.GRAVEL); s.world().set(38,1,Material.SOIL);
        }
        int water=count(a.world(),Material.WATER);
        for(int i=0;i<10000;i++) {
            a.tick(); b.tick(); assertEquals(i+1,a.cellularTicks()); assertEquals(a.blocks().blocks(),b.blocks().blocks());
            if(i%100==0 || i==9999) {
                assertEquals(water,count(a.world(),Material.WATER)); assertOwnership(a);
                for(int y=0;y<65;y++) for(int x=0;x<48;x++) assertEquals(a.world().get(x,y),b.world().get(x,y));
            }
        }
    }
    @Test void removalAllowsWaterToFillAndReplayMatches() {
        var a=new FixedBlockSession(new World(48,65,World.Mode.BEDROCK_ENABLED),55); var b=new FixedBlockSession(new World(48,65,World.Mode.BEDROCK_ENABLED),55);
        for(var s:List.of(a,b)) { assertTrue(s.blocks().place(8,48,true)); s.world().set(10,47,Material.WATER); s.blocks().removeAt(8,48); }
        for(int i=0;i<10;i++) { a.tick(); b.tick(); assertEquals(a.blocks().blocks(),b.blocks().blocks());
            for(int y=0;y<65;y++) for(int x=0;x<48;x++) assertEquals(a.world().get(x,y),b.world().get(x,y)); }
        assertEquals(Material.WATER,a.world().get(10,57));
    }
}
