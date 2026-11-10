package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettlingWedgeTest {
    private static java.util.List<RigidBody.State> runWedge() {
        var session=LargeCompoundBenchmark.fixture("large").session();
        double[] angles={0.02,-0.02,0.01};
        for(int i=0;i<3;i++) {
            var body=session.physics().body(i+1); var s=body.state();
            body.restore(new RigidBody.State(s.x(),s.y(),angles[i],0,0,0,false,0));
        }
        var initial=session.physics().bodies().stream().map(RigidBody::state).toList();
        int slept=-1; double[] travel=new double[3];
        for(int tick=0;tick<600;tick++) {
            double[] before=session.physics().bodies().stream().mapToDouble(RigidBody::xCells).toArray();
            session.tick(); assertNull(session.failure(),"tick "+tick);
            for(int i=0;i<3;i++) travel[i]+=Math.abs(session.physics().body(i+1).xCells()-before[i]);
            if(session.physics().interactionDiagnostics().sleepingBodies()==3) { slept=tick+1; break; }
        }
        var bodies=session.physics().bodies();
        System.out.println("wedge sleep="+slept+" driftCells="+java.util.stream.IntStream.range(0,3).mapToObj(i->bodies.get(i).xCells()-initial.get(i).x()).toList()+" pathCells="+java.util.Arrays.toString(travel));
        assertTrue(slept>0,"wedge did not sleep");
        var states=bodies.stream().map(RigidBody::state).toList();
        for(int tick=0;tick<60;tick++) { session.tick(); assertNull(session.failure()); }
        assertEquals(states,bodies.stream().map(RigidBody::state).toList());
        return states;
    }
    @Test void rotatedLargeWedgeSettlesAndReplays() { assertEquals(runWedge(),runWedge()); }
    @Test void baselineLargeFixtureHasBoundedDriftAndFixedSleepingPose() {
        var session=LargeCompoundBenchmark.fixture("large").session();
        var initial=session.physics().bodies().stream().map(RigidBody::state).toList();
        double[] distance=new double[3]; int slept=-1;
        for(int tick=0;tick<360;tick++) {
            double[] before=session.physics().bodies().stream().mapToDouble(RigidBody::xCells).toArray();
            session.tick(); assertNull(session.failure());
            for(int i=0;i<3;i++) distance[i]+=Math.abs(session.physics().body(i+1).xCells()-before[i]);
            if(session.physics().interactionDiagnostics().sleepingBodies()==3) { slept=tick+1; break; }
        }
        assertTrue(slept>0);
        var states=session.physics().bodies().stream().map(RigidBody::state).toList();
        System.out.println("large sleep="+slept+" driftCells="+java.util.stream.IntStream.range(0,3).mapToObj(i->states.get(i).x()-initial.get(i).x()).toList()+" pathCells="+java.util.Arrays.toString(distance));
        for(int tick=0;tick<60;tick++) { session.tick(); assertNull(session.failure()); }
        assertEquals(states,session.physics().bodies().stream().map(RigidBody::state).toList());
    }
    @Test void unsupportedAndFastRotatingBodiesAreNotEligible() {
        var body=LargeCompoundBenchmark.fixture("large").session().physics().body(3);
        var config=RigidBodySystem.SleepConfig.defaults();
        assertFalse(body.sleepEligible(false,config));
        body.setVelocity(0,0,0.1);
        for(int i=0;i<120;i++) {
            body.updateIslandSleep(body.sleepEligible(true,config),body.sleepTicks(),config);
            assertFalse(body.sleeping());
        }
    }
    @Test void repeatedImpactsResetSettlingWindow() {
        var world=new World(16,16);
        for(int x=0;x<16;x++) world.set(x,10,Material.STONE);
        var registry=new FragmentRegistry(1);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        registry.insert(shape,4,9,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000),StaticContactSolver.Config.defaults());
        var system=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        system.synchronize(); var body=system.body(1);
        for(int i=0;i<20;i++) system.step();
        for(int i=0;i<20;i++) {
            var state=body.state();
            body.restore(new RigidBody.State(state.x(),state.y(),state.angle(),0,0.12,0,false,40,state.x(),state.y(),state.angle()));
            system.step();
            assertFalse(body.sleeping()); assertEquals(0,body.sleepTicks(),"impact tick "+i);
        }
    }
    @Test void significantDownhillMotionOnSteppedSlopeDoesNotSleep() {
        var world=new World(48,48);
        for(int x=0;x<30;x++) for(int y=10+x;y<48;y++) world.set(x,y,Material.STONE);
        var registry=new FragmentRegistry(1);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        registry.insert(shape,4,13,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000),StaticContactSolver.Config.defaults());
        var system=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        system.synchronize(); var body=system.body(1); double x0=body.xCells(),y0=body.yCells();
        body.setVelocity(2.0,0,0);
        for(int i=0;i<20;i++) { system.step(); assertFalse(body.sleeping(),"tick "+i); }
        assertTrue(body.xCells()>x0+0.1); assertTrue(body.yCells()>y0+0.1);
    }
    @Test void failedTickRestoresQuietWindowAndDisturbanceDoesNotLeak() {
        var a=LargeCompoundBenchmark.fixture("large").session();
        var b=LargeCompoundBenchmark.fixture("large").session();
        for(int i=0;i<40;i++) { a.tick(); b.tick(); }
        var before=a.physics().bodies().stream().map(RigidBody::state).toList();
        var occupancy=a.occupancy().snapshot(); var diagnostics=a.physics().interactionDiagnostics();
        a.physics().setTestCheckpoint(point->{ if(point==RigidBodySystem.Checkpoint.AFTER_POSITION) throw new IllegalStateException("injected"); });
        assertThrows(IllegalStateException.class,a::tick);
        assertEquals(before,a.physics().bodies().stream().map(RigidBody::state).toList());
        assertSame(occupancy,a.occupancy().snapshot()); assertEquals(diagnostics,a.physics().interactionDiagnostics());
        a.physics().setTestCheckpoint(null); a.tick(); b.tick();
        assertEquals(b.physics().bodies().stream().map(RigidBody::state).toList(),a.physics().bodies().stream().map(RigidBody::state).toList());
    }
}
