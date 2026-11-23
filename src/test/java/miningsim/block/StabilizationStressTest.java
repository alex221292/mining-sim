package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class StabilizationStressTest {
    private int count(World w,Material m) { int n=0; for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) if(w.get(x,y)==m) n++; return n; }
    private FixedBlockSession fixture() {
        var w=new World(96,81,World.Mode.BEDROCK_ENABLED); var s=new FixedBlockSession(w,12345);
        for(int y=24;y<80;y++) for(int x=0;x<24;x++) w.set(x,y,Material.WATER);
        for(int y=24;y<80;y++) w.set(24,y,Material.WOOD);
        for(int x=0;x<=24;x++) w.set(x,23,Material.WOOD);
        for(int y=24;y<32;y++) for(int x=8;x<16;x++) w.set(x,y,Material.AIR);
        assertTrue(s.blocks().place(8,24,false));
        for(int y=48;y<80;y++) for(int x=32;x<48;x++) w.set(x,y,Material.WATER);
        assertTrue(s.blocks().place(32,8,false)); assertTrue(s.blocks().place(48,8,false));
        assertTrue(s.blocks().place(64,64,false)); assertTrue(s.blocks().place(64,56,false));
        w.set(88,1,Material.SAND); return s;
    }
    @Test void drawingAndErasingContinueAfterWaterObstruction() {
        var s=fixture(); int water=count(s.world(),Material.WATER);
        for(int i=0;i<=FixedBlockWorld.FALL_DELAY_TICKS;i++) s.tick();
        assertTrue(s.blocks().obstructed()>0);
        s.enqueue(()->s.paintCells(80,1,0,Material.WOOD)); s.tick(); assertEquals(Material.WOOD,s.world().get(80,1));
        s.enqueue(()->s.paintCells(80,1,0,Material.AIR)); s.tick(); assertEquals(Material.AIR,s.world().get(80,1));
        assertEquals(water,count(s.world(),Material.WATER)); assertEquals(FixedBlockWorld.FALL_DELAY_TICKS+3,s.cellularTicks());
    }
    @Test void multiplePoolsRepeatedEditsAndObstructionsReplayTenThousandTicks() {
        var a=fixture(); var b=fixture(); int water=count(a.world(),Material.WATER),sand=count(a.world(),Material.SAND); long failures=0;
        for(int tick=0;tick<10000;tick++) {
            int t=tick;
            for(var s:List.of(a,b)) {
                if(t%250==20) s.enqueue(()->s.blocks().removeAt(64,64));
                if(t%250==80) s.enqueue(()->s.blocks().place(64,8,false));
                if(t%250==150) s.enqueue(()->s.blocks().removeAt(64,72));
                s.tick();
            }
            assertEquals(tick+1,a.cellularTicks()); assertEquals(a.blocks().blocks(),b.blocks().blocks()); assertEquals(a.blocks().diagnostics(),b.blocks().diagnostics());
            failures=a.blocks().obstructed();
            if(tick%100==0 || tick==9999) {
                assertEquals(water,count(a.world(),Material.WATER)); assertEquals(sand,count(a.world(),Material.SAND));
                assertEquals(a.blocks().size()*64,count(a.world(),Material.STONE));
                for(var block:a.blocks().blocks()) for(int y=block.y();y<block.y()+8;y++) for(int x=block.x();x<block.x()+8;x++) assertEquals(block.id(),a.blocks().blockAt(x,y).id());
                for(int y=0;y<81;y++) for(int x=0;x<96;x++) assertEquals(a.world().get(x,y),b.world().get(x,y));
            }
        }
        assertTrue(failures>10,"sealed basin must repeatedly refuse movement");
    }
}
