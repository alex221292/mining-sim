package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import org.junit.jupiter.api.Test;

class BodyCollisionQueryTest {
    private FragmentDescriptor shape(Cell... cells) {
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE), List.of(cells));
    }
    @Test void contactsAreIdOrderedAndUseActualCells() {
        var registry = new FragmentRegistry(3);
        registry.insert(shape(new Cell(0,0), new Cell(1,0), new Cell(0,1)), 2,2,0);
        registry.insert(shape(new Cell(0,0)), 3,3,0);
        var system = new RigidBodySystem(registry); system.synchronize();
        var query = new BodyCollisionQuery(new BodyCollisionQuery.Limits(10, 100, 100, 100));
        assertTrue(query.query(system.bodies()).contacts().isEmpty());
        system.body(2).setVelocity(-1, -1, 0); system.step();
        var result = query.query(system.bodies());
        assertFalse(result.contacts().isEmpty());
        assertEquals(1, result.contacts().getFirst().bodyA()); assertEquals(2, result.contacts().getFirst().bodyB());
        assertEquals(result, query.query(system.bodies().reversed()));
    }
    @Test void pairAndNarrowLimitsFailExplicitly() {
        var registry = new FragmentRegistry(3);
        registry.insert(shape(new Cell(0,0),new Cell(1,0)), 1,1,0);
        registry.insert(shape(new Cell(0,0),new Cell(1,0)), 1,1,0);
        var system = new RigidBodySystem(registry); system.synchronize();
        assertThrows(IllegalStateException.class, () -> new BodyCollisionQuery(new BodyCollisionQuery.Limits(1,1,10,10)).query(system.bodies()));
        registry.insert(shape(new Cell(0,0)), 4,4,0);
        system.synchronize();
        assertThrows(IllegalStateException.class, () -> new BodyCollisionQuery(new BodyCollisionQuery.Limits(1,100,100,100)).query(system.bodies()));
    }
    @Test void rawAndReducedContactBudgetsAreExplicit() {
        var r=new FragmentRegistry(2);
        var bar=shape(new Cell(0,0),new Cell(0,1)); r.insert(bar,2,2,0); r.insert(bar,2,2,0);
        var s=new RigidBodySystem(r); s.synchronize();
        assertThrows(IllegalStateException.class,()->new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,100,1,100)).query(s.bodies()));
        assertThrows(IllegalStateException.class,()->new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,100,100,1)).query(s.bodies()));
    }
    @Test void rotatedFragmentUsesCellSquares() {
        var r=new FragmentRegistry(2);
        r.insert(shape(new Cell(0,0),new Cell(1,0),new Cell(0,1)),3,3,0);
        r.insert(shape(new Cell(0,0)),4,4,0);
        var s=new RigidBodySystem(r); s.synchronize(); s.body(1).setVelocity(0,0,12); s.step();
        var result=new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,100,100,100)).query(s.bodies());
        assertNotEquals(0,s.body(1).angleRadians()); assertTrue(result.overlappingPairs()>0);
        for(var contact:result.contacts()) assertEquals(1,Math.hypot(contact.normalX(),contact.normalY()),1e-10);
    }
    @Test void twoSidedImpulseConservesLinearMomentum() {
        var registry = new FragmentRegistry(2);
        registry.insert(shape(new Cell(0,0)), 1,1,0);
        registry.insert(shape(new Cell(0,0)), 2,1,0);
        var system = new RigidBodySystem(registry); system.synchronize();
        system.body(1).setVelocity(2,0,0); system.body(2).setVelocity(-2,0,0); system.step();
        var contacts = new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,100,100,100)).query(system.bodies()).contacts();
        assertFalse(contacts.isEmpty());
        double before = system.body(1).velocityXMetresPerSecond() + system.body(2).velocityXMetresPerSecond();
        new BodyContactSolver(BodyContactSolver.Config.defaults()).solve(contacts, java.util.Map.of(1L,system.body(1),2L,system.body(2)));
        assertEquals(before,system.body(1).velocityXMetresPerSecond()+system.body(2).velocityXMetresPerSecond(),1e-12);
        assertTrue(system.body(1).velocityXMetresPerSecond() < 0);
        assertTrue(system.body(2).velocityXMetresPerSecond() > 0);
    }
    @Test void sharedTimelinePreventsFastCrossing() {
        var registry=new FragmentRegistry(2);
        registry.insert(shape(new Cell(0,0)),4,4,0); registry.insert(shape(new Cell(0,0)),14,4,0);
        var query=new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000));
        var system=new RigidBodySystem(registry,4,null,RigidBodySystem.MotionConfig.defaults(),query,
                new BodyContactSolver(BodyContactSolver.Config.defaults()));
        system.synchronize(); system.body(1).setVelocity(40,0,0); system.body(2).setVelocity(-40,0,0);
        system.step();
        assertTrue(system.body(1).xCells()<system.body(2).xCells());
        assertTrue(system.body(1).velocityXMetresPerSecond()<0);
        assertTrue(system.body(2).velocityXMetresPerSecond()>0);
    }
    @Test void threeBodyStackRemainsStable() {
        var registry=new FragmentRegistry(3);
        for (int y : new int[]{8,7,6}) registry.insert(shape(new Cell(0,0)),4,y,0);
        var world=new miningsim.world.World(12,12);
        for(int x=0;x<12;x++) world.set(x,9,miningsim.world.Material.STONE);
        var statics=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000),StaticContactSolver.Config.defaults());
        var system=new RigidBodySystem(registry,4,statics,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for(int i=0;i<600;i++) {
            system.step();
            if (i >= 150) {
                assertTrue(system.body(1).yCells()<8.65 && system.body(2).yCells()<7.65 && system.body(3).yCells()<6.65);
                assertTrue(system.bodies().stream().allMatch(body -> Math.abs(body.velocityYMetresPerSecond())<0.3));
            }
        }
        assertEquals(8.5,system.body(1).yCells(),0.15);
        assertEquals(7.5,system.body(2).yCells(),0.15);
        assertEquals(6.5,system.body(3).yCells(),0.15);
        assertEquals(7_500_000,registry.totalMassMilligrams());
        assertTrue(system.body(1).sleeping() && system.body(2).sleeping() && system.body(3).sleeping());
        world.set(4,9,miningsim.world.Material.AIR); system.step();
        assertTrue(system.bodies().stream().noneMatch(RigidBody::sleeping));
    }
    @Test void supportedBodySleepsAndWorldRemovalWakesIt() {
        var registry=new FragmentRegistry(1); registry.insert(shape(new Cell(0,0)),4,7,0);
        var world=new miningsim.world.World(12,12); world.set(4,8,miningsim.world.Material.STONE);
        var statics=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000),StaticContactSolver.Config.defaults());
        var system=new RigidBodySystem(registry,4,statics,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for(int i=0;i<300;i++) system.step();
        assertTrue(system.body(1).sleeping());
        system.body(1).setVelocity(0.1,0,0); assertFalse(system.body(1).sleeping());
        system.body(1).setVelocity(0,0,0); for(int i=0;i<300;i++) system.step();
        assertTrue(system.body(1).sleeping());
        world.set(4,8,miningsim.world.Material.AIR); system.step();
        assertFalse(system.body(1).sleeping()); assertTrue(system.body(1).velocityYMetresPerSecond()>0);
    }
    @Test void unsupportedBodyDoesNotSleepAndVelocityWakes() {
        var registry=new FragmentRegistry(1); registry.insert(shape(new Cell(0,0)),4,4,0);
        var system=new RigidBodySystem(registry,4,null,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,1000,100,100)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for (int i=0;i<80;i++) system.step();
        assertFalse(system.body(1).sleeping());
    }
    @Test void worldEditPreservesStructuralSink() {
        var world=new miningsim.world.World(10,10);
        var connectivity=new miningsim.structural.RockConnectivity(world);
        long revision=connectivity.revision();
        world.set(3,3,miningsim.world.Material.STONE);
        assertTrue(world.hasChangeSink(connectivity)); assertTrue(connectivity.revision()>revision);
        assertTrue(world.mutationRevision()>0);
    }
    @Test void contactLimitFailureRollsBackStates() {
        var registry=new FragmentRegistry(2);
        registry.insert(shape(new Cell(0,0)),2,2,0); registry.insert(shape(new Cell(0,0)),2,2,0);
        var system=new RigidBodySystem(registry,4,null,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,100,100,100)),new BodyContactSolver(BodyContactSolver.Config.defaults()),
                new RigidBodySystem.InteractionConfig(100,100,100,1,100,3));
        system.synchronize(); var body=system.body(1); double y=body.yCells();
        assertThrows(RigidBodySystem.MotionBudgetException.class,system::step);
        assertEquals(y,body.yCells()); assertEquals(0,body.velocityYMetresPerSecond()); assertNotNull(system.lastFailure());
        assertEquals(0,system.interactionDiagnostics().sharedMicrosteps());
    }
    @Test void hitWakesSleepingBodyAndPreservesMaterial() {
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),4,7,0);
        var w=new miningsim.world.World(20,12); for(int x=0;x<20;x++) w.set(x,8,miningsim.world.Material.STONE);
        var statics=new StaticContactSolver(new StaticCollisionQuery(w,10000,100000),StaticContactSolver.Config.defaults());
        var s=new RigidBodySystem(r,4,statics,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for(int i=0;i<200;i++) s.step(); assertTrue(s.body(1).sleeping());
        r.insert(shape(new Cell(0,0)),2,7,0); s.synchronize(); s.body(2).setVelocity(8,0,0);
        for(int i=0;i<4;i++) s.step();
        assertFalse(s.body(1).sleeping()); assertEquals(5_000_000,r.totalMassMilligrams());
        assertEquals(miningsim.resource.SubstanceRegistry.STONE,s.body(1).fragment().substance());
    }
    @Test void deterministicSleepReplay() {
        var w=new miningsim.world.World(12,12); for(int x=0;x<12;x++) w.set(x,9,miningsim.world.Material.STONE);
        var a=stackSystem(w); var b=stackSystem(w);
        for(int i=0;i<150;i++) { a.step(); b.step(); assertEquals(a.interactionDiagnostics(),b.interactionDiagnostics()); }
        for(long id=1;id<=3;id++) { assertEquals(a.body(id).yCells(),b.body(id).yCells()); assertEquals(a.body(id).sleeping(),b.body(id).sleeping()); }
    }
    @Test void unequalMassOffCenterCollisionTransfersMomentumAndRotation() {
        var r=new FragmentRegistry(2);
        r.insert(shape(new Cell(0,0),new Cell(1,0),new Cell(2,0)),3,3,0);
        r.insert(shape(new Cell(0,0)),3,4,0);
        var s=new RigidBodySystem(r); s.synchronize(); s.body(2).setVelocity(0,-1,0); s.step();
        var contacts=new BodyCollisionQuery(new BodyCollisionQuery.Limits(10,100,100,100)).query(s.bodies()).contacts();
        assertFalse(contacts.isEmpty());
        double m1=s.body(1).geometry().massKg(),m2=s.body(2).geometry().massKg();
        double momentum=m1*s.body(1).velocityYMetresPerSecond()+m2*s.body(2).velocityYMetresPerSecond();
        new BodyContactSolver(BodyContactSolver.Config.defaults()).solve(contacts,java.util.Map.of(1L,s.body(1),2L,s.body(2)));
        assertEquals(momentum,m1*s.body(1).velocityYMetresPerSecond()+m2*s.body(2).velocityYMetresPerSecond(),1e-10);
        assertTrue(Math.abs(s.body(1).angularVelocityRadiansPerSecond())>0.01);
    }
    @Test void sleepingBodyIsNotChargedForRemoteMotion() {
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),3,7,0);
        var w=new miningsim.world.World(40,30); w.set(3,8,miningsim.world.Material.STONE);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,10000,100000),StaticContactSolver.Config.defaults()),
                RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for(int i=0;i<200;i++) s.step(); assertTrue(s.body(1).sleeping());
        r.insert(shape(new Cell(0,0)),30,3,0); s.synchronize(); s.body(2).setVelocity(-2,0,0); s.step();
        assertTrue(s.body(1).sleeping()); assertEquals(4,s.motionDiagnostics().executedMicrosteps());
    }
    @Test void removingSleepingSupportAfterExplicitSynchronizationWakesStack() {
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),4,8,0); r.insert(shape(new Cell(0,0)),4,7,0);
        var w=new miningsim.world.World(12,12); w.set(4,9,miningsim.world.Material.STONE);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,10000,100000),StaticContactSolver.Config.defaults()),
                RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for(int i=0;i<300;i++) s.step(); assertTrue(s.body(1).sleeping() && s.body(2).sleeping());
        r.remove(1); s.synchronize(); s.step();
        assertFalse(s.body(2).sleeping()); assertTrue(s.body(2).velocityYMetresPerSecond()>0);
    }
    @Test void crossingTrajectoriesCollideOnSharedClock() {
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),4,8,0); r.insert(shape(new Cell(0,0)),8,4,0);
        var s=new RigidBodySystem(r,4,null,RigidBodySystem.MotionConfig.defaults(),
                new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        s.synchronize(); s.body(1).setVelocity(30,0,0); s.body(2).setVelocity(0,30,0); s.step();
        assertTrue(s.interactionDiagnostics().rawContacts()>0);
        assertTrue(Double.isFinite(s.body(1).angularVelocityRadiansPerSecond()));
    }
    @Test void pairFrictionReducesRelativeTangentialSpeed() {
        double noFriction = tangentialVelocityAfterCollision(0);
        double withFriction = tangentialVelocityAfterCollision(0.7);
        assertEquals(-1,noFriction,1e-12); assertTrue(Math.abs(withFriction)<Math.abs(noFriction));
    }
    private double tangentialVelocityAfterCollision(double friction) {
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),1,1,0); r.insert(shape(new Cell(0,0)),2,1,0);
        var s=new RigidBodySystem(r); s.synchronize(); s.body(1).setVelocity(1,1,0); s.body(2).setVelocity(-1,0,0);
        var c=new BodyCollisionQuery.Contact(1,2,1,0,0.01,2,1.5);
        new BodyContactSolver(new BodyContactSolver.Config(0,friction,friction,8)).solve(List.of(c),java.util.Map.of(1L,s.body(1),2L,s.body(2)));
        return s.body(2).velocityYMetresPerSecond()+s.body(2).angularVelocityRadiansPerSecond()*(-0.05)
                - s.body(1).velocityYMetresPerSecond()-s.body(1).angularVelocityRadiansPerSecond()*0.05;
    }
    @Test void twoBodyStackStaysSupportedFor600Ticks() {
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),4,8,0); r.insert(shape(new Cell(0,0)),4,7,0);
        var w=new miningsim.world.World(12,12); for(int x=0;x<12;x++) w.set(x,9,miningsim.world.Material.STONE);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,10000,100000),StaticContactSolver.Config.defaults()),
                RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for(int i=0;i<700;i++) { s.step(); if(i>=100) { assertEquals(8.5,s.body(1).yCells(),0.1); assertEquals(7.5,s.body(2).yCells(),0.1); } }
        assertTrue(s.body(1).sleeping() && s.body(2).sleeping());
        assertEquals(5_000_000,r.totalMassMilligrams()); assertEquals(12,w.occupiedCount());
    }
    @Test void sleepStateRestoredAfterBudgetFailure() {
        var r=new FragmentRegistry(2); r.insert(shape(new Cell(0,0)),4,7,0);
        var w=new miningsim.world.World(12,12); w.set(4,8,miningsim.world.Material.STONE);
        var s=new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,10000,100000),StaticContactSolver.Config.defaults()),
                new RigidBodySystem.MotionConfig(0.25,128,4),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        for(int i=0;i<200;i++) s.step(); assertTrue(s.body(1).sleeping());
        r.insert(shape(new Cell(0,0)),3,7,0); s.synchronize(); s.body(2).setVelocity(100,0,0);
        double y=s.body(1).yCells(); int sleepTicks=s.body(1).sleepTicks();
        assertThrows(RigidBodySystem.MotionBudgetException.class,s::step);
        assertEquals(y,s.body(1).yCells()); assertTrue(s.body(1).sleeping()); assertEquals(sleepTicks,s.body(1).sleepTicks());
    }
    private RigidBodySystem stackSystem(miningsim.world.World w) {
        var r=new FragmentRegistry(3); for(int y:new int[]{8,7,6}) r.insert(shape(new Cell(0,0)),4,y,0);
        return new RigidBodySystem(r,4,new StaticContactSolver(new StaticCollisionQuery(w,10000,100000),StaticContactSolver.Config.defaults()),
                RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,10000,1000,1000)),
                new BodyContactSolver(BodyContactSolver.Config.defaults()));
    }
}
