package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.*;
import miningsim.world.*;
import org.junit.jupiter.api.Test;

class RockCalibrationTest {
    private static RockDamageSystem.Snapshot analyze(RockReferenceScenes.Scene scene) {
        RockReferenceScenes.finishConnectivity(scene.connectivity());
        assertEquals(RockDamageSystem.Status.CONVERGED,scene.damage().analyze(scene.x(),scene.y()));
        var result = scene.damage().snapshot();
        for(var b:result.bonds()) {
            assertTrue(Double.isFinite(b.utilization())); assertTrue(Double.isFinite(b.moment1())); assertTrue(Double.isFinite(b.moment2()));
        }
        assertTrue(Double.isFinite(result.maxDisplacementMetres()));
        return result;
    }
    private static double peak(RockDamageSystem.Snapshot s) { return s.bonds().stream().mapToDouble(RockDamageSystem.Bond::utilization).max().orElseThrow(); }
    private static double moment(RockDamageSystem.Snapshot s) { return s.bonds().stream().mapToDouble(b->Math.max(Math.abs(b.moment1()),Math.abs(b.moment2()))).max().orElseThrow(); }

    @Test void shortRoofRemainsIntact() {
        var s=RockReferenceScenes.roof("short",4,1); var initial=analyze(s); assertTrue(peak(initial)<0.6);
        for(int tick=0;tick<600;tick++) assertEquals(RockDamageSystem.Status.CONVERGED,s.damage().advanceTick(tick));
        assertTrue(s.damage().snapshot().bonds().stream().allMatch(b->b.damage()==0));
        for(var b:initial.bonds()) assertEquals(RockConnectivity.BondState.INTACT,s.connectivity().bondState(b.x1(),b.y1(),b.x2(),b.y2()));
    }
    private static String failureHistory() {
        var s=RockReferenceScenes.roof("long",12,1); var initial=analyze(s); assertTrue(peak(initial)>1);
        int firstDamage=-1, failed=-1, key=-1; double prior=0;
        for(int tick=0;tick<600;tick++) {
            var status=s.damage().advanceTick(tick);
            if(status==RockDamageSystem.Status.PENDING) {
                failed=tick;
                assertThrows(IllegalStateException.class,s.damage()::snapshot);
                for(var b:initial.bonds()) if(s.connectivity().bondState(b.x1(),b.y1(),b.x2(),b.y2())==RockConnectivity.BondState.BROKEN) { key=b.key(); break; }
                break;
            }
            assertEquals(RockDamageSystem.Status.CONVERGED,status);
            double damage=s.damage().snapshot().bonds().stream().mapToDouble(RockDamageSystem.Bond::damage).max().orElseThrow();
            if(damage>0 && firstDamage<0) firstDamage=tick;
            assertTrue(damage>=prior); prior=damage;
            if(tick==1) { s.damage().analyze(s.x(),s.y()); assertEquals(damage,s.damage().snapshot().bonds().stream().mapToDouble(RockDamageSystem.Bond::damage).max().orElseThrow(),1e-12); }
        }
        assertEquals(0,firstDamage); assertTrue(failed>1 && failed<600); assertTrue(key>=0);
        RockReferenceScenes.finishConnectivity(s.connectivity()); assertEquals(2,s.connectivity().componentCount());
        var post=analyze(s); int finalKey=key; assertTrue(post.bonds().stream().noneMatch(b->b.key()==finalKey));
        for(int i=0;i<s.connectivity().componentCount();i++) {
            int id=s.connectivity().componentId(i);
            assertEquals(RockDamageSystem.Status.CONVERGED,s.damage().analyze(id%s.world().width(),id/s.world().width()));
        }
        return failed+":"+key;
    }
    @Test void longRoofDamageAndReplay() { assertEquals(failureHistory(),failureHistory()); }
    @Test void cantileverHasIndependentMomentReference() {
        double shortM=moment(analyze(RockReferenceScenes.cantilever(4)));
        double longM=moment(analyze(RockReferenceScenes.cantilever(8)));
        double weight=RockReferenceScenes.GAMEPLAY_STONE.densityKgPerM3()*0.001*RockReferenceScenes.GRAVITY;
        // Discrete nodal gravity: sum P*(i*0.1), i=1..n; continuum w*L²/2 omits half an end cell.
        assertEquals(weight*0.1*4*5/2,shortM,1e-6);
        assertEquals(weight*0.1*8*9/2,longM,1e-5);
        assertTrue(longM>shortM);
    }
    @Test void thickAndThinRemainDiagnosticAndIslandUnsupported() {
        var thin=analyze(RockReferenceScenes.roof("thin",12,1));
        var thick=analyze(RockReferenceScenes.roof("thick",12,2));
        assertTrue(thick.maxDisplacementMetres()<thin.maxDisplacementMetres());
        assertTrue(peak(thick)>0);
        var island=RockReferenceScenes.island(); RockReferenceScenes.finishConnectivity(island.connectivity());
        assertEquals(RockDamageSystem.Status.UNSUPPORTED,island.damage().analyze(island.x(),island.y()));
    }
}
