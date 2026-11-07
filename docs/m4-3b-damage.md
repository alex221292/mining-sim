# M4.3b.2 — headless rock mechanics and damage

`World` owns materials, `RockConnectivity` owns bond topology, `RockMaterialRegistry` owns immutable profiles and `RockDamageSystem` owns damage and diagnostics. Construct the latter with the existing connectivity for the same world. No second change sink is installed. Call `analyze(x,y)` on a stone cell after connectivity is complete; it analyzes only that complete component, not the whole world. Call `snapshot()` only when `status()` is `CONVERGED`. A snapshot is immutable but is historical after a revision change; callers must not interpret it as current. Unsupported, pending, too-large or failed calculations are not stability claims. Create a new system to reset damage and tick history; discard old snapshots. Everything is single-threaded.

Nodes are cells in row-major order, at `(0.1*(x+0.5), -0.1*(y+0.5))` metres. Intact orthogonal bonds become frame elements in canonical right/down order; broken bonds are omitted. Each node gets one downward force `density * 0.1³ * gravity`; elements have zero linear mass, preventing double counting. A direct bedrock contact clamps all three node DOFs; this is a strong simplifying approximation, not a finite-strength anchor. There are no artificial supports at component or world edges. The maximum is 64 nodes/256 elements, with explicit `TOO_LARGE`; unsupported components return `UNSUPPORTED`. This is a small Euler–Bernoulli lattice, not a validated geotechnical model. Dense Gaussian elimination, pivot thresholds and floating-point limitations are documented in `m4-3a-frame.md`.

The reference square cross-section has area A=0.01 m², I=0.1⁴/12 m⁴ and section modulus Z=0.1³/6 m³. EA=E*A and EI=E*I. Profiles contain density in kg/m³, Young modulus and strengths in Pa and damage rate in s⁻¹. For a future mixed interface, `weakerInterface` takes the lower modulus and each strength, and the larger damage rate; cell weight uses each cell's own density. M4.2 still recognizes STONE only; adding a registry entry alone does not add topology or a new material ID. Current reference values are gameplay placeholders.

For local element end forces `[N1,V1,M1,N2,V2,M2]`, tensile demand is max(0,-N1,N2), compression is max(0,N1,-N2). Utilization is max(tension/(A*tensile strength), compression/(A*compressive strength)) + max(|V1|,|V2|)/(A*shear strength) + max(|M1|,|M2|)/(Z*bending strength). `RockBondStrength` validates finite values. A tick changes damage by `rate/60 * max(0, utilization-1)`, clamped to 1; it never heals. Damage is indexed by canonical bond key and incarnation. Absent/replaced bonds are discarded on the next analysis; an unchanged bond retains damage across recomputation.

`advanceTick(id)` accepts strictly increasing nonnegative IDs, permits gaps without replay and consumes even a pending/failed tick. It adds at most one 1/60 s damage increment; `analyze` and queries do not advance time. In canonical bond order, updates are committed until the first bond reaches 1; that bond is broken through `RockConnectivity`, remaining bonds wait for a new topology and equilibrium. Component membership and cached forces are invalid until connectivity and a new `analyze` complete. No crack rendering or fragment movement exists; M4.3c and M4.4 are intended for those later steps. Broken stone cells remain in the world.

Topology revisions are checked around assembly and solve and before publishing; tick application requires a current converged snapshot. Endpoint incarnation changes invalidate old damage even when a cell is removed and restored between analyses. Sink replacement makes connectivity calls fail; reattachment invalidates incarnations. `RockConnectivity` remains STONE-specific and its full-world connectivity scan and this implementation's full-world component lookup are not optimized for large worlds. No data is saved to MSIM.

## Tests and exploratory measurement

`gradlew.bat test build` passes; tests cover analytical column weight, tick increment, repeated tick, break/replacement, stale/pending, unsupported/oversized and cross-chunk cantilever. More independent roof, failure-mode and performance fixtures are needed before treating this as calibrated mechanics.

`gradlew.bat rockDamageBenchmark`, Windows 11 Java 25, one run, 20 repeated analyses/ticks, strong test profile; times in ms, assembly includes component lookup and matrix assembly, solve includes force recovery. First analysis includes JIT. No memory instrumentation; dense solver uses O((3N)²) storage and O((3N)³) elimination.

| Nodes / bonds | Status | First total | Assembly | Solve | Repeat/call | Damage/tick | Break rebuild | Edit rebuild |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 8 / 7 | CONVERGED | 8.254 | 6.464 | 0.193 | 0.457 | 0.029 | 0.309 | 0.397 |
| 24 / 23 | CONVERGED | 0.849 | 0.523 | 0.274 | 0.434 | 0.029 | 0.368 | 0.388 |
| 48 / 47 | CONVERGED | 2.214 | 0.304 | 1.851 | 1.861 | 0.035 | 1.852 | 1.816 |
| 64 / 63 | CONVERGED | 3.923 | 0.250 | 3.643 | 2.989 | 0.044 | 0.916 | 0.981 |

Break and edit measurements include connectivity rebuild and reanalysis, excluding the mutation itself. These are exploratory values, not full-world performance guarantees.
