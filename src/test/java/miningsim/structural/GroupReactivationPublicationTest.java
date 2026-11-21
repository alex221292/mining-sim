package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class GroupReactivationPublicationTest {
    @Test void trueForeignOverlapRejectsWithBodyIdsWithoutPublication() {
        var f=fixture();
        var cell=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        long foreign=f.registry().insert(cell,3,18,0).id(); f.physics().synchronize(); f.occupancy().rebuild(f.physics(),0);
        var old=f.occupancy().snapshot(); long revision=f.world().mutationRevision();
        var result=prepare(f,f.occupancy());
        assertEquals(GroupRockReactivation.Status.CONFLICT,result.status()); assertTrue(result.reason().contains("/"+foreign),result.reason());
        assertEquals(revision,f.world().mutationRevision()); assertSame(old,f.occupancy().snapshot());
        assertEquals(2,f.settled().liveRocks()); assertEquals(1,f.physics().bodies().size()); assertNull(result.plan());
    }
    @Test void independentRigidBodyDoesNotPreventGroupReactivation() {
        var f=fixture(); var cell=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        long foreign=f.registry().insert(cell,10,16,0).id(); f.physics().synchronize(); f.occupancy().rebuild(f.physics(),0);
        var before=f.physics().body(foreign); double x=before.xCells(),y=before.yCells();
        var result=prepare(f,f.occupancy()); assertEquals(GroupRockReactivation.Status.READY,result.status(),result.reason());
        assertEquals(World.BatchResult.CHANGED,f.world().commitGroupReactivation(result.plan()));
        assertSame(before,f.physics().body(foreign)); assertEquals(x,before.xCells()); assertEquals(y,before.yCells());
    }
    private record Fixture(World world,RockConnectivity topology,SettledRockRegistry settled,FragmentRegistry registry,
                           RigidBodySystem physics,RigidBodyOccupancyIndex occupancy) { }
    private static Fixture fixture() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,6,10);
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0)));
        for(int i=0;i<2;i++) { world.set(3,18-i,Material.STONE); world.set(4,18-i,Material.STONE); settled.registerExisting(i+1,descriptor,3,18-i); }
        var registry=new FragmentRegistry(4);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        return new Fixture(world,topology,settled,registry,physics,occupancy);
    }
    private static GroupRockReactivation.Result prepare(Fixture f,RigidBodyOccupancyIndex index) {
        return GroupRockReactivation.prepareGroupReactivation(List.of(1L,2L),f.world(),f.topology(),f.settled(),f.physics(),index,0,new GroupRockReactivation.Limits(2,4));
    }
    @Test void stalePlansAndOccupancyFailureNeverPublishPartialGroup() {
        var f=fixture(); var result=prepare(f,f.occupancy()); assertEquals(GroupRockReactivation.Status.READY,result.status(),result.reason());
        f.world().set(0,0,Material.SAND);
        assertEquals(World.BatchResult.REJECTED,f.world().commitGroupReactivation(result.plan()));
        assertEquals(4,f.settled().snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum()); assertTrue(f.physics().bodies().isEmpty());
        var g=fixture(); var generation=prepare(g,g.occupancy()); assertEquals(GroupRockReactivation.Status.READY,generation.status(),generation.reason());
        assertTrue(g.topology().breakBond(3,18,4,18));
        assertEquals(World.BatchResult.REJECTED,g.world().commitGroupReactivation(generation.plan()));
        assertEquals(4,g.settled().snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum()); assertTrue(g.physics().bodies().isEmpty());
        var h=fixture(); var limited=new RigidBodyOccupancyIndex(h.world(),new RigidBodyOccupancyIndex.Limits(0,0,0,0,0,0,0));
        assertEquals(GroupRockReactivation.Status.BUDGET,prepare(h,limited).status());
        assertEquals(4,h.settled().snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum()); assertTrue(h.physics().bodies().isEmpty());
    }
    @Test void coordinatorAttachmentInvalidatesPreviouslyPreparedDirectPlan() {
        var f=fixture(); var plan=prepare(f,f.occupancy()); assertEquals(GroupRockReactivation.Status.READY,plan.status(),plan.reason());
        var single=SettledRockReactivation.prepareReactivation(1,f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),0);
        assertEquals(SettledRockReactivation.Status.READY,single.status(),single.reason());
        var coordinator=new SettledSupportCoordinator(f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),new SettledSupportCoordinator.Limits(5,10,20,100,5,20,1));
        for(int i=0;i<100;i++) if(coordinator.step(0).status()==SettledSupportCoordinator.Status.IDLE) break;
        assertEquals(World.BatchResult.REJECTED,f.world().commitGroupReactivation(plan.plan()));
        assertEquals(World.BatchResult.REJECTED,f.world().commitReactivation(single.plan()));
        assertEquals(4,f.settled().snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum()); assertTrue(f.physics().bodies().isEmpty());
    }
    @Test void movingForeignBodyInvalidatesPreparedGroup() {
        var f=fixture();
        var one=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        var foreign=f.registry().insert(one,12,18,0); f.physics().synchronize(); f.occupancy().rebuild(f.physics(),0);
        var plan=prepare(f,f.occupancy()); assertEquals(GroupRockReactivation.Status.READY,plan.status(),plan.reason());
        f.physics().body(foreign.id()).setVelocity(0.1,0,0);
        assertEquals(World.BatchResult.REJECTED,f.world().commitGroupReactivation(plan.plan()));
        assertEquals(4,f.settled().snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum());
        assertEquals(1,f.registry().size()); assertEquals(1,f.physics().bodies().size());
    }
    @Test void bondDamageCannotBeHiddenBySubsequentWorldChange() {
        var f=fixture();
        var coordinator=new SettledSupportCoordinator(f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),new SettledSupportCoordinator.Limits(5,10,20,2,5,20,1));
        for(int i=0;i<200;i++) if(coordinator.step(0).status()==SettledSupportCoordinator.Status.IDLE) break;
        assertTrue(f.settled().supportTransitionsAllowed()); assertTrue(f.topology().breakBond(3,18,4,18));
        f.world().set(0,0,Material.STONE); assertFalse(f.settled().supportTransitionsAllowed());
        List<Long> changed=List.of();
        for(int i=0;i<500;i++) { var result=coordinator.step(0); if(result.status()==SettledSupportCoordinator.Status.COMMITTED) { changed=result.reactivated(); break; } }
        assertEquals(List.of(1L,2L),changed); assertEquals(2,f.physics().bodies().size());
    }
    @Test void ownershipRegistrationCannotBeHiddenBySubsequentWorldChange() {
        var f=fixture();
        var coordinator=new SettledSupportCoordinator(f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),new SettledSupportCoordinator.Limits(5,10,20,3,5,20,1));
        for(int i=0;i<200;i++) if(coordinator.step(0).status()==SettledSupportCoordinator.Status.IDLE) break;
        assertTrue(f.settled().supportTransitionsAllowed());
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0)));
        f.world().set(10,10,Material.STONE); long id=f.settled().registerExisting(3,descriptor,10,10);
        f.world().set(0,0,Material.STONE); assertFalse(f.settled().supportTransitionsAllowed());
        List<Long> changed=List.of();
        for(int i=0;i<500;i++) { var result=coordinator.step(0); if(result.status()==SettledSupportCoordinator.Status.COMMITTED) { changed=result.reactivated(); break; } }
        assertEquals(List.of(id),changed); assertEquals(1,f.physics().bodies().size());
    }
    @Test void groupIsAtomicAndPreservesIdentity() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,6,10);
        for(int i=0;i<3;i++) {
            var material=i==1?SubstanceRegistry.MARBLE:SubstanceRegistry.STONE;
            var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(material),List.of(new FragmentDescriptor.Cell(0,0)));
            world.set(3,18-i,Material.STONE); settled.registerExisting(i+1,descriptor,3,18-i);
        }
        var registry=new FragmentRegistry(4);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var prepared=GroupRockReactivation.prepareGroupReactivation(List.of(3L,1L,2L),world,topology,settled,physics,occupancy,0,new GroupRockReactivation.Limits(3,3));
        assertEquals(GroupRockReactivation.Status.READY,prepared.status(),prepared.reason()); var plan=prepared.plan();
        long mass=settled.snapshot().stream().mapToLong(SettledRockRegistry.Rock::massMilligrams).sum();
        Runnable old=()->{ assertEquals(3,settled.snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum());
            assertTrue(physics.bodies().isEmpty()); assertEquals(0,registry.size()); assertEquals(0,occupancy.snapshot().diagnostics().indexedBodies()); };
        topology.setTestBatchObserver(()->{old.run(); return false;},null);
        assertEquals(World.BatchResult.REJECTED,world.commitGroupReactivation(plan)); old.run();
        topology.setTestBatchObserver(()->{old.run(); throw new IllegalStateException("injected");},null);
        assertThrows(IllegalStateException.class,()->world.commitGroupReactivation(plan)); old.run();
        boolean[] observed={false}; topology.setTestBatchObserver(()->{old.run(); return true;},()->{
            observed[0]=true; assertEquals(3,physics.bodies().size()); assertEquals(mass,registry.totalMassMilligrams());
            assertEquals(0,settled.snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum());
            assertEquals(3,occupancy.snapshot().diagnostics().indexedBodies());
            for(var m:plan.mappings()) assertEquals(m.bodyId(),settled.rock(m.settledId()).reactivatedBodyId());
        });
        assertEquals(World.BatchResult.CHANGED,world.commitGroupReactivation(plan)); assertTrue(observed[0]);
        topology.setTestBatchObserver(null,null); assertEquals(World.BatchResult.REJECTED,world.commitGroupReactivation(plan));
        assertEquals(SubstanceRegistry.MARBLE,physics.body(plan.mappings().get(1).bodyId()).fragment().substance());
    }
}
