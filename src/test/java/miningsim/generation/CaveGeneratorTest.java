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
    private static boolean voidCell(Material material) { return material==Material.AIR || material==Material.WATER; }
    private byte[] save(FixedBlockSession s) throws Exception { var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray(); }
    @ParameterizedTest @ValueSource(ints={4,8}) void generatedCavesAreOwnedStableAndDeterministic(int size) throws Exception {
        var c=WorldGenerationConfig.preset(12345,size); var a=WorldGenerator.generate(c); var b=WorldGenerator.generate(c);
        assertTrue(a.caves().accepted()>0); assertEquals(0,a.session().cellularTicks()); assertArrayEquals(save(a.session()),save(b.session()));
        assertTrue(a.caves().candidates()<=CaveGenerator.MAX_CANDIDATES); assertTrue(a.caves().branchedSystems()>0);
        int air=0;
        for(int y=0;y<c.height()-1;y++) for(int x=0;x<c.width();x++) {
            var layer=WorldGenerator.layers(c,x); var m=a.session().world().get(x,y);
            if(y>=layer.stoneTop() && voidCell(m)) { air++; assertNull(a.session().blocks().blockAt(x,y)); assertTrue(y>=layer.stoneTop()+16); assertTrue(x>=16 && x<c.width()-16); assertTrue(y<c.height()-1-16); }
        }
        assertEquals(a.caves().blocksRemoved()*size*size,air); assertEquals(0,a.caves().supportRejected());
        int cols=c.width()/size,rows=(c.height()-1)/size,components=0; boolean[] seen=new boolean[cols*rows];
        for(int y=0;y<rows;y++) for(int x=0;x<cols;x++) {
            int index=y*cols+x; if(seen[index] || y*size<WorldGenerator.layers(c,x*size).stoneTop() || !voidCell(a.session().world().get(x*size,y*size))) continue;
            components++; int count=0; var queue=new java.util.ArrayDeque<Integer>(); queue.add(index); seen[index]=true;
            while(!queue.isEmpty()) { int p=queue.removeFirst(),px=p%cols,py=p/cols; count++;
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) { int nx=px+d[0],ny=py+d[1]; if(nx<0 || nx>=cols || ny<0 || ny>=rows) continue; int n=ny*cols+nx;
                    if(!seen[n] && ny*size>=WorldGenerator.layers(c,nx*size).stoneTop() && voidCell(a.session().world().get(nx*size,ny*size))) { seen[n]=true; queue.add(n); }
                }
            }
            assertTrue(count>=4,"clipping must not leave small isolated remnants");
        }
        assertTrue(components>0); assertTrue(components<=a.caves().blocksRemoved());
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
        var a=generated.session(); var b=WorldGenerator.generate(c).session(); int initialBlocks=a.blocks().size();
        for(int tick=0;tick<10000;tick++) {
            for(var s:List.of(a,b)) {
                if(tick==700) s.enqueue(()->s.paintCells(240,1,0,Material.WATER));
                if(tick==900) s.enqueue(()->s.blocks().removeAt(0,200-size));
                if(tick==1100) s.enqueue(()->s.blockStroke(80,0,88,0,false));
                if(tick==1101) s.enqueue(()->s.blocks().releaseStroke());
            }
            a.tick(); b.tick();
            if(tick==599) assertEquals(initialBlocks,a.blocks().size());
            if(tick%500==0) { byte[] snapshot=save(a); assertArrayEquals(snapshot,save(b)); a=FixedSessionIO.read(new ByteArrayInputStream(snapshot)).fixed(); b=FixedSessionIO.read(new ByteArrayInputStream(snapshot)).fixed(); }
        }
        assertArrayEquals(save(a),save(b));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void deterministicMorphologyVariesBySeed(int size) throws Exception {
        var first=WorldGenerator.generate(WorldGenerationConfig.preset(431039,size));
        var other=WorldGenerator.generate(WorldGenerationConfig.preset(357355,size));
        assertTrue(first.caves().accepted()>0); assertTrue(other.caves().accepted()>0);
        assertFalse(java.util.Arrays.equals(save(first.session()),save(other.session())));
        assertArrayEquals(save(first.session()),save(WorldGenerator.generate(WorldGenerationConfig.preset(431039,size)).session()));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void separateWalksCanJoinIntoOneCave(int size) {
        var c=WorldGenerationConfig.preset(357355,size); var g=WorldGenerator.generate(c); int cols=c.width()/size,rows=(c.height()-1)/size;
        boolean[] cave=new boolean[cols*rows],seen=new boolean[cave.length]; int components=0;
        for(int y=0;y<rows;y++) for(int x=0;x<cols;x++) cave[y*cols+x]=y*size>=WorldGenerator.layers(c,x*size).stoneTop() && voidCell(g.session().world().get(x*size,y*size));
        for(int p=0;p<cave.length;p++) if(cave[p] && !seen[p]) {
            components++; var q=new java.util.ArrayDeque<Integer>(); q.add(p); seen[p]=true;
            while(!q.isEmpty()) { int cell=q.removeFirst(),x=cell%cols,y=cell/cols;
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) { int nx=x+d[0],ny=y+d[1]; if(nx<0 || nx>=cols || ny<0 || ny>=rows) continue; int n=ny*cols+nx;
                    if(cave[n] && !seen[n]) { seen[n]=true; q.add(n); }
                }
            }
        }
        assertTrue(g.caves().branchedSystems()>0); assertTrue(components<g.caves().accepted());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void generatedUnsupportedRockUsesExistingFallDelay(int size) {
        var s=WorldGenerator.generate(WorldGenerationConfig.preset(12345,size)).session();
        assertFalse(s.blocks().supportedAfterRemoving(List.of()));
        var before=s.blocks().blocks();
        for(int i=0;i<FixedBlockWorld.FALL_DELAY_TICKS;i++) {
            s.tick(); var current=s.blocks().blocks();
            for(int j=0;j<before.size();j++) assertEquals(before.get(j).y(),current.get(j).y());
        }
        s.tick(); var after=s.blocks().blocks(); boolean moved=false;
        for(int j=0;j<before.size();j++) moved|=before.get(j).y()!=after.get(j).y();
        assertTrue(moved,"unsupported generated rock should begin falling on the ninth tick");
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void multipleSeedsPreserveRockDuringSettling(int size) throws Exception {
        for(int seed:new int[]{1,357355}) {
            var g=WorldGenerator.generate(new WorldGenerationConfig(seed,256,201,size,64,16,32,8));
            var before=g.session().blocks().blocks().stream().map(FixedBlockWorld.Block::id).sorted().toList();
            for(int i=0;i<600;i++) g.session().tick();
            assertEquals(before,g.session().blocks().blocks().stream().map(FixedBlockWorld.Block::id).sorted().toList());
            byte[] state=save(g.session()); assertArrayEquals(state,save(FixedSessionIO.read(new ByteArrayInputStream(state)).fixed()));
        }
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void waterCanEnterOpenedCave(int size) {
        var c=WorldGenerationConfig.preset(12345,size); var s=WorldGenerator.generate(c).session(); int caveX=-1,caveY=-1;
        // Keep the original single-particle drainage fixture independent of natural water.
        for(int y=0;y<c.height();y++) for(int x=0;x<c.width();x++) if(s.world().get(x,y)==Material.WATER) s.world().set(x,y,Material.AIR);
        search: for(int y=0;y<c.height()-1;y+=size) for(int x=0;x<c.width();x+=size)
            if(y>=WorldGenerator.layers(c,x).stoneTop()+16 && s.world().get(x,y)==Material.AIR) { caveX=x; caveY=y; break search; }
        assertTrue(caveX>=0); int start=WorldGenerator.layers(c,caveX).stoneTop();
        for(int y=start;y<caveY;y+=size) assertTrue(s.blocks().removeAt(caveX,y));
        assertEquals(World.Mutation.CHANGED,s.world().set(caveX+size/2,start,Material.WATER));
        for(int i=0;i<100;i++) s.tick(); int water=0,waterY=-1; for(int y=0;y<c.height();y++) for(int x=0;x<c.width();x++) if(s.world().get(x,y)==Material.WATER) { water++; waterY=y; }
        assertEquals(1,water); assertTrue(waterY>=caveY);
    }
}
