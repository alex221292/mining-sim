package miningsim.benchmark;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.app.*;
import miningsim.physics.*;
import miningsim.render.FragmentOverlay;
import miningsim.resource.*;
import miningsim.structural.*;
import miningsim.world.*;

/** Reproducible single-owner interactive timings; failures are never counted as successes. */
public final class IntegrationHardeningBenchmark {
    private static void run(String name,int bodies,boolean cellular,boolean dense,boolean sleeping,boolean structuralDamage) {
        World world; StructuralSimulationController structural;
        if(structuralDamage) {
            var scene=RockReferenceScenes.roof("long",12,1); world=scene.world(); structural=new StructuralSimulationController(scene);
        } else {
            world=new World(64,64,World.Mode.BEDROCK_ENABLED);
            var connectivity=new RockConnectivity(world);
            var damage=new RockDamageSystem(world,connectivity,RockReferenceScenes.MATERIALS,RockReferenceScenes.GRAVITY);
            structural=new StructuralSimulationController(new RockReferenceScenes.Scene(name,world,connectivity,damage,1,1));
        }
        if(cellular || dense) for(int y=40;y<45;y++) for(int x=20;x<40;x++) world.set(x,y,cellular && x%2==0?Material.WATER:Material.SAND);
        var session=new InteractiveSession(world,structural,true);
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        for(int i=0;i<bodies;i++) {
            int x=structuralDamage?1:dense?25:2+(i%10)*5;
            int y=structuralDamage?0:dense?39:sleeping?61-(i/10)*2:2+(i/10)*4;
            structural.registry().insert(descriptor,x,y,0);
        }
        session.physics().synchronize(); session.occupancy().rebuild(session.physics(),0);
        if(sleeping) for(int i=0;i<300 && !session.blocked();i++) session.tick();
        var image=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics(); var camera=new Camera2D(); camera.zoomAt(8,0,0);
        long[] samples=new long[50], render=new long[50]; int n=0; long structure=0,physics=0,occupancy=0,planning=0,cells=0,commit=0; String failure="";
        for(int i=0;i<50;i++) {
            long start=System.nanoTime(); session.tick(); long elapsed=System.nanoTime()-start;
            if(session.failure()!=null) { failure=session.failure(); break; }
            samples[n]=elapsed; var p=session.coupled().phaseTimes();
            structure+=session.structuralNanos(); physics+=p.physicsNanos(); occupancy+=p.occupancyNanos(); planning+=p.planningNanos(); commit+=p.commitNanos(); cells+=p.cellularNanos();
            start=System.nanoTime(); FragmentOverlay.renderBodies(graphics,camera,session.physics().bodies()); render[n]=System.nanoTime()-start; n++;
        }
        graphics.dispose();
        if(n==0) { System.out.println(name+" successes=0 failure="+failure); return; }
        samples=Arrays.copyOf(samples,n); render=Arrays.copyOf(render,n); double mean=Arrays.stream(samples).average().orElseThrow()/1e6, rm=Arrays.stream(render).average().orElseThrow()/1e6;
        Arrays.sort(samples); Arrays.sort(render); int p95=(int)Math.ceil(n*.95)-1;
        System.out.printf(Locale.ROOT,"%s n=%d mean=%.3f p95=%.3f structural=%.3f physics=%.3f occupancy=%.3f planning=%.3f commit=%.3f cells=%.3f render=%.3f/%.3f sleeping=%d failure=%s%n",
                name,n,mean,samples[p95]/1e6,structure/(double)n/1e6,physics/(double)n/1e6,occupancy/(double)n/1e6,planning/(double)n/1e6,
                commit/(double)n/1e6,cells/(double)n/1e6,rm,render[p95]/1e6,session.physics().interactionDiagnostics().sleepingBodies(),failure.isEmpty()?"none":failure);
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+" 64x64 except structural scene; 50 consecutive ticks, no warmup");
        run("empty",0,false,false,false,false); run("cellular",0,true,false,false,false);
        for(int n:new int[]{1,10,32,100}) run("bodies-"+n,n,false,false,false,false);
        run("dense",1,false,true,false,false); run("sleeping",10,false,false,true,false); run("structural",1,false,false,false,true);
    }
}
