# M5.5b — procedural rock geology (accepted)

The user visually accepted the diversity of formations, rock boundaries, local inclusions and the interaction of geology with existing caves and water bodies. Screenshots were supplied for seeds 431039 and 357355 at 4×4. The user did not explicitly confirm individual checks of 8×8, resuming collapses, excavation near water, or save/load; those scenarios must not be reported as passed. The agent did not run the GUI.

F8 creates a paused world with rock identities sampled during the original row-major block placement, before the unchanged cave and natural-water stages. Ordinary startup remains empty. Each whole 4×4 or 8×8 block samples the field at its centre in world cells, and is placed using the existing material-aware fixed-block API. IDs, ownership and the no-hidden-tick contract are unchanged. A separate seed channel (`seed ^ 0x5cb46a19e2d783f1`) feeds coordinate-hashed, smooth bilinear value noise; no existing random stream is consumed. The brush is not an input to generation. One cell is 0.1 m, y increases downwards.

STONE is the background. GRANITE thresholds a weighted 180/85-cell noise field with a modest depth bias. LIMESTONE uses a seeded sloping sinusoidal layer coordinate (220-cell period), warped by 150-cell noise and locally interrupted by another field; its nominal thickness varies with the noise. MARBLE thresholds a separate 80/42-cell field, producing comparatively rare, irregular patches with no forced relationship to limestone. The field is independent of world size and block size; different block centres, terrain and caves prevent exact matching between 4 and 8. No rock strength, density or support difference is implemented. MSIM v3 is unchanged and stores block and cell identities, not geology seed or parameters.

## Verification and limitations

`gradlew.bat test --tests 'miningsim.generation.*'` and `gradlew.bat test build` passed on Corretto 25.0.5. The pre-change M5.5a `generationParityProbe` SHA-256 cell maps for 128×129, seeds 1, 71, 12345 and both sizes match after mapping all four rocks to STONE. These are six actual baseline fingerprints, not an exhaustive proof. Existing terrain/deposit, cave and water tests remain enabled; water's independent dry reference now assigns the same geology before water. Focused mixed-world tests compare all normalized cells and full block state after 600 ticks against a same-RNG all-STONE equivalent, for seeds 8 and 12345 at both sizes; they verify per-material conservation, IDs, restored ownership and v3 round-trip before and after ticks. Existing v1/v2 regression tests remain enabled. No GUI was run.

## Fixed headless calibration

`gradlew.bat rockGeologyCalibration`: 800×401, seeds 0–15, 12345, 357355, 431039, each block size. Components are orthogonally adjacent same-material blocks after caves; surface means the first rock row per column, including rock beneath deposits. Small components contain at most four blocks, so their physical size differs by block size. The task prints every world's share, number of components, largest component and its share, small-component count and surface blocks.

| Size | Rock | Mean rock share | Present | Surface worlds |
|---|---|---:|---:|---:|
| 8 | STONE | 42.77% | 19/19 | 19/19 |
| 8 | GRANITE | 37.48% | 19/19 | 16/19 |
| 8 | LIMESTONE | 16.78% | 19/19 | 17/19 |
| 8 | MARBLE | 2.96% | 19/19 | 9/19 |
| 4 | STONE | 42.72% | 19/19 | 19/19 |
| 4 | GRANITE | 36.96% | 19/19 | 15/19 |
| 4 | LIMESTONE | 17.13% | 19/19 | 17/19 |
| 4 | MARBLE | 3.19% | 19/19 | 10/19 |

For 8×8, granite ranges 5.99–70.97%, limestone 7.80–27.39%, marble 0.08–6.76%. Seed 2's largest granite component is 1,709 blocks (99.7% of its granite); seed 8's largest limestone component is 334 blocks (96.5%). Seed 4 has a marble component of 81 blocks, but also six marble components of at most four blocks: tiny remnants do occur. Seed 5 has only two marble blocks at 8×8. All sampled worlds have some marble, so the sample does not demonstrate a marble-free world. No minimum-component filter or forced material presence is used. Statistics do not establish visual quality.

The calibrated distributions for both block sizes are close. Marble appearing in all 19 sampled worlds and small isolated inclusions do not require further calibration at this stage. Individual rock strengths remain a task for M5.5c.

## Exploratory performance

`gradlew.bat worldGenerationBenchmark`, Windows, Corretto 25.0.5+10, 800×401, seed 12345; two generation warmups and ten runs, then first tick, 120 early ticks and 20 warmup/100 late ticks. Milliseconds mean/p95/max:

| Size | Generation | First tick | Early ticks | Late ticks |
|---|---|---:|---|---|
| 8 | 15.280/37.410/37.410 | 23.910 | 4.406/6.784/12.096 | 3.867/4.429/4.778 |
| 4 | 12.806/18.374/18.374 | 7.391 | 4.151/5.805/6.211 | 3.809/4.029/4.439 |

Field sampling is inline with placement and was not timed separately. The historical M5.4 generation means were 12.956/11.259 ms for sizes 8/4; historical late means were 1.875/3.763 ms. The present 8×8 late result is notably higher; different runs, JVM warmup and OS noise prevent attribution, and the reason for the difference has not been established. These are not controlled M5.5a comparisons or performance guarantees.

## Additional user-only manual checks

Suggested seeds 2 (granite-dominant), 4 and 8 (substantial marble), 13 (stone/limestone), 12345 and 357355 (known water/collapse scenarios) were selected by headless statistics; of these, the user supplied a 4×4 screenshot for 357355 only. Additional checks can include resuming natural collapses, excavating mixed rock near water, save/load and repeating the main checks in 8×8. F3, brushes and controls are unchanged. World pre-simulation and dynamic activation remain future work.
