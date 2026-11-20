package miningsim.app;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.render.FragmentOverlay;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SandboxSessionFactoryTest {
    @Test void controlPauseStepResetAndWorldOnlyWarning() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE); world.set(5,8,Material.STONE);
        var s=SandboxSessionFactory.create(world,false); var control=new SimulationControl(s::tick);
        assertNotNull(s.lifecycle()); long tick=s.coupled().diagnostics().ticks();
        assertTrue(control.tick()); assertEquals(tick+1,s.coupled().diagnostics().ticks());
        control.togglePause(); assertFalse(control.tick()); assertEquals(tick+1,s.coupled().diagnostics().ticks());
        assertTrue(control.singleStep()); assertEquals(tick+2,s.coupled().diagnostics().ticks());
        control.togglePause();
        for(int i=0;i<1500 && s.lifecycle().settled().liveRocks()<2;i++) { control.tick(); assertNull(s.failure()); assertFalse(s.blocked()); }
        assertEquals(2,s.lifecycle().settled().liveRocks()); assertTrue(s.physics().bodies().isEmpty());
        assertEquals(2,s.lifecycle().diagnostics().settledRocks());
        var hud=new java.awt.image.BufferedImage(800,400,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var hg=hud.createGraphics(); var before=s.lifecycle().diagnostics();
        miningsim.render.LifecycleDiagnosticsOverlay.render(hg,s); hg.dispose();
        assertEquals(before,s.lifecycle().diagnostics(),"rendering must not advance lifecycle");
        assertTrue(SandboxSessionFactory.losesLifecycleState(s));
        assertEquals(0,s.occupancy().snapshot().diagnostics().indexedBodies());
        var image=new java.awt.image.BufferedImage(40,40,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics(); FragmentOverlay.renderBodies(graphics,new Camera2D(),s.physics().bodies()); graphics.dispose();
        for(int y=0;y<40;y++) for(int x=0;x<40;x++) assertEquals(0,image.getRGB(x,y));
        s.enqueue(()->Brush.paintWorld(world,5,18,0,Material.AIR,s.occupancy().snapshot()));
        for(int i=0;i<100 && s.lifecycle().diagnostics().reactivated()==0;i++) { control.tick(); assertNull(s.failure()); }
        assertEquals(1,s.lifecycle().diagnostics().reactivated());
        var fresh=SandboxSessionFactory.create(new World(20,20,World.Mode.BEDROCK_ENABLED),false); s.retire();
        assertEquals(0,fresh.physics().bodies().size()); assertEquals(0,fresh.lifecycle().settled().liveRocks());
        assertEquals(0,fresh.lifecycle().diagnostics().pendingSupport()); assertEquals(0,fresh.occupancy().snapshot().diagnostics().indexedBodies());
        assertFalse(SandboxSessionFactory.losesLifecycleState(fresh));
    }
    @Test void legacyAndReferenceSessionsRemainUnchanged() {
        var legacy=SandboxSessionFactory.create(new World(20,20,World.Mode.LEGACY),false);
        assertNull(legacy.lifecycle()); assertFalse(legacy.isCoupled()); legacy.tick(); assertEquals(1,legacy.cellularSteps());
        var scene=miningsim.structural.RockReferenceScenes.island();
        var reference=new InteractiveSession(scene.world(),new StructuralSimulationController(scene),false,false);
        assertNull(reference.lifecycle());
    }
    @Test void profilingCanBeEnabledAfterSessionConstruction() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE);
        var s=SandboxSessionFactory.create(world,false); s.enableProfiling(true);
        boolean measured=false;
        for(int i=0;i<900;i++) { s.tick(); assertNull(s.failure());
            if(s.lifecycle().diagnostics().planningNanos()>0) { measured=true; break; } }
        assertTrue(measured); assertTrue(s.coupled().phaseTimes().physicsNanos()>0);
    }
}
