package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.World;
import miningsim.world.Material;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlockSupportTest {
    private FixedBlockSession world(int s) { return new FixedBlockSession(new World(32*s,12*s+1,World.Mode.BEDROCK_ENABLED),12345,s); }
    private void place(FixedBlockSession s,int x,int y) { assertTrue(s.blocks().place(x,y,false)); }
    @ParameterizedTest @ValueSource(ints={4,8}) void cantileverLimitedAndNoDownwardPropagation(int z) {
        var s=world(z); int ground=11*z; place(s,0,ground);
        int horizontal=32/z; for(int i=1;i<=horizontal+1;i++) place(s,i*z,ground);
        // Remove ground under the cantilever by using a one-block pedestal one row higher.
        for(int i=1;i<=horizontal+1;i++) assertTrue(s.blocks().removeAt(i*z,ground));
        place(s,0,ground-z); for(int i=1;i<=horizontal+1;i++) place(s,i*z,ground-z);
        s.tick(); assertEquals(32,s.blocks().blockSupportAt(horizontal*z,ground-z).distanceCells());
        assertFalse(s.blocks().blockSupportAt((horizontal+1)*z,ground-z).supported());
        for(int i=0;i<600;i++) s.tick();
        assertNotNull(s.blocks().blockAt(horizontal*z,ground-z));
        assertNull(s.blocks().blockAt((horizontal+1)*z,ground-z));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void bridgeUsesNearestSupportAndRestoration(int z) {
        var s=world(z); int y=10*z,span=32/z*2; place(s,0,11*z); place(s,span*z,11*z);
        for(int i=0;i<=span;i++) place(s,i*z,y); s.tick();
        assertEquals(32,s.blocks().blockSupportAt(span/2*z,y).distanceCells());
        assertEquals(z,s.blocks().blockSupportAt((span-1)*z,y).distanceCells());
        for(int i=0;i<600;i++) s.tick(); assertNotNull(s.blocks().blockAt(span/2*z,y));
        assertTrue(s.blocks().removeAt(0,11*z)); for(int i=0;i<4;i++) s.tick();
        assertEquals(4,s.blocks().saveState().blocks().stream().filter(b->b.x()==0 && b.y()==y).findFirst().orElseThrow().unsupportedTicks());
        place(s,0,11*z); s.tick(); assertEquals(0,s.blocks().saveState().blocks().stream().filter(b->b.x()==0 && b.y()==y).findFirst().orElseThrow().unsupportedTicks());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void floatingCycleAndDiagonalNeverSupport(int z) {
        var s=world(z); for(int x=0;x<3;x++) for(int y=0;y<3;y++) if(x==0 || x==2 || y==0 || y==2) place(s,x*z,y*z);
        for(int i=0;i<10;i++) s.tick(); assertNull(s.blocks().blockAt(z,0));
        var a=world(z); place(a,0,11*z); place(a,z,10*z); a.tick(); assertFalse(a.blocks().blockSupportAt(z,10*z).supported());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void gridGapsAndVerticalBudget(int z) {
        var s=world(z); place(s,0,11*z); place(s,0,10*z);
        for(int i=1;i<=32/z;i++) place(s,i*z,10*z);
        place(s,32,9*z); place(s,32+z,9*z); place(s,32+3*z,10*z);
        s.tick(); assertEquals(32,s.blocks().blockSupportAt(32,9*z).distanceCells());
        assertFalse(s.blocks().blockSupportAt(32+z,9*z).supported());
        assertFalse(s.blocks().blockSupportAt(32+3*z,10*z).supported());
        assertNull(s.blocks().blockSupportAt(-1,0)); assertNull(s.blocks().blockSupportAt(s.world().width(),0));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void fallingDoesNotStopAtWall(int z) {
        var s=world(z); place(s,0,11*z); for(int i=1;i<11;i++) place(s,0,i*z);
        place(s,z,0); for(int i=0;i<9;i++) s.tick();
        assertEquals(FixedBlockWorld.State.FALLING,s.blocks().blockAt(z,z).state());
        // It first contacts the wall laterally while already FALLING.
        for(int i=0;i<4;i++) s.tick(); assertNotNull(s.blocks().blockAt(z,5*z));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void pinnedStrokeDoesNotSupportOtherBlocks(int z) {
        var s=world(z); assertTrue(s.blocks().place(0,11*z,true)); place(s,z,11*z-z); place(s,0,11*z-z);
        s.tick(); assertFalse(s.blocks().blockSupportAt(0,11*z-z).supported());
        assertFalse(s.blocks().blockSupportAt(z,11*z-z).supported());
        s.blocks().releaseStroke(); s.tick(); assertTrue(s.blocks().blockSupportAt(z,11*z-z).supported());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void persistenceKeepsAlignmentAndDelay(int z) throws Exception {
        var s=world(z); place(s,0,11*z); place(s,0,10*z); place(s,z,10*z); place(s,4*z,0);
        for(int i=0;i<3;i++) s.tick(); var before=s.blocks().saveState(); var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out);
        var loaded=FixedSessionIO.read(new ByteArrayInputStream(out.toByteArray())).fixed(); assertEquals(before,loaded.blocks().saveState());
        loaded.tick(); assertTrue(loaded.blocks().blockSupportAt(z,10*z).supported());
        for(var b:loaded.blocks().blocks()) { assertEquals(0,b.x()%z); assertEquals(0,b.y()%z); }
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void replayWithEditsWaterAndPeriodicReload(int z) throws Exception {
        var a=world(z); var b=world(z);
        for(var s:new FixedBlockSession[]{a,b}) {
            place(s,0,11*z); for(int i=0;i<7;i++) place(s,i*z,10*z);
            s.world().set(25*z,1,Material.WATER); s.world().set(26*z,1,Material.SAND);
        }
        for(int tick=0;tick<10000;tick++) {
            for(var s:new FixedBlockSession[]{a,b}) {
                if(tick%1000==100) s.enqueue(()->s.blocks().removeAt(0,11*z));
                if(tick%1000==300) s.enqueue(()->s.blockStroke(0,0,3*z,0,false));
                if(tick%1000==301) s.enqueue(()->s.blocks().releaseStroke());
                if(tick%1000==600) s.enqueue(()->s.blockStroke(0,0,3*z,0,true));
                s.tick();
            }
            if(tick%250==0) {
                var ao=new ByteArrayOutputStream(); var bo=new ByteArrayOutputStream(); FixedSessionIO.write(a,ao); FixedSessionIO.write(b,bo);
                assertArrayEquals(ao.toByteArray(),bo.toByteArray());
                a=FixedSessionIO.read(new ByteArrayInputStream(ao.toByteArray())).fixed(); b=FixedSessionIO.read(new ByteArrayInputStream(bo.toByteArray())).fixed();
                assertEquals(a.blocks().saveState(),b.blocks().saveState());
                int water=0,sand=0,stone=0;
                for(int y=0;y<a.world().height();y++) for(int x=0;x<a.world().width();x++) {
                    var m=a.world().get(x,y); if(m==Material.WATER) water++; if(m==Material.SAND) sand++; if(m==Material.STONE) { stone++; assertNotNull(a.blocks().blockAt(x,y)); }
                }
                assertEquals(1,water); assertEquals(1,sand); assertEquals(a.blocks().size()*z*z,stone);
            }
        }
    }
}
