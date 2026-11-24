package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalSupportTest {
    private record Point(int x,int y) { }
    private FixedBlockSession session(int size) { return new FixedBlockSession(new World(48*size,18*size+1,World.Mode.BEDROCK_ENABLED),12345,size); }
    private List<Point> stepped(int base,int levels) {
        var p=new ArrayList<Point>(); int x=base; p.add(new Point(x,17)); p.add(new Point(x,16));
        for(int i=0;i<levels;i++) { p.add(new Point(++x,16-i*2)); p.add(new Point(x,15-i*2)); if(i+1<levels) p.add(new Point(x,14-i*2)); }
        return p;
    }
    private void place(FixedBlockSession s,int z,List<Point> points) { for(var p:points) assertTrue(s.blocks().place(p.x*z,p.y*z,false),p.toString()); }
    private int cost(FixedBlockSession s,int z,int x,int y) { return s.blocks().blockSupportAt(x*z,y*z).distanceCells(); }
    @ParameterizedTest @ValueSource(ints={4,8}) void steppedColumnsBothDirectionsAndMultipleSteps(int z) {
        for(int levels:new int[]{1,3}) for(int direction:new int[]{-1,1}) {
            var s=session(z); var points=stepped(14,levels); int x=14+levels,y=15-2*(levels-1),reach=32/z;
            if(direction<0) points=points.stream().map(p->new Point(2*x-p.x,p.y)).toList();
            place(s,z,points);
            for(int i=1;i<=reach+1;i++) assertTrue(s.blocks().place((x+direction*i)*z,y*z,false));
            s.tick(); assertEquals(0,cost(s,z,x,y)); assertEquals(32,cost(s,z,x+direction*reach,y));
            assertEquals(-1,cost(s,z,x+direction*(reach+1),y));
            for(int i=0;i<600;i++) s.tick(); assertNotNull(s.blocks().blockAt((x+direction*reach)*z,y*z));
            assertNull(s.blocks().blockAt((x+direction*(reach+1))*z,y*z));
        }
    }
    private List<Point> bridge(int reach,int extra) {
        var p=new ArrayList<Point>(stepped(2,1)); int left=3,right=left+2*reach+1+extra;
        for(int y=17;y>=15;y--) p.add(new Point(right,y));
        for(int x=left+1;x<right;x++) p.add(new Point(x,15));
        return p;
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void bridgeOrderingAndExcessSpan(int z) {
        int reach=32/z; var geometry=bridge(reach,0); List<Integer> expected=null;
        for(int order=0;order<3;order++) {
            var s=session(z); var points=new ArrayList<>(geometry);
            if(order==1) Collections.reverse(points);
            if(order==2) { points.sort((a,b)->Integer.compare(Math.abs(a.x-12),Math.abs(b.x-12))); }
            place(s,z,points); s.tick(); var costs=new ArrayList<Integer>();
            for(var p:geometry) { int c=cost(s,z,p.x,p.y); assertTrue(c>=0,p.toString()); costs.add(c); }
            if(expected==null) expected=costs; else assertEquals(expected,costs);
            for(int i=0;i<600;i++) s.tick(); for(var p:geometry) assertNotNull(s.blocks().blockAt(p.x*z,p.y*z));
        }
        var s=session(z); var excess=bridge(reach,1); place(s,z,excess); s.tick();
        int middle=3+reach+1; assertEquals(-1,cost(s,z,middle,15));
        for(int i=0;i<9;i++) s.tick(); assertNull(s.blocks().blockAt(middle*z,15*z));
        assertNotNull(s.blocks().blockAt((3+reach)*z,15*z));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void foundationRemovalRestorationAndPersistence(int z) throws Exception {
        var s=session(z); var points=stepped(8,2); place(s,z,points); int x=10,y=13;
        for(int i=1;i<=32/z;i++) assertTrue(s.blocks().place((x+i)*z,y*z,false));
        s.tick(); var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out);
        var loaded=FixedSessionIO.read(new ByteArrayInputStream(out.toByteArray())).fixed(); loaded.tick(); assertEquals(32,cost(loaded,z,x+32/z,y));
        assertTrue(s.blocks().removeAt(8*z,17*z));
        for(int i=0;i<4;i++) { s.tick(); assertEquals(-1,cost(s,z,x,y)); }
        assertTrue(s.blocks().place(8*z,17*z,false)); s.tick(); assertEquals(0,cost(s,z,x,y));
        for(int i=0;i<12;i++) s.tick(); assertNotNull(s.blocks().blockAt((x+32/z)*z,y*z));
        assertTrue(s.blocks().removeAt(8*z,17*z)); for(int i=0;i<9;i++) s.tick();
        assertNull(s.blocks().blockAt((x+32/z)*z,y*z));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void bridgeRoundTripAndFloatingSteps(int z) throws Exception {
        var s=session(z); var points=bridge(32/z,0); place(s,z,points); s.tick();
        var out=new ByteArrayOutputStream(); FixedSessionIO.write(s,out);
        var loaded=FixedSessionIO.read(new ByteArrayInputStream(out.toByteArray())).fixed(); loaded.tick();
        for(var p:points) assertTrue(cost(loaded,z,p.x,p.y)>=0);
        var floating=session(z); var steps=new ArrayList<>(stepped(8,2)); steps.removeFirst();
        place(floating,z,steps); floating.tick();
        for(var p:steps) assertEquals(-1,cost(floating,z,p.x,p.y));
        long topId=floating.blocks().blockAt(10*z,13*z).id();
        for(int i=0;i<30;i++) floating.tick();
        assertTrue(floating.blocks().blocks().stream().anyMatch(b->b.id()==topId && b.y()>13*z));
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void steppedReplayTenThousandTicks(int z) throws Exception {
        var a=session(z); var b=session(z); var fixture=bridge(32/z,0);
        for(var s:List.of(a,b)) { place(s,z,fixture); s.world().set(35*z,1,Material.WATER); s.world().set(36*z,1,Material.SAND); s.world().set(37*z,1,Material.GRAVEL); s.world().set(38*z,1,Material.SOIL); }
        for(int tick=0;tick<10000;tick++) {
            for(var s:List.of(a,b)) {
                if(tick%1000==10) s.enqueue(()->s.blocks().removeAt(2*z,17*z));
                if(tick%1000==300) s.enqueue(()->s.blockStroke(0,0,3*z,0,false));
                if(tick%1000==301) s.enqueue(()->s.blocks().releaseStroke());
                if(tick%1000==600) s.enqueue(()->s.blockStroke(0,0,3*z,0,true));
                s.tick();
            }
            if(tick%250==0) {
                var ao=new ByteArrayOutputStream(); var bo=new ByteArrayOutputStream(); FixedSessionIO.write(a,ao); FixedSessionIO.write(b,bo); assertArrayEquals(ao.toByteArray(),bo.toByteArray());
                a=FixedSessionIO.read(new ByteArrayInputStream(ao.toByteArray())).fixed(); b=FixedSessionIO.read(new ByteArrayInputStream(bo.toByteArray())).fixed();
                int stone=0; int[] materials=new int[Material.values().length];
                for(int y=0;y<a.world().height();y++) for(int x=0;x<a.world().width();x++) { var m=a.world().get(x,y); materials[Byte.toUnsignedInt(m.id())]++; if(m==Material.STONE) { stone++; assertNotNull(a.blocks().blockAt(x,y)); } }
                assertEquals(a.blocks().size()*z*z,stone); for(var m:List.of(Material.WATER,Material.SAND,Material.GRAVEL,Material.SOIL)) assertEquals(1,materials[Byte.toUnsignedInt(m.id())]);
            }
        }
    }
}
