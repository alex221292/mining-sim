package miningsim.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import miningsim.app.InteractiveSession;
import miningsim.app.StructuralSimulationController;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.structural.RockConnectivity;
import miningsim.structural.RockDamageSystem;
import miningsim.structural.RockReferenceScenes;
import miningsim.world.World;
import miningsim.world.Material;

/** Same bounded, instrumented interactive fixture for before/after comparison. */
public final class LargeCompoundBenchmark {
    private static FragmentDescriptor shape(int width,int height) {
        var cells=new ArrayList<Cell>();
        for(int y=0;y<height;y++) for(int x=0;x<width;x++)
            if(!(y<2 && (x<2 || x>=width-2)) && !(y==2 && x%9==4)) cells.add(new Cell(x,y));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
    public record Fixture(InteractiveSession session,List<Integer> sizes) { }
    public static Fixture fixture(String kind) {
        var world=new World(64,64,World.Mode.BEDROCK_ENABLED); var connectivity=new RockConnectivity(world);
        var damage=new RockDamageSystem(world,connectivity,RockReferenceScenes.MATERIALS,RockReferenceScenes.GRAVITY);
        var structural=new StructuralSimulationController(new RockReferenceScenes.Scene(kind,world,connectivity,damage,0,0));
        boolean large=kind.equals("large") || kind.equals("largeWood");
        var lower=shape(18,8); var upper=shape(large?54:43,large?10:4);
        boolean separate=kind.equals("control");
        if(kind.equals("largeWood")) for(int x=0;x<64;x++) world.set(x,62,Material.WOOD);
        int lowerY=kind.equals("largeWood")?54:55;
        structural.registry().insert(lower,6,lowerY,0); structural.registry().insert(lower,31,lowerY,0);
        structural.registry().insert(upper,6,separate?0:kind.equals("rest")?51:40,0);
        var session=new InteractiveSession(world,structural,true); session.physics().synchronize(); session.physics().enableProfiling(true);
        session.occupancy().rebuild(session.physics(),0);
        return new Fixture(session,List.of(lower.cellCount(),lower.cellCount(),upper.cellCount()));
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+" 64x64, default interactive configuration, 360 ticks (control 40)");
        for(String kind:args.length==0?List.of("rest","impact","large","control"):List.of(args)) {
            var fixture=fixture(kind); var s=fixture.session(); int max=kind.equals("control")?40:360;
            long[][] times={new long[10],new long[20],new long[60],new long[150],new long[120]};
            long[] body=new long[5],stat=new long[5],occupancy=new long[5],plan=new long[5],checks=new long[5],raw=new long[5],reduced=new long[5],micro=new long[5],wakes=new long[5],cellular=new long[5],solver=new long[5];
            int[] counts=new int[5]; int count=0,firstSleep=-1; String failure="";
            for(int i=0;i<max;i++) {
                long start=System.nanoTime();
                try { s.tick(); } catch(RuntimeException ex) { failure=ex.toString(); break; }
                if(s.failure()!=null) { failure=s.failure(); break; }
                int phase=i<10?0:i<30?1:i<90?2:i<240?3:4; times[phase][counts[phase]++]=System.nanoTime()-start; count++;
                var p=s.physics().profile(); body[phase]+=p.bodyQueryNanos(); stat[phase]+=p.staticQueryNanos(); solver[phase]+=p.bodySolverNanos()+p.staticSolverNanos();
                var cp=s.coupled().phaseTimes(); occupancy[phase]+=cp.occupancyNanos(); plan[phase]+=cp.planningNanos(); cellular[phase]+=cp.cellularNanos();
                var d=s.physics().interactionDiagnostics(); checks[phase]+=d.checks(); raw[phase]+=d.rawContacts(); reduced[phase]+=d.reducedContacts(); wakes[phase]+=d.wokenBodies(); micro[phase]+=s.physics().motionDiagnostics().executedMicrosteps();
                if(firstSleep<0 && d.sleepingBodies()==3) firstSleep=i+1;
            }
            System.out.println(kind+" sizes="+fixture.sizes()+" ticks="+count+" sleeping="+s.physics().interactionDiagnostics().sleepingBodies()+" firstAllSleep="+firstSleep+" failure="+(failure.isEmpty()?"none":failure));
            for(int phase=0;phase<5;phase++) {
                int n=counts[phase]; if(n==0) continue; var actual=Arrays.copyOf(times[phase],n); double mean=Arrays.stream(actual).average().orElseThrow()/1e6; Arrays.sort(actual);
                System.out.printf(Locale.ROOT,"  phase=%d n=%d mean=%.3f p95=%.3f max=%.3f body=%.3f static=%.3f solver=%.3f occupancy=%.3f plan=%.3f cells=%.3f checks=%.0f raw=%.1f reduced=%.1f micro=%.1f wakes=%d%n",phase,n,mean,actual[(int)Math.ceil(n*.95)-1]/1e6,actual[n-1]/1e6,body[phase]/(double)n/1e6,stat[phase]/(double)n/1e6,solver[phase]/(double)n/1e6,occupancy[phase]/(double)n/1e6,plan[phase]/(double)n/1e6,cellular[phase]/(double)n/1e6,checks[phase]/(double)n,raw[phase]/(double)n,reduced[phase]/(double)n,micro[phase]/(double)n,wakes[phase]);
            }
        }
    }
}
