package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.Brush;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.simulation.Simulation;
import org.junit.jupiter.api.Test;

class SequentialSandAfterCollapseTest {
    private static int count(World world,Material material) {
        int result=0;
        for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) if(world.get(x,y)==material) result++;
        return result;
    }
    private static String replay() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        for(int stroke=0;stroke<3;stroke++) {
            int x=stroke==1?10:24;
            session.enqueue(()->Brush.paintWorld(scene.world(),x,4,3,Material.SAND,scene.occupancy().snapshot()));
            for(int i=0;i<100;i++) { session.tick(); assertNull(session.failure()); }
        }
        var output=new StringBuilder();
        for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) output.append((char)('A'+scene.world().get(x,y).ordinal()));
        output.append(scene.physics().bodies().stream().map(RigidBody::state).toList());
        output.append(session.coupled().diagnostics()); return output.toString();
    }
    @Test void sequentialStrokesReplayDeterministically() { assertEquals(replay(),replay()); }
    @Test void sandboxCutCreatesBodiesAndRepeatedSandKeepsFalling() {
        var world=new World(64,40,World.Mode.BEDROCK_ENABLED);
        var session=new miningsim.app.InteractiveSession(world,null);
        int[] centers={18,24,30};
        session.enqueue(()->{
            for(int x:centers) {
                for(int y=20;y<=22;y++) for(int xx=x-1;xx<=x+1;xx++) world.set(xx,y,Material.STONE);
                for(int y=23;y<39;y++) world.set(x,y,Material.STONE);
            }
        });
        for(int i=0;i<12;i++) { session.tick(); assertNull(session.failure()); }
        assertTrue(session.physics().bodies().isEmpty());
        session.enqueue(()->{ for(int x:centers) world.set(x,23,Material.AIR); });
        for(int i=0;i<400;i++) { session.tick(); assertNull(session.failure(),"settling tick "+i); }
        assertEquals(3,session.physics().bodies().size());
        assertEquals(3,session.physics().interactionDiagnostics().sleepingBodies());
        for(int stroke=0;stroke<3;stroke++) {
            int x=stroke==1?8:24;
            session.enqueue(()->Brush.paintWorld(world,x,4,2,Material.SAND,session.occupancy().snapshot()));
            session.tick(); assertNull(session.failure()); assertEquals(Material.AIR,world.get(x,2));
            for(int i=0;i<70;i++) { session.tick(); assertNull(session.failure(),"stroke "+stroke+" tick "+i); }
        }
        assertEquals(39,count(world,Material.SAND));
    }
    @Test void sleepingStackAndRepeatedCellularStrokesConserveMatter() {
        for(var material:new Material[]{Material.SAND,Material.WATER,Material.GRAVEL,Material.SOIL}) {
            var scene=RestingStackBenchmark.scene(); var session=scene.session();
            for(int i=0;i<500;i++) { session.tick(); assertNull(session.failure()); }
            assertEquals(3,scene.physics().interactionDiagnostics().sleepingBodies());
            var states=scene.physics().bodies().stream().map(RigidBody::state).toList();
            for(int stroke=0;stroke<3;stroke++) {
                int x=stroke==1?10:24; int previous=count(scene.world(),material);
                long ticks=session.coupled().diagnostics().ticks();
                session.enqueue(()->Brush.paintWorld(scene.world(),x,4,2,material,scene.occupancy().snapshot()));
                for(int tick=0;tick<60;tick++) {
                    session.tick(); assertNull(session.failure(),material+" stroke="+stroke+" tick="+tick);
                    assertEquals(ticks+tick+1,session.coupled().diagnostics().ticks());
                    assertEquals(CoupledSimulationStep.CellularOutcome.COMPLETED,session.coupled().diagnostics().cellularOutcome());
                    assertEquals(previous+13,count(scene.world(),material));
                    assertEquals(states,scene.physics().bodies().stream().map(RigidBody::state).toList());
                    for(int y=0;y<scene.world().height();y++) for(int xx=0;xx<scene.world().width();xx++)
                        if(scene.world().get(xx,y)==material) assertFalse(scene.occupancy().snapshot().occupied(xx,y));
                    if(tick==0) assertEquals(Material.AIR,scene.world().get(x,2),material+" did not start falling");
                }
            }
            for(int x=0;x<scene.world().width();x++) assertEquals(Material.BEDROCK,scene.world().get(x,scene.world().height()-1));
        }
    }
    @Test void activeAndFullScanBothReactivateRepeatedStrokes() {
        for(var mode:new Simulation.Mode[]{Simulation.Mode.FULL_SCAN,Simulation.Mode.ACTIVE_REGIONS}) {
            var world=new World(80,80,World.Mode.BEDROCK_ENABLED);
            var simulation=new Simulation(world,12345,mode,false);
            for(int i=0;i<5;i++) simulation.step();
            for(int stroke=0;stroke<3;stroke++) {
                int x=stroke*25+10;
                Brush.paintWorld(world,x,5,2,Material.SAND,null);
                simulation.step(); assertEquals(Material.AIR,world.get(x,3),mode+" stroke="+stroke);
                for(int i=0;i<5;i++) simulation.step();
            }
            assertEquals(39,count(world,Material.SAND));
        }
    }
    @Test void fullAndActiveScansMatchForRepeatedFreeFall() {
        var full=new World(80,80,World.Mode.BEDROCK_ENABLED); var active=new World(80,80,World.Mode.BEDROCK_ENABLED);
        var first=new Simulation(full,12345,Simulation.Mode.FULL_SCAN,false);
        var second=new Simulation(active,12345,Simulation.Mode.ACTIVE_REGIONS,false);
        for(int i=0;i<3;i++) { first.step(); second.step(); }
        for(int stroke=0;stroke<3;stroke++) {
            int x=10+25*stroke; full.set(x,3,Material.SAND); active.set(x,3,Material.SAND);
            for(int tick=0;tick<10;tick++) {
                first.step(); second.step();
                for(int y=0;y<80;y++) for(int xx=0;xx<80;xx++) assertEquals(full.get(xx,y),active.get(xx,y),"stroke="+stroke+" tick="+tick);
            }
        }
    }
    @Test void drawingAfterWakeAndAfterFreshResetStillFalls() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        scene.world().set(1,1,Material.WOOD); session.tick();
        assertTrue(scene.physics().interactionDiagnostics().wokenBodies()>0);
        session.enqueue(()->Brush.paintWorld(scene.world(),10,4,2,Material.SAND,scene.occupancy().snapshot()));
        session.tick(); assertNull(session.failure()); assertEquals(Material.AIR,scene.world().get(10,2));
        session.retire();
        var fresh=new miningsim.app.InteractiveSession(new World(64,40,World.Mode.BEDROCK_ENABLED),null);
        fresh.enqueue(()->Brush.paintWorld(fresh.world(),10,4,2,Material.SAND,fresh.occupancy().snapshot()));
        fresh.tick(); assertNull(fresh.failure()); assertEquals(Material.AIR,fresh.world().get(10,2));
    }
    @Test void retryAfterRejectedTickRestoresCellularProgress() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        scene.physics().setTestCheckpoint(point->{ if(point==RigidBodySystem.Checkpoint.AFTER_PAIR_QUERY) throw new IllegalStateException("injected budget exceeded"); });
        // A world obstacle edit wakes the sleeping stack and reaches the checkpoint.
        scene.world().set(1,1,Material.WOOD);
        for(int i=0;i<3;i++) session.tick();
        assertTrue(session.blocked());
        scene.physics().setTestCheckpoint(null); session.resume();
        session.enqueue(()->Brush.paintWorld(scene.world(),10,4,2,Material.SAND,scene.occupancy().snapshot()));
        session.tick(); assertFalse(session.blocked()); assertNull(session.failure());
        assertEquals(Material.AIR,scene.world().get(10,2));
    }
    @Test void rigidRevisionTracksOnlyPotentialObstacleChanges() {
        var world=new World(16,16,World.Mode.BEDROCK_ENABLED); long revision=world.rigidTerrainRevision();
        for(var material:new Material[]{Material.SAND,Material.WATER,Material.GRAVEL,Material.SOIL,Material.AIR}) {
            world.set(3,3,material); assertEquals(revision,world.rigidTerrainRevision());
        }
        assertEquals(World.Mutation.CHANGED,world.set(3,3,Material.STONE)); assertEquals(++revision,world.rigidTerrainRevision());
        assertEquals(World.Mutation.NO_OP,world.set(3,3,Material.STONE)); assertEquals(revision,world.rigidTerrainRevision());
        assertEquals(World.Mutation.REJECTED,world.set(3,15,Material.AIR)); assertEquals(revision,world.rigidTerrainRevision());
        assertEquals(World.BatchResult.CHANGED,world.replaceAllIfMatches(java.util.List.of(
                new World.CellChange(3,3,Material.STONE,Material.AIR),new World.CellChange(4,4,Material.AIR,Material.WOOD))));
        assertEquals(++revision,world.rigidTerrainRevision());
        assertEquals(World.BatchResult.REJECTED,world.replaceAllIfMatches(java.util.List.of(new World.CellChange(4,4,Material.STONE,Material.AIR))));
        assertEquals(revision,world.rigidTerrainRevision());
        assertEquals(World.BatchResult.CHANGED,world.replaceAllIfMatches(java.util.List.of(new World.CellChange(7,7,Material.AIR,Material.SAND))));
        assertEquals(revision,world.rigidTerrainRevision());
        world.clear(); assertEquals(++revision,world.rigidTerrainRevision());
    }
    @Test void legacySolidGranularEditsStillWakeBodies() {
        var world=new World(16,16);
        for(int x=0;x<16;x++) world.set(x,10,Material.STONE);
        var registry=new miningsim.fragment.FragmentRegistry(1);
        var shape=new miningsim.resource.FragmentDescriptor(miningsim.resource.SubstanceRegistry.reference().get(miningsim.resource.SubstanceRegistry.STONE),java.util.List.of(new miningsim.resource.FragmentDescriptor.Cell(0,0)));
        registry.insert(shape,4,9,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000),StaticContactSolver.Config.defaults());
        var system=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        system.synchronize(); for(int i=0;i<100;i++) system.step(); assertTrue(system.body(1).sleeping());
        world.set(12,4,Material.SAND); system.step();
        assertTrue(system.interactionDiagnostics().wokenBodies()>0);
    }
    @Test void laterSandDoesNotHangWhenSimulationTicksContinue() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        session.enqueue(()->Brush.paintWorld(scene.world(),24,4,3,Material.SAND,scene.occupancy().snapshot()));
        for(int i=0;i<100;i++) session.tick();
        session.enqueue(()->Brush.paintWorld(scene.world(),10,4,3,Material.SAND,scene.occupancy().snapshot()));
        session.tick();
        // The top cell has no particle above it that could refill it in this tick.
        assertEquals(Material.AIR,scene.world().get(10,1),"second stroke stuck; blocked="+session.blocked()+" failure="+session.failure());
    }
    @Test void repeatedSandBrushesContinueFallingAboveSleepingStack() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) { session.tick(); assertNull(session.failure()); }
        assertEquals(3,scene.physics().interactionDiagnostics().sleepingBodies());
        for(int stroke=0;stroke<3;stroke++) {
            var snapshot=scene.occupancy().snapshot();
            int x=stroke==1?10:24,y=4;
            session.enqueue(()->Brush.paintWorld(scene.world(),x,y,3,Material.SAND,snapshot));
            session.tick();
            assertNull(session.failure(),"stroke="+stroke+" first tick");
            for(int tick=0;tick<100;tick++) {
                session.tick();
                assertNull(session.failure(),"stroke="+stroke+" tick="+tick+" blocked="+session.blocked());
            }
        }
    }
}
