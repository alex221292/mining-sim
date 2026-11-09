package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.InteractiveSession;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class AutomaticRockDetachmentTest {
    @Test void adjacentPaintCommandsCoalesceAfterSixQuietTicks() {
        var w=new World(12,12,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()-> { w.set(4,2,Material.STONE); w.set(5,2,Material.STONE); }); s.tick();
        s.enqueue(()-> { w.set(4,3,Material.STONE); w.set(5,3,Material.STONE); });
        for(int i=0;i<6;i++) { s.tick(); assertTrue(s.physics().bodies().isEmpty()); }
        s.tick(); assertEquals(1,s.physics().bodies().size());
        var body=s.physics().body(1); assertEquals(4,body.fragment().descriptor().cellCount());
        assertEquals(10_000_000,body.fragment().massMilligrams());
        for(int y=2;y<=3;y++) for(int x=4;x<=5;x++) assertEquals(Material.AIR,w.get(x,y));
        for(int i=0;i<15;i++) s.tick(); assertEquals(1,s.physics().bodies().size()); assertTrue(body.yCells()>3);
    }
    @Test void foundationAndBridgeRemainUntilLastSupportIsRemoved() {
        var w=new World(12,12,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()-> { for(int y=3;y<11;y++) w.set(4,y,Material.STONE); for(int x=5;x<9;x++) w.set(x,3,Material.STONE); });
        for(int i=0;i<12;i++) s.tick(); assertTrue(s.physics().bodies().isEmpty());
        s.enqueue(()->w.set(4,10,Material.AIR)); s.tick();
        assertEquals(1,s.physics().bodies().size()); assertEquals(11,s.physics().body(1).fragment().descriptor().cellCount());
        assertEquals(Material.AIR,w.get(8,3));
    }
    @Test void brokenIntactBondDetachesUnsupportedSide() {
        var w=new World(12,12,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()-> { for(int y=3;y<11;y++) w.set(4,y,Material.STONE); });
        for(int i=0;i<12;i++) s.tick(); assertTrue(s.physics().bodies().isEmpty());
        s.automatic().topology().breakBond(4,9,4,10); s.tick();
        assertEquals(1,s.physics().bodies().size()); assertEquals(7,s.physics().body(1).fragment().descriptor().cellCount());
        assertEquals(Material.STONE,w.get(4,10));
    }
    @Test void boundedTopologyNeverExtractsWhilePending() {
        var w=new World(400,300,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()->w.set(3,3,Material.STONE));
        for(int i=0;i<6;i++) { s.tick(); assertTrue(s.automatic().diagnostics().pending()); assertEquals(Material.STONE,w.get(3,3)); }
        for(int i=0;i<40 && s.physics().bodies().isEmpty();i++) s.tick();
        assertEquals(1,s.physics().bodies().size());
    }
    @Test void largerComponentsAreNotTruncatedAndOversizedIsRetained() {
        for(int count:new int[]{128,256,512,1024,1025}) {
            var w=new World(40,40,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
            s.enqueue(()-> { for(int i=0;i<count;i++) w.set(2+i%32,2+i/32,Material.STONE); });
            for(int i=0;i<8;i++) s.tick();
            if(count==1025) {
                assertTrue(s.physics().bodies().isEmpty()); assertEquals(1,s.automatic().diagnostics().tooLarge());
                for(int i=0;i<count;i++) assertEquals(Material.STONE,w.get(2+i%32,2+i/32));
                long revision=w.mutationRevision(); for(int i=0;i<10;i++) s.tick();
                assertEquals(revision,w.mutationRevision()); assertEquals(1,s.automatic().diagnostics().tooLarge());
            } else {
                assertEquals(1,s.physics().bodies().size(),"size "+count+" failure "+s.failure());
                var body=s.physics().body(1); assertEquals(count,body.fragment().descriptor().cellCount());
                assertFalse(s.blocked(),s.failure()); assertTrue(body.yCells()>body.geometry().initialCenter().y(),"body did not fall: "+count);
                assertEquals(2_500_000L*count,body.fragment().massMilligrams());
                for(int i=0;i<count;i++) assertEquals(Material.AIR,w.get(2+i%32,2+i/32));
            }
        }
    }
    @Test void calibrationAndLegacyAreOptedOut() {
        var legacy=new InteractiveSession(new World(10,10),null); assertNull(legacy.automatic());
        var scene=miningsim.structural.RockReferenceScenes.island();
        var ref=new InteractiveSession(scene.world(),new miningsim.app.StructuralSimulationController(scene)); assertNull(ref.automatic());
        for(int i=0;i<20;i++) ref.tick(); assertEquals(Material.STONE,scene.world().get(3,1));
    }
    @Test void multipleComponentsDetachInStableOrderWithoutDuplicates() {
        var w=new World(30,20,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()-> { w.set(3,2,Material.STONE); w.set(11,2,Material.STONE); w.set(20,2,Material.STONE); });
        for(int i=0;i<30;i++) s.tick();
        assertEquals(3,s.physics().bodies().size());
        assertEquals(3,s.physics().body(1).fragment().originX()); assertEquals(11,s.physics().body(2).fragment().originX());
        assertEquals(20,s.physics().body(3).fragment().originX()); assertEquals(3,s.automatic().diagnostics().total());
        for(int i=0;i<20;i++) s.tick(); assertEquals(3,s.physics().bodies().size());
    }
    @Test void manualCommandWinsBeforeAutomaticDetachment() {
        var w=new World(12,12,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()->w.set(4,2,Material.STONE)); s.tick();
        s.enqueue(()-> { s.selectRock(4,2); assertEquals(miningsim.fragment.ExtractionCommit.Status.COMMITTED,s.extractSelected()); });
        s.tick(); for(int i=0;i<12;i++) s.tick();
        assertEquals(1,s.physics().bodies().size()); assertEquals(0,s.automatic().diagnostics().total());
    }
    @Test void detachedBodyStillCouplesWithSandAndWater() {
        for(var material:new Material[]{Material.SAND,Material.WATER}) {
            var scene=miningsim.structural.RockReferenceScenes.interactive("automatic",material);
            var structural=new miningsim.app.StructuralSimulationController(scene);
            var s=new InteractiveSession(scene.world(),structural,false,true);
            int displaced=0;
            for(int i=0;i<100;i++) { s.tick(); var d=s.coupled().diagnostics(); displaced+=d.sand()+d.water(); }
            assertFalse(s.blocked(),s.failure()); assertEquals(1,s.physics().bodies().size()); assertTrue(displaced>0);
            int count=0; for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) if(scene.world().get(x,y)==material) count++;
            assertEquals(5,count);
        }
    }
    @Test void deterministicReplayUsesIdenticalComponentAndBodyState() {
        var results=new java.util.ArrayList<String>();
        for(int run=0;run<2;run++) {
            var w=new World(16,16,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
            s.enqueue(()-> { w.set(3,2,Material.STONE); w.set(4,2,Material.STONE); w.set(3,3,Material.STONE); });
            for(int i=0;i<20;i++) s.tick();
            var body=s.physics().body(1); results.add(body.fragment().descriptor().cells()+":"+body.xCells()+":"+body.yCells()+":"+body.angleRadians()+":"+body.velocityYMetresPerSecond()+":"+s.automatic().diagnostics());
        }
        assertEquals(results.get(0),results.get(1));
    }
    @Test void optInStructuralBreakCanDetachWithoutStaleLabels() {
        var scene=miningsim.structural.RockReferenceScenes.cantilever(12);
        var structural=new miningsim.app.StructuralSimulationController(scene);
        var s=new InteractiveSession(scene.world(),structural,false,true);
        for(int i=0;i<200 && s.automatic().diagnostics().total()==0;i++) s.tick();
        assertTrue(s.automatic().diagnostics().total()>0,"no disconnected component detached");
        assertFalse(s.physics().bodies().isEmpty());
    }
    @Test void largeFragmentReachesFloorWithoutPartialFailure() {
        var w=new World(40,40,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()-> { for(int i=0;i<1024;i++) w.set(2+i%32,2+i/32,Material.STONE); });
        for(int i=0;i<100;i++) { s.tick(); assertFalse(s.blocked(),s.failure()); assertNull(s.failure()); }
        var body=s.physics().body(1); assertNotNull(body); assertTrue(body.yCells()>body.geometry().initialCenter().y());
        assertTrue(body.geometry().bounds(body.xCells(),body.yCells(),body.angleRadians()).maxY()>38.5);
        assertEquals(1024,body.fragment().descriptor().cellCount());
    }
    @Test void committedAutomaticExtractionSurvivesLaterPhysicsFailure() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()->w.set(3,2,Material.STONE)); for(int i=0;i<8;i++) s.tick();
        s.physics().body(1).setVelocity(Double.MAX_VALUE,0,0);
        s.enqueue(()->w.set(12,2,Material.STONE)); for(int i=0;i<12;i++) s.tick();
        assertTrue(s.blocked()); assertEquals(2,s.physics().bodies().size()); assertEquals(Material.AIR,w.get(12,2));
        assertEquals(5_000_000,s.physics().body(1).fragment().massMilligrams()+s.physics().body(2).fragment().massMilligrams());
        assertTrue(s.occupancy().snapshot().occupied(12,2));
    }
    @Test void unrelatedWaterEditsDoNotPostponeStoneQuietInterval() {
        var w=new World(16,16,World.Mode.BEDROCK_ENABLED); var s=new InteractiveSession(w,null);
        s.enqueue(()->w.set(3,2,Material.STONE));
        for(int i=0;i<6;i++) { int x=8+i; s.enqueue(()->w.set(x,2,Material.WATER)); s.tick(); assertTrue(s.physics().bodies().isEmpty()); }
        s.tick(); assertEquals(1,s.physics().bodies().size());
    }
}
