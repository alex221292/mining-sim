package miningsim.benchmark;

import java.util.Arrays;
import java.util.Locale;
import miningsim.block.FixedBlockSession;
import miningsim.world.World;
import miningsim.world.Material;

/** Reproducible owner-thread fixed-block tick benchmark; excludes rendering. */
public final class BlockSupportBenchmark {
    public static void main(String[] args) throws Exception {
        System.out.println(System.getProperty("java.runtime.version")+"; warmup=20 samples=100; seed=12345");
        for(int size:new int[]{8,4}) for(String scenario:new String[]{"static100","static1000","static5000","equal-area","beam","bridges","stepped","remove-support","water","mixed1000","mixed-water","mixed-beam","mixed-stepped"}) {
            int count=switch(scenario) { case "static100" -> 100; case "static5000" -> 5000; case "equal-area" -> size==8?1000:4000; default -> 1000; };
            int width=800,height=401,columns=width/size;
            var s=new FixedBlockSession(new World(width,height,World.Mode.BEDROCK_ENABLED),12345,size);
            if(scenario.equals("beam") || scenario.equals("mixed-beam")) {
                count=columns; for(int i=0;i<count;i++) s.blocks().place(i*size,height-1-2*size,false,scenario.equals("mixed-beam")?rock(i):Material.STONE);
                s.blocks().place(0,height-1-size,false); count++;
            } else if(scenario.equals("bridges")) {
                count=0; for(int row=0;row<5;row++) for(int i=0;i<columns;i++) { s.blocks().place(i*size,height-1-(2*row+2)*size,false); count++; }
                for(int i=0;i<10;i++) { s.blocks().place(0,height-1-(i+1)*size,false); s.blocks().place((columns-1)*size,height-1-(i+1)*size,false); } count=s.blocks().size();
            } else if(scenario.equals("stepped") || scenario.equals("mixed-stepped")) {
                for(int x=0;x<width-4*size;x+=8*size) {
                    int ground=height-1-size; boolean mixed=scenario.equals("mixed-stepped"); s.blocks().place(x,ground,false,mixed?rock(x/size):Material.STONE); s.blocks().place(x,ground-size,false,mixed?rock(1):Material.STONE);
                    s.blocks().place(x+size,ground-size,false,mixed?rock(2):Material.STONE); s.blocks().place(x+size,ground-2*size,false,mixed?rock(3):Material.STONE);
                    for(int i=1;i<=4;i++) s.blocks().place(x+(i+1)*size,ground-2*size,false,mixed?rock(i):Material.STONE);
                }
                count=s.blocks().size();
            } else if(scenario.equals("water") || scenario.equals("mixed-water")) {
                count=10; for(int y=height-1-10*size;y<height-1;y++) for(int x=0;x<20*size;x++) s.world().set(x,y,Material.WATER);
                for(int i=0;i<count;i++) s.blocks().place(i*2*size,height-1-13*size,false,scenario.equals("mixed-water")?rock(i):Material.STONE);
            } else for(int i=0;i<count;i++) if(!s.blocks().place(i%columns*size,height-1-size-i/columns*size,false,scenario.equals("mixed1000")?rock(i):Material.STONE)) throw new IllegalStateException("fixture");
            java.lang.reflect.Method diagnostic=null,analysis=null;
            try { diagnostic=s.blocks().getClass().getMethod("supportDiagnostics"); analysis=diagnostic.getReturnType().getMethod("analysisNanos"); } catch(NoSuchMethodException ignored) { }
            long[] ticks=new long[100],support=new long[100];
            for(int i=-20;i<ticks.length;i++) { if(i==0 && scenario.equals("remove-support")) for(int x=0;x<width;x+=size) s.blocks().removeAt(x,height-1-size); long start=System.nanoTime(); s.tick(); long elapsed=System.nanoTime()-start; if(i>=0) { ticks[i]=elapsed; if(diagnostic!=null) support[i]=(long)analysis.invoke(diagnostic.invoke(s.blocks())); } }
            Arrays.sort(ticks); Arrays.sort(support);
            System.out.printf(Locale.ROOT,"scenario=%s size=%d world=%dx%d blocks=%d full mean=%.3f p95=%.3f max=%.3f ms support mean=%.3f p95=%.3f ms%n",scenario,size,width,height,count,Arrays.stream(ticks).average().orElseThrow()/1e6,ticks[94]/1e6,ticks[99]/1e6,Arrays.stream(support).average().orElseThrow()/1e6,support[94]/1e6);
        }
    }
    private static Material rock(int i) { return switch(i%4) { case 0 -> Material.STONE; case 1 -> Material.GRANITE; case 2 -> Material.LIMESTONE; default -> Material.MARBLE; }; }
}
