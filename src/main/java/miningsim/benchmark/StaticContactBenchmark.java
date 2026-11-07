package miningsim.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.*;

/** Exploratory full-tick timings; includes allocation and all position re-queries. */
public final class StaticContactBenchmark {
    private static FragmentDescriptor shape(int cells) {
        var list=new ArrayList<Cell>();
        for(int i=0;i<cells;i++) list.add(new Cell(i % 8,i / 8));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),list);
    }
    private static void run(String name, FragmentDescriptor shape, int count) {
        var w=new World(128,48); for(int x=0;x<128;x++) w.set(x,40,Material.STONE);
        var registry=new FragmentRegistry(count);
        for(int i=0;i<count;i++) registry.insert(shape,2+i*12,20,0);
        var system=new RigidBodySystem(registry,4,new StaticContactSolver(new StaticCollisionQuery(w,100000,1000000),StaticContactSolver.Config.defaults()));
        for(int i=0;i<120;i++) system.step();
        int samples=300; long[] times=new long[samples]; long candidates=0, checks=0, contacts=0, iterations=0;
        for(int i=0;i<samples;i++) {
            long start=System.nanoTime(); system.step(); times[i]=System.nanoTime()-start;
            var d=system.diagnostics(); candidates+=d.candidates(); checks+=d.checks(); contacts+=d.contacts(); iterations+=d.velocityIterations();
        }
        double average=Arrays.stream(times).average().orElseThrow()/1e6; Arrays.sort(times);
        System.out.printf(Locale.ROOT,"%s mean=%.4fms p95=%.4fms candidates=%.1f checks=%.1f contacts=%.1f iterations=%.1f%n",
                name,average,times[(int)(samples*0.95)-1]/1e6,candidates/(double)samples,checks/(double)samples,contacts/(double)samples,iterations/(double)samples);
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name"));
        run("single",shape(1),1); run("irregular16", irregular(),1); run("64",shape(64),1); run("several",shape(1),6);
    }
    private static FragmentDescriptor irregular() {
        var cells=new ArrayList<Cell>(); for(int x=0;x<8;x++) cells.add(new Cell(x,0));
        for(int x=0;x<5;x++) cells.add(new Cell(x,1)); for(int x=0;x<3;x++) cells.add(new Cell(x,2));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
}
