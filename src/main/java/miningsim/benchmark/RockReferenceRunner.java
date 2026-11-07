package miningsim.benchmark;

import java.util.Locale;
import miningsim.structural.*;

public final class RockReferenceRunner {
    public static void main(String[] args) {
        for (var scene : new RockReferenceScenes.Scene[]{RockReferenceScenes.roof("short",4,1), RockReferenceScenes.roof("long",12,1),
                RockReferenceScenes.roof("thick",12,2), RockReferenceScenes.cantilever(4), RockReferenceScenes.cantilever(8), RockReferenceScenes.island()}) run(scene);
    }
    private static void run(RockReferenceScenes.Scene scene) {
        long totalStart=System.nanoTime(), start=totalStart;
        RockReferenceScenes.finishConnectivity(scene.connectivity()); long connect=System.nanoTime()-start;
        var s=scene.damage(); var status=s.analyze(scene.x(),scene.y());
        if(status != RockDamageSystem.Status.CONVERGED) {
            System.out.printf(Locale.ROOT,"%s %dx%d nodes=%d status=%s connect=%.3fms total=%.3fms%n",scene.name(),
                    scene.world().width(),scene.world().height(),scene.connectivity().componentSize(0),status,connect/1e6,(System.nanoTime()-totalStart)/1e6);
            return;
        }
        var snap=s.snapshot(); double util=snap.bonds().stream().mapToDouble(RockDamageSystem.Bond::utilization).max().orElse(0);
        double moment=snap.bonds().stream().mapToDouble(b->Math.max(Math.abs(b.moment1()),Math.abs(b.moment2()))).max().orElse(0);
        long assembly=s.assemblyNanos(), solve=s.solveNanos(), tickNanos=0, rebuild=0; int firstDamage=-1, firstBreak=-1, broken=-1;
        for(int tick=0;tick<600;tick++) {
            start=System.nanoTime(); status=s.advanceTick(tick); tickNanos += System.nanoTime()-start;
            if(status==RockDamageSystem.Status.PENDING) {
                firstBreak=tick;
                for(var b:snap.bonds()) if(scene.connectivity().bondState(b.x1(),b.y1(),b.x2(),b.y2())==RockConnectivity.BondState.BROKEN) { broken=b.key(); break; }
                start=System.nanoTime(); RockReferenceScenes.finishConnectivity(scene.connectivity()); status=s.analyze(scene.x(),scene.y()); rebuild=System.nanoTime()-start; break;
            }
            if(status!=RockDamageSystem.Status.CONVERGED) throw new IllegalStateException(status.toString());
            if(firstDamage<0 && s.snapshot().bonds().stream().anyMatch(b->b.damage()>0)) firstDamage=tick;
        }
        System.out.printf(Locale.ROOT,"%s %dx%d nodes=%d bonds=%d initial=CONVERGED util=%.6f moment=%.6f displacement=%.9g damageTick=%d breakTick=%d bond=%d components=%d final=%s connect=%.3fms assembly=%.3fms solve=%.3fms tickAvg=%.4fms rebuild=%.3fms total=%.3fms%n",
                scene.name(),scene.world().width(),scene.world().height(),snap.nodeCount(),snap.bonds().size(),util,moment,snap.maxDisplacementMetres(),firstDamage,firstBreak,broken,scene.connectivity().componentCount(),status,
                connect/1e6,assembly/1e6,solve/1e6,tickNanos/1e6/(firstBreak<0?600:firstBreak+1),rebuild/1e6,(System.nanoTime()-totalStart)/1e6);
        if(firstBreak>=0) for(int i=0;i<scene.connectivity().componentCount();i++) {
            int id=scene.connectivity().componentId(i);
            System.out.printf("  component=%d cells=%d status=%s%n",id,scene.connectivity().componentSize(i),s.analyze(id%scene.world().width(),id/scene.world().width()));
        }
    }
}
