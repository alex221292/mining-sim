package miningsim.benchmark;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import miningsim.block.FixedBlockWorld;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.world.Material;

/** Fixed seeded, owner-thread headless sample; run through the Gradle task. */
public final class StrengthCalibration {
    public static void main(String[] args) {
        for (int size : new int[]{8,4}) for (int index=0;index<19;index++) {
            int seed=index<16?index:index==16?431039:index==17?357355:12345;
            var s=WorldGenerator.generate(WorldGenerationConfig.preset(seed,size)).session();
            var original=new HashMap<Long,FixedBlockWorld.Block>();
            for(var b:s.blocks().blocks()) original.put(b.id(),b);
            long[] counts=new long[256]; boolean[] air=new boolean[800*401];
            for(int y=0;y<401;y++) for(int x=0;x<800;x++) {
                var m=s.world().get(x,y); counts[Byte.toUnsignedInt(m.id())]++; air[y*800+x]=m==Material.AIR;
            }
            var moved=new HashSet<Long>(); int initiallyUnsupported=0;
            for(int t=0;t<600;t++) {
                s.tick();
                if(t==0) initiallyUnsupported=s.blocks().supportDiagnostics().unsupported();
                for(var b:s.blocks().blocks()) if(original.get(b.id()).y()!=b.y()) moved.add(b.id());
            }
            int active=0,retained=0; long[] after=new long[256];
            for(var b:s.blocks().blocks()) {
                var old=original.get(b.id());
                if(old==null || !old.substance().equals(b.substance())) throw new IllegalStateException("ID or material changed");
                if(b.state()==FixedBlockWorld.State.FALLING) active++;
            }
            if(s.blocks().size()!=original.size()) throw new IllegalStateException("ID count changed");
            new FixedBlockWorld(s.world(),size).restoreState(s.blocks().saveState());
            for(int y=0;y<401;y++) for(int x=0;x<800;x++) {
                var m=s.world().get(x,y); after[Byte.toUnsignedInt(m.id())]++;
                if(air[y*800+x] && m==Material.AIR) retained++;
            }
            for(var m:new Material[]{Material.STONE,Material.GRANITE,Material.MARBLE,Material.LIMESTONE}) if(counts[Byte.toUnsignedInt(m.id())]!=after[Byte.toUnsignedInt(m.id())]) throw new IllegalStateException("rock conservation");
            System.out.printf(Locale.ROOT,"size=%d seed=%d blocks=%d initialUnsupported=%d moved=%d active600=%d unsupported600=%d initialAirRetained=%d%n",size,seed,original.size(),initiallyUnsupported,moved.size(),active,s.blocks().supportDiagnostics().unsupported(),retained);
        }
    }
}
