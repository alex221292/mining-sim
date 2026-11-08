package miningsim.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.World;

/** Headless rebuild timings; no physics is advanced during measurement. */
public final class RigidOccupancyBenchmark {
    private record Scene(String name,int count,int cells,boolean rotated,boolean dense) { }
    private static FragmentDescriptor shape(int n,boolean irregular) {
        var cells=new ArrayList<Cell>();
        if(irregular) { for(int x=0;x<8;x++) cells.add(new Cell(x,0)); for(int x=0;x<5;x++) cells.add(new Cell(x,1)); for(int x=0;x<3;x++) cells.add(new Cell(x,2)); }
        else for(int i=0;i<n;i++) cells.add(new Cell(i%8,i/8));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
    private static void run(Scene scenario) {
        var world=new World(400,300); var registry=new FragmentRegistry(scenario.count()); var descriptor=shape(scenario.cells(),scenario.rotated());
        for(int i=0;i<scenario.count();i++) registry.insert(descriptor,scenario.dense()?20:4+(i%10)*30,scenario.dense()?20:4+(i/10)*25,0);
        var system=new RigidBodySystem(registry); system.synchronize();
        if(scenario.rotated()) for(var body:system.bodies()) { var s=body.state(); body.restore(new RigidBody.State(s.x(),s.y(),0.37,s.vx(),s.vy(),s.omega(),s.sleeping(),s.sleepTicks())); }
        var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults());
        try {
            for(int i=0;i<60;i++) index.rebuild(system,i);
            long[] times=new long[200];
            for(int i=0;i<times.length;i++) { long start=System.nanoTime(); index.rebuild(system,i); times[i]=System.nanoTime()-start; }
            var d=index.snapshot().diagnostics(); double mean=Arrays.stream(times).average().orElseThrow()/1e6; Arrays.sort(times);
            System.out.printf(Locale.ROOT,"%s mean=%.4fms p95=%.4fms candidates=%d checks=%d cells=%d memberships=%d%n",
                    scenario.name(),mean,times[189]/1e6,d.candidates(),d.areaChecks(),d.occupiedCells(),d.memberships());
        } catch(RuntimeException ex) { System.out.println(scenario.name()+" FAILED: "+ex); }
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        for(var s:List.of(new Scene("single",1,1,false,false),new Scene("16",1,16,false,false),new Scene("64",1,64,false,false),
                new Scene("ten",10,1,false,false),new Scene("32",32,1,false,false),new Scene("100",100,1,false,false),
                new Scene("rotated-irregular",10,16,true,false),new Scene("dense",100,1,false,true))) run(s);
    }
}
