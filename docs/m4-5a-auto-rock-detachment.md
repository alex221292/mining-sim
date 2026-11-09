# M4.5a — automatic unsupported rock detachment

In the ordinary BEDROCK_ENABLED sandbox, draw connected STONE in the air: after six fixed ticks without another topology-changing STONE addition and after connectivity completes, the component transfers as one rigid fragment and falls. A STONE component is supported only if intact rock bonds connect it to BEDROCK. AIR below a bridge does not make it unsupported. WOOD, granular material and other bodies are not structural bonds; a detached body may subsequently collide with them. Erasing the last support or breaking a bond bypasses the paint quiet period, but never bypasses topology completion. Sand/water edits do not extend the quiet interval. The quiet interval groups edits; it is not a stability calculation.

One authoritative `RockConnectivity` is attached to ordinary bedrock worlds, without a full-world stress/FEM solve. Legacy worlds remain cellular-only. Existing five F4 structural calibration scenes retain automatic detachment disabled. After the existing three physics demos, F4 adds an opt-in 12-cell cantilever damage/break demo and a 1,025-cell oversized demo. F3 shows enabled/pending state, topology revision and work, unsupported/eligible counts, current detachments and cells, quiet deferrals, size/capacity rejections, total and last reason. Enumeration is bounded to 256 components per tick; displayed counts are accumulated for the current completed revision and may be partial while enumeration continues. Quiet deferrals count up to the first 256 components. Repaint never advances topology.

The EDT tick order is queued commands (including F5), bounded topology and optional selected-component damage, automatic detachment using only current completed labels, coupled physics/displacement, separate cellular movement, then repaint. The optional reference controller's connectivity work counts against the same 8,192-unit automatic budget. A structural break invalidates labels and must be revalidated. F5 uses the same atomic transfer and runs before automatic selection; in ordinary worlds middle-click selects a stone component, and F5 requires completed topology. Existing reference-scene F5 retains its 64-cell selected-analysis limit. Automatic detachment does not depend on selection or a converged stress solve.

Default limits are 8,192 connectivity work units/tick, six quiet ticks, 1,024 cells per component, four components and 4,096 transferred cells per tick, and 256 component checks/tick. Component IDs are minimum row-major indices and are considered in ascending order. Extraction changes topology, so processing stops after the first successful component and resumes only after a fresh bounded connectivity pass; thus currently at most one successful extraction occurs per tick. Initial full-world scans and subsequent invalidations can take many ticks. `ExtractionPlanner` uses the completed component's bounded cell list rather than scanning the whole World; it rechecks revision and source state at commit.

The four-component and 4,096-cell limits also bound attempted preparations, including rejected attempts; oversized components require no preparation and remain subject to the separate 256-check limit.

Each component is its own transaction: prebuilt registry reservation, physics membership and occupancy are validated, then World source cells and ownership are published together through the existing batch API. Failure preserves all four owners; a later failure never undoes an earlier successful component. A coupled physics failure after extraction preserves the committed body and its occupancy, but does not undo extraction. World callbacks still obey the non-throwing single-owner contract. Components over 1,024 cells remain STONE, are reported `TOO_LARGE` and are not retried while topology/capacity is unchanged. Registry capacity failures retry when registry revision changes; occupancy-budget rejections are retried no more often than every 60 ticks and only after a new occupancy publication. Other rejections wait for topology or registry revision. Components are never truncated or split. The 1,024-cell limit is a tested reference envelope, not a guarantee for arbitrary geometry, contact density or existing bodies; downstream limits still apply.

## Performance

`gradlew.bat automaticRockBenchmark`, Windows, AMD Ryzen 9 5900X, OpenJDK 25.0.5+8-LTS, 400×300 World, default budgets, cold sequential ticks; mean/p95 milliseconds. No allocation/GC or desktop FPS measurements. Each successful extraction invalidates topology, so 100 components require repeated full passes. Timing instrumentation is separate from deterministic simulation. `prep` is total planner time; `transaction` includes registry/body and occupancy preparation plus commit; `commit` is the included `ExtractionCommit` validation and World publication, not an additional cost. Physics and occupancy are mean per successful full tick.

| scenario | ticks / first topology complete | work | detached | mean/p95 | prep / transaction / commit total ms | physics / occupancy mean ms |
|---|---:|---:|---:|---:|---:|---:|
| empty | 30 / 15 | 120000 | 0 | 1.584/2.343 | 0/0/0 | .428/.025 |
| sparse 10 | 180 / 15 | 1320055 | 10 | 1.097/2.925 | 16.478/7.302/4.653 | .535/.021 |
| dense supported | 40 / 30 | 239600 | 0 | .250/.464 | 0/0/0 | .014/.002 |
| 20 brush edits | 70 / 34 | 395668 | 1 | .881/2.005 | .375/.567/.469 | .434/.013 |
| 1 component | 45 / 15 | 240001 | 1 | .213/.259 | .149/.161/.133 | .054/.002 |
| 10 components | 180 / 15 | 1320055 | 10 | .406/.711 | 1.050/1.525/1.201 | .184/.006 |
| 100 components | 1530 / 15 | 12125050 | 100 | 6.081/27.405 | 8.599/14.441/8.904 | 5.456/.021 |
| 64 cells | 45 / 15 | 240064 | 1 | .354/.537 | .872/.447/.355 | .070/.053 |
| 128 cells | 45 / 15 | 240128 | 1 | .503/.744 | .380/.593/.437 | .140/.078 |
| 256 cells | 45 / 15 | 240256 | 1 | .782/1.220 | .451/.911/.644 | .261/.098 |
| 512 cells | 45 / 15 | 240512 | 1 | 1.469/2.767 | .673/1.286/.990 | .516/.227 |
| 1024 cells | 45 / 15 | 241024 | 1 | 2.814/4.254 | 2.315/2.544/1.967 | 1.029/.194 |

Each measured stone shape fell for at least 30 ticks after extraction; a separate headless test brings a 1,024-cell square to the floor for 100 ticks without a blocked session. This does not establish all contact configurations. All listed benchmark ticks succeeded; the 100-body p95 remains above 16.67 ms due to the existing pairwise physics cost. Cold-start effects are visible in the first sparse run. Topology work is capped at 8,192 units per tick; empty 400×300 completes in 15 ticks and fully supported dense stone in 30. Repeated additions restart in-progress topology. Sampled swept collision is not full CCD, MSIM v1 does not save fragments, and no manual Swing verification was performed.
