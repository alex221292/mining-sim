package miningsim.block;

import java.util.Arrays;
import java.util.Locale;
import miningsim.world.World;

/** Static dense grids; elapsed time excludes initialization, cellular work and repaint. */
public final class FixedBlockBenchmark {
    private FixedBlockBenchmark() { }
    public static void main(String[] args) {
        int samples=args.length==0?100:Integer.parseInt(args[0]);
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+"; dense 800x401; warmup=20; samples="+samples);
        for(int count:new int[]{100,1000,5000}) {
            var blocks=new FixedBlockWorld(new World(800,401,World.Mode.BEDROCK_ENABLED));
            for(int i=0;i<count;i++) if(!blocks.place((i%100)*8,392-(i/100)*8,false)) throw new IllegalStateException("Fixture placement failed");
            for(int i=0;i<20;i++) blocks.step();
            long[] times=new long[samples]; for(int i=0;i<samples;i++) { blocks.step(); times[i]=blocks.updateNanos(); }
            Arrays.sort(times);
            System.out.printf(Locale.ROOT,"blocks=%d mean=%.3fms p95=%.3fms%n",count,Arrays.stream(times).average().orElseThrow()/1e6,times[(int)Math.ceil(samples*.95)-1]/1e6);
        }
    }
}
