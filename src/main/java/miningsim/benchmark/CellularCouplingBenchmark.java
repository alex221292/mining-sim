package miningsim.benchmark;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.*;
import miningsim.world.*;

/** One-tick repeatable exploratory samples, setup outside the timed section. */
public final class CellularCouplingBenchmark {
    private record Scene(String name,int bodies,Material material,boolean dense,boolean full) { }
    private static void run(Scene scene) {
        long[] samples=new long[40], failures=new long[40]; int successes=0,failed=0; long occ=0,plan=0,commit=0,cells=0,displaced=0,search=0,sweep=0,poses=0; String failure="";
        for(int iteration=-10;iteration<40;iteration++) {
            var world=new World(64,64);
            if(scene.full()) for(int y=0;y<64;y++) for(int x=0;x<64;x++) world.set(x,y,Material.STONE);
            if(scene.dense()) for(int y=16;y<28;y++) for(int x=16;x<28;x++) world.set(x,y,Material.SAND);
            var registry=new FragmentRegistry(scene.bodies());
            var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0)));
            for(int i=0;i<scene.bodies();i++) {
                int x=scene.dense()?20:3+(i%8)*7,y=scene.dense()?15:3+(i/8)*7;
                if(scene.full()) world.set(x,y,Material.AIR);
                registry.insert(shape,x,y,0);
                if(scene.material()!=Material.AIR) world.set(x,y+1,scene.material());
            }
            var solver=new StaticContactSolver(new StaticCollisionQuery(world,100000,1000000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
            var physics=new RigidBodySystem(registry,4,solver); physics.synchronize();
            for(var body:physics.bodies()) body.setVelocity(0,3,0);
            var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults());
            var coupled=new CoupledSimulationStep(world,physics,index,CellularDisplacementPlanner.Limits.defaults(),true);
            var simulation=new Simulation(world,123);
            long start=System.nanoTime();
            try {
                coupled.step(); coupled.stepCells(simulation); long elapsed=System.nanoTime()-start;
                if(iteration>=0) {
                    samples[successes++]=elapsed; var p=coupled.phaseTimes(); var d=coupled.diagnostics();
                    occ+=p.occupancyNanos(); plan+=p.planningNanos(); commit+=p.commitNanos(); cells+=p.cellularNanos();
                    displaced+=d.sand()+d.gravel()+d.soil()+d.water(); search+=d.candidates(); sweep+=d.sweepCandidates(); poses+=d.sweepPoses();
                }
            } catch(RuntimeException ex) { if(iteration>=0) { failures[failed++]=System.nanoTime()-start; failure=coupled.lastFailureStage()+": "+ex.getMessage(); } }
        }
        if(failed>0) {
            long[] actual=Arrays.copyOf(failures,failed); double mean=Arrays.stream(actual).average().orElseThrow()/1e6; Arrays.sort(actual);
            System.out.printf(Locale.ROOT,"%s rejected=%d mean=%.3fms p95=%.3fms reason=%s%n",scene.name(),failed,mean,actual[(int)Math.ceil(failed*0.95)-1]/1e6,failure);
        }
        if(successes==0) return;
        long[] actual=Arrays.copyOf(samples,successes); double mean=Arrays.stream(actual).average().orElseThrow()/1e6; Arrays.sort(actual);
        System.out.printf(Locale.ROOT,"%s mean=%.3fms p95=%.3fms occupancy=%.3fms plan=%.3fms commit=%.3fms cells=%.3fms moved=%.1f search=%.1f sweep=%.1f poses=%.1f failures=%d%n",
                scene.name(),mean,actual[(int)Math.ceil(successes*0.95)-1]/1e6,occ/(double)successes/1e6,plan/(double)successes/1e6,
                commit/(double)successes/1e6,cells/(double)successes/1e6,displaced/(double)successes,search/(double)successes,sweep/(double)successes,poses/(double)successes,failed);
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        for(var scene:List.of(new Scene("no-bodies",0,Material.AIR,false,false),new Scene("air",1,Material.AIR,false,false),
                new Scene("sand",1,Material.SAND,false,false),new Scene("water",1,Material.WATER,false,false),
                new Scene("ten",10,Material.AIR,false,false),new Scene("32",32,Material.AIR,false,false),
                new Scene("dense",1,Material.SAND,true,false),new Scene("near-full",1,Material.SAND,false,true))) run(scene);
    }
}
