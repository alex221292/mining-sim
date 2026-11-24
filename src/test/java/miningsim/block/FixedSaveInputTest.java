package miningsim.block;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import miningsim.app.SimulationControl;
import miningsim.persistence.FixedSessionIO;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FixedSaveInputTest {
    private FixedBlockSession session(int size) { return new FixedBlockSession(new World(64,65,World.Mode.BEDROCK_ENABLED),12345,size); }
    private void draw(FixedBlockSession s,int x,int end) {
        s.enqueue(()->s.blockStroke(x,0,x,0,false));
        s.enqueue(()->s.blockStroke(x,0,end,0,false));
        s.enqueue(()->s.blocks().releaseStroke());
    }
    private FixedBlockSession load(FixedSessionIO.Snapshot snapshot,Path file) throws IOException {
        snapshot.write(file); return FixedSessionIO.read(file).fixed();
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void drawAndSaveOnPauseWithoutTick(int size,@TempDir Path dir) throws Exception {
        var s=session(size); var control=new SimulationControl(s::tick); control.togglePause();
        draw(s,0,size*2); var loaded=load(FixedSessionIO.capture(s),dir.resolve("draw.msim"));
        assertTrue(control.paused()); assertEquals(0,s.cellularTicks()); assertFalse(s.hasPendingCommands());
        assertEquals(3,loaded.blocks().size()); assertEquals(3,s.blocks().size());
        assertEquals(4,s.blocks().saveState().nextId());
        assertTrue(loaded.blocks().saveState().blocks().stream().noneMatch(FixedBlockWorld.SavedBlock::pinned));
        for(int i=0;i<12;i++) loaded.tick(); assertTrue(loaded.blocks().blocks().getFirst().y()>0);
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void runningRepeatedSavesAndBarrierOrdering(int size,@TempDir Path dir) throws Exception {
        var s=session(size); var control=new SimulationControl(s::tick); draw(s,0,size);
        s.enqueue(()->s.enqueue(()->s.blockStroke(size*3,0,size*3,0,false)));
        var first=FixedSessionIO.capture(s); assertTrue(s.hasPendingCommands());
        s.enqueue(()->s.blocks().releaseStroke());
        var saved=load(first,dir.resolve("first.msim")); assertEquals(2,saved.blocks().size());
        var second=load(FixedSessionIO.capture(s),dir.resolve("second.msim")); assertEquals(3,second.blocks().size());
        assertFalse(s.hasPendingCommands()); assertEquals(0,s.cellularTicks()); assertFalse(control.paused());
        assertTrue(control.tick()); assertEquals(1,s.cellularTicks());
        s.enqueue(()->s.blockStroke(0,0,0,0,true));
        var third=load(FixedSessionIO.capture(s),dir.resolve("third.msim"));
        assertEquals(2,third.blocks().size()); assertEquals(2,saved.blocks().size());
        assertEquals(3,second.blocks().size()); assertFalse(s.hasPendingCommands());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void delayFallingAndActiveStroke(int size,@TempDir Path dir) throws Exception {
        var s=session(size); var control=new SimulationControl(s::tick); assertTrue(s.blocks().place(0,0,false));
        for(int i=0;i<3;i++) assertTrue(control.tick()); control.togglePause();
        var waiting=s.blocks().saveState().blocks().getFirst(); long tick=s.cellularTicks();
        s.enqueue(()->s.blockStroke(size*2,0,size*2,0,false));
        var a=load(FixedSessionIO.capture(s),dir.resolve("wait.msim"));
        assertEquals(waiting,a.blocks().saveState().blocks().getFirst()); assertEquals(tick,s.cellularTicks()); assertTrue(control.paused());
        assertFalse(a.blocks().saveState().blocks().get(1).pinned());
        assertTrue(control.singleStep()); assertEquals(tick+1,s.cellularTicks());
        for(int i=0;i<6;i++) assertTrue(control.singleStep());
        assertEquals(FixedBlockWorld.State.FALLING,s.blocks().blocks().getFirst().state());
        var before=s.blocks().saveState(); long now=s.cellularTicks();
        var falling=load(FixedSessionIO.capture(s),dir.resolve("falling.msim"));
        assertEquals(before.blocks().getFirst(),falling.blocks().saveState().blocks().getFirst()); assertEquals(now,s.cellularTicks());
    }
    @ParameterizedTest @ValueSource(ints={4,8}) void loadFailureCancellationAndOldQueueIsolation(int size,@TempDir Path dir) throws Exception {
        var old=session(size); draw(old,0,size); var replacement=session(size); replacement.blocks().place(size*4,0,false);
        Path valid=dir.resolve("valid.msim"); FixedSessionIO.capture(replacement).write(valid);
        Path invalid=dir.resolve("bad.msim"); Files.write(invalid,new byte[]{1,2,3});
        var active=old; // Cancellation performs no read or publication, as in chooseFile.
        assertSame(old,active); assertThrows(IOException.class,()->FixedSessionIO.read(invalid));
        assertSame(old,active); assertTrue(old.hasPendingCommands());
        var prepared=FixedSessionIO.read(valid); active=prepared.fixed();
        assertNotSame(old,active); assertFalse(active.hasPendingCommands()); active.tick();
        assertEquals(1,active.blocks().size()); assertNull(active.blocks().blockAt(0,0));
        assertTrue(old.hasPendingCommands());
    }
}
