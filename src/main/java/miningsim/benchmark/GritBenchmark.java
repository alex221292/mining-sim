package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import miningsim.resource.OreComposition;
import miningsim.resource.OreId;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

/** Exploratory fixed-order headless cellular benchmark, not a frame-time guarantee. */
public final class GritBenchmark {
    private static String stats(long[] a) {
        Arrays.sort(a);
        return String.format(Locale.ROOT,"%.3f/%.3f/%.3f",Arrays.stream(a).average().orElseThrow()/1e6,a[(int)Math.ceil(a.length*.95)-1]/1e6,a[a.length-1]/1e6);
    }
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.runtime.version")+" world=400x401; seed=12345; sequential order sand,empty,one,four,varied; active warmup=10 measured=40; late warmup=400 measured=40; settled separate packed fixture warmup=20 measured=40; ms mean/p95/max");
        for(int count:new int[]{10000,50000}) for(int kind=0;kind<5;kind++) {
            var w=new World(400,401,World.Mode.BEDROCK_ENABLED);
            for(int x=0;x<400;x++) for(int y=320;y<400;y++) w.set(x,y,Material.WATER);
            for(int x=30;x<370;x++) w.set(x,260+Math.abs(x-200)/4,Material.WOOD);
            var palette=new OreComposition[256];
            for(int i=0;i<palette.length;i++) palette[i]=kind==2?OreComposition.of(new OreComposition.Component(OreId.IRON,120000)):
                    OreComposition.of(new OreComposition.Component(OreId.IRON,120000+(kind==4?i:0)),new OreComposition.Component(OreId.COPPER,25000),new OreComposition.Component(OreId.SILVER,80),new OreComposition.Component(OreId.GOLD,5));
            for(int i=0;i<count;i++) {
                int x=20+i%360,y=5+i/360;
                w.set(x,y,kind==0?Material.SAND:Material.STONE_GRIT);
                if(kind>=2) w.setGritOre(x,y,palette[kind==4?i%256:0]);
            }
            var sim=new Simulation(w,12345); long[] active=new long[40],steady=new long[40];
            for(int t=-10;t<40;t++) { long start=System.nanoTime(); sim.step(); if(t>=0) active[t]=System.nanoTime()-start; }
            for(int t=0;t<400;t++) sim.step();
            for(int t=0;t<40;t++) { long start=System.nanoTime(); sim.step(); steady[t]=System.nanoTime()-start; }
            var packed=new World(400,401,World.Mode.BEDROCK_ENABLED);
            for(int i=0;i<count;i++) {
                int x=i%400,y=399-i/400;
                packed.set(x,y,kind==0?Material.SAND:Material.STONE_GRIT);
                if(kind>=2) packed.setGritOre(x,y,palette[kind==4?i%256:0]);
            }
            var settled=new Simulation(packed,12345); long[] idle=new long[40];
            for(int t=-20;t<40;t++) { long start=System.nanoTime(); settled.step(); if(t>=0) idle[t]=System.nanoTime()-start; }
            System.out.printf(Locale.ROOT,"count=%d kind=%s unique=%d active=%s late=%s settled=%s%n",count,new String[]{"sand","empty","one","four","varied"}[kind],kind<2?0:kind==4?256:1,stats(active),stats(steady),stats(idle));
        }
    }
}
