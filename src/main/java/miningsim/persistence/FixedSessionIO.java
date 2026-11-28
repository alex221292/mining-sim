package miningsim.persistence;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import miningsim.block.FixedBlockSession;
import miningsim.block.FixedBlockWorld;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.resource.OreComposition;
import miningsim.resource.OreId;

/** MSIM v5 fixed-block sessions; reads v2–v4 and delegates v1 to WorldIO. Streams remain caller-owned. */
public final class FixedSessionIO {
    private static final int MAGIC=0x4d53494d, VERSION=5;
    private static int rockCode(Material m) {
        return switch(m) { case STONE -> 0; case GRANITE -> 1; case LIMESTONE -> 2; case MARBLE -> 3; default -> throw new IllegalArgumentException("Invalid rock material"); };
    }
    private static Material rockMaterial(int code) throws IOException {
        return switch(code) { case 0 -> Material.STONE; case 1 -> Material.GRANITE; case 2 -> Material.LIMESTONE; case 3 -> Material.MARBLE; default -> throw new IOException("Invalid rock substance"); };
    }
    public record Loaded(World world,FixedBlockSession fixed) { public boolean legacy() { return fixed==null; } }
    private FixedSessionIO() { }
    private static void dimensions(int w,int h) throws IOException {
        if(w<1 || h<1 || w>WorldIO.MAX_DIMENSION || h>WorldIO.MAX_DIMENSION || (long)w*h>WorldIO.MAX_CELLS)
            throw new IOException("Invalid or oversized world dimensions: "+w+"x"+h);
    }
    public static void write(FixedBlockSession session,OutputStream output) throws IOException {
        if(session.hasPendingCommands()) throw new IOException("Cannot save while input commands are pending");
        writeState(session,output);
    }
    private static void writeState(FixedBlockSession session,OutputStream output) throws IOException {
        var w=session.world(); dimensions(w.width(),w.height()); var blocks=session.blocks(); var saved=blocks.saveState();
        try { new FixedBlockWorld(w,blocks.rockBlockSizeCells()).restoreState(saved); }
        catch(IllegalArgumentException | IllegalStateException ex) { throw new IOException("Invalid fixed-block state: "+ex.getMessage(),ex); }
        var crc=new CRC32(); var out=new DataOutputStream(new CheckedOutputStream(output,crc));
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(w.width()); out.writeInt(w.height());
        out.writeInt(1); out.writeInt(blocks.rockBlockSizeCells()); out.writeInt(saved.blocks().size()); out.writeLong(saved.nextId());
        for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) out.writeByte(w.get(x,y).id());
        for(var b:saved.blocks()) {
            out.writeLong(b.id()); out.writeInt(b.x()); out.writeInt(b.y()); out.writeByte(b.state()==FixedBlockWorld.State.STATIC?0:1);
            out.writeByte(b.unsupportedTicks()); out.writeByte(b.pinned()?1:0); out.writeByte(rockCode(b.material()));
        }
        int oreBlocks=0; for(var b:saved.blocks()) if(!b.ores().components().isEmpty()) oreBlocks++;
        out.writeInt(oreBlocks);
        for(var b:saved.blocks()) if(!b.ores().components().isEmpty()) {
            out.writeLong(b.id()); out.writeByte(b.ores().components().size());
            for(var c:b.ores().components()) { out.writeByte(c.ore().id()); out.writeInt(c.ppm()); }
        }
        var palette = new java.util.LinkedHashMap<OreComposition,Integer>();
        int oreCells = 0;
        for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) {
            var ore=w.gritOre(x,y);
            if(!ore.components().isEmpty()) {
                if(!w.get(x,y).isGrit()) throw new IOException("Ore on non-grit cell");
                palette.computeIfAbsent(ore,k->palette.size()+1); oreCells++;
            }
        }
        out.writeInt(palette.size());
        for(var ore:palette.keySet()) {
            out.writeByte(ore.components().size());
            for(var c:ore.components()) { out.writeByte(c.ore().id()); out.writeInt(c.ppm()); }
        }
        out.writeInt(oreCells);
        for(int y=0;y<w.height();y++) for(int x=0;x<w.width();x++) {
            var ore=w.gritOre(x,y);
            if(!ore.components().isEmpty()) { out.writeInt(y*w.width()+x); out.writeInt(palette.get(ore)); }
        }
        out.flush(); var raw=new DataOutputStream(output); raw.writeInt((int)crc.getValue()); raw.flush();
    }
    /** Owner-thread save barrier: later commands stay queued and cannot change these bytes. */
    public static Snapshot capture(FixedBlockSession session) throws IOException {
        session.applyCommandsAtSaveBarrier();
        var out=new ByteArrayOutputStream();
        writeState(session,out);
        return new Snapshot(out.toByteArray());
    }
    public static final class Snapshot {
        private final byte[] bytes;
        private Snapshot(byte[] bytes) { this.bytes=bytes; }
        public void write(Path path) throws IOException { writeAtomically(path,out->out.write(bytes)); }
    }
    public static Loaded read(InputStream source) throws IOException {
        var input=source instanceof BufferedInputStream b?b:new BufferedInputStream(source);
        input.mark(8); var header=new DataInputStream(input);
        try {
            if(header.readInt()!=MAGIC) throw new IOException("Invalid MSIM magic");
            int version=header.readInt(); input.reset();
            if(version==1) { var world=WorldIO.read(input); return new Loaded(world,null); }
            if(version!=2 && version!=3 && version!=4 && version!=VERSION) throw new IOException("Unsupported MSIM version: "+version);
            var crc=new CRC32(); var in=new DataInputStream(new CheckedInputStream(input,crc));
            in.readInt(); in.readInt(); int width=in.readInt(),height=in.readInt(),mode=in.readInt(),size=in.readInt(),count=in.readInt(); long nextId=in.readLong();
            dimensions(width,height);
            if(mode!=1) throw new IOException("Invalid world mode");
            if(size!=4 && size!=8) throw new IOException("Invalid block size");
            if(count<0 || count>(long)width*height/(size*size)) throw new IOException("Invalid block count");
            var world=new World(width,height,World.Mode.BEDROCK_ENABLED);
            int gritCount=0;
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                int id=in.readUnsignedByte(); Material material;
                if(version==2 && id>7) throw new IOException("Invalid v2 material ID "+id);
                if(version<5 && id>10) throw new IOException("Invalid historical material ID "+id);
                try { material=Material.fromId((byte)id); } catch(IllegalArgumentException ex) { throw new IOException("Invalid material ID "+id,ex); }
                if(material.isGrit()) gritCount++;
                if((y==height-1)!=(material==Material.BEDROCK)) throw new IOException("Invalid BEDROCK boundary");
                if(material!=Material.AIR && material!=Material.BEDROCK) world.set(x,y,material);
            }
            var records=new ArrayList<FixedBlockWorld.SavedBlock>(count);
            for(int i=0;i<count;i++) {
                long id=in.readLong(); int x=in.readInt(),y=in.readInt(),state=in.readUnsignedByte(),wait=in.readUnsignedByte(),pinned=in.readUnsignedByte(),substance=in.readUnsignedByte();
                if(state>1 || pinned>1 || version==2 && substance!=0) throw new IOException("Invalid block state or substance");
                records.add(new FixedBlockWorld.SavedBlock(id,x,y,state==0?FixedBlockWorld.State.STATIC:FixedBlockWorld.State.FALLING,wait,pinned==1,rockMaterial(substance)));
            }
            if(version>=4) {
                int oreBlocks=in.readInt(); if(oreBlocks<0 || oreBlocks>count) throw new IOException("Invalid ore block count");
                var indexes=new java.util.HashMap<Long,Integer>();
                for(int i=0;i<records.size();i++) if(indexes.put(records.get(i).id(),i)!=null) throw new IOException("Duplicate block ID");
                var seen=new java.util.HashSet<Long>();
                for(int i=0;i<oreBlocks;i++) {
                    long id=in.readLong(); int n=in.readUnsignedByte(); Integer index=indexes.get(id);
                    if(index==null || !seen.add(id) || n==0 || n>OreId.values().length) throw new IOException("Invalid ore block record");
                    var components=new OreComposition.Component[n];
                    try {
                        for(int j=0;j<n;j++) components[j]=new OreComposition.Component(OreId.fromId(in.readUnsignedByte()),in.readInt());
                        var b=records.get(index);
                        records.set(index,new FixedBlockWorld.SavedBlock(b.id(),b.x(),b.y(),b.state(),b.unsupportedTicks(),b.pinned(),b.material(),OreComposition.of(components)));
                    } catch(IllegalArgumentException | NullPointerException ex) { throw new IOException("Invalid ore composition",ex); }
                }
            }
            if(version>=5) {
                int n=in.readInt();
                if(n<0 || n>gritCount) throw new IOException("Invalid grit palette size");
                var palette=new ArrayList<OreComposition>(n);
                var unique=new java.util.HashSet<OreComposition>();
                for(int i=0;i<n;i++) {
                    int countComponents=in.readUnsignedByte();
                    if(countComponents==0 || countComponents>OreId.values().length) throw new IOException("Invalid grit composition size");
                    var components=new OreComposition.Component[countComponents];
                    try {
                        for(int j=0;j<countComponents;j++) components[j]=new OreComposition.Component(OreId.fromId(in.readUnsignedByte()),in.readInt());
                        var ore=OreComposition.of(components);
                        if(!unique.add(ore)) throw new IOException("Duplicate grit composition");
                        palette.add(ore);
                    } catch(IllegalArgumentException | NullPointerException ex) { throw new IOException("Invalid grit composition",ex); }
                }
                int cells=in.readInt();
                if(cells<0 || cells>gritCount || cells<n) throw new IOException("Invalid grit ore count");
                var used=new boolean[n];
                int previous=-1;
                for(int i=0;i<cells;i++) {
                    int index=in.readInt(), ref=in.readInt();
                    if(index<=previous || index>=(long)width*height || ref<1 || ref>n) throw new IOException("Invalid grit ore record");
                    previous=index; int x=index%width,y=index/width;
                    if(!world.get(x,y).isGrit()) throw new IOException("Ore on non-grit cell");
                    world.setGritOre(x,y,palette.get(ref-1));
                    used[ref-1]=true;
                }
                for(boolean value:used) if(!value) throw new IOException("Unused grit palette entry");
            }
            long actual=crc.getValue(); var raw=new DataInputStream(input); long expected=Integer.toUnsignedLong(raw.readInt());
            if(actual!=expected) throw new IOException("MSIM checksum mismatch");
            if(raw.read()!=-1) throw new IOException("Unexpected trailing data after MSIM checksum");
            var session=new FixedBlockSession(world,12345,size);
            try { session.blocks().restoreState(new FixedBlockWorld.SavedState(nextId,records)); }
            catch(IllegalArgumentException | IllegalStateException ex) { throw new IOException("Invalid fixed-block state: "+ex.getMessage(),ex); }
            // Mouse strokes do not survive session replacement; release their temporary pins.
            session.blocks().releaseStroke();
            return new Loaded(world,session);
        } catch(EOFException ex) { throw new IOException("Truncated MSIM file",ex); }
    }
    public static Loaded read(Path path) throws IOException { try(var in=Files.newInputStream(path)) { return read(in); } }
    /** An unsupported atomic replacement fails rather than risking an existing save. */
    public static void write(FixedBlockSession session,Path path) throws IOException {
        writeAtomically(path,out->write(session,out));
    }
    @FunctionalInterface private interface Writer { void write(OutputStream out) throws IOException; }
    private static void writeAtomically(Path path,Writer writer) throws IOException {
        Path absolute=path.toAbsolutePath(),temp=Files.createTempFile(absolute.getParent(),".msim-",".tmp");
        try {
            try(var out=new BufferedOutputStream(Files.newOutputStream(temp))) { writer.write(out); }
            Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
