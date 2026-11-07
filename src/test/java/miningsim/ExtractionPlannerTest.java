package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.ExtractionPlanner;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.structural.RockConnectivity;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class ExtractionPlannerTest {
    @Test void planningIsReadOnlyAndUsesCompletedConnectivity() {
        World w = new World(66,67,World.Mode.BEDROCK_ENABLED);
        w.set(63,2,Material.STONE); w.set(64,2,Material.STONE); w.set(64,3,Material.STONE);
        w.set(5,65,Material.STONE); w.set(2,2,Material.GRAVEL);
        RockConnectivity c = new RockConnectivity(w);
        assertEquals(ExtractionPlanner.Status.PENDING,ExtractionPlanner.prepare(w,c,63,2,64).status());
        while (c.pending()) c.advance(1000);
        long revision=c.revision(); int before=w.occupiedCount();
        assertEquals(ExtractionPlanner.Status.NOT_STONE,ExtractionPlanner.prepare(w,c,2,2,64).status());
        assertEquals(ExtractionPlanner.Status.SUPPORTED,ExtractionPlanner.prepare(w,c,5,65,64).status());
        assertEquals(ExtractionPlanner.Status.TOO_LARGE,ExtractionPlanner.prepare(w,c,63,2,2).status());
        var result=ExtractionPlanner.prepare(w,c,63,2,64);
        assertEquals(ExtractionPlanner.Status.READY,result.status());
        assertEquals(List.of(new Cell(63,2),new Cell(64,2),new Cell(64,3)),result.plan().worldCells());
        assertEquals(List.of(new Cell(0,0),new Cell(1,0),new Cell(1,1)),result.plan().descriptor().cells());
        assertEquals(7_500_000,result.plan().descriptor().massMilligrams());
        assertEquals(before,w.occupiedCount()); assertEquals(revision,c.revision());
    }
}
