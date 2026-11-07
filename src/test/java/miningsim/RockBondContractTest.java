package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.structural.RockConnectivity;
import miningsim.structural.RockConnectivity.BondState;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class RockBondContractTest {
    @Test void statesRevisionsAndIncarnationsSurvivePendingEdits() {
        World w = new World(66, 66, World.Mode.BEDROCK_ENABLED);
        RockConnectivity c = new RockConnectivity(w);
        long r = c.revision();
        assertEquals(BondState.ABSENT, c.bondState(63, 63, 64, 63));
        w.set(63, 63, Material.STONE); assertTrue(c.revision() > r); r = c.revision();
        w.set(64, 63, Material.STONE); assertTrue(c.revision() > r);
        assertEquals(c.bondKey(63, 63, 64, 63), c.bondKey(64, 63, 63, 63));
        assertEquals(BondState.INTACT, c.bondState(64, 63, 63, 63));
        long incarnation = c.bondIncarnation(63, 63, 64, 63);
        r = c.revision(); assertTrue(c.breakBond(63, 63, 64, 63)); assertTrue(c.revision() > r);
        assertEquals(BondState.BROKEN, c.bondState(64, 63, 63, 63));
        assertEquals(incarnation, c.bondIncarnation(64, 63, 63, 63));
        r = c.revision(); assertFalse(c.breakBond(64, 63, 63, 63)); assertEquals(r, c.revision());
        w.set(63, 63, Material.AIR); assertEquals(BondState.ABSENT, c.bondState(63, 63, 64, 63));
        assertThrows(IllegalStateException.class, () -> c.bondIncarnation(63, 63, 64, 63));
        w.set(63, 63, Material.STONE); assertEquals(BondState.INTACT, c.bondState(63, 63, 64, 63));
        assertTrue(c.bondIncarnation(63, 63, 64, 63) > incarnation);
        r = c.revision(); w.set(63, 63, Material.STONE); w.set(0, 65, Material.AIR);
        w.set(0, 0, Material.WATER); assertEquals(r, c.revision());
        w.clear(); assertTrue(c.revision() > r); assertEquals(BondState.ABSENT, c.bondState(63, 63, 64, 63));
    }

    @Test void deterministicBoundedEnumerationIncludesBrokenAndCrossChunkBonds() {
        World w = new World(66, 66);
        for (int y = 63; y <= 64; y++) for (int x = 63; x <= 64; x++) w.set(x, y, Material.STONE);
        RockConnectivity c = new RockConnectivity(w); c.breakBond(63, 63, 64, 63);
        List<String> bonds = new ArrayList<>();
        c.forEachBond(63, 63, 65, 65, (x,y,nx,ny,state) -> bonds.add(x+","+y+"-"+nx+","+ny+":"+state));
        assertEquals(List.of("63,63-64,63:BROKEN", "63,63-63,64:INTACT", "64,63-64,64:INTACT", "63,64-64,64:INTACT"), bonds);
        assertThrows(IllegalStateException.class, () -> c.forEachBond(63,63,65,65,
                (x,y,nx,ny,state) -> w.set(x,y,Material.AIR)));
    }

    @Test void bulkInvalidationChangesIncarnation() {
        World w = new World(2, 2); w.set(0,0,Material.STONE); w.set(1,0,Material.STONE);
        RockConnectivity c = new RockConnectivity(w);
        long old = c.bondIncarnation(0,0,1,0), rev = c.revision();
        w.clear(); assertTrue(c.revision() > rev);
        w.set(0,0,Material.STONE); w.set(1,0,Material.STONE);
        assertTrue(c.bondIncarnation(0,0,1,0) > old);
    }

    @Test void revisionExhaustionFailsClosedWithoutThrowingFromCallback() throws Exception {
        World w = new World(2,1); RockConnectivity c = new RockConnectivity(w);
        var field = RockConnectivity.class.getDeclaredField("revision"); field.setAccessible(true); field.setLong(c, Long.MAX_VALUE - 1);
        w.set(0,0,Material.STONE); assertEquals(Long.MAX_VALUE,c.revision());
        assertDoesNotThrow(() -> w.set(1,0,Material.STONE));
        assertThrows(IllegalStateException.class, c::revision);
        assertThrows(IllegalStateException.class, () -> c.bondState(0,0,1,0));
        assertThrows(IllegalStateException.class, () -> c.advance(1));
    }
}
