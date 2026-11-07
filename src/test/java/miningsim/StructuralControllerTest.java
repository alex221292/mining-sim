package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.*;
import miningsim.render.CrackOverlay;
import miningsim.structural.*;
import miningsim.world.*;
import miningsim.simulation.Simulation;
import org.junit.jupiter.api.Test;

class StructuralControllerTest {
    @Test void shortAndLongMatchReferenceAndSplitCanBeInspected() {
        var shortScene = RockReferenceScenes.roof("short",4,1);
        var a = new StructuralSimulationController(shortScene);
        for(int i=0;i<600;i++) a.tick();
        assertEquals("CONVERGED",a.view().status()); assertEquals(0,a.view().damage());
        var scene = RockReferenceScenes.roof("long",12,1); var c = new StructuralSimulationController(scene);
        for(int i=0;i<36;i++) c.tick();
        assertTrue(c.view().damage()>0); long ticks=c.view().ticks();
        assertEquals(c.view(),c.view()); assertEquals(ticks,c.view().ticks());
        c.tick(); assertEquals(RockConnectivity.BondState.BROKEN,scene.connectivity().bondState(2,2,3,2));
        c.tick(); assertTrue(c.view().marks().stream().anyMatch(StructuralSimulationController.Mark::broken));
        c.select(3,2); c.tick(); if (c.view().status().equals("PENDING")) c.tick();
        assertTrue(c.view().status().equals("CONVERGED") || c.view().status().equals("UNSUPPORTED"));
        assertTrue(c.view().marks().stream().anyMatch(StructuralSimulationController.Mark::broken));
    }
    @Test void editsInvalidateAndRestoreFreshDamage() {
        var scene=RockReferenceScenes.roof("long",12,1); var c=new StructuralSimulationController(scene);
        c.tick(); assertTrue(c.view().damage()>0); scene.world().set(3,2,Material.AIR);
        assertEquals("PENDING",c.view().status()); scene.world().set(3,2,Material.STONE);
        c.tick(); assertEquals("CONVERGED",c.view().status());
        assertTrue(c.view().marks().stream().filter(m->m.x1()==2 && m.y1()==2 && m.x2()==3 && m.y2()==2).allMatch(m->m.damage()<0.1));
    }
    @Test void unsupportedAndGeometry() {
        var c = new StructuralSimulationController(RockReferenceScenes.island()); c.tick(); assertEquals("UNSUPPORTED",c.view().status());
        Camera2D camera=new Camera2D(); camera.zoomAt(10,0,0); camera.pan(2,3);
        var vertical=CrackOverlay.line(camera,new StructuralSimulationController.Mark(2,3,3,3,0.5,false));
        assertEquals(10,vertical.x1); assertEquals(0,vertical.y1); assertEquals(10,vertical.y2);
        var horizontal=CrackOverlay.line(camera,new StructuralSimulationController.Mark(2,3,2,4,1,true));
        assertEquals(0,horizontal.x1); assertEquals(10,horizontal.x2); assertEquals(10,horizontal.y1);
    }
    @Test void pauseSingleStepAndTooLarge() {
        var scene = RockReferenceScenes.roof("long",12,1);
        var c = new StructuralSimulationController(scene);
        var sim = new SimulationControl(new Simulation(scene.world(),12345));
        sim.togglePause();
        assertFalse(sim.tick()); assertEquals(0,c.view().ticks());
        assertTrue(sim.singleStep()); c.tick(); assertEquals(1,c.view().ticks());
        assertFalse(sim.tick()); assertEquals(1,c.view().ticks());
        World w = new World(70,3,World.Mode.BEDROCK_ENABLED);
        for (int x=0;x<65;x++) w.set(x,1,Material.STONE);
        RockConnectivity connectivity = new RockConnectivity(w);
        RockDamageSystem damage = new RockDamageSystem(w,connectivity,RockReferenceScenes.MATERIALS,RockReferenceScenes.GRAVITY);
        var big = new StructuralSimulationController(new RockReferenceScenes.Scene("big",w,connectivity,damage,0,1));
        big.tick(); assertEquals("TOO_LARGE",big.view().status());
    }
}
