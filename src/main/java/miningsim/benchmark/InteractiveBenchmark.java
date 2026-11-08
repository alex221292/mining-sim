package miningsim.benchmark;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.app.*;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.RigidBodySystem;
import miningsim.render.FragmentOverlay;
import miningsim.render.CoupledDiagnosticsOverlay;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.structural.RockReferenceScenes;
import miningsim.world.Material;

/** Exploratory buffered-image rendering and fixed-tick measurements, no visible window. */
public final class InteractiveBenchmark {
    private static void report(String name,long[] times) {
        Arrays.sort(times); double mean=Arrays.stream(times).average().orElseThrow()/1e6;
        System.out.printf(Locale.ROOT,"%s mean=%.3fms p95=%.3fms n=%d%n",name,mean,times[(int)Math.ceil(times.length*.95)-1]/1e6,times.length);
    }
    private static void render(int count,boolean overlay) {
        var registry=new FragmentRegistry(count);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        for(int i=0;i<count;i++) registry.insert(shape,2+(i%20)*2,2+(i/20)*2,0);
        var physics=new RigidBodySystem(registry); physics.synchronize(); var camera=new Camera2D(); camera.zoomAt(8,0,0);
        var scene=RockReferenceScenes.interactive("overlay",Material.AIR); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick(); session.extractSelected(); session.tick();
        var image=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB); var g=image.createGraphics(); var bodies=physics.bodies(); long[] samples=new long[100];
        for(int i=-20;i<samples.length;i++) {
            long start=System.nanoTime(); g.setColor(Color.BLACK); g.fillRect(0,0,640,480); FragmentOverlay.renderBodies(g,camera,bodies);
            if(overlay) { g.setColor(Color.WHITE); CoupledDiagnosticsOverlay.render(g,session); }
            long elapsed=System.nanoTime()-start; if(i>=0) samples[i]=elapsed;
        }
        g.dispose(); report("render-"+count+"-overlay-"+overlay,samples);
    }
    private static void tick(Material material) {
        var scene=RockReferenceScenes.interactive("benchmark",material); var structural=new StructuralSimulationController(scene);
        var session=new InteractiveSession(scene.world(),structural); structural.tick(); session.extractSelected();
        long[] samples=new long[100]; int failures=0;
        for(int i=0;i<samples.length;i++) { long start=System.nanoTime(); session.tick(); samples[i]=System.nanoTime()-start; if(session.failure()!=null) failures++; }
        report("tick-"+material,samples); System.out.println("rejected="+failures+" blocked="+session.blocked()+" reason="+session.failure());
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        for(int count:new int[]{1,10,32,100}) { render(count,false); render(count,true); }
        tick(Material.SAND); tick(Material.WATER);
    }
}
