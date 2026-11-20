package miningsim.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import javax.swing.SwingUtilities;
import miningsim.app.SandboxSessionFactory;
import miningsim.app.InteractiveSession;
import miningsim.world.Material;
import miningsim.world.World;

/** Measures sandbox simulation on the EDT without rendering, not FPS. */
public final class SwingLifecycleBenchmark {
    private static void report(String label,ArrayList<Long> values) {
        if(values.isEmpty()) { System.out.println(label+" n=0"); return; }
        Collections.sort(values); double mean=values.stream().mapToDouble(Long::doubleValue).average().orElseThrow()/1e6;
        System.out.printf(Locale.ROOT,"%s n=%d mean=%.3f p95=%.3f max=%.3f ms%n",label,values.size(),mean,values.get((int)Math.ceil(values.size()*.95)-1)/1e6,values.getLast()/1e6);
    }
    private static long tick(InteractiveSession s) {
        long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start;
        if(s.failure()!=null || s.blocked()) throw new IllegalStateException("failed: "+s.failure());
        return elapsed;
    }
    private static void sample(String name,InteractiveSession s,int count) {
        var times=new ArrayList<Long>(); for(int i=0;i<count;i++) times.add(tick(s)); report(name,times);
    }
    private static void run() {
        System.out.println(System.getProperty("java.runtime.version")+"; EDT simulation only; 400x300; profiling enabled; no rendering");
        var empty=SandboxSessionFactory.create(new World(400,300,World.Mode.BEDROCK_ENABLED),true);
        for(int i=0;i<120;i++) tick(empty); sample("empty",empty,120);
        var threeWorld=new World(400,300,World.Mode.BEDROCK_ENABLED);
        for(int i=0;i<3;i++) threeWorld.set(20+10*i,290,Material.STONE);
        var three=SandboxSessionFactory.create(threeWorld,true); var falling=new ArrayList<Long>();
        for(int i=0;i<1200 && three.lifecycle().settled().liveRocks()<3;i++) {
            long elapsed=tick(three); if(three.physics().bodies().size()==3 && three.physics().bodies().stream().noneMatch(b->b.sleeping())) falling.add(elapsed);
        }
        if(three.lifecycle().settled().liveRocks()!=3) throw new IllegalStateException("three settled not reached");
        report("three-falling",falling); sample("three-settled",three,120);
        var world=new World(400,300,World.Mode.BEDROCK_ENABLED);
        for(int i=0;i<10;i++) world.set(20+10*i,290,Material.STONE);
        var s=SandboxSessionFactory.create(world,true);
        var transitions=new ArrayList<Long>();
        for(int i=0;i<2000 && s.lifecycle().settled().liveRocks()<10;i++) {
            long elapsed=tick(s); var d=s.lifecycle().diagnostics();
            if(d.planningNanos()>0) transitions.add(elapsed);
        }
        if(s.lifecycle().settled().liveRocks()!=10) throw new IllegalStateException("ten settled not reached: "+s.lifecycle().diagnostics());
        report("settling-transition-ticks",transitions); sample("ten-settled",s,120);
        for(int batch=0;batch<3;batch++) {
            for(int x=200;x<210;x++) world.set(x,200+batch,Material.SAND);
            sample("sand-batch-"+(batch+1),s,60);
        }
        var repeatWorld=new World(400,300,World.Mode.BEDROCK_ENABLED);
        repeatWorld.set(5,290,Material.STONE); repeatWorld.set(5,285,Material.STONE);
        var repeat=SandboxSessionFactory.create(repeatWorld,true);
        for(int i=0;i<1500 && repeat.lifecycle().settled().liveRocks()<2;i++) tick(repeat);
        if(repeat.lifecycle().settled().liveRocks()!=2) throw new IllegalStateException("repeat setup");
        repeat.enqueue(()->repeatWorld.set(5,298,Material.AIR));
        var reactivation=new ArrayList<Long>();
        for(int i=0;i<1500;i++) {
            long previous=repeat.lifecycle().diagnostics().reactivated(); long elapsed=tick(repeat);
            if(repeat.lifecycle().diagnostics().reactivated()>previous) reactivation.add(elapsed);
            if(previous>0 && repeat.lifecycle().settled().liveRocks()==1 && repeat.physics().bodies().isEmpty()) break;
        }
        report("reactivation-transition-tick",reactivation);
    }
    public static void main(String[] args) throws Exception { SwingUtilities.invokeAndWait(SwingLifecycleBenchmark::run); }
}
