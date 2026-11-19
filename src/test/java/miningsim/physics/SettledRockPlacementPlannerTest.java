package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettledRockPlacementPlannerTest {
    private static final SettledRockPlacementPlanner.Limits LIMITS=new SettledRockPlacementPlanner.Limits(100,100000,0.3,0.90);
    private static FragmentDescriptor shape(FragmentDescriptor.Cell... cells) {
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(cells));
    }
    private static SettledRockPlacementPlanner.Result plan(World w,FragmentDescriptor shape,double x,double y,double angle) {
        var topology=new RockConnectivity(w); var settled=new SettledRockRegistry(w,topology,5,100);
        var occupancy=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults());
        return SettledRockPlacementPlanner.prepare(new SettledRockPlacementPlanner.Pose(1,shape,x,y,angle),w,occupancy.snapshot(),settled,LIMITS);
    }
    @Test void rectangleAndQuarterTurnPreserveExactCellsAndMass() {
        var shape=shape(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0));
        var a=plan(new World(20,20),shape,6.5,5.5,0);
        assertEquals(SettledRockPlacementPlanner.Status.READY,a.status()); assertEquals(3,a.plan().cells().size());
        assertEquals(shape.massMilligrams(),a.plan().descriptor().massMilligrams()); assertEquals(shape.substance(),a.plan().descriptor().substance());
        var b=plan(new World(20,20),shape,5.5,6.5,Math.PI/2);
        assertEquals(SettledRockPlacementPlanner.Status.READY,b.status());
        assertEquals(List.of(new SettledRockPlacementPlanner.Cell(5,5),new SettledRockPlacementPlanner.Cell(5,6),new SettledRockPlacementPlanner.Cell(5,7)),b.plan().cells());
    }
    @Test void concavityIsNotFilledAndReplayIsStable() {
        var shape=shape(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(0,1));
        var a=plan(new World(20,20),shape,5+5.0/6,5+5.0/6,0);
        var b=plan(new World(20,20),shape,5+5.0/6,5+5.0/6,0);
        assertEquals(SettledRockPlacementPlanner.Status.READY,a.status()); assertEquals(a.plan().cells(),b.plan().cells());
        assertFalse(a.plan().cells().contains(new SettledRockPlacementPlanner.Cell(6,6)));
    }
    @Test void materialConflictRejectsWithoutMutation() {
        var world=new World(20,20); world.set(5,5,Material.SAND); long revision=world.mutationRevision();
        var result=plan(world,shape(new FragmentDescriptor.Cell(0,0)),5.5,5.5,0);
        assertEquals(SettledRockPlacementPlanner.Status.NOT_SETTLEABLE,result.status());
        assertEquals(Material.SAND,world.get(5,5)); assertEquals(revision,world.mutationRevision());
    }
    @Test void bedrockAndForeignStoneAreNeverOverwritten() {
        var shape=shape(new FragmentDescriptor.Cell(0,0));
        var floor=new World(20,20,World.Mode.BEDROCK_ENABLED);
        assertEquals(SettledRockPlacementPlanner.Status.NOT_SETTLEABLE,plan(floor,shape,5.5,19.5,0).status());
        var stone=new World(20,20); stone.set(5,5,Material.STONE);
        assertEquals(SettledRockPlacementPlanner.Status.NOT_SETTLEABLE,plan(stone,shape,5.5,5.5,0).status());
    }
    @Test void otherRigidOccupantRejectsPlacement() {
        var world=new World(20,20); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,5,100);
        var fragments=new miningsim.fragment.FragmentRegistry(2); var shape=shape(new FragmentDescriptor.Cell(0,0));
        fragments.insert(shape,1,1,0); fragments.insert(shape,5,5,0);
        var physics=new RigidBodySystem(fragments); physics.synchronize();
        var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var result=SettledRockPlacementPlanner.prepare(new SettledRockPlacementPlanner.Pose(1,shape,5.5,5.5,0),world,occupancy.snapshot(),settled,LIMITS);
        assertEquals(SettledRockPlacementPlanner.Status.NOT_SETTLEABLE,result.status());
    }
    @Test void smallRotationAndLongShapeStayConnected() {
        var cells=new FragmentDescriptor.Cell[12]; for(int i=0;i<cells.length;i++) cells[i]=new FragmentDescriptor.Cell(i,0);
        var shape=shape(cells); var result=plan(new World(40,40),shape,16,15.5,0.01);
        assertEquals(SettledRockPlacementPlanner.Status.READY,result.status()); assertEquals(12,result.plan().cells().size());
        assertEquals(shape.massMilligrams(),result.plan().descriptor().massMilligrams());
    }
}
