package miningsim.benchmark;

import java.awt.image.BufferedImage;
import java.util.Locale;
import miningsim.app.*;
import miningsim.render.*;
import miningsim.simulation.Simulation;
import miningsim.structural.RockReferenceScenes;
import miningsim.world.World;

/** Exploratory headless timings; not a GUI frame-time benchmark. */
public final class StructuralUiBenchmark {
    public static void main(String[] args) {
        var normal = new Simulation(new World(400,300,World.Mode.BEDROCK_ENABLED),12345);
        long start=System.nanoTime(); for(int i=0;i<200;i++) normal.step();
        System.out.printf(Locale.ROOT,"ordinary empty 400x300 disabled: %.4f ms/tick%n",(System.nanoTime()-start)/200e6);
        var scene=RockReferenceScenes.roof("long",12,1); var controller=new StructuralSimulationController(scene);
        start=System.nanoTime(); for(int i=0;i<100;i++) controller.tick();
        System.out.printf(Locale.ROOT,"reference ticks: %.4f ms/tick%n",(System.nanoTime()-start)/100e6);
        scene.world().set(5,2,miningsim.world.Material.AIR);
        start=System.nanoTime(); controller.tick();
        System.out.printf(Locale.ROOT,"edit tick with connectivity/reanalysis: %.4f ms; assembly %.4f ms solve %.4f ms status=%s%n",
                (System.nanoTime()-start)/1e6,scene.damage().assemblyNanos()/1e6,scene.damage().solveNanos()/1e6,controller.view().status());
        var camera=new Camera2D(); var renderer=new Java2DRenderer(scene.world().width(),scene.world().height());
        var image=new BufferedImage(800,600,BufferedImage.TYPE_INT_RGB); var g=image.createGraphics();
        for(double zoom:new double[]{2,8,16}) {
            camera.reset(); camera.zoomAt(zoom,0,0);
            for(boolean overlay:new boolean[]{false,true}) {
                start=System.nanoTime(); for(int i=0;i<500;i++) { renderer.render(g,scene.world(),camera,800,600); if(overlay) CrackOverlay.render(g,camera,controller.view().marks()); }
                System.out.printf(Locale.ROOT,"zoom=%.0f overlay=%s repaint=%.4f ms%n",zoom,overlay,(System.nanoTime()-start)/500e6);
            }
        }
        g.dispose();
    }
}
