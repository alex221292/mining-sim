package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.*;
import miningsim.world.*;
import org.junit.jupiter.api.Test;

class RockDamageSystemTest {
    private static void finish(RockConnectivity c) { while(c.pending()) c.advance(10000); }
    private static RockMaterialRegistry registry(double strength, double rate) {
        return new RockMaterialRegistry.Builder().register(1,new RockProperties(2500,1e8,strength,strength,strength,strength,rate)).build();
    }
    @Test void anchoredColumnMatchesAnalyticalWeightAndDamageTicks() {
        World w = new World(2,4,World.Mode.BEDROCK_ENABLED);
        w.set(0,2,Material.STONE); w.set(0,1,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        RockDamageSystem s = new RockDamageSystem(w,c,registry(100,0.3),10);
        assertEquals(RockDamageSystem.Status.CONVERGED,s.analyze(0,1));
        var b = s.snapshot().bonds().getFirst();
        // Upper cell weight = 2500 kg/m³ * 0.001 m³ * 10 m/s² = 25 N.
        assertEquals(25,Math.abs(b.axial1()),1e-8); assertEquals(25,Math.abs(b.axial2()),1e-8);
        assertEquals(25, b.utilization(),1e-8);
        s.advanceTick(2); assertEquals(0.12,s.snapshot().bonds().getFirst().damage(),1e-10);
        assertThrows(IllegalArgumentException.class,()->s.advanceTick(2));
        s.analyze(0,1); assertEquals(0.12,s.snapshot().bonds().getFirst().damage(),1e-10);
        s.advanceTick(9); assertEquals(0.24,s.snapshot().bonds().getFirst().damage(),1e-10);
    }
    @Test void breakReplacementAndStaleTicks() {
        World w = new World(2,4,World.Mode.BEDROCK_ENABLED);
        w.set(0,2,Material.STONE); w.set(0,1,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        RockDamageSystem s = new RockDamageSystem(w,c,registry(100,100),10);
        s.analyze(0,1); assertEquals(RockDamageSystem.Status.PENDING,s.advanceTick(0));
        assertEquals(RockConnectivity.BondState.BROKEN,c.bondState(0,1,0,2));
        assertThrows(IllegalStateException.class,s::snapshot);
        finish(c); assertEquals(RockDamageSystem.Status.UNSUPPORTED,s.analyze(0,1));
        w.set(0,1,Material.AIR); w.set(0,1,Material.STONE);
        assertEquals(RockDamageSystem.Status.PENDING,s.advanceTick(1)); finish(c);
        assertEquals(RockDamageSystem.Status.CONVERGED,s.analyze(0,1));
        assertEquals(0,s.snapshot().bonds().getFirst().damage());
        w.set(0,1,Material.AIR); assertEquals(RockDamageSystem.Status.PENDING,s.status());
        assertEquals(RockDamageSystem.Status.PENDING,s.advanceTick(2));
    }
    @Test void disconnectedAndLargeAreExplicit() {
        World w = new World(70,3,World.Mode.BEDROCK_ENABLED);
        for(int x=0;x<65;x++) w.set(x,1,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        RockDamageSystem s = new RockDamageSystem(w,c,registry(1e8,1),10);
        assertEquals(RockDamageSystem.Status.TOO_LARGE,s.analyze(0,1));
        w.clear(); w.set(0,0,Material.STONE); finish(c);
        assertEquals(RockDamageSystem.Status.UNSUPPORTED,s.analyze(0,0));
    }
    private static double cantileverMoment(int length) {
        World w = new World(70,4,World.Mode.BEDROCK_ENABLED);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        w.set(63,2,Material.STONE);
        for(int x=63;x<63+length;x++) w.set(x,1,Material.STONE);
        finish(c); RockDamageSystem s = new RockDamageSystem(w,c,registry(1e8,1),10);
        assertEquals(RockDamageSystem.Status.CONVERGED,s.analyze(63,1));
        return s.snapshot().bonds().stream().mapToDouble(b -> Math.max(Math.abs(b.moment1()),Math.abs(b.moment2()))).max().orElseThrow();
    }
    @Test void crossChunkCantileverMomentGrowsAndNoopDoesNotInvalidate() {
        assertTrue(cantileverMoment(5) > cantileverMoment(2));
        World w = new World(2,4,World.Mode.BEDROCK_ENABLED); w.set(0,1,Material.STONE); w.set(0,2,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        RockDamageSystem s = new RockDamageSystem(w,c,registry(1e8,1),10); s.analyze(0,1);
        w.set(0,1,Material.STONE); w.set(0,3,Material.AIR);
        assertEquals(RockDamageSystem.Status.CONVERGED,s.status()); s.advanceTick(0);
        assertEquals(0,s.snapshot().bonds().getFirst().damage());
    }
    @Test void numericalFailureConsumesTickWithoutDamage() {
        World w = new World(2,4,World.Mode.BEDROCK_ENABLED);
        w.set(0,1,Material.STONE); w.set(0,2,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        var bad = new RockMaterialRegistry.Builder().register(1,new RockProperties(Double.MAX_VALUE,1e8,1,1,1,1,1)).build();
        RockDamageSystem s = new RockDamageSystem(w,c,bad,Double.MAX_VALUE);
        assertEquals(RockDamageSystem.Status.NON_CONVERGED,s.analyze(0,1));
        assertEquals(RockDamageSystem.Status.NON_CONVERGED,s.advanceTick(0));
        assertEquals(RockConnectivity.BondState.INTACT,c.bondState(0,1,0,2));
        assertThrows(IllegalArgumentException.class,()->s.advanceTick(0));
    }
    private static double roofMoment(int span) {
        World w = new World(span + 1,4,World.Mode.BEDROCK_ENABLED);
        for (int x=0;x<=span;x++) w.set(x,1,Material.STONE);
        w.set(0,2,Material.STONE); w.set(span,2,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        RockDamageSystem s = new RockDamageSystem(w,c,registry(1e10,0.3),10);
        assertEquals(RockDamageSystem.Status.CONVERGED,s.analyze(0,1));
        assertTrue(s.snapshot().bonds().stream().allMatch(b -> b.utilization() < 1));
        s.advanceTick(0); assertTrue(s.snapshot().bonds().stream().allMatch(b -> b.damage() == 0));
        return s.snapshot().bonds().stream().mapToDouble(b -> Math.max(Math.abs(b.moment1()),Math.abs(b.moment2()))).max().orElseThrow();
    }
    @Test void longerSupportedRoofHasHigherMoment() { assertTrue(roofMoment(6) > roofMoment(2)); }

    @Test void removingAndRestoringBeforeReanalysisResetsDamage() {
        World w = new World(2,4,World.Mode.BEDROCK_ENABLED);
        w.set(0,2,Material.STONE); w.set(0,1,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); finish(c);
        RockDamageSystem s = new RockDamageSystem(w,c,registry(100,0.3),10);
        s.analyze(0,1); s.advanceTick(0); assertTrue(s.snapshot().bonds().getFirst().damage() > 0);
        w.set(0,1,Material.AIR); w.set(0,1,Material.STONE); finish(c);
        s.analyze(0,1); assertEquals(0,s.snapshot().bonds().getFirst().damage());
    }
}
