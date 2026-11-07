package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.StructuralSimulationController;
import miningsim.fragment.ExtractionCommit;
import miningsim.structural.RockReferenceScenes;
import org.junit.jupiter.api.Test;

class FragmentControllerTest {
    @Test void unsupportedVisibleBeforeExplicitExtractionAndSceneResets() {
        var scene=RockReferenceScenes.island(); var c=new StructuralSimulationController(scene);
        assertEquals(ExtractionCommit.Status.INVALID,c.extractSelected());
        c.tick(); assertEquals("UNSUPPORTED",c.view().status()); long tick=c.view().ticks();
        assertEquals(ExtractionCommit.Status.COMMITTED,c.extractSelected()); assertEquals(tick,c.view().ticks());
        assertEquals(1,c.fragments().size()); assertEquals(1,c.fragments().getFirst().id());
        c.tick(); assertEquals("NO SELECTION",c.view().status());
        var reset=new StructuralSimulationController(RockReferenceScenes.island()); assertTrue(reset.fragments().isEmpty());
        reset.tick(); reset.extractSelected(); assertEquals(1,reset.fragments().getFirst().id());
    }
}
