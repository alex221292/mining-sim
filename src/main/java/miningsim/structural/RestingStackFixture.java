package miningsim.structural;

import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.World;

/** Deterministic stress fixture; initial fragments are scene-owned generated material. */
public final class RestingStackFixture {
    private RestingStackFixture() { }
    public static RockReferenceScenes.Scene scene() {
        var world=new World(64,40,World.Mode.BEDROCK_ENABLED);
        var connectivity=new RockConnectivity(world);
        var damage=new RockDamageSystem(world,connectivity,RockReferenceScenes.MATERIALS,RockReferenceScenes.GRAVITY);
        return new RockReferenceScenes.Scene("resting stack",world,connectivity,damage,0,0);
    }
    public static void populate(FragmentRegistry registry) {
        var stone=SubstanceRegistry.reference().get(SubstanceRegistry.STONE);
        var lower=new FragmentDescriptor(stone,List.of(new Cell(0,0),new Cell(1,0),new Cell(2,0),new Cell(0,1),new Cell(1,1),new Cell(2,1),new Cell(1,2)));
        var upper=new FragmentDescriptor(stone,List.of(new Cell(0,0),new Cell(1,0),new Cell(2,0),new Cell(3,0),new Cell(4,0),new Cell(5,0),new Cell(6,0),new Cell(7,0),new Cell(1,1),new Cell(2,1),new Cell(3,1),new Cell(4,1),new Cell(5,1),new Cell(6,1)));
        registry.insert(lower,20,32,0); registry.insert(lower,25,32,0); registry.insert(upper,20,25,0);
    }
}
