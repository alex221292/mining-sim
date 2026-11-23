package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.world.Material;
import miningsim.world.World;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenPoolRegressionTest {
    private int water(World world) { int n=0; for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) if(world.get(x,y)==Material.WATER) n++; return n; }
    @Test void granularMaterialsRemainSolidObstacles() {
        for(var material:List.of(Material.SAND,Material.GRAVEL,Material.SOIL)) {
            var w=new World(24,65,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,8);
            assertTrue(s.blocks().place(8,24,false)); w.set(8,32,material); s.blocks().step();
            var block=s.blocks().blockAt(8,24); assertNotNull(block); assertEquals(material,w.get(8,32));
            assertEquals(FixedBlockWorld.Reason.SUPPORTED_BY_SOLID,s.blocks().diagnostics().stream().filter(d->d.id()==block.id()).findFirst().orElseThrow().reason());
        }
    }
    @Test void openShaftAllowsBlockToSinkWithoutLosingWater() {
        var world=new World(24,81,World.Mode.BEDROCK_ENABLED); var session=new FixedBlockSession(world,123);
        for(int y=0;y<=72;y+=8) { assertTrue(session.blocks().place(0,y,true)); assertTrue(session.blocks().place(16,y,true)); }
        for(int y=32;y<80;y++) for(int x=8;x<16;x++) world.set(x,y,Material.WATER);
        assertTrue(session.blocks().place(8,24,false)); long before=session.blocks().obstructed();
        int water=water(world); session.blocks().step(); assertEquals(water,water(world));
        assertNotNull(session.blocks().blockAt(8,32),"open shaft with free air above: obstructed="+(session.blocks().obstructed()-before));
    }
    @Test void fiveSequentialBlocksReachSupportedStack() {
        var w=new World(24,121,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,5);
        for(int y=0;y<=112;y+=8) { assertTrue(s.blocks().place(0,y,true)); assertTrue(s.blocks().place(16,y,true)); }
        for(int y=56;y<120;y++) for(int x=8;x<16;x++) w.set(x,y,Material.WATER);
        int water=water(w);
        for(int n=0;n<5;n++) {
            assertTrue(s.blocks().place(8,8,false));
            for(int tick=0;tick<20;tick++) { s.blocks().step(); assertEquals(water,water(w)); }
            int expected=112-8*n; assertNotNull(s.blocks().blockAt(8,expected),"block "+n+" positions="+s.blocks().blocks()+" diagnostics="+s.blocks().diagnostics());
        }
        for(int y=80;y<=112;y+=8) {
            long id=s.blocks().blockAt(8,y).id(); var d=s.blocks().diagnostics().stream().filter(v->v.id()==id).findFirst().orElseThrow();
            assertEquals(FixedBlockWorld.Reason.SUPPORTED_BY_SOLID,d.reason());
        }
    }
    @Test void stackMovesDownTogetherThroughWater() {
        var w=new World(24,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,6);
        for(int y=0;y<=72;y+=8) { assertTrue(s.blocks().place(0,y,true)); assertTrue(s.blocks().place(16,y,true)); }
        assertTrue(s.blocks().place(8,16,false)); assertTrue(s.blocks().place(8,24,false));
        for(int y=32;y<80;y++) for(int x=8;x<16;x++) w.set(x,y,Material.WATER);
        int water=water(w); s.blocks().step();
        assertNotNull(s.blocks().blockAt(8,32)); assertNotNull(s.blocks().blockAt(8,24)); assertEquals(water,water(w));
    }
    @Test void removingLowerSupportLetsStackFallIntoWater() {
        var w=new World(24,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,9);
        for(int y=0;y<=72;y+=8) { assertTrue(s.blocks().place(0,y,true)); assertTrue(s.blocks().place(16,y,true)); }
        for(int y:new int[]{40,48,56}) assertTrue(s.blocks().place(8,y,false));
        for(int y=64;y<80;y++) for(int x=8;x<16;x++) w.set(x,y,Material.WATER);
        int water=water(w); assertTrue(s.blocks().removeAt(8,56));
        s.blocks().step(); assertNotNull(s.blocks().blockAt(8,56)); assertNotNull(s.blocks().blockAt(8,48));
        s.blocks().step(); assertNotNull(s.blocks().blockAt(8,64)); assertNotNull(s.blocks().blockAt(8,56)); assertEquals(water,water(w));
    }
    @Test void sealedObstructionCanRetryAfterOpening() {
        var w=new World(24,65,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,7);
        for(int y=0;y<=56;y+=8) { assertTrue(s.blocks().place(0,y,true)); assertTrue(s.blocks().place(16,y,true)); }
        for(int y=32;y<64;y++) for(int x=8;x<16;x++) w.set(x,y,Material.WATER);
        for(int x=8;x<16;x++) w.set(x,23,Material.WOOD);
        assertTrue(s.blocks().place(8,24,false)); int water=water(w); var before=s.blocks().blockAt(8,24);
        s.blocks().step(); assertEquals(before,s.blocks().blockAt(8,24));
        var reason=s.blocks().diagnostics().stream().filter(d->d.id()==before.id()).findFirst().orElseThrow();
        assertEquals(FixedBlockWorld.Reason.WATER_DISPLACEMENT_BLOCKED,reason.reason()); assertEquals(water,water(w));
        w.set(8,23,Material.AIR); s.blocks().step(); assertNotNull(s.blocks().blockAt(8,32)); assertEquals(water,water(w));
    }
    @Test void openPoolReplayForTenThousandTicks() {
        var a=new FixedBlockSession(new World(24,81,World.Mode.BEDROCK_ENABLED),71);
        var b=new FixedBlockSession(new World(24,81,World.Mode.BEDROCK_ENABLED),71);
        for(var s:List.of(a,b)) {
            for(int y=0;y<=72;y+=8) { assertTrue(s.blocks().place(0,y,true)); assertTrue(s.blocks().place(16,y,true)); }
            for(int y=48;y<80;y++) for(int x=8;x<16;x++) s.world().set(x,y,Material.WATER);
            assertTrue(s.blocks().place(8,8,false));
        }
        int total=water(a.world());
        for(int tick=0;tick<10000;tick++) {
            a.tick(); b.tick(); assertEquals(tick+1,a.cellularTicks()); assertEquals(a.blocks().blocks(),b.blocks().blocks());
            assertEquals(a.blocks().diagnostics(),b.blocks().diagnostics());
            var work=a.blocks().tickDiagnostics(); assertTrue(work.maxVisits()<=FixedBlockWorld.WATER_MAX_VISITS); assertTrue(work.maxChecks()<=FixedBlockWorld.WATER_MAX_CHECKS);
            if(tick%100==0 || tick==9999) {
                assertEquals(total,water(a.world())); int stone=0;
                for(int y=0;y<81;y++) for(int x=0;x<24;x++) { var material=a.world().get(x,y); assertEquals(material,b.world().get(x,y)); if(material==Material.STONE) stone++; }
                assertEquals(a.blocks().size()*64,stone);
            }
        }
    }
}
