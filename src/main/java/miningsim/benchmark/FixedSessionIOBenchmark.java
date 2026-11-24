package miningsim.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.nio.file.Files;
import miningsim.block.FixedBlockSession;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.World;

/** In-memory encoding/decoding only; excludes disk and Swing. */
public final class FixedSessionIOBenchmark {
    private FixedSessionIOBenchmark() { }
    public static void main(String[] args) throws Exception {
        int runs=args.length==0?10:Integer.parseInt(args[0]);
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+"; warmup=2 runs="+runs);
        for(int size:new int[]{8,4}) for(boolean large:new boolean[]{false,true}) {
            int width=large?800:64,height=large?401:65,count=large?(size==8?1000:4000):10;
            var s=new FixedBlockSession(new World(width,height,World.Mode.BEDROCK_ENABLED),12345,size);
            int columns=width/size; for(int i=0;i<count;i++) if(!s.blocks().place((i%columns)*size,height-1-size-(i/columns)*size,false)) throw new IllegalStateException();
            long[] writes=new long[runs],reads=new long[runs]; int bytes=0;
            for(int i=-2;i<runs;i++) {
                var out=new ByteArrayOutputStream(); long start=System.nanoTime(); FixedSessionIO.write(s,out); long write=System.nanoTime()-start;
                byte[] data=out.toByteArray(); start=System.nanoTime(); var loaded=FixedSessionIO.read(new ByteArrayInputStream(data)); long read=System.nanoTime()-start;
                if(loaded.fixed().blocks().size()!=count) throw new IllegalStateException();
                if(i>=0) { writes[i]=write; reads[i]=read; bytes=data.length; }
            }
            Arrays.sort(writes); Arrays.sort(reads); int p=(int)Math.ceil(runs*.95)-1;
            System.out.printf(Locale.ROOT,"size=%d world=%dx%d blocks=%d bytes=%d write=%.3f/%.3fms read=%.3f/%.3fms%n",size,width,height,count,bytes,
                    Arrays.stream(writes).average().orElseThrow()/1e6,writes[p]/1e6,Arrays.stream(reads).average().orElseThrow()/1e6,reads[p]/1e6);
            var path=Files.createTempFile("msim-v2-benchmark-",".msim");
            try {
                for(int i=-2;i<runs;i++) {
                    long start=System.nanoTime(); FixedSessionIO.write(s,path); long write=System.nanoTime()-start;
                    start=System.nanoTime(); var loaded=FixedSessionIO.read(path); long read=System.nanoTime()-start;
                    if(loaded.fixed().blocks().size()!=count) throw new IllegalStateException();
                    if(i>=0) { writes[i]=write; reads[i]=read; }
                }
                Arrays.sort(writes); Arrays.sort(reads);
                System.out.printf(Locale.ROOT,"disk size=%d world=%dx%d blocks=%d bytes=%d write=%.3f/%.3fms read=%.3f/%.3fms%n",size,width,height,count,Files.size(path),
                        Arrays.stream(writes).average().orElseThrow()/1e6,writes[p]/1e6,Arrays.stream(reads).average().orElseThrow()/1e6,reads[p]/1e6);
            } finally { Files.deleteIfExists(path); }
        }
    }
}
