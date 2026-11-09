package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.InteractiveSession;
import miningsim.app.StructuralSimulationController;
import miningsim.fragment.ExtractionCommit;
import miningsim.structural.RockReferenceScenes;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.app.Brush;
import miningsim.app.Camera2D;
import miningsim.render.FragmentOverlay;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.RigidBodySystem;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class InteractiveSessionTest {
    @Test void demoExtractionAndMotion() {
        for(var material:new Material[]{Material.AIR,Material.SAND,Material.WATER}) {
            var scene=RockReferenceScenes.interactive("test",material);
            var structural=new StructuralSimulationController(scene);
            var session=new InteractiveSession(scene.world(),structural);
            structural.tick();
            assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected(),structural.view().status()+" "+structural.extractionMessage());
            assertEquals(Material.AIR,scene.world().get(12,3)); assertEquals(1,session.physics().bodies().size());
            long mass=structural.registry().totalMassMilligrams(); int count=0;
            for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) if(scene.world().get(x,y)==material) count++;
            int displaced=0;
            for(int i=0;i<100;i++) { session.tick(); var d=session.coupled().diagnostics(); displaced+=d.sand()+d.water(); }
            assertFalse(session.blocked(),material+" "+session.failure());
            assertTrue(session.physics().body(1).yCells()>3.5); assertEquals(mass,structural.registry().totalMassMilligrams());
            if(material!=Material.AIR) {
                assertTrue(displaced>0,material+" never displaced");
                int after=0; for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) if(scene.world().get(x,y)==material) after++;
                assertEquals(count,after);
            }
        }
    }
    @Test void queuedCommandsPrecedePhysicsAndBrushHonorsOccupancy() {
        var scene=RockReferenceScenes.interactive("test",Material.AIR); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick();
        session.enqueue(()->assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected()));
        session.tick(); assertEquals(1,session.physics().bodies().size()); assertEquals(1,session.coupled().diagnostics().ticks());
        assertEquals(miningsim.physics.CoupledSimulationStep.CellularOutcome.COMPLETED,session.coupled().diagnostics().cellularOutcome());
        var snapshot=session.occupancy().snapshot();
        for(var material:List.of(Material.STONE,Material.SAND,Material.GRAVEL,Material.SOIL,Material.WATER,Material.WOOD)) {
            Brush.paintWorld(scene.world(),12.5,3.5,0,material,snapshot); assertEquals(Material.AIR,scene.world().get(12,3));
        }
        Brush.paintWorld(scene.world(),12.5,3.5,0,Material.AIR,snapshot); assertEquals(1,session.physics().bodies().size());
    }
    @Test void failureSkipsCellularAndNewSessionDoesNotRetainBodies() {
        var scene=RockReferenceScenes.interactive("test",Material.AIR); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick(); assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
        var body=session.physics().body(1); body.setVelocity(Double.MAX_VALUE,0,0); var snapshot=session.occupancy().snapshot();
        double y=body.yCells(); session.tick(); assertSame(snapshot,session.occupancy().snapshot()); assertEquals(y,body.yCells());
        assertEquals(0,session.coupled().diagnostics().ticks()); assertNotNull(session.failure());
        session.tick(); session.tick(); assertTrue(session.blocked());
        body.setVelocity(0,0,0); session.resume(); assertFalse(session.blocked()); session.tick();
        assertNull(session.failure()); assertTrue(session.recovered()); assertEquals(1,session.coupled().diagnostics().ticks());
        var other=RockReferenceScenes.island(); var next=new InteractiveSession(other.world(),new StructuralSimulationController(other));
        assertNotSame(session.world(),next.world()); assertTrue(next.physics().bodies().isEmpty());
        var legacy=new InteractiveSession(new World(5,5),null); assertFalse(legacy.isCoupled()); legacy.tick();
    }
    @Test void transformedConcaveFragmentUsesActualCells() {
        var registry=new FragmentRegistry(1);
        registry.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),
                List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(0,1))),3,3,0);
        var physics=new RigidBodySystem(registry); physics.synchronize(); var body=physics.body(1);
        body.setVelocity(0.2,0.1,1.0); physics.step();
        var camera=new Camera2D(); camera.zoomAt(16,0,0);
        var image=new BufferedImage(160,160,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics();
        FragmentOverlay.renderBodies(graphics,camera,physics.bodies()); graphics.dispose();
        for(var cell:body.fragment().descriptor().cells()) {
            var p=body.geometry().transform(cell.x()+0.5,cell.y()+0.5,body.xCells(),body.yCells(),body.angleRadians());
            assertEquals(0x808890,image.getRGB((int)camera.worldToScreenX(p.x()),(int)camera.worldToScreenY(p.y()))&0xffffff);
        }
        var hole=body.geometry().transform(1.5,1.5,body.xCells(),body.yCells(),body.angleRadians());
        assertEquals(0,image.getRGB((int)camera.worldToScreenX(hole.x()),(int)camera.worldToScreenY(hole.y()))&0xffffff);
    }
    @Test void fallingRockEventuallySettles() {
        var scene=RockReferenceScenes.interactive("fall",Material.AIR); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick(); assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
        for(int i=0;i<400;i++) session.tick();
        assertFalse(session.blocked(),session.failure());
        assertTrue(session.physics().body(1).sleeping(),"body did not sleep");
        assertTrue(session.physics().body(1).yCells()>17);
    }
    @Test void versionOneLoadRemainsLegacy() throws Exception {
        var original=new World(5,5); original.set(2,2,Material.SAND);
        var bytes=new java.io.ByteArrayOutputStream(); miningsim.persistence.WorldIO.write(original,bytes);
        var loaded=miningsim.persistence.WorldIO.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        var session=new InteractiveSession(loaded,null); assertFalse(session.isCoupled());
        assertEquals(World.Mode.LEGACY,loaded.mode()); assertEquals(Material.SAND,loaded.get(2,2));
    }
    @Test void unsupportedExtractionMustBeAnalyzedAndNeverDuplicates() {
        var scene=RockReferenceScenes.interactive("test",Material.AIR); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); long revision=scene.world().mutationRevision();
        assertEquals(ExtractionCommit.Status.INVALID,session.extractSelected());
        assertEquals(revision,scene.world().mutationRevision()); assertEquals(0,structural.registry().size());
        structural.tick(); assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
        assertEquals(ExtractionCommit.Status.INVALID,session.extractSelected());
        assertEquals(1,structural.registry().size()); assertEquals(1,session.physics().bodies().size());
    }
    @Test void retiredSessionDiscardsQueuedAndLateCommands() {
        var scene=RockReferenceScenes.interactive("old",Material.AIR);
        var old=new InteractiveSession(scene.world(),new StructuralSimulationController(scene));
        long revision=scene.world().mutationRevision();
        old.enqueue(()->scene.world().set(1,1,Material.SAND)); old.retire();
        old.enqueue(()->scene.world().set(2,1,Material.WATER)); old.tick();
        assertEquals(revision,scene.world().mutationRevision());
        assertEquals(Material.AIR,scene.world().get(1,1)); assertEquals(Material.AIR,scene.world().get(2,1));
    }
    @Test void renderingAndLongDiagnosticsNeverAdvanceState() {
        var scene=RockReferenceScenes.interactive("render",Material.AIR); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick(); assertEquals(ExtractionCommit.Status.COMMITTED,session.extractSelected());
        session.physics().body(1).setVelocity(Double.MAX_VALUE,0,0); session.tick();
        long worldRevision=scene.world().mutationRevision(), occupancyRevision=session.occupancy().snapshot().diagnostics().revision();
        double x=session.physics().body(1).xCells(), y=session.physics().body(1).yCells();
        var camera=new Camera2D(); camera.zoomAt(16,40,30); camera.pan(12,3);
        var image=new BufferedImage(80,80,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics();
        FragmentOverlay.renderBodies(graphics,camera,session.physics().bodies());
        miningsim.render.CoupledDiagnosticsOverlay.render(graphics,session); graphics.dispose();
        assertEquals(worldRevision,scene.world().mutationRevision()); assertEquals(occupancyRevision,session.occupancy().snapshot().diagnostics().revision());
        assertEquals(x,session.physics().body(1).xCells()); assertEquals(y,session.physics().body(1).yCells());
    }
    @Test void overlappingBodyRenderingIsOrderIndependentAndClipped() {
        var registry=new FragmentRegistry(2); var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        registry.insert(shape,0,0,0); registry.insert(shape,0,0,0); var physics=new RigidBodySystem(registry); physics.synchronize();
        var camera=new Camera2D(); camera.zoomAt(16,0,0); camera.pan(.5,.5);
        var first=new BufferedImage(30,30,BufferedImage.TYPE_INT_RGB); var second=new BufferedImage(30,30,BufferedImage.TYPE_INT_RGB);
        var a=first.createGraphics(); var b=second.createGraphics();
        FragmentOverlay.renderBodies(a,camera,physics.bodies());
        FragmentOverlay.renderBodies(b,camera,physics.bodies().reversed()); a.dispose(); b.dispose();
        assertEquals(0x808890,first.getRGB(0,0)&0xffffff);
        assertArrayEquals(first.getRGB(0,0,30,30,null,0,30),second.getRGB(0,0,30,30,null,0,30));
        assertEquals(2,physics.bodies().size()); assertEquals(5_000_000,registry.totalMassMilligrams());
    }
}
