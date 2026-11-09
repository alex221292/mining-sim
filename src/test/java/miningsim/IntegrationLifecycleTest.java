package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.*;
import miningsim.fragment.ExtractionCommit;
import miningsim.structural.*;
import miningsim.world.*;
import org.junit.jupiter.api.Test;

class IntegrationLifecycleTest {
    private static RockReferenceScenes.Scene scene(int[][] cells) {
        var world=new World(12,12,World.Mode.BEDROCK_ENABLED);
        for(var cell:cells) world.set(cell[0],cell[1],Material.STONE);
        var connectivity=new RockConnectivity(world);
        var damage=new RockDamageSystem(world,connectivity,RockReferenceScenes.MATERIALS,RockReferenceScenes.GRAVITY);
        return new RockReferenceScenes.Scene("lifecycle",world,connectivity,damage,cells[0][0],cells[0][1]);
    }
    @Test void irregularAndBoundaryExtractionPreserveGeometryAndMatter() {
        for(var cells:new int[][][]{{{0,1},{0,2},{1,2}},{{5,3},{6,3},{5,4}}}) {
            var scene=scene(cells); var world=scene.world(); world.set(3,6,Material.SAND); world.set(4,6,Material.WATER);
            var structural=new StructuralSimulationController(scene); var session=new InteractiveSession(world,structural);
            structural.tick(); assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected(),structural.extractionMessage());
            assertEquals(1,structural.registry().size()); assertEquals(1,session.physics().bodies().size());
            assertEquals(2_500_000L*cells.length,structural.registry().totalMassMilligrams());
            for(var cell:cells) { assertEquals(Material.AIR,world.get(cell[0],cell[1])); assertTrue(session.occupancy().snapshot().occupied(cell[0],cell[1])); }
            assertEquals(Material.SAND,world.get(3,6)); assertEquals(Material.WATER,world.get(4,6));
        }
    }
    @Test void adjacentNewExtractionDoesNotDuplicateFirstBody() {
        var scene=scene(new int[][]{{4,3}}); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick();
        assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
        scene.world().set(5,3,Material.STONE); structural.select(5,3); structural.tick();
        assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected(),structural.extractionMessage());
        assertEquals(2,structural.registry().size()); assertEquals(2,session.physics().bodies().size());
        assertArrayEquals(new long[]{1},session.occupancy().snapshot().bodyIdsAt(4,3));
        assertArrayEquals(new long[]{2},session.occupancy().snapshot().bodyIdsAt(5,3));
        assertEquals(5_000_000,structural.registry().totalMassMilligrams());
    }
    @Test void staleSelectionAfterEditCannotExtract() {
        var scene=scene(new int[][]{{4,3},{5,3}}); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick();
        scene.world().set(4,3,Material.AIR); long revision=scene.world().mutationRevision();
        assertEquals(ExtractionCommit.Status.INVALID,session.extractSelected()); assertEquals(revision,scene.world().mutationRevision());
        assertEquals(Material.STONE,scene.world().get(5,3)); assertEquals(0,structural.registry().size());
        assertTrue(session.physics().bodies().isEmpty());
    }
    @Test void supportEditWakesSleepingBodyOnNextTick() {
        var scene=RockReferenceScenes.interactive("wake",Material.AIR); var structural=new StructuralSimulationController(scene);
        scene.world().set(12,15,Material.WOOD);
        var session=new InteractiveSession(scene.world(),structural); structural.tick();
        assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
        for(int i=0;i<400;i++) session.tick(); assertTrue(session.physics().body(1).sleeping());
        session.enqueue(()->Brush.paintWorld(scene.world(),12.5,15.5,0,Material.AIR,session.occupancy().snapshot())); session.tick();
        assertFalse(session.physics().body(1).sleeping());
    }
    @Test void oversizedExtractionIsRejectedWithoutMutation() {
        int[][] cells=new int[72][2]; int i=0;
        for(int y=1;y<=8;y++) for(int x=1;x<=9;x++) cells[i++]=new int[]{x,y};
        var scene=scene(cells); var structural=new StructuralSimulationController(scene); var session=new InteractiveSession(scene.world(),structural);
        structural.tick(); long revision=scene.world().mutationRevision();
        assertEquals(ExtractionCommit.Status.INVALID,session.extractSelected());
        assertTrue(structural.extractionMessage().contains("TOO_LARGE")); assertEquals(revision,scene.world().mutationRevision());
        assertEquals(0,structural.registry().size()); assertTrue(session.physics().bodies().isEmpty());
        for(var cell:cells) assertEquals(Material.STONE,scene.world().get(cell[0],cell[1]));
    }
}
