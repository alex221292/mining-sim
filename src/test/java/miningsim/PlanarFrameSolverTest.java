package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.PlanarFrameSolver;
import miningsim.structural.PlanarFrameSolver.Status;
import org.junit.jupiter.api.Test;

class PlanarFrameSolverTest {
    private static final double EA = 1000, EI = 4;
    private PlanarFrameSolver cantilever(double length, double load) {
        var s = new PlanarFrameSolver(0, 1e-10);
        s.addNode(0,0,true,true,true,0,0,0); s.addNode(length,0,false,false,false,0,-load,0);
        s.addElement(0,1,EA,EI,0); return s;
    }
    @Test void cantileverAnalyticalAndSpanScaling() {
        var a = cantilever(1, 3).solve(); var b = cantilever(2, 3).solve();
        assertEquals(Status.CONVERGED,a.status()); assertEquals(Status.CONVERGED,b.status());
        assertEquals(-3.0/(3*EI),a.displacements()[4],1e-10);
        assertEquals(-3.0*8/(3*EI),b.displacements()[4],1e-10);
        assertEquals(6,Math.abs(b.endForces()[0][2]),1e-10);
    }
    @Test void simplySupportedBeamPointLoadMatchesTheory() {
        var s = new PlanarFrameSolver(0,1e-10);
        s.addNode(0,0,true,true,false,0,0,0); s.addNode(1,0,false,false,false,0,-3,0);
        s.addNode(2,0,false,true,false,0,0,0);
        s.addElement(0,1,EA,EI,0); s.addElement(1,2,EA,EI,0);
        var r=s.solve(); assertEquals(Status.CONVERGED,r.status());
        assertEquals(-3.0*8/(48*EI),r.displacements()[4],1e-10);
        assertEquals(3.0*2/4,Math.abs(r.endForces()[0][5]),1e-10);
    }
    @Test void selfWeightAndVerticalColumn() {
        var s = new PlanarFrameSolver(10,1e-10);
        s.addNode(0,0,true,true,true,0,0,0); s.addNode(0,1,false,false,false,0,0,0);
        s.addElement(0,1,1000,EI,2); var r = s.solve();
        assertEquals(Status.CONVERGED,r.status()); assertEquals(-0.01,r.displacements()[4],1e-12);
        assertEquals(20,Math.abs(r.endForces()[0][0]),1e-10);
    }
    @Test void simplySupportedUniformSelfWeight() {
        var s = new PlanarFrameSolver(10,1e-10);
        s.addNode(0,0,true,true,false,0,0,0); s.addNode(1,0,false,false,false,0,0,0);
        s.addNode(2,0,false,true,false,0,0,0);
        s.addElement(0,1,EA,EI,2); s.addElement(1,2,EA,EI,2);
        var r = s.solve(); assertEquals(Status.CONVERGED,r.status());
        assertEquals(-5.0 * 20 * Math.pow(2,4) / (384 * EI),r.displacements()[4],1e-10);
    }
    @Test void missingSupportsAndDisconnectedDofsAreSingular() {
        var s = new PlanarFrameSolver(0,1e-10);
        s.addNode(0,0,false,false,false,0,0,0); s.addNode(1,0,false,false,false,0,-1,0);
        s.addElement(0,1,EA,EI,0); assertEquals(Status.SINGULAR,s.solve().status());
        var d = new PlanarFrameSolver(0,1e-10); d.addNode(0,0,false,false,false,0,0,0);
        assertEquals(Status.SINGULAR,d.solve().status());
    }
    @Test void oversizedAndDeterministic() {
        var s = new PlanarFrameSolver(0,1e-10);
        for(int i=0;i<=PlanarFrameSolver.MAX_NODES;i++) s.addNode(i,0,true,true,true,0,0,0);
        assertEquals(Status.TOO_LARGE,s.solve().status());
        var beam = cantilever(2,3); assertArrayEquals(beam.solve().displacements(),beam.solve().displacements());
    }
}
