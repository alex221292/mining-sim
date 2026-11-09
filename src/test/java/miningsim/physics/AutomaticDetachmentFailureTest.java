package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.fragment.FragmentRegistry;
import miningsim.structural.AutomaticRockDetachment;
import miningsim.structural.RockConnectivity;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class AutomaticDetachmentFailureTest {
    @Test void capacityAndOccupancyFailuresLeaveSourceAndOwnersUnchanged() {
        for(boolean registryFull:new boolean[]{true,false}) {
            var w=new World(8,8,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(w); w.set(2,2,Material.STONE);
            var registry=new FragmentRegistry(registryFull?0:1);
            var query=new StaticCollisionQuery(w,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
            var physics=new RigidBodySystem(registry,4,new StaticContactSolver(query,StaticContactSolver.Config.defaults()));
            var index=new RigidBodyOccupancyIndex(w,registryFull?RigidBodyOccupancyIndex.Limits.defaults():new RigidBodyOccupancyIndex.Limits(0,1,1,1,1,1,1));
            var auto=new AutomaticRockDetachment(w,topology,registry,physics,index,AutomaticRockDetachment.Limits.defaults());
            long revision=w.mutationRevision(); var previous=index.snapshot();
            for(int i=0;i<10;i++) auto.tick(i);
            assertEquals(Material.STONE,w.get(2,2)); assertEquals(revision,w.mutationRevision());
            assertEquals(0,registry.size()); assertTrue(physics.bodies().isEmpty()); assertSame(previous,index.snapshot());
            assertEquals(1,auto.diagnostics().rejected()); assertEquals(0,auto.diagnostics().total());
        }
    }
    @Test void registryCapacityChangeRetriesPreviouslyRejectedComponent() {
        var w=new World(8,8,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(w); w.set(2,2,Material.STONE);
        var registry=new FragmentRegistry(1);
        var descriptor=new miningsim.resource.FragmentDescriptor(miningsim.resource.SubstanceRegistry.reference().get(miningsim.resource.SubstanceRegistry.STONE),
                java.util.List.of(new miningsim.resource.FragmentDescriptor.Cell(0,0)));
        registry.insert(descriptor,5,2,0);
        var query=new StaticCollisionQuery(w,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
        var physics=new RigidBodySystem(registry,4,new StaticContactSolver(query,StaticContactSolver.Config.defaults())); physics.synchronize();
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        var auto=new AutomaticRockDetachment(w,topology,registry,physics,index,AutomaticRockDetachment.Limits.defaults());
        for(int i=0;i<10;i++) auto.tick(i);
        assertEquals(1,auto.diagnostics().rejected()); assertEquals(Material.STONE,w.get(2,2));
        assertTrue(registry.remove(1)); auto.tick(11);
        assertEquals(Material.AIR,w.get(2,2)); assertEquals(1,registry.size()); assertNotNull(physics.body(2));
        assertArrayEquals(new long[]{2},index.snapshot().bodyIdsAt(2,2));
    }
    @Test void traversalRejectionCannotPartiallyCommitAutomaticExtraction() {
        var w=new World(8,8,World.Mode.BEDROCK_ENABLED); w.set(2,2,Material.STONE); w.set(3,2,Material.STONE);
        var topology=new RockConnectivity(w); while(topology.pending()) topology.advance(1000);
        var registry=new FragmentRegistry(1); var query=new StaticCollisionQuery(w,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
        var physics=new RigidBodySystem(registry,4,new StaticContactSolver(query,StaticContactSolver.Config.defaults()));
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var before=index.snapshot();
        var auto=new AutomaticRockDetachment(w,topology,registry,physics,index,AutomaticRockDetachment.Limits.defaults());
        long revision=w.mutationRevision();
        topology.forEachBond(0,0,8,8,(x,y,nx,ny,state)->auto.tick(0));
        assertEquals("TRAVERSING",auto.diagnostics().failure()); assertEquals(1,auto.diagnostics().rejected());
        assertEquals(revision,w.mutationRevision()); assertSame(before,index.snapshot()); assertTrue(physics.bodies().isEmpty());
        assertEquals(0,registry.size()); assertEquals(Material.STONE,w.get(2,2)); assertEquals(Material.STONE,w.get(3,2));
    }
    @Test void failedAttemptsAlsoConsumePerTickWorkBudget() {
        var w=new World(30,8,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(w);
        for(int i=0;i<10;i++) w.set(1+i*2,2,Material.STONE);
        var registry=new FragmentRegistry(0); var query=new StaticCollisionQuery(w,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR);
        var physics=new RigidBodySystem(registry,4,new StaticContactSolver(query,StaticContactSolver.Config.defaults()));
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults());
        var auto=new AutomaticRockDetachment(w,topology,registry,physics,index,new AutomaticRockDetachment.Limits(1000,0,1024,4,4096,256));
        auto.tick(0); assertEquals(4,auto.diagnostics().rejected());
        auto.tick(1); assertEquals(8,auto.diagnostics().rejected());
        auto.tick(2); assertEquals(10,auto.diagnostics().rejected());
        assertTrue(physics.bodies().isEmpty()); assertEquals(40,w.occupiedCount());
    }
}
