package miningsim.block;

import java.util.Arrays;
import java.util.Locale;
import miningsim.world.Material;
import miningsim.world.World;

/** Fresh identical one-tick fixtures: no cellular phase or rendering in timings. */
public final class WaterDisplacementBenchmark {
    private WaterDisplacementBenchmark() { }
    private static FixedBlockWorld scene(int count,boolean sealed) {
        int width=count*16; var world=new World(width,65,World.Mode.BEDROCK_ENABLED); var blocks=new FixedBlockWorld(world);
        for(int y=32;y<64;y++) for(int i=0;i<count;i++) for(int x=i*16;x<i*16+8;x++) world.set(x,y,Material.WATER);
        for(int i=0;i<count;i++) if(!blocks.place(i*16,24,false)) throw new IllegalStateException("Fixture placement failed");
        if(sealed) {
            for(int y=24;y<64;y++) for(int x=0;x<width;x++) if(world.get(x,y)==Material.AIR) world.set(x,y,Material.WATER);
            for(int x=0;x<width;x++) world.set(x,23,Material.WOOD);
        }
        return blocks;
    }
    public static void main(String[] args) {
        int runs=args.length==0?10:Integer.parseInt(args[0]);
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+"; fresh pools height=65; warmup=2; runs="+runs);
        for(int count:new int[]{10,50}) measure(count,false,runs);
        measure(10,true,runs);
    }
    private static void measure(int count,boolean sealed,int runs) {
        long[] samples=new long[runs]; long rejected=0,attempts=0,moves=0; int maxVisits=0,maxChecks=0;
        var reasons=new java.util.EnumMap<FixedBlockWorld.Reason,Long>(FixedBlockWorld.Reason.class);
        for(int i=-2;i<runs;i++) { var scene=scene(count,sealed); scene.step(); if(i>=0) {
            samples[i]=scene.updateNanos(); rejected+=scene.obstructed(); var d=scene.tickDiagnostics(); attempts+=d.waterAttempts(); moves+=d.moved();
            maxVisits=Math.max(maxVisits,d.maxVisits()); maxChecks=Math.max(maxChecks,d.maxChecks());
            for(var block:scene.diagnostics()) if(block.reason()!=FixedBlockWorld.Reason.NONE) reasons.merge(block.reason(),1L,Long::sum);
        } }
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT,"%s blocks=%d mean=%.3fms p95=%.3fms obstructed=%d waterAttempts=%d moved=%d maxVisits=%d maxChecks=%d reasons=%s%n",sealed?"sealed":"pool",count,Arrays.stream(samples).average().orElseThrow()/1e6,samples[(int)Math.ceil(runs*.95)-1]/1e6,rejected,attempts,moves,maxVisits,maxChecks,reasons);
    }
}
