package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.simulation.Simulation;
import miningsim.world.*;
import org.junit.jupiter.api.Test;

class CellularDisplacementPlannerTest {
    private static RigidBodyOccupancyIndex.Snapshot occupancy(World w,int x,int y) {
        var r=new FragmentRegistry(1);
        r.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0))),x,y,0);
        var system=new RigidBodySystem(r); system.synchronize();
        return new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()).rebuild(system,0);
    }
    @Test void eachMaterialMovesConservativelyAndDeterministically() {
        for(var material:List.of(Material.SAND,Material.GRAVEL,Material.SOIL,Material.WATER)) {
            var world=new World(5,5); world.set(2,2,material); var occupancy=occupancy(world,2,2);
            var plan=CellularDisplacementPlanner.plan(world,occupancy,CellularDisplacementPlanner.Limits.defaults());
            assertEquals(1,plan.moves().size()); assertEquals(material,world.get(2,2));
            assertEquals(plan,CellularDisplacementPlanner.plan(world,occupancy,CellularDisplacementPlanner.Limits.defaults()));
            assertEquals(World.BatchResult.CHANGED,world.replaceAllIfMatches(plan.changes()));
            assertEquals(Material.AIR,world.get(2,2)); var move=plan.moves().getFirst();
            assertEquals(material,world.get(move.toX(),move.toY())); assertEquals(1,world.occupiedCount());
        }
    }
    @Test void barrierAndBudgetsFailWithoutChanges() {
        var world=new World(3,3); world.set(1,1,Material.SAND);
        for(int[] p:new int[][]{{0,1},{2,1},{1,0},{1,2}}) world.set(p[0],p[1],Material.STONE);
        var occupancy=occupancy(world,1,1); long revision=world.mutationRevision();
        var ex=assertThrows(IllegalStateException.class,()->CellularDisplacementPlanner.plan(world,occupancy,CellularDisplacementPlanner.Limits.defaults()));
        assertTrue(ex.getMessage().contains("1,1")); assertEquals(Material.SAND,world.get(1,1)); assertEquals(revision,world.mutationRevision());
        var limit=new CellularDisplacementPlanner.Limits(1,0,100,1,100,3);
        assertThrows(IllegalStateException.class,()->CellularDisplacementPlanner.plan(world,occupancy,limit));
    }
    @Test void cellularMaterialsCannotEnterBodyAcrossModes() {
        for(var mode:List.of(Simulation.Mode.FULL_SCAN,Simulation.Mode.ACTIVE_REGIONS))
            for(var material:List.of(Material.SAND,Material.GRAVEL,Material.SOIL,Material.WATER)) {
                var world=new World(5,5); world.set(2,1,material); world.set(1,2,Material.STONE); world.set(3,2,Material.STONE);
                if(material==Material.WATER) { world.set(1,1,Material.STONE); world.set(3,1,Material.STONE); }
                var simulation=new Simulation(world,42,mode,false); simulation.setOccupancy(occupancy(world,2,2));
                for(int i=0;i<5;i++) simulation.step();
                assertEquals(Material.AIR,world.get(2,2),mode+" "+material);
                assertEquals(material,world.get(2,1),mode+" "+material);
            }
    }
    @Test void waterCannotScanAcrossAnOccupiedCell() {
        var w=new World(8,5); w.set(1,1,Material.WATER); w.set(0,1,Material.STONE);
        w.set(0,2,Material.STONE); w.set(1,2,Material.STONE); w.set(2,2,Material.STONE);
        var sim=new Simulation(w,19); sim.setOccupancy(occupancy(w,3,1));
        sim.step(); assertNotEquals(Material.WATER,w.get(4,1)); assertEquals(5,w.occupiedCount());
    }
    @Test void stalePlanNeverOverwritesDestination() {
        var w=new World(5,5); w.set(2,2,Material.SAND);
        var plan=CellularDisplacementPlanner.plan(w,occupancy(w,2,2),CellularDisplacementPlanner.Limits.defaults());
        var destination=plan.moves().getFirst(); w.set(destination.toX(),destination.toY(),Material.WOOD); long before=w.mutationRevision();
        assertEquals(World.BatchResult.REJECTED,w.replaceAllIfMatches(plan.changes()));
        assertEquals(Material.SAND,w.get(2,2)); assertEquals(Material.WOOD,w.get(destination.toX(),destination.toY())); assertEquals(before,w.mutationRevision());
    }
    @Test void changedOccupancyWakesInactiveRegion() {
        for(var mode:List.of(Simulation.Mode.FULL_SCAN,Simulation.Mode.ACTIVE_REGIONS)) {
            var w=new World(70,8); w.set(40,1,Material.SOIL);
            var sim=new Simulation(w,3,mode,true); sim.setOccupancy(occupancy(w,40,2));
            for(int i=0;i<4;i++) sim.step();
            assertEquals(Material.SOIL,w.get(40,1));
            sim.setOccupancy(occupancy(w,1,1)); sim.step();
            assertEquals(Material.AIR,w.get(40,1)); assertEquals(Material.SOIL,w.get(40,2));
            if(mode==Simulation.Mode.ACTIVE_REGIONS) assertEquals(sim.totalTiles(),sim.activeTiles());
        }
    }
    @Test void multipleSourcesAndOverlappingBodiesReserveDistinctDestinations() {
        var w=new World(7,7); w.set(2,2,Material.SAND); w.set(3,2,Material.WATER);
        var registry=new FragmentRegistry(3);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0)));
        registry.insert(shape,2,2,0); registry.insert(shape,2,2,0); registry.insert(shape,3,2,0);
        var physics=new RigidBodySystem(registry); physics.synchronize();
        var index=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); var snapshot=index.rebuild(physics,0);
        assertEquals(2,snapshot.bodyIdsAt(2,2).length);
        var plan=CellularDisplacementPlanner.plan(w,snapshot,CellularDisplacementPlanner.Limits.defaults());
        assertEquals(2,plan.moves().size()); assertEquals(2,plan.moves().get(0).fromX()); assertEquals(3,plan.moves().get(1).fromX());
        assertNotEquals(List.of(plan.moves().get(0).toX(),plan.moves().get(0).toY()),List.of(plan.moves().get(1).toX(),plan.moves().get(1).toY()));
        assertEquals(World.BatchResult.CHANGED,w.replaceAllIfMatches(plan.changes()));
        assertEquals(2,w.occupiedCount());
        for(var move:plan.moves()) { assertEquals(move.material(),w.get(move.toX(),move.toY())); assertFalse(snapshot.occupied(move.toX(),move.toY())); }
    }
    @Test void searchAtEveryWorldCornerStaysInBounds() {
        for(int[] source:new int[][]{{0,0},{3,0},{0,3},{3,3}}) {
            var w=new World(4,4); w.set(source[0],source[1],Material.GRAVEL);
            var snapshot=occupancy(w,source[0],source[1]);
            var plan=CellularDisplacementPlanner.plan(w,snapshot,CellularDisplacementPlanner.Limits.defaults());
            var move=plan.moves().getFirst(); assertTrue(w.contains(move.toX(),move.toY())); assertFalse(snapshot.occupied(move.toX(),move.toY()));
            assertEquals(World.BatchResult.CHANGED,w.replaceAllIfMatches(plan.changes())); assertEquals(1,w.occupiedCount());
        }
    }
    @Test void occupiedDiagonalCannotReceiveFallingSand() {
        for(var mode:List.of(Simulation.Mode.FULL_SCAN,Simulation.Mode.ACTIVE_REGIONS)) {
            var w=new World(5,5); w.set(2,1,Material.SAND); w.set(2,2,Material.STONE); w.set(1,2,Material.STONE);
            var sim=new Simulation(w,42,mode,false); sim.setOccupancy(occupancy(w,3,2)); sim.step();
            assertEquals(Material.SAND,w.get(2,1)); assertEquals(Material.AIR,w.get(3,2)); assertEquals(3,w.occupiedCount());
        }
    }
}
