package miningsim.benchmark;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import miningsim.generation.WorldGenerationConfig;
import miningsim.generation.WorldGenerator;
import miningsim.persistence.FixedSessionIO;

/** Prints deterministic cell fingerprints and a baseline-compatible session fixture. */
public final class GenerationParityProbe {
    public static void main(String[] args) throws Exception {
        if(args.length>0) {
            var fixture=new miningsim.block.FixedBlockSession(new miningsim.world.World(8,9,miningsim.world.World.Mode.BEDROCK_ENABLED),1,4);
            fixture.blocks().place(0,0,false);
            var bytes=new ByteArrayOutputStream(); FixedSessionIO.write(fixture,bytes);
            System.out.println("v2-fixture="+java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
        }
        for(int size:new int[]{4,8}) for(long seed:new long[]{1,71,12345}) {
            var config=new WorldGenerationConfig(seed,128,129,size,48,16,32,8);
            var s=WorldGenerator.generate(config).session();
            var digest=MessageDigest.getInstance("SHA-256");
            for(int y=0;y<s.world().height();y++) for(int x=0;x<s.world().width();x++) digest.update(s.world().get(x,y).id());
            System.out.printf("size=%d seed=%d cells=%s blocks=%d%n",size,seed,HexFormat.of().formatHex(digest.digest()),s.blocks().size());
        }
    }
}
