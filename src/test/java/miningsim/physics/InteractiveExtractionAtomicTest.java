package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.fragment.ExtractionCommit;
import miningsim.fragment.ExtractionPlanner;
import miningsim.fragment.FragmentRegistry;
import miningsim.structural.RockReferenceScenes;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;
import miningsim.app.InteractiveSession;
import miningsim.app.StructuralSimulationController;

class InteractiveExtractionAtomicTest {
    @Test void occupancyFailureBeforeExtractionPreservesAllOwners() {
        var scene=RockReferenceScenes.interactive("atomic",Material.AIR);
        RockReferenceScenes.finishConnectivity(scene.connectivity());
        var plan=ExtractionPlanner.prepare(scene.world(),scene.connectivity(),12,3,64).plan();
        var registry=new FragmentRegistry(4);
        var solver=new StaticContactSolver(new StaticCollisionQuery(scene.world(),1000,10000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver);
        var index=new RigidBodyOccupancyIndex(scene.world(),new RigidBodyOccupancyIndex.Limits(0,100,100,100,100,100,100));
        var snapshot=index.snapshot(); long revision=scene.world().mutationRevision(),topology=scene.connectivity().revision();
        assertThrows(IllegalStateException.class,()->physics.extract(plan,index,0));
        assertEquals(Material.STONE,scene.world().get(12,3)); assertEquals(0,registry.size()); assertTrue(physics.bodies().isEmpty());
        assertSame(snapshot,index.snapshot()); assertEquals(revision,scene.world().mutationRevision()); assertEquals(topology,scene.connectivity().revision());
    }
    @Test void successPublishesRegistryBodyAndOccupancyTogether() {
        var scene=RockReferenceScenes.interactive("atomic",Material.AIR); RockReferenceScenes.finishConnectivity(scene.connectivity());
        var plan=ExtractionPlanner.prepare(scene.world(),scene.connectivity(),12,3,64).plan(); var registry=new FragmentRegistry(4);
        var solver=new StaticContactSolver(new StaticCollisionQuery(scene.world(),1000,10000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver); var index=new RigidBodyOccupancyIndex(scene.world(),RigidBodyOccupancyIndex.Limits.defaults());
        var result=physics.extract(plan,index,0); assertEquals(ExtractionCommit.Status.COMMITTED,result.status());
        assertEquals(Material.AIR,scene.world().get(12,3)); assertEquals(1,registry.size()); assertEquals(1,physics.bodies().size());
        assertArrayEquals(new long[]{result.fragment().id()},index.snapshot().bodyIdsAt(12,3));
        assertEquals(result.fragment().massMilligrams(),registry.totalMassMilligrams());
    }
    @Test void unexpectedPhysicsFailureIsNotSwallowedByUiCoordinator() {
        var scene=RockReferenceScenes.interactive("failure",Material.AIR);
        var structural=new StructuralSimulationController(scene); var session=new InteractiveSession(scene.world(),structural);
        structural.tick(); assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
        var snapshot=session.occupancy().snapshot(); var before=session.physics().body(1).state();
        session.physics().setTestCheckpoint(point->{ throw new IllegalStateException("unexpected invariant"); });
        var ex=assertThrows(IllegalStateException.class,session::tick); assertEquals("unexpected invariant",ex.getMessage());
        assertSame(snapshot,session.occupancy().snapshot()); assertEquals(before,session.physics().body(1).state());
    }
}
