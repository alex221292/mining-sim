package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class ReactivationPublicationTest {
    private record Fixture(World world,RockConnectivity topology,SettledRockRegistry settled,FragmentRegistry registry,
                           RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,long id) { }
    private static Fixture fixture() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10);
        world.set(3,17,Material.STONE); world.set(3,18,Material.STONE);
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(0,1)));
        long id=settled.registerExisting(1,descriptor,3,17);
        var registry=new FragmentRegistry(4);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        return new Fixture(world,topology,settled,registry,physics,occupancy,id);
    }
    private static SettledRockReactivation.Result prepare(Fixture f) {
        return SettledRockReactivation.prepareReactivation(f.id(),f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),0);
    }
    @Test void vetoExceptionAndCallbacksSeeConsistentOwnership() {
        var f=fixture(); var result=prepare(f); assertEquals(SettledRockReactivation.Status.READY,result.status(),result.reason()); var plan=result.plan();
        long mass=f.settled().rock(f.id()).massMilligrams(); var old=f.occupancy().snapshot();
        Runnable verifyOld=()->{ assertEquals(Material.STONE,f.world().get(3,17)); assertEquals(f.id(),f.settled().ownerAt(3,17));
            assertTrue(f.physics().bodies().isEmpty()); assertEquals(0,f.registry().size()); assertSame(old,f.occupancy().snapshot()); };
        f.topology().setTestBatchObserver(()->{verifyOld.run(); return false;},null);
        assertEquals(World.BatchResult.REJECTED,f.world().commitReactivation(plan)); verifyOld.run();
        f.topology().setTestBatchObserver(()->{verifyOld.run(); throw new IllegalStateException("injected");},null);
        assertThrows(IllegalStateException.class,()->f.world().commitReactivation(plan)); verifyOld.run();
        boolean[] observed={false}; f.topology().setTestBatchObserver(()->{verifyOld.run(); return true;},()->{
            observed[0]=true; assertEquals(Material.AIR,f.world().get(3,17)); assertEquals(0,f.settled().ownerAt(3,17));
            assertEquals(0,f.settled().rock(f.id()).massMilligrams()); assertEquals(plan.bodyId(),f.settled().rock(f.id()).reactivatedBodyId());
            assertEquals(mass,f.registry().totalMassMilligrams()); assertNotNull(f.physics().body(plan.bodyId()));
            assertArrayEquals(new long[]{plan.bodyId()},f.occupancy().snapshot().bodyIdsAt(3,17));
        });
        assertEquals(World.BatchResult.CHANGED,f.world().commitReactivation(plan)); assertTrue(observed[0]);
        f.topology().setTestBatchObserver(null,null); assertEquals(World.BatchResult.REJECTED,f.world().commitReactivation(plan));
    }
    @Test void staleWorldAndGenerationAreRejected() {
        var f=fixture(); var plan=prepare(f).plan(); f.world().set(0,0,Material.SAND);
        assertEquals(World.BatchResult.REJECTED,f.world().commitReactivation(plan)); assertEquals(f.id(),f.settled().ownerAt(3,17));
        var g=fixture(); var stale=prepare(g).plan(); g.world().set(3,17,Material.AIR);
        assertEquals(World.BatchResult.REJECTED,g.world().commitReactivation(stale)); assertTrue(g.physics().bodies().isEmpty());
        assertEquals(1,g.settled().rock(g.id()).remainingCells());
        var h=fixture(); var changedBond=prepare(h).plan(); long revision=h.world().mutationRevision();
        assertTrue(h.topology().breakBond(3,17,3,18)); assertEquals(revision,h.world().mutationRevision());
        assertEquals(World.BatchResult.REJECTED,h.world().commitReactivation(changedBond)); assertEquals(2,h.settled().rock(h.id()).remainingCells());
    }
    @Test void occupancyBudgetAndRigidOverlapRejectWithoutPublishing() {
        var f=fixture(); var bad=new RigidBodyOccupancyIndex(f.world(),new RigidBodyOccupancyIndex.Limits(0,0,0,0,0,0,0));
        var limited=SettledRockReactivation.prepareReactivation(f.id(),f.world(),f.topology(),f.settled(),f.physics(),bad,0);
        assertEquals(SettledRockReactivation.Status.CAPACITY_OR_BUDGET,limited.status());
        assertEquals(f.id(),f.settled().ownerAt(3,17)); assertTrue(f.physics().bodies().isEmpty());
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        f.registry().insert(descriptor,3,17,0); f.physics().synchronize(); f.occupancy().rebuild(f.physics(),0);
        var conflict=prepare(f); assertEquals(SettledRockReactivation.Status.CONFLICT,conflict.status());
        assertEquals(f.id(),f.settled().ownerAt(3,17)); assertEquals(1,f.physics().bodies().size());
    }
    @Test void currentOwnedGeometryAndUniqueBodyIdAreUsed() {
        var f=fixture(); f.world().set(3,17,Material.AIR);
        var other=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        var foreign=f.registry().insert(other,12,18,0); f.physics().synchronize(); f.occupancy().rebuild(f.physics(),0);
        var result=prepare(f); assertEquals(SettledRockReactivation.Status.READY,result.status(),result.reason());
        assertEquals(1,result.plan().descriptor().cellCount()); assertNotEquals(foreign.id(),result.plan().bodyId());
        assertEquals(World.BatchResult.CHANGED,f.world().commitReactivation(result.plan()));
        assertEquals(2,f.registry().size()); assertEquals(1,f.physics().body(result.plan().bodyId()).fragment().descriptor().cellCount());
        assertNotNull(f.physics().body(foreign.id())); assertEquals(result.plan().bodyId(),f.settled().rock(f.id()).reactivatedBodyId());
        assertEquals(SubstanceRegistry.MARBLE,f.physics().body(result.plan().bodyId()).fragment().substance());
    }
    @Test void foreignBodyMotionInvalidatesPreparedOccupancy() {
        var f=fixture(); var other=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        var foreign=f.registry().insert(other,12,18,0); f.physics().synchronize(); f.occupancy().rebuild(f.physics(),0);
        var result=prepare(f); assertEquals(SettledRockReactivation.Status.READY,result.status(),result.reason());
        f.physics().body(foreign.id()).setVelocity(1,0,0);
        assertEquals(World.BatchResult.REJECTED,f.world().commitReactivation(result.plan()));
        assertEquals(f.id(),f.settled().ownerAt(3,17)); assertEquals(1,f.registry().size());
    }
    @Test void rectangularThinAndConcaveGeometriesAreExact() {
        for(var shape:List.of(
                List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(0,1),new FragmentDescriptor.Cell(1,1)),
                java.util.stream.IntStream.range(0,12).mapToObj(x->new FragmentDescriptor.Cell(x,0)).toList(),
                List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(0,1),new FragmentDescriptor.Cell(0,2),new FragmentDescriptor.Cell(1,2),new FragmentDescriptor.Cell(2,2)))) {
            var world=new World(30,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,2,20);
            var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),shape);
            for(var cell:shape) world.set(3+cell.x(),10+cell.y(),Material.STONE);
            long id=settled.registerExisting(1,descriptor,3,10); var registry=new FragmentRegistry(2);
            var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
            var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
            physics.synchronize(); var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
            var plan=SettledRockReactivation.prepareReactivation(id,world,topology,settled,physics,index,0);
            assertEquals(SettledRockReactivation.Status.READY,plan.status(),plan.reason()); assertEquals(descriptor.cells(),plan.plan().descriptor().cells());
            assertEquals(World.BatchResult.CHANGED,world.commitReactivation(plan.plan()));
            var body=physics.body(plan.plan().bodyId()); assertTrue(body.geometry().inertiaKgMetresSquared()>0);
            assertEquals(descriptor.massMilligrams(),body.fragment().massMilligrams());
            for(var c:shape) assertArrayEquals(new long[]{body.id()},index.snapshot().bodyIdsAt(3+c.x(),10+c.y()));
        }
    }
    @Test void disconnectedRemainingCellsAreRejected() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,2,5);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(0,1),new FragmentDescriptor.Cell(0,2)));
        world.set(2,3,Material.STONE); world.set(2,4,Material.STONE); world.set(2,5,Material.STONE); long id=settled.registerExisting(1,shape,2,3);
        world.set(2,4,Material.AIR); var registry=new FragmentRegistry(2);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        assertEquals(SettledRockReactivation.Status.INVALID_ROCK,SettledRockReactivation.prepareReactivation(id,world,topology,settled,physics,index,0).status());
        assertEquals(2,settled.rock(id).remainingCells()); assertEquals(0,registry.size());
    }
    @Test void unsupportedReactivatedRockFallsAndContactsBedrock() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10); world.set(3,5,Material.STONE);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0)));
        long id=settled.registerExisting(1,shape,3,5); var registry=new FragmentRegistry(4);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults());
        var result=SettledRockReactivation.prepareReactivation(id,world,topology,settled,physics,occupancy,0);
        assertEquals(SettledRockReactivation.Status.READY,result.status(),result.reason()); assertEquals(World.BatchResult.CHANGED,world.commitReactivation(result.plan()));
        var body=physics.body(result.plan().bodyId()); double before=body.yCells(); boolean contacted=false;
        for(int i=0;i<240;i++) { coupled.step(); assertNull(coupled.lastFailure()); if(physics.diagnostics().contacts()>0) contacted=true; }
        assertTrue(body.yCells()>before+5); assertTrue(contacted); assertTrue(body.yCells()<19); assertEquals(Material.BEDROCK,world.get(3,19));
    }
}
