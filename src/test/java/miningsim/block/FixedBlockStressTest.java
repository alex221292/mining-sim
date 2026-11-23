package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class FixedBlockStressTest {
    @Test void tenThousandTicksReplayWithoutMatterLoss() {
        var a=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),12345);
        var b=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),12345);
        for(var s:List.of(a,b)) {
            for(int y:new int[]{40,48,56}) assertTrue(s.blocks().place(8,y,false));
            int x=32; for(var m:List.of(Material.SAND,Material.GRAVEL,Material.SOIL,Material.WATER)) { s.world().set(x,1,m); x+=4; }
        }
        for(int tick=0;tick<10000;tick++) {
            int t=tick;
            for(var s:List.of(a,b)) {
                if(t%200==0) s.enqueue(()->s.blocks().removeAt(8,56));
                if(t%200==30) s.enqueue(()->s.blocks().place(8,8,false));
                if(t%200==90) s.enqueue(()->s.blocks().removeAt(8,56));
                s.tick();
            }
            assertEquals(tick+1,a.cellularTicks()); assertEquals(a.blocks().blocks(),b.blocks().blocks());
            if(tick%50==0 || tick==9999) {
                int stone=0; int[] counts=new int[Material.values().length];
                for(int y=0;y<65;y++) for(int x=0;x<64;x++) {
                    var material=a.world().get(x,y); assertEquals(material,b.world().get(x,y)); counts[material.ordinal()]++;
                    if(material==Material.STONE) stone++;
                }
                assertEquals(a.blocks().size()*64,stone);
                for(var m:List.of(Material.SAND,Material.GRAVEL,Material.SOIL,Material.WATER)) assertEquals(1,counts[m.ordinal()],m.toString());
            }
        }
    }
}
