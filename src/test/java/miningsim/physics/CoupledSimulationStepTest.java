package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.world.*;
import org.junit.jupiter.api.Test;

class CoupledSimulationStepTest {
    private FragmentRegistry registry(int x,int y) {
        var r=new FragmentRegistry(1); r.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0))),x,y,0); return r;
    }
    private RigidBodySystem system(World w,FragmentRegistry r) {
        var query=new StaticCollisionQuery(w,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(query,StaticContactSolver.Config.defaults())); s.synchronize(); return s;
    }
    @Test void bodyMovesThroughAndDisplacesEachMaterial() {
        for(var material:List.of(Material.SAND,Material.GRAVEL,Material.SOIL,Material.WATER)) {
            var w=new World(8,8); w.set(2,2,material); var r=registry(2,1); var physics=system(w,r);
            var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
            physics.body(1).setVelocity(0,3,0);
            var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults()); coupled.step();
            assertEquals(Material.AIR,w.get(2,2));
            assertEquals(material,w.get(material==Material.WATER?1:2,material==Material.WATER?2:3));
            assertTrue(index.snapshot().occupied(2,2)); assertEquals(1,w.occupiedCount()); assertEquals(2_500_000,r.totalMassMilligrams());
        }
    }
    @Test void noSpaceRollsBackPhysicsWorldAndOccupancy() {
        var w=new World(3,3); w.set(1,1,Material.SAND);
        for(int[] p:new int[][]{{0,1},{2,1},{1,0},{1,2}}) w.set(p[0],p[1],Material.STONE);
        var physics=system(w,registry(1,1)); var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults());
        var previous=index.rebuild(physics,0); var body=physics.body(1); var state=body.state(); long revision=w.mutationRevision();
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults());
        assertThrows(IllegalStateException.class,coupled::step); assertEquals(CoupledSimulationStep.FailureStage.PLANNING,coupled.lastFailureStage()); assertEquals(state,body.state()); assertSame(previous,index.snapshot());
        assertEquals(revision,w.mutationRevision()); assertEquals(Material.SAND,w.get(1,1)); assertEquals(0,coupled.diagnostics().ticks());
    }
    @Test void callbackObservesCompleteWorldAndOccupancy() {
        var w=new World(8,8); w.set(2,2,Material.SAND); var physics=system(w,registry(2,1));
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        int[] calls={0}; w.setChangeSink(new World.ChangeSink() {
            public void changed(int x,int y,byte before,byte after) { fail("per-cell callback"); }
            public void invalidated() { }
            public void changedBatch(List<World.CellChange> changes) {
                calls[0]++; assertEquals(Material.AIR,w.get(2,2)); assertEquals(Material.SAND,w.get(2,3));
                assertTrue(index.snapshot().occupied(2,2));
            }
        });
        physics.body(1).setVelocity(0,3,0); new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults()).step();
        assertEquals(1,calls[0]);
    }
    @Test void fastCrossingFailsConservativelyWithoutChangingOwners() {
        var w=new World(20,20); w.set(3,5,Material.SAND); var physics=system(w,registry(3,1));
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var old=index.rebuild(physics,0);
        var body=physics.body(1); body.setVelocity(0,60,0); var state=body.state(); long revision=w.mutationRevision();
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults());
        var error=assertThrows(IllegalStateException.class,coupled::step);
        assertTrue(error.getMessage().contains("swept")); assertEquals(CoupledSimulationStep.FailureStage.SWEEP,coupled.lastFailureStage()); assertEquals(state,body.state()); assertSame(old,index.snapshot());
        assertEquals(Material.SAND,w.get(3,5)); assertEquals(revision,w.mutationRevision());
    }
    @Test void occupancyAndPhysicsFailuresRollback() {
        var w=new World(8,8); var physics=system(w,registry(2,1));
        var index=new RigidBodyOccupancyIndex(w,new RigidBodyOccupancyIndex.Limits(0,10,10,10,10,10,10));
        var old=index.snapshot(); var body=physics.body(1); var before=body.state();
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults());
        assertThrows(IllegalStateException.class,coupled::step); assertEquals(CoupledSimulationStep.FailureStage.OCCUPANCY,coupled.lastFailureStage()); assertEquals(before,body.state()); assertSame(old,index.snapshot());
        body.setVelocity(Double.MAX_VALUE,0,0); var bad=body.state();
        assertThrows(IllegalStateException.class,coupled::step); assertEquals(CoupledSimulationStep.FailureStage.PHYSICS,coupled.lastFailureStage()); assertEquals(bad,body.state()); assertSame(old,index.snapshot());
    }
    @Test void rejectedWorldBatchRollsBackAndRetrySucceeds() {
        var w=new World(8,8); w.set(2,2,Material.SAND); var physics=system(w,registry(2,1));
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var old=index.rebuild(physics,0);
        var flag=new boolean[]{false}; w.setChangeSink(new World.ChangeSink() {
            public void changed(int x,int y,byte a,byte b) { }
            public void invalidated() { }
            public boolean allowBatch() { return flag[0]; }
        });
        var body=physics.body(1); body.setVelocity(0,3,0); var state=body.state();
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults());
        assertThrows(IllegalStateException.class,coupled::step); assertEquals(CoupledSimulationStep.FailureStage.WORLD_COMMIT,coupled.lastFailureStage()); assertEquals(state,body.state()); assertSame(old,index.snapshot());
        assertEquals(Material.SAND,w.get(2,2)); flag[0]=true; coupled.step();
        assertEquals(Material.AIR,w.get(2,2)); assertNull(coupled.lastFailure());
    }
    @Test void concaveVoidDoesNotBecomeSolidSweptRectangle() {
        var w=new World(10,10); w.set(4,4,Material.SAND);
        var r=new FragmentRegistry(1);
        r.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),
                List.of(new Cell(0,0),new Cell(1,0),new Cell(0,1))),3,3,0);
        var physics=system(w,r); var body=physics.body(1); body.setVelocity(0,-0.3,0); var before=body.state();
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults()); coupled.step();
        assertTrue(body.yCells()<before.y()); assertFalse(index.snapshot().occupied(4,4));
        assertEquals(Material.SAND,w.get(4,4)); assertEquals(1,w.occupiedCount()); assertEquals(0,coupled.diagnostics().sand());
        assertEquals(7_500_000,r.totalMassMilligrams());
    }
    @Test void denseSandCanMoveIntoReachableSpace() {
        var w=new World(64,64); for(int y=16;y<28;y++) for(int x=16;x<28;x++) w.set(x,y,Material.SAND);
        var r=registry(20,15); var physics=system(w,r); physics.body(1).setVelocity(0,3,0);
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults()); coupled.step();
        assertEquals(144,w.occupiedCount()); assertEquals(Material.AIR,w.get(20,16));
        assertTrue(index.snapshot().occupied(20,16)); assertEquals(1,coupled.diagnostics().sand());
        assertEquals(2_500_000,r.totalMassMilligrams());
    }
    @Test void replayMatchesWorldPhysicsOccupancyAndDiagnostics() {
        var worlds=List.of(new World(8,8),new World(8,8));
        var states=new java.util.ArrayList<RigidBody.State>(); var diagnostics=new java.util.ArrayList<CoupledSimulationStep.Diagnostics>();
        var occupied=new java.util.ArrayList<java.util.List<String>>();
        for(var w:worlds) {
            w.set(2,2,Material.SAND); w.set(5,5,Material.WATER);
            var physics=system(w,registry(2,1)); physics.body(1).setVelocity(0,3,0);
            var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
            var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults()); coupled.step();
            states.add(physics.body(1).state()); diagnostics.add(coupled.diagnostics());
            var cells=new java.util.ArrayList<String>(); index.snapshot().forEachCell((x,y)->cells.add(x+","+y+":"+java.util.Arrays.toString(index.snapshot().bodyIdsAt(x,y)))); occupied.add(cells);
        }
        assertEquals(states.get(0),states.get(1)); assertEquals(diagnostics.get(0),diagnostics.get(1)); assertEquals(occupied.get(0),occupied.get(1));
        for(int y=0;y<8;y++) for(int x=0;x<8;x++) assertEquals(worlds.get(0).get(x,y),worlds.get(1).get(x,y));
    }
    @Test void sealedStaticWallCannotBeCrossed() {
        for(var material:List.of(Material.STONE,Material.WOOD,Material.BEDROCK)) {
            var w=new World(12,12,World.Mode.BEDROCK_ENABLED);
            // BEDROCK is provided by the world's immutable bottom boundary.
            int wallY=material==Material.BEDROCK?11:6;
            if(material!=Material.BEDROCK) for(int x=0;x<12;x++) w.set(x,wallY,material);
            var physics=system(w,registry(4,wallY-3)); var body=physics.body(1); body.setVelocity(0,30,0);
            var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var old=index.rebuild(physics,0);
            var before=body.state(); var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults());
            try { coupled.step(); assertTrue(body.yCells()<wallY+0.5); assertNotSame(old,index.snapshot()); }
            catch(IllegalStateException expected) { assertEquals(before,body.state()); assertSame(old,index.snapshot()); }
            for(int x=0;x<12;x++) assertEquals(material,w.get(x,wallY));
        }
    }
    @Test void worldValidationAndStalePublicationRejectBeforeWrites() {
        var w=new World(8,8); w.set(2,2,Material.SAND); var physics=system(w,registry(4,4));
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var old=index.rebuild(physics,0);
        var prepared=index.prepare(physics,1); long revision=w.mutationRevision();
        var mismatch=List.of(new World.CellChange(2,2,Material.WATER,Material.AIR));
        assertEquals(World.BatchResult.REJECTED,w.replaceAllAndPublishOccupancy(mismatch,prepared));
        assertSame(old,index.snapshot()); assertEquals(revision,w.mutationRevision()); assertEquals(Material.SAND,w.get(2,2));
        var stale=index.prepare(physics,1); var newer=index.rebuild(physics,2);
        var changes=List.of(new World.CellChange(2,2,Material.SAND,Material.AIR),new World.CellChange(2,3,Material.AIR,Material.SAND));
        assertEquals(World.BatchResult.REJECTED,w.replaceAllAndPublishOccupancy(changes,stale));
        assertSame(newer,index.snapshot()); assertEquals(revision,w.mutationRevision()); assertEquals(Material.SAND,w.get(2,2)); assertEquals(Material.AIR,w.get(2,3));
    }
    @Test void worldCommitPreparationExceptionRestoresPhysics() {
        var w=new World(8,8); w.set(2,2,Material.SAND); var physics=system(w,registry(2,1));
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var old=index.rebuild(physics,0);
        w.setChangeSink(new World.ChangeSink() {
            public void changed(int x,int y,byte a,byte b) { }
            public void invalidated() { }
            public boolean allowBatch() { throw new IllegalStateException("preflight failure"); }
        });
        physics.body(1).setVelocity(0,3,0); var state=physics.body(1).state(); var rev=w.mutationRevision();
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults());
        var failure=assertThrows(IllegalStateException.class,coupled::step);
        assertEquals("preflight failure",failure.getMessage()); assertEquals(CoupledSimulationStep.FailureStage.WORLD_COMMIT,coupled.lastFailureStage());
        assertEquals(state,physics.body(1).state()); assertSame(old,index.snapshot()); assertEquals(rev,w.mutationRevision()); assertEquals(Material.SAND,w.get(2,2));
    }
    @Test void motionNearWallSucceedsWithoutMovingWall() {
        var w=new World(10,10); for(int y=0;y<10;y++) w.set(5,y,Material.WOOD);
        var physics=system(w,registry(3,2)); var body=physics.body(1); body.setVelocity(0,1,0); var before=body.state();
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var old=index.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults()); coupled.step();
        assertTrue(body.yCells()>before.y()); assertNotSame(old,index.snapshot()); assertTrue(index.snapshot().occupied(3,2));
        assertEquals(10,w.occupiedCount()); for(int y=0;y<10;y++) assertEquals(Material.WOOD,w.get(5,y));
    }
    @Test void failedReplayHasSameReasonAndPreservesState() {
        var reasons=new java.util.ArrayList<String>();
        for(int run=0;run<2;run++) {
            var w=new World(3,3); w.set(1,1,Material.SAND);
            for(int[] p:new int[][]{{0,1},{2,1},{1,0},{1,2}}) w.set(p[0],p[1],Material.STONE);
            var physics=system(w,registry(1,1)); var body=physics.body(1); var before=body.state();
            var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var old=index.rebuild(physics,0);
            var coupled=new CoupledSimulationStep(w,physics,index,CellularDisplacementPlanner.Limits.defaults());
            var error=assertThrows(IllegalStateException.class,coupled::step); reasons.add(error.getMessage());
            assertEquals(CoupledSimulationStep.FailureStage.PLANNING,coupled.lastFailureStage());
            assertEquals(before,body.state()); assertSame(old,index.snapshot()); assertEquals(5,w.occupiedCount()); assertEquals(Material.SAND,w.get(1,1));
        }
        assertEquals(reasons.get(0),reasons.get(1));
    }
}
