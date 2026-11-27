package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class WeightedSupportTest {
    @Test void homogeneousCantileversInBothDirections() {
        for (int size : new int[]{4, 8}) for (Material rock : new Material[]{Material.GRANITE, Material.MARBLE, Material.STONE, Material.LIMESTONE}) for (int direction : new int[]{-1, 1}) {
            var s = new FixedBlockSession(new World(256, 81, World.Mode.BEDROCK_ENABLED), 1, size);
            int x=128, ground=80-size, y=ground-size, length=rock.rockSupportRangeCells()/size;
            assertTrue(s.blocks().place(x,ground,false,rock));
            for (int i=0;i<=length+1;i++) assertTrue(s.blocks().place(x+direction*i*size,y,false,rock));
            for (int tick=0;tick<8;tick++) s.blocks().step();
            var edge=s.blocks().blockSupportAt(x+direction*length*size,y);
            assertEquals(240,edge.supportCost());
            assertFalse(s.blocks().blockSupportAt(x+direction*(length+1)*size,y).supported());
            assertNotNull(s.blocks().blockAt(x+direction*(length+1)*size,y));
            s.blocks().step();
            assertNull(s.blocks().blockAt(x+direction*(length+1)*size,y));
            assertNotNull(s.blocks().blockAt(x+direction*length*size,y));
        }
    }

    @Test void weakBoundaryAccumulatesAndDoesNotRecover() {
        var s=new FixedBlockSession(new World(128,81,World.Mode.BEDROCK_ENABLED),1,8);
        assertTrue(s.blocks().place(16,72,false,Material.GRANITE));
        Material[] rocks={Material.GRANITE,Material.GRANITE,Material.GRANITE,Material.LIMESTONE,Material.GRANITE,Material.GRANITE};
        for(int i=0;i<rocks.length;i++) assertTrue(s.blocks().place(16+i*8,64,false,rocks[i]));
        s.blocks().step();
        int[] expected={0,40,80,160,240,-1};
        for(int i=0;i<rocks.length;i++) assertEquals(expected[i],s.blocks().blockSupportAt(16+i*8,64).supportCost());
        for(int i=0;i<8;i++) s.blocks().step();
        assertNull(s.blocks().blockAt(56,64));
    }

    @Test void mixedBoundariesAreSymmetricAndVerticalResetIsIndependentOfMaterial() {
        for(int size:new int[]{4,8}) for(Material a:new Material[]{Material.GRANITE,Material.MARBLE,Material.STONE,Material.LIMESTONE})
            for(Material b:new Material[]{Material.GRANITE,Material.MARBLE,Material.STONE,Material.LIMESTONE}) {
                var s=new FixedBlockSession(new World(128,81,World.Mode.BEDROCK_ENABLED),1,size);
                int ground=80-size,y=ground-size,x=48;
                assertTrue(s.blocks().place(x,ground,false,a));
                assertTrue(s.blocks().place(x,y,false,a));
                assertTrue(s.blocks().place(x-size,y,false,b));
                assertTrue(s.blocks().place(x+size,y,false,b));
                assertTrue(s.blocks().place(x+size,y-size,false,a));
                s.blocks().step();
                int cost=240*size/Math.min(a.rockSupportRangeCells(),b.rockSupportRangeCells());
                assertEquals(cost,s.blocks().blockSupportAt(x-size,y).supportCost());
                assertEquals(cost,s.blocks().blockSupportAt(x+size,y).supportCost());
                assertEquals(0,s.blocks().blockSupportAt(x+size,y-size).supportCost());
            }
    }

    @Test void cheaperAlternativeReachesAcrossCycleRegardlessOfPlacementOrder() {
        for(boolean reverse:new boolean[]{false,true}) {
            var s=new FixedBlockSession(new World(128,81,World.Mode.BEDROCK_ENABLED),1,8);
            assertTrue(s.blocks().place(8,72,false,Material.GRANITE));
            assertTrue(s.blocks().place(56,72,false,Material.GRANITE));
            for(int i=0;i<=6;i++) {
                int j=reverse?6-i:i;
                assertTrue(s.blocks().place(8+j*8,64,false,j==1?Material.LIMESTONE:Material.GRANITE));
                assertTrue(s.blocks().place(8+j*8,56,false,Material.GRANITE));
            }
            s.blocks().step();
            assertEquals(40,s.blocks().blockSupportAt(48,64).supportCost());
            assertEquals(0,s.blocks().blockSupportAt(48,56).supportCost());
            assertEquals(80,s.blocks().blockSupportAt(16,64).supportCost());
        }
    }

    @Test void improvedCostIsPropagatedAfterEarlierQueueEntry() {
        var s=new FixedBlockSession(new World(128,81,World.Mode.BEDROCK_ENABLED),1,8);
        assertTrue(s.blocks().place(8,72,false,Material.GRANITE));
        assertTrue(s.blocks().place(56,72,false,Material.GRANITE));
        for(int i=0;i<=6;i++) assertTrue(s.blocks().place(8+8*i,64,false,i==1?Material.LIMESTONE:Material.GRANITE));
        s.blocks().step();
        assertEquals(120,s.blocks().blockSupportAt(32,64).supportCost());
        assertTrue(s.blocks().supportDiagnostics().processed()>s.blocks().supportDiagnostics().supported());
    }

    @Test void mixedWaitingStateSurvivesRoundTripAndRestorationClearsDelay() throws Exception {
        var s=new FixedBlockSession(new World(128,81,World.Mode.BEDROCK_ENABLED),1,8);
        assertTrue(s.blocks().place(16,72,false,Material.GRANITE));
        assertTrue(s.blocks().place(16,64,false,Material.MARBLE));
        assertTrue(s.blocks().place(24,64,false,Material.LIMESTONE));
        assertTrue(s.blocks().removeAt(16,72));
        for(int i=0;i<4;i++) s.tick();
        var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out);
        var loaded=FixedSessionIO.read(new ByteArrayInputStream(out.toByteArray())).fixed();
        assertEquals(s.blocks().saveState(),loaded.blocks().saveState());
        assertTrue(loaded.blocks().place(16,72,false,Material.GRANITE));
        loaded.tick();
        assertTrue(loaded.blocks().blockSupportAt(24,64).supported());
        for(var b:loaded.blocks().saveState().blocks()) assertEquals(0,b.unsupportedTicks());
    }

    @Test void removingWeakLinkTriggersCascadeWithoutChangingBlockIdentity() {
        for(int size:new int[]{4,8}) {
            var s=new FixedBlockSession(new World(160,97,World.Mode.BEDROCK_ENABLED),42,size);
            int ground=96-size,y=ground-size,x=16;
            assertTrue(s.blocks().place(x,ground,false,Material.GRANITE));
            assertTrue(s.blocks().place(x,y,false,Material.GRANITE));
            assertTrue(s.blocks().place(x+size,y,false,Material.LIMESTONE));
            assertTrue(s.blocks().place(x+2*size,y,false,Material.MARBLE));
            assertTrue(s.blocks().place(x+3*size,y,false,Material.STONE));
            long marble=s.blocks().blockAt(x+2*size,y).id(),stone=s.blocks().blockAt(x+3*size,y).id();
            s.blocks().step();
            assertTrue(s.blocks().removeAt(x+size,y));
            for(int i=0;i<8;i++) s.blocks().step();
            assertEquals(marble,s.blocks().blockAt(x+2*size,y).id());
            s.blocks().step();
            assertEquals(marble,s.blocks().blockAt(x+2*size,y+size).id());
            assertEquals(stone,s.blocks().blockAt(x+3*size,y+size).id());
            assertEquals(Material.MARBLE,s.world().get(x+2*size,y+size));
            assertEquals(Material.STONE,s.world().get(x+3*size,y+size));
            new FixedBlockWorld(s.world(),size).restoreState(s.blocks().saveState());
        }
    }
}
