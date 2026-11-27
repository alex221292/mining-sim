package miningsim.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import miningsim.resource.OreComposition;
import org.junit.jupiter.api.Test;

class OreDepositsTest {
    @Test void survivingBlocksContainConnectedVariableDeposits() {
        for(int size:new int[]{4,8}) {
            var blocks=WorldGenerator.generate(WorldGenerationConfig.preset(357355,size)).session().blocks().blocks();
            var grid=new java.util.HashMap<Long,miningsim.block.FixedBlockWorld.Block>();
            boolean empty=false,single=false,mixed=false;
            for(var block:blocks) {
                grid.put(((long)block.x()<<32)|(block.y()&0xffffffffL),block);
                int n=block.ores().components().size(); empty|=n==0; single|=n==1; mixed|=n>1;
            }
            assertTrue(empty && single && mixed);
            var seen=new HashSet<Long>(); int largest=0; var values=new HashSet<Integer>();
            for(var block:blocks) {
                long key=((long)block.x()<<32)|(block.y()&0xffffffffL);
                if(block.ores().ppm(miningsim.resource.OreId.IRON)==0 || !seen.add(key)) continue;
                var queue=new java.util.ArrayDeque<miningsim.block.FixedBlockWorld.Block>(); queue.add(block); int count=0;
                while(!queue.isEmpty()) {
                    var current=queue.remove(); count++; values.add(current.ores().ppm(miningsim.resource.OreId.IRON));
                    for(int[] d:new int[][]{{size,0},{-size,0},{0,size},{0,-size}}) {
                        long k=((long)(current.x()+d[0])<<32)|((current.y()+d[1])&0xffffffffL);
                        var next=grid.get(k);
                        if(next!=null && next.ores().ppm(miningsim.resource.OreId.IRON)>0 && seen.add(k)) queue.add(next);
                    }
                }
                largest=Math.max(largest,count);
            }
            assertTrue(largest>=20); assertTrue(values.size()>20);
        }
    }
    @Test void seededFieldsAreReproducibleAndSpatiallyVariable() {
        var a=new OreDeposits(12345,800,401);
        var b=new OreDeposits(12345,800,401);
        var c=new OreDeposits(12346,800,401);
        int nonempty=0,different=0,mixed=0,adjacent=0;
        var concentrations=new HashSet<OreComposition>();
        for(int y=0;y<400;y+=4) for(int x=0;x<800;x+=4) {
            var value=a.sample(x+2,y+2);
            assertEquals(value,b.sample(x+2,y+2));
            if(!value.equals(c.sample(x+2,y+2))) different++;
            if(!value.components().isEmpty()) {
                nonempty++; concentrations.add(value);
                if(value.components().size()>1) mixed++;
                if(!a.sample(x+6,y+2).components().isEmpty()) adjacent++;
            } else assertSame(OreComposition.EMPTY,value);
            assertTrue(value.components().stream().mapToInt(OreComposition.Component::ppm).sum()<=1_000_000);
        }
        assertTrue(nonempty>0 && nonempty<20000);
        assertTrue(different>0 && mixed>0 && adjacent>0 && concentrations.size()>20);
    }
}
