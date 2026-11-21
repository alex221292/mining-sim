package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.app.InteractiveSession;
import miningsim.physics.LargeCompoundBenchmark;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class DisconnectedStackRegressionTest {
    private static FragmentDescriptor row(int width) {
        var cells=new ArrayList<FragmentDescriptor.Cell>();
        for(int x=0;x<width;x++) cells.add(new FragmentDescriptor.Cell(x,0));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
    @Test void erasedRockComponentsAreDeterministicWithoutChangingOwnership() {
        var world=new miningsim.world.World(20,20,miningsim.world.World.Mode.BEDROCK_ENABLED);
        var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,20);
        for(int x=3;x<=7;x++) world.set(x,18,Material.STONE);
        long id=settled.registerExisting(1,row(5),3,18);
        for(int x=4;x<=6;x++) world.set(x,18,Material.AIR);
        long revision=settled.revision(),worldRevision=world.mutationRevision();
        var expected=List.of(List.of(new SettledRockRegistry.WorldCell(3,18)),List.of(new SettledRockRegistry.WorldCell(7,18)));
        assertEquals(expected,settled.connectedComponents(id)); assertEquals(expected,settled.connectedComponents(id));
        assertEquals(revision,settled.revision()); assertEquals(worldRevision,world.mutationRevision());
        assertEquals(id,settled.ownerAt(3,18)); assertEquals(id,settled.ownerAt(7,18));
        assertEquals(2,settled.rock(id).remainingCells());
    }
    @Test void selectiveOwnershipPreparationIsReadOnlyAndStaleAfterEdit() {
        var world=new miningsim.world.World(20,20,miningsim.world.World.Mode.BEDROCK_ENABLED);
        var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,20);
        for(int x=3;x<=7;x++) world.set(x,18,Material.STONE);
        long id=settled.registerExisting(1,row(5),3,18);
        for(int x=4;x<=6;x++) world.set(x,18,Material.AIR);
        while(topology.pending()) topology.advance(1000);
        var components=settled.connectedComponents(id); long mass=settled.rock(id).massMilligrams(); long revision=settled.revision();
        var prepared=settled.prepareComponentReactivationRemoval(List.of(new SettledRockRegistry.ComponentReactivationMapping(id,42,components.getFirst())));
        assertTrue(prepared.valid()); assertEquals(revision,settled.revision()); assertEquals(mass,settled.rock(id).massMilligrams());
        assertEquals(id,settled.ownerAt(3,18)); assertEquals(id,settled.ownerAt(7,18));
        world.set(0,0,Material.STONE); assertFalse(prepared.valid());
        assertEquals(id,settled.ownerAt(3,18)); assertEquals(id,settled.ownerAt(7,18));
    }
    @Test void selectiveTransferPublishesWholeStateAtExistingBoundary() {
        var world=new miningsim.world.World(20,20,miningsim.world.World.Mode.BEDROCK_ENABLED);
        var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,20);
        for(int x=3;x<=5;x++) world.set(x,18,Material.STONE);
        long id=settled.registerExisting(17,row(3),3,18); world.set(4,18,Material.AIR);
        while(topology.pending()) topology.advance(1000);
        var registry=new miningsim.fragment.FragmentRegistry(4);
        var solver=new miningsim.physics.StaticContactSolver(new miningsim.physics.StaticCollisionQuery(world,10000,100000,miningsim.physics.StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),miningsim.physics.StaticContactSolver.Config.defaults());
        var physics=new miningsim.physics.RigidBodySystem(registry,4,solver,miningsim.physics.RigidBodySystem.MotionConfig.defaults(),new miningsim.physics.BodyCollisionQuery(new miningsim.physics.BodyCollisionQuery.Limits(10000,100000,4096,4096)),new miningsim.physics.BodyContactSolver(miningsim.physics.BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new miningsim.physics.RigidBodyOccupancyIndex(world,miningsim.physics.RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var component=settled.connectedComponents(id).getLast();
        var result=miningsim.physics.GroupRockReactivation.prepareComponentReactivation(List.of(new miningsim.physics.GroupRockReactivation.Component(id,component)),world,topology,settled,physics,occupancy,0,new miningsim.physics.GroupRockReactivation.Limits(4,20),null);
        assertEquals(miningsim.physics.GroupRockReactivation.Status.READY,result.status(),result.reason());
        var old=occupancy.snapshot(); long mass=settled.rock(id).massMilligrams();
        topology.setTestBatchObserver(()->{ assertSame(old,occupancy.snapshot()); assertEquals(id,settled.ownerAt(5,18)); assertTrue(physics.bodies().isEmpty()); return false; },null);
        assertEquals(miningsim.world.World.BatchResult.REJECTED,world.commitGroupReactivation(result.plan()));
        assertEquals(mass,settled.rock(id).massMilligrams()); assertEquals(Material.STONE,world.get(5,18));
        boolean[] observed={false}; topology.setTestBatchObserver(()->true,()->{
            observed[0]=true; assertEquals(Material.AIR,world.get(5,18)); assertEquals(0,settled.ownerAt(5,18));
            assertEquals(id,settled.ownerAt(3,18)); assertEquals(1,physics.bodies().size());
            assertEquals(mass,settled.rock(id).massMilligrams()+registry.totalMassMilligrams());
        });
        assertEquals(miningsim.world.World.BatchResult.CHANGED,world.commitGroupReactivation(result.plan())); assertTrue(observed[0]);
        assertEquals(List.of(physics.bodies().getFirst().id()),settled.reactivatedBodyIds(id));
        topology.setTestBatchObserver(null,null);
    }
    @Test void onlyUnsupportedComponentOfDamagedRockReactivates() {
        var original=LargeCompoundBenchmark.fixture("large").session(); var fragments=original.structural().registry();
        for(var f:fragments.snapshot()) assertTrue(fragments.remove(f.id()));
        var s=InteractiveSession.headlessLifecycle(original.world(),original.structural(),false,false,SettledSupportCoordinator.Limits.measuredDefaults());
        original.retire(); var world=s.world();
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(
                new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0),new FragmentDescriptor.Cell(0,1),new FragmentDescriptor.Cell(1,1)));
        for(var cell:shape.cells()) world.set(3+cell.x(),61+cell.y(),Material.STONE);
        long id=s.lifecycle().settled().registerExisting(101,shape,3,61);
        for(int i=0;i<300 && !s.lifecycle().settled().supportTransitionsAllowed();i++) { s.tick(); assertNull(s.failure()); }
        s.enqueue(()->{ world.set(4,61,Material.AIR); world.set(4,62,Material.AIR); });
        boolean fell=false;
        for(int i=0;i<500;i++) { s.tick(); assertNull(s.failure()); assertFalse(s.blocked());
            if(!s.physics().bodies().isEmpty()) { fell=true; break; } }
        assertTrue(fell,s.lifecycle().diagnostics().toString());
        assertEquals(Material.STONE,world.get(3,62)); assertEquals(id,s.lifecycle().settled().ownerAt(3,62));
        assertEquals(Material.AIR,world.get(5,61)); assertEquals(0,s.lifecycle().settled().ownerAt(5,61));
    }
    @Test void erasedBridgeDoesNotStrandUnsupportedUpperBlocks() {
        var original=LargeCompoundBenchmark.fixture("large").session(); var fragments=original.structural().registry();
        for(var f:fragments.snapshot()) assertTrue(fragments.remove(f.id()));
        var s=InteractiveSession.headlessLifecycle(original.world(),original.structural(),false,false,SettledSupportCoordinator.Limits.measuredDefaults());
        original.retire(); var world=s.world();
        for(int x=3;x<=7;x++) world.set(x,62,Material.STONE);
        long base=s.lifecycle().settled().registerExisting(100,row(5),3,62);
        for(int y=60;y<=61;y++) for(int x=4;x<=6;x++) world.set(x,y,Material.STONE);
        s.lifecycle().settled().registerExisting(101,row(3),4,61);
        s.lifecycle().settled().registerExisting(102,row(3),4,60);
        for(int i=0;i<300 && !s.lifecycle().settled().supportTransitionsAllowed();i++) { s.tick(); assertNull(s.failure()); }
        assertEquals(3,s.lifecycle().settled().liveRocks());
        s.enqueue(()->{ for(int x=4;x<=6;x++) world.set(x,62,Material.AIR); });
        boolean fell=false;
        for(int i=0;i<500;i++) { s.tick(); assertNull(s.failure()); assertFalse(s.blocked());
            assertFalse(s.lifecycle().diagnostics().lastReject().contains("Fragment must be connected"),s.lifecycle().diagnostics().toString());
            if(s.lifecycle().diagnostics().reactivated()>=2 && !s.physics().bodies().isEmpty()) { fell=true; break; }
        }
        assertTrue(fell,s.lifecycle().diagnostics().toString());
        assertEquals(base,s.lifecycle().settled().ownerAt(3,62)); assertEquals(base,s.lifecycle().settled().ownerAt(7,62));
    }
}
