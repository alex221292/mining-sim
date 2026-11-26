package miningsim.generation;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import miningsim.block.FixedBlockSession;
import miningsim.block.FixedBlockWorld;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.World;
import miningsim.world.Material;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CaveGeneratorTest {
    private byte[] save(FixedBlockSession s) throws Exception { var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray(); }
    @ParameterizedTest @ValueSource(ints={4,8}) void generatedCavesAreOwnedStableAndDeterministic(int size) throws Exception {
        var c=WorldGenerationConfig.preset(12345,size); var a=WorldGenerator.generate(c); var b=WorldGenerator.generate(c);
        assertTrue(a.caves().accepted()>0); assertEquals(0,a.session().cellularTicks()); assertArrayEquals(save(a.session()),save(b.session()));
        int air=0;
        for(int y=0;y<c.height()-1;y++) for(int x=0;x<c.width();x++) {
            var layer=WorldGenerator.layers(c,x); var m=a.session().world().get(x,y);
            if(y>=layer.stoneTop() && m==Material.AIR) { air++; assertNull(a.session().blocks().blockAt(x,y)); assertTrue(y>=layer.stoneTop()+16); assertTrue(x>=16 && x<c.width()-16); assertTrue(y<c.height()-1-16); }
        }
        assertEquals(a.caves().blocksRemoved()*size*size,air); assertTrue(a.session().blocks().supportedAfterRemoving(List.of()));
        int cols=c.width()/size,rows=(c.height()-1)/size,components=0; boolean[] seen=new boolean[cols*rows];
        for(int y=0;y<rows;y++) for(int x=0;x<cols;x++) {
            int index=y*cols+x; if(seen[index] || y*size<WorldGenerator.layers(c,x*size).stoneTop() || a.session().world().get(x*size,y*size)!=Material.AIR) continue;
            components++; int count=0; var queue=new java.util.ArrayDeque<Integer>(); queue.add(index); seen[index]=true;
            while(!queue.isEmpty()) { int p=queue.removeFirst(),px=p%cols,py=p/cols; count++;
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) { int nx=px+d[0],ny=py+d[1]; if(nx<0 || nx>=cols || ny<0 || ny>=rows) continue; int n=ny*cols+nx;
                    if(!seen[n] && ny*size>=WorldGenerator.layers(c,nx*size).stoneTop() && a.session().world().get(nx*size,ny*size)==Material.AIR) { seen[n]=true; queue.add(n); }
                }
            }
            assertTrue(count>=2);
        }
        assertEquals(a.caves().accepted(),components);
        byte[] saved=save(a.session()); var loaded=FixedSessionIO.read(new ByteArrayInputStream(saved)).fixed(); assertArrayEquals(saved,save(loaded));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void hypotheticalSupportCheckLeavesStateUnchanged(int size) {
        var s=new FixedBlockSession(new World(24*size,10*size+1,World.Mode.BEDROCK_ENABLED),1,size);
        for(int y=0;y<10;y++) assertTrue(s.blocks().place(0,y*size,false));
        var before=s.blocks().saveState(); long revision=s.world().mutationRevision();
        assertFalse(s.blocks().supportedAfterRemoving(List.of(new FixedBlockWorld.BlockPosition(0,9*size))));
        assertTrue(s.blocks().supportedAfterRemoving(List.of(new FixedBlockWorld.BlockPosition(0,0))));
        assertEquals(before,s.blocks().saveState()); assertEquals(revision,s.world().mutationRevision()); assertEquals(0,s.cellularTicks());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void noCavesInSmallWorld(int size) {
        var config=new WorldGenerationConfig(1,4*size,9*size+1,size,4*size,size,2*size,2);
        assertEquals(0,WorldGenerator.generate(config).caves().accepted());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void replayAndStabilityForCompactCaves(int size) throws Exception {
        var c=new WorldGenerationConfig(12345,256,201,size,64,16,32,8);
        var generated=WorldGenerator.generate(c); assertTrue(generated.caves().accepted()>0);
        var a=generated.session(); var b=WorldGenerator.generate(c).session(); var initial=a.blocks().saveState();
        for(int tick=0;tick<10000;tick++) {
            for(var s:List.of(a,b)) {
                if(tick==700) s.enqueue(()->s.paintCells(240,1,0,Material.WATER));
                if(tick==900) s.enqueue(()->s.blocks().removeAt(0,200-size));
                if(tick==1100) s.enqueue(()->s.blockStroke(80,0,88,0,false));
                if(tick==1101) s.enqueue(()->s.blocks().releaseStroke());
            }
            a.tick(); b.tick();
            if(tick==599) assertEquals(initial,a.blocks().saveState());
            if(tick%500==0) { byte[] snapshot=save(a); assertArrayEquals(snapshot,save(b)); a=FixedSessionIO.read(new ByteArrayInputStream(snapshot)).fixed(); b=FixedSessionIO.read(new ByteArrayInputStream(snapshot)).fixed(); }
        }
        assertArrayEquals(save(a),save(b));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void multipleSeedsKeepRockStable(int size) {
        for(int seed:new int[]{1,357355}) {
            var g=WorldGenerator.generate(new WorldGenerationConfig(seed,256,201,size,64,16,32,8));
            var before=g.session().blocks().saveState(); for(int i=0;i<600;i++) g.session().tick(); assertEquals(before,g.session().blocks().saveState());
        }
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void waterCanEnterOpenedCave(int size) {
        var c=WorldGenerationConfig.preset(12345,size); var s=WorldGenerator.generate(c).session(); int caveX=-1,caveY=-1;
        search: for(int y=0;y<c.height()-1;y+=size) for(int x=0;x<c.width();x+=size)
            if(y>=WorldGenerator.layers(c,x).stoneTop()+16 && s.world().get(x,y)==Material.AIR) { caveX=x; caveY=y; break search; }
        assertTrue(caveX>=0); int start=WorldGenerator.layers(c,caveX).stoneTop();
        for(int y=start;y<caveY;y+=size) assertTrue(s.blocks().removeAt(caveX,y));
        assertEquals(World.Mutation.CHANGED,s.world().set(caveX+size/2,start,Material.WATER));
        for(int i=0;i<100;i++) s.tick(); int water=0,waterY=-1; for(int y=0;y<c.height();y++) for(int x=0;x<c.width();x++) if(s.world().get(x,y)==Material.WATER) { water++; waterY=y; }
        assertEquals(1,water); assertTrue(waterY>=caveY);
    }
}
