package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class HeldSolverTest {
    private static final FragmentDescriptor CELL=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
    private record Scene(World world,FragmentRegistry registry,RigidBodySystem physics) { }
    private static Scene scene(int... coords) {
        return scene(CELL,coords);
    }
    private static Scene scene(FragmentDescriptor first,int... coords) {
        var world=new World(50,50,World.Mode.BEDROCK_ENABLED); var registry=new FragmentRegistry(20);
        for(int i=0;i<coords.length;i+=2) registry.insert(i==0?first:CELL,coords[i],coords[i+1],0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,100000,1000000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var query=new BodyCollisionQuery(new BodyCollisionQuery.Limits(100000,1000000,4096,4096));
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),query,new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); return new Scene(world,registry,physics);
    }
    @Test void heldRemainsFixedAndInOccupancy() {
        var s=scene(10,10); var body=s.physics.body(1); body.setVelocity(3,4,8);
        double x=body.xCells(),y=body.yCells(),angle=body.angleRadians(),mass=body.geometry().massKg(),inertia=body.geometry().inertiaKgMetresSquared();
        long revision=s.world.mutationRevision();
        for(int i=0;i<100;i++) s.physics.step(Set.of(1L));
        assertEquals(x,body.xCells()); assertEquals(y,body.yCells()); assertEquals(angle,body.angleRadians());
        assertEquals(0,body.velocityXMetresPerSecond()); assertEquals(0,body.velocityYMetresPerSecond()); assertEquals(0,body.angularVelocityRadiansPerSecond());
        assertFalse(body.sleeping()); assertSame(CELL,body.fragment().descriptor()); assertEquals(mass,body.geometry().massKg()); assertEquals(inertia,body.geometry().inertiaKgMetresSquared());
        assertEquals(revision,s.world.mutationRevision());
        var index=new RigidBodyOccupancyIndex(s.world,RigidBodyOccupancyIndex.Limits.defaults());
        assertArrayEquals(new long[]{1},index.rebuild(s.physics,1).bodyIdsAt(10,10));
    }
    private static String falling() {
        var platform=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0)));
        var s=scene(platform,10,15,10,12,11,12); var held=s.physics.body(1); var before=held.state();
        for(int i=0;i<90;i++) s.physics.step(Set.of(1L));
        assertEquals(before.x(),held.xCells()); assertEquals(before.y(),held.yCells());
        assertTrue(s.physics.body(2).yCells()>12.5 && s.physics.body(2).yCells()<15); assertTrue(s.physics.body(3).yCells()>12.5 && s.physics.body(3).yCells()<15);
        return s.physics.bodies().stream().map(b->b.state().toString()).toList().toString();
    }
    @Test void supportsActiveAndReplays() { assertEquals(falling(),falling()); }
    @Test void sideImpactAndFriction() {
        var s=scene(15,15,12,15); var held=s.physics.body(1); var active=s.physics.body(2);
        active.setVelocity(2,0,0); double x=held.xCells();
        for(int i=0;i<12;i++) s.physics.step(Set.of(1L));
        assertEquals(x,held.xCells()); assertTrue(active.xCells()<held.xCells()); assertTrue(active.velocityXMetresPerSecond()<2);
    }
    @Test void heldHeldAndSleepingContactsRemainStable() {
        var s=scene(10,10,11,10,20,10); var a=s.physics.body(1); var b=s.physics.body(2); var sleeper=s.physics.body(3);
        var old=sleeper.state(); sleeper.restore(new RigidBody.State(old.x(),old.y(),old.angle(),0,0,0,true,0));
        for(int i=0;i<10;i++) s.physics.step(Set.of(1L,2L));
        assertEquals(10.5,a.xCells()); assertEquals(11.5,b.xCells()); assertTrue(sleeper.sleeping());
    }
    @Test void approachingActiveWakesSleeperWithoutWakingHeld() {
        var s=scene(10,10,20,10,22,10); var held=s.physics.body(1); var sleeper=s.physics.body(2); var incoming=s.physics.body(3);
        var old=sleeper.state(); sleeper.restore(new RigidBody.State(old.x(),old.y(),old.angle(),0,0,0,true,0)); incoming.setVelocity(-8,0,0);
        s.physics.step(Set.of(1L)); assertFalse(sleeper.sleeping()); assertEquals(10.5,held.xCells());
    }
    @Test void injectedFailureRestoresBodyAndDiagnostics() {
        var s=scene(10,10,15,10); var a=s.physics.body(1); a.setVelocity(1,2,3); var before=s.physics.bodies().stream().map(RigidBody::state).toList();
        var diagnostics=s.physics.interactionDiagnostics();
        s.physics.setTestCheckpoint(p->{if(p==RigidBodySystem.Checkpoint.AFTER_PAIR_QUERY) throw new IllegalStateException("injected");});
        assertThrows(IllegalStateException.class,()->s.physics.step(Set.of(1L)));
        assertEquals(before,s.physics.bodies().stream().map(RigidBody::state).toList()); assertEquals(diagnostics,s.physics.interactionDiagnostics());
    }
    @Test void emptySetMatchesOrdinaryStep() {
        var a=scene(10,10,12,10); var b=scene(10,10,12,10);
        for(int i=0;i<100;i++) { a.physics.step(); b.physics.step(Set.of()); }
        assertEquals(a.physics.bodies().stream().map(RigidBody::state).toList(),b.physics.bodies().stream().map(RigidBody::state).toList());
    }
    @Test void residualPenetrationBoundIsEnforcedAndRollsBack() {
        var s=scene(10,10,11,10); var b=s.physics.body(2); var original=b.state();
        b.restore(new RigidBody.State(original.x()-0.2,original.y(),original.angle(),0,0,0,false,0));
        var before=s.physics.bodies().stream().map(RigidBody::state).toList();
        assertThrows(IllegalStateException.class,()->s.physics.step(Set.of(1L,2L)));
        assertEquals(before,s.physics.bodies().stream().map(RigidBody::state).toList());
        b.restore(new RigidBody.State(original.x()-0.02,original.y(),original.angle(),0,0,0,true,0));
        s.physics.step(Set.of(1L)); assertTrue(b.sleeping());
        assertTrue(s.physics.interactionDiagnostics().maxPenetrationCells()<=RigidBodySystem.MAX_HELD_PENETRATION_CELLS);
    }
    @Test void contactSolverTransfersNormalAndFrictionOnlyToActive() {
        var s=scene(10,10,11,10); var held=s.physics.body(1); var active=s.physics.body(2);
        active.setVelocity(-2,1,0);
        var contact=new BodyCollisionQuery.Contact(1,2,1,0,0.01,11,10.5);
        var solver=new BodyContactSolver(BodyContactSolver.Config.defaults());
        var prepared=solver.prepare(List.of(contact),java.util.Map.of(1L,held,2L,active),Set.of(1L));
        for(int i=0;i<8;i++) prepared.iterate();
        assertEquals(0,held.velocityXMetresPerSecond()); assertEquals(0,held.velocityYMetresPerSecond()); assertEquals(0,held.angularVelocityRadiansPerSecond());
        assertTrue(active.velocityXMetresPerSecond()>-2); assertTrue(active.velocityYMetresPerSecond()<1);
    }
}
