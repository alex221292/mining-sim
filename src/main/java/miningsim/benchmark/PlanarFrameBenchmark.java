package miningsim.benchmark;

import miningsim.structural.PlanarFrameSolver;

public final class PlanarFrameBenchmark {
    public static void main(String[] args) {
        for (int n : new int[]{8, 24, 48, 64}) {
            var s = new PlanarFrameSolver(9.81, 1e-8);
            for (int i = 0; i < n; i++) s.addNode(i * 0.1, 0, i == 0, i == 0, i == 0, 0, 0, 0);
            for (int i = 1; i < n; i++) s.addElement(i-1,i,1e6,1e3,10);
            for (int i = 0; i < 5; i++) s.solve();
            long assembly = 0, solution = 0;
            for (int i = 0; i < 10; i++) {
                var r = s.solve(); if (r.status() != PlanarFrameSolver.Status.CONVERGED) throw new IllegalStateException(r.status().toString());
                assembly += s.assemblyNanos(); solution += s.solutionNanos();
            }
            System.out.printf("nodes=%d elements=%d assembly=%.3f ms solve+forces=%.3f ms%n", n,n-1,assembly/1e7,solution/1e7);
        }
    }
}
