package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import miningsim.persistence.FixedSessionIO;
import miningsim.persistence.WorldIO;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FixedSessionIOTest {
    private byte[] bytes(FixedBlockSession s) throws IOException { var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out); return out.toByteArray(); }
    private FixedBlockSession load(byte[] data) throws IOException { return FixedSessionIO.read(new ByteArrayInputStream(data)).fixed(); }
    private void crc(byte[] data) { var c=new CRC32(); c.update(data,0,data.length-4); ByteBuffer.wrap(data).putInt(data.length-4,(int)c.getValue()); }
    private void same(FixedBlockSession a,FixedBlockSession b) {
        assertEquals(a.blocks().rockBlockSizeCells(),b.blocks().rockBlockSizeCells()); assertEquals(a.blocks().saveState(),b.blocks().saveState());
        for(int y=0;y<a.world().height();y++) for(int x=0;x<a.world().width();x++) assertEquals(a.world().get(x,y),b.world().get(x,y));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void emptyAndMixedRoundTripPreservesAuthoritativeStates(int size) throws Exception {
        var s=new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),17,size); same(s,load(bytes(s)));
        assertTrue(s.blocks().place(0,0,false)); assertTrue(s.blocks().place(16,0,false)); assertTrue(s.blocks().place(32,0,false));
        for(int i=0;i<3;i++) s.tick();
        var r=load(bytes(s)); same(s,r); assertEquals(FixedBlockWorld.State.STATIC,r.blocks().blocks().getFirst().state());
        for(int i=0;i<6;i++) { s.tick(); r.tick(); same(s,r); }
        assertTrue(s.blocks().blocks().stream().anyMatch(b->b.state()==FixedBlockWorld.State.FALLING));
        s.world().set(50,1,Material.SAND); s.world().set(51,1,Material.GRAVEL); s.world().set(52,1,Material.SOIL); s.world().set(53,1,Material.WATER);
        r=load(bytes(s)); same(s,r); long next=r.blocks().saveState().nextId(); assertTrue(r.blocks().place(48,16,false));
        assertEquals(next,r.blocks().blockAt(48,16).id());
        for(int i=0;i<20;i++) r.tick(); assertTrue(r.cellularTicks()>=20);
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void loadedStrokePinsAreReleased(int size) throws Exception {
        var s=new FixedBlockSession(new World(32,33,World.Mode.BEDROCK_ENABLED),1,size);
        s.blockStroke(0,0,0,0,false);
        assertTrue(s.blocks().saveState().blocks().getFirst().pinned());
        var loaded=load(bytes(s));
        assertFalse(loaded.blocks().saveState().blocks().getFirst().pinned());
        for(int i=0;i<12;i++) loaded.tick();
        assertTrue(loaded.blocks().blocks().getFirst().y()>0);
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void stableStackAndDiskRoundTrip(int size,@TempDir Path dir) throws Exception {
        var s=new FixedBlockSession(new World(8*size,10*size+1,World.Mode.BEDROCK_ENABLED),3,size);
        for(int i=0;i<3;i++) assertTrue(s.blocks().place(size,(9-i)*size,false));
        s.tick(); Path path=dir.resolve("stack.msim"); FixedSessionIO.write(s,path);
        var r=FixedSessionIO.read(path).fixed(); same(s,r); assertEquals(3,r.blocks().size());
        assertTrue(r.blocks().blocks().stream().allMatch(b->b.state()==FixedBlockWorld.State.STATIC));
        assertTrue(r.blocks().removeAt(size,9*size)); for(int i=0;i<40;i++) r.tick(); assertEquals(2,r.blocks().size());
    }
    private long[] materials(FixedBlockSession s) {
        long[] counts=new long[Material.values().length];
        for(int y=0;y<s.world().height();y++) for(int x=0;x<s.world().width();x++) counts[Byte.toUnsignedInt(s.world().get(x,y).id())]++;
        return counts;
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void poolReplayAndEditingAfterLoad(int size) throws Exception {
        var s=new FixedBlockSession(new World(32,33,World.Mode.BEDROCK_ENABLED),12345,size);
        assertTrue(s.blocks().place(0,0,false));
        for(int y=20;y<32;y++) for(int x=0;x<32;x++) s.world().set(x,y,Material.WATER);
        s.world().set(30,1,Material.SAND); s.world().set(29,1,Material.GRAVEL); s.world().set(28,1,Material.SOIL);
        byte[] file=bytes(s); var a=load(file); var b=load(file); same(s,a); same(a,b);
        long[] counts=materials(a);
        for(int i=0;i<80;i++) { a.tick(); b.tick(); if(i%10==0) { same(a,b); assertArrayEquals(counts,materials(a)); } }
        same(a,b); assertArrayEquals(counts,materials(a));
        a.blockStroke(16,0,16,0,false); assertNotNull(a.blocks().blockAt(16,0));
        a.blockStroke(16,0,16,0,true); assertNull(a.blocks().blockAt(16,0));
    }
    @Test void rejectsCorruptAndInconsistentPayloads() throws Exception {
        var s=new FixedBlockSession(new World(32,33,World.Mode.BEDROCK_ENABLED),1,4);
        s.blocks().place(4,4,false); s.blocks().place(12,4,false); byte[] good=bytes(s); int records=36+32*33;
        for(int len=0;len<good.length;len++) { int length=len; assertThrows(IOException.class,()->load(Arrays.copyOf(good,length))); }
        byte[] bad=good.clone(); bad[bad.length-1]^=1; final byte[] corrupted=bad; assertThrows(IOException.class,()->load(corrupted));
        bad=good.clone(); ByteBuffer.wrap(bad).putInt(4,99); final byte[] version=bad; assertThrows(IOException.class,()->load(version));
        bad=good.clone(); ByteBuffer.wrap(bad).putInt(8,99999); final byte[] dimensions=bad; assertThrows(IOException.class,()->load(dimensions));
        bad=good.clone(); ByteBuffer.wrap(bad).putLong(records+20,ByteBuffer.wrap(bad).getLong(records)); crc(bad); final byte[] duplicate=bad; assertThrows(IOException.class,()->load(duplicate));
        bad=good.clone(); ByteBuffer.wrap(bad).putInt(records+20+8,4); crc(bad); final byte[] overlap=bad; assertThrows(IOException.class,()->load(overlap));
        bad=good.clone(); bad[36+4*32+4]=0; crc(bad); final byte[] mismatch=bad; assertThrows(IOException.class,()->load(mismatch));
        bad=good.clone(); bad[36]=(byte)255; crc(bad); final byte[] invalidMaterial=bad; assertThrows(IOException.class,()->load(invalidMaterial));
        bad=good.clone(); bad[records+16]=7; crc(bad); final byte[] invalidState=bad; assertThrows(IOException.class,()->load(invalidState));
        bad=good.clone(); ByteBuffer.wrap(bad).putLong(28,1); crc(bad); final byte[] badNext=bad; assertThrows(IOException.class,()->load(badNext));
    }
    @Test void legacyV1AndFailureDoNotReplaceExistingScene(@TempDir Path dir) throws Exception {
        var old=new World(8,8); old.set(2,3,Material.STONE); var out=new ByteArrayOutputStream(); WorldIO.write(old,out);
        var legacy=FixedSessionIO.read(new ByteArrayInputStream(out.toByteArray())); assertTrue(legacy.legacy()); assertEquals(Material.STONE,legacy.world().get(2,3));
        var active=new FixedBlockSession(new World(24,25,World.Mode.BEDROCK_ENABLED),2,4); active.blocks().place(4,4,false);
        var before=active.blocks().saveState(); assertThrows(IOException.class,()->FixedSessionIO.read(new ByteArrayInputStream(new byte[]{1,2,3})));
        assertEquals(before,active.blocks().saveState());
        Path path=dir.resolve("existing.msim"); byte[] prior={1,2,3}; Files.write(path,prior);
        active.enqueue(()->{}); assertThrows(IOException.class,()->FixedSessionIO.write(active,path)); assertArrayEquals(prior,Files.readAllBytes(path)); assertEquals(before,active.blocks().saveState());
    }
    @Test void removedBlockIdsAreNotReusedAfterLoading() throws Exception {
        var s=new FixedBlockSession(new World(24,25,World.Mode.BEDROCK_ENABLED),1,4);
        assertTrue(s.blocks().place(4,4,false)); long old=s.blocks().blockAt(4,4).id(); assertTrue(s.blocks().removeAt(4,4));
        var loaded=load(bytes(s)); assertTrue(loaded.blocks().place(4,4,false)); assertTrue(loaded.blocks().blockAt(4,4).id()>old);
    }
    @Test void invalidOwnershipDoesNotOverwriteExistingFile(@TempDir Path dir) throws Exception {
        var s=new FixedBlockSession(new World(24,25,World.Mode.BEDROCK_ENABLED),1,4); s.blocks().place(4,4,false);
        s.world().set(4,4,Material.AIR); Path path=dir.resolve("safe.msim"); byte[] original={3,1,4}; Files.write(path,original);
        assertThrows(IOException.class,()->FixedSessionIO.write(s,path)); assertArrayEquals(original,Files.readAllBytes(path));
        assertEquals(Material.AIR,s.world().get(4,4)); assertEquals(1,s.blocks().size());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void seededReplayWithPeriodicSaveLoad(int size) throws Exception {
        var a=new FixedBlockSession(new World(24,25,World.Mode.BEDROCK_ENABLED),12345,size);
        var b=new FixedBlockSession(new World(24,25,World.Mode.BEDROCK_ENABLED),12345,size);
        for(var s:List.of(a,b)) { s.blocks().place(0,0,false); s.world().set(20,1,Material.WATER); s.world().set(22,1,Material.SAND); }
        long[] counts=materials(a);
        for(int tick=0;tick<10000;tick++) {
            a.tick(); b.tick();
            if(tick%250==0) { a=load(bytes(a)); b=load(bytes(b)); same(a,b); assertArrayEquals(counts,materials(a)); }
        }
        same(a,b);
    }
}
