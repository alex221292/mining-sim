package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class PhysicsRollbackTest {
    private static final FragmentDescriptor SHAPE = new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0)));
    private static final class Fixture {
        final World world = new World(16,16);
        final FragmentRegistry registry = new FragmentRegistry(4);
        final StaticContactSolver solver;
        final RigidBodySystem system;
        Fixture() {
            for (int x=0;x<16;x++) world.set(x,10,Material.STONE);
            registry.insert(SHAPE,4,9,0);
            solver = new StaticContactSolver(new StaticCollisionQuery(world,10000,100000),StaticContactSolver.Config.defaults());
            system = new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),
                    new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
            system.synchronize();
        }
        void sleep() { for(int i=0;i<100;i++) system.step(); assertTrue(system.body(1).sleeping()); }
    }
    private static void same(RigidBodySystem a, RigidBodySystem b) {
        assertEquals(a.bodies().size(),b.bodies().size());
        for(int i=0;i<a.bodies().size();i++) {
            assertEquals(a.bodies().get(i).id(),b.bodies().get(i).id());
            assertEquals(a.bodies().get(i).state(),b.bodies().get(i).state());
        }
        assertEquals(a.diagnostics(),b.diagnostics()); assertEquals(a.motionDiagnostics(),b.motionDiagnostics());
        assertEquals(a.interactionDiagnostics(),b.interactionDiagnostics());
    }
    @Test void worldRevisionIsNotConsumedAndRetryMatchesCleanExecution() {
        worldRevisionRetry(RigidBodySystem.Checkpoint.AFTER_WAKE);
        worldRevisionRetry(RigidBodySystem.Checkpoint.AFTER_PUBLICATION);
    }
    private void worldRevisionRetry(RigidBodySystem.Checkpoint failurePoint) {
        var a=new Fixture(); var b=new Fixture(); a.sleep(); b.sleep(); same(a.system,b.system);
        var original=a.system.body(1); var saved=original.state(); var before=a.system.diagnostics();
        var motion=a.system.motionDiagnostics(); var interaction=a.system.interactionDiagnostics();
        a.world.set(4,10,Material.AIR); b.world.set(4,10,Material.AIR); long revision=a.world.mutationRevision();
        a.system.setTestCheckpoint(p -> { if(p==failurePoint) throw new IllegalStateException("after wake"); });
        var ex=assertThrows(IllegalStateException.class,a.system::step); assertEquals("after wake",ex.getMessage());
        assertSame(original,a.system.body(1)); assertEquals(saved,original.state()); assertTrue(original.sleeping());
        assertEquals(before,a.system.diagnostics()); assertEquals(motion,a.system.motionDiagnostics()); assertEquals(interaction,a.system.interactionDiagnostics());
        assertTrue(a.system.lastFailure().contains("after wake")); assertEquals(revision,a.world.mutationRevision());
        a.system.setTestCheckpoint(null); a.system.step(); b.system.step();
        assertFalse(original.sleeping()); assertNull(a.system.lastFailure()); same(a.system,b.system);
        for(int i=0;i<20;i++) { a.system.step(); b.system.step(); same(a.system,b.system); }
    }
    @Test void everyCheckpointRestoresSuccessfulDiagnosticsAndSolverCounters() {
        for(var point:RigidBodySystem.Checkpoint.values()) {
            var a=new Fixture(); var b=new Fixture(); a.system.step(); b.system.step();
            var body=a.system.body(1); var state=body.state(); var counter=a.solver.counterState();
            var error=new IllegalArgumentException("injected " + point);
            a.system.setTestCheckpoint(p -> { if(p==point) throw error; });
            assertSame(error,assertThrows(IllegalArgumentException.class,a.system::step));
            assertSame(body,a.system.body(1)); assertEquals(state,body.state());
            assertEquals(counter,a.solver.counterState()); same(a.system,b.system);
            a.system.setTestCheckpoint(null); a.system.step(); b.system.step(); same(a.system,b.system);
        }
    }
    @Test void registryAddAndRemoveDoNotPartiallySynchronize() {
        var f=new Fixture(); f.system.step(); var body=f.system.body(1); var state=body.state();
        f.registry.insert(SHAPE,7,2,0);
        f.system.setTestCheckpoint(p -> { if(p==RigidBodySystem.Checkpoint.AFTER_SYNC) throw new IllegalStateException("sync"); });
        assertThrows(IllegalStateException.class,f.system::step);
        assertSame(body,f.system.body(1)); assertEquals(state,body.state()); assertNull(f.system.body(2)); assertEquals(2,f.registry.size());
        f.system.setTestCheckpoint(null); f.system.step(); assertNotNull(f.system.body(2));
        var second=f.system.body(2); var secondState=second.state(); f.registry.remove(2);
        f.system.setTestCheckpoint(p -> { if(p==RigidBodySystem.Checkpoint.AFTER_SYNC) throw new IllegalStateException("sync"); });
        assertThrows(IllegalStateException.class,f.system::step);
        assertSame(second,f.system.body(2)); assertEquals(secondState,second.state()); assertEquals(1,f.registry.size());
        f.system.setTestCheckpoint(null); f.system.step(); assertNull(f.system.body(2));
    }
    @Test void failedPublicationDoesNotConsumeRegistryRemovalObservation() {
        var f=new Fixture(); f.registry.insert(SHAPE,4,8,0);
        for(int i=0;i<300;i++) f.system.step();
        assertTrue(f.system.body(1).sleeping() && f.system.body(2).sleeping());
        var removed=f.system.body(1); var survivor=f.system.body(2); var state=survivor.state();
        f.registry.remove(1);
        f.system.setTestCheckpoint(p -> { if(p==RigidBodySystem.Checkpoint.AFTER_PUBLICATION) throw new IllegalStateException("publication"); });
        assertThrows(IllegalStateException.class,f.system::step);
        assertSame(removed,f.system.body(1)); assertSame(survivor,f.system.body(2)); assertEquals(state,survivor.state());
        f.system.setTestCheckpoint(null); f.system.step();
        assertNull(f.system.body(1)); assertFalse(survivor.sleeping()); assertTrue(survivor.velocityYMetresPerSecond()>0);
    }
    @Test void emptySystemAndBeforeFirstTickDiagnostics() {
        var f=new Fixture(); f.registry.remove(1); f.system.synchronize();
        assertEquals(0,f.system.diagnostics().candidates()); assertEquals(0,f.system.motionDiagnostics().baseSubsteps());
        assertEquals(0,f.system.interactionDiagnostics().sharedMicrosteps()); assertNull(f.system.lastFailure());
        f.system.step(); assertTrue(f.system.bodies().isEmpty());
    }
    @Test void realBroadNarrowAndReductionFailuresRollBack() {
        var bar=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0),new Cell(0,1)));
        for(int mode=0;mode<3;mode++) {
            var registry=new FragmentRegistry(3); registry.insert(bar,2,2,0); registry.insert(bar,2,2,0);
            if(mode==0) registry.insert(SHAPE,8,8,0);
            var limits=switch(mode) {
                case 0 -> new BodyCollisionQuery.Limits(1,100,100,100);
                case 1 -> new BodyCollisionQuery.Limits(100,1,100,100);
                default -> new BodyCollisionQuery.Limits(100,100,100,1);
            };
            var s=new RigidBodySystem(registry,4,null,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(limits),new BodyContactSolver(BodyContactSolver.Config.defaults()));
            s.synchronize(); var first=s.body(1); var state=first.state();
            var ex=assertThrows(IllegalStateException.class,s::step);
            assertTrue(ex.getMessage().contains("limit")); assertSame(first,s.body(1)); assertEquals(state,first.state());
            assertEquals(0,s.interactionDiagnostics().sharedMicrosteps());
        }
    }
    @Test void staticQueryLimitFailureRestoresPublishedDiagnostics() {
        var w=new World(8,8); var r=new FragmentRegistry(1); r.insert(SHAPE,2,2,0);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,100,4),StaticContactSolver.Config.defaults()));
        s.synchronize(); s.step(); var before=s.diagnostics(); var motion=s.motionDiagnostics(); var state=s.body(1).state();
        w.set(2,2,Material.STONE);
        assertThrows(IllegalStateException.class,s::step);
        assertEquals(before,s.diagnostics()); assertEquals(motion,s.motionDiagnostics()); assertEquals(state,s.body(1).state());
        w.set(2,2,Material.AIR); s.step(); assertNull(s.lastFailure());
    }
    @Test void failureAfterBodyImpulseOrCorrectionRestoresBothBodies() {
        for(var point:List.of(RigidBodySystem.Checkpoint.AFTER_VELOCITY,RigidBodySystem.Checkpoint.AFTER_POSITION)) {
            var registry=new FragmentRegistry(2); registry.insert(SHAPE,2,2,0); registry.insert(SHAPE,3,2,0);
            var s=new RigidBodySystem(registry,4,null,RigidBodySystem.MotionConfig.defaults(),
                    new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,1000,100,100)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
            s.synchronize(); var a=s.body(1); var b=s.body(2); a.setVelocity(2,0,0); b.setVelocity(-2,0,0);
            var stateA=a.state(); var stateB=b.state();
            s.setTestCheckpoint(p -> { if(p==point) throw new IllegalStateException("after contact"); });
            assertThrows(IllegalStateException.class,s::step);
            assertSame(a,s.body(1)); assertSame(b,s.body(2)); assertEquals(stateA,a.state()); assertEquals(stateB,b.state());
        }
    }
}
