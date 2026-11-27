package miningsim.benchmark;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import miningsim.block.FixedBlockSession;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.generation.WorldGenerator;
import miningsim.generation.WorldGenerationConfig;

/** Per-tick full serialized state digest for controlled baseline comparison. */
public final class StoneSupportParity {
    public static void main(String[] args) throws Exception {
        for(int size:new int[]{4,8}) {
            var s=new FixedBlockSession(new World(128,97,World.Mode.BEDROCK_ENABLED),12345,size);
            int ground=96-size,y=ground-size;
            for(int x=0;x<=12;x++) s.blocks().place(x*size,y,false);
            s.blocks().place(0,ground,false); s.blocks().place(12*size,ground,false);
            for(int x=2;x<7;x++) s.blocks().place(x*size,y-size,false);
            for(int x=70;x<100;x++) for(int j=70;j<90;j++) if(s.world().get(x,j)==Material.AIR) s.world().set(x,j,Material.WATER);
            var digest=MessageDigest.getInstance("SHA-256");
            for(int tick=0;tick<200;tick++) {
                if(tick==10) s.blocks().removeAt(0,ground);
                if(tick==14) s.blocks().place(0,ground,false);
                if(tick==30) s.blocks().removeAt(12*size,ground);
                s.tick(); var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); digest.update(out.toByteArray());
            }
            System.out.println("size="+size+" ticks=200 sha256="+HexFormat.of().formatHex(digest.digest()));
        }
        var generated=MessageDigest.getInstance("SHA-256");
        for(int size:new int[]{4,8}) for(int i=0;i<19;i++) {
            int seed=i<16?i:i==16?431039:i==17?357355:12345;
            var session=WorldGenerator.generate(WorldGenerationConfig.preset(seed,size)).session();
            var out=new ByteArrayOutputStream(); FixedSessionIO.write(session,out); generated.update(out.toByteArray());
        }
        System.out.println("generated=38 initial sessions sha256="+HexFormat.of().formatHex(generated.digest()));
    }
}
