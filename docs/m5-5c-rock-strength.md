# M5.5c — material-weighted support (accepted)

This is a game rule, not real rock strength. Horizontal support ranges in world cells are GRANITE 48, MARBLE 40, STONE 32 and LIMESTONE 24. Both block sizes therefore use the same physical range.

Each path has budget 240. A horizontal edge costs `240 * blockSize / min(rangeA, rangeB)` using integers; 240 is allowed. Costs for 8/4 cells are granite 40/20, marble 48/24, stone 60/30 and limestone 80/40. Branches do not share or consume a global resource. A granite support followed by granite, granite, limestone, granite, granite has costs 40, 80, 160, 240, 280; the last block has no path on that beam alone.

The existing multi-source queue performs strict minimum-cost relaxation. Each improved block is processed again; an upward edge resets cost to zero, no downward or diagonal edge exists. Horizontal costs are positive and accepted costs are bounded nonnegative integers, so strict improvements terminate even with cycles and upward resets. Processing to a fixed point yields the minimum admissible cost independent of traversal order. Pinned and falling blocks and source/contact rules are unchanged. Consecutive upward resets still permit indefinitely long stepped structures; this is intentional and not structural mechanics.

The model does not account for total supported mass, bending moments or mechanical stress concentration at the cantilever base. A weaker rock can support a long granite cantilever as a consequence of this simplified path-budget algorithm, not a claim of physical realism.

F3 shows the last tick's cost and range; it can be stale after edits, movement, loading or while paused. MSIM v3 is unchanged: costs are derived, not serialized. Older saves can collapse under the new rules; loading does not tick. Seeded generation remains unchanged and local natural collapses are expected. World pre-simulation and dynamic activation are not implemented.

## Verification

`gradlew.bat test --tests 'miningsim.block.WeightedSupportTest' --tests 'miningsim.block.RockIdentityTest' --tests 'miningsim.generation.RockGeologyTest'` and `gradlew.bat test build` passed on Corretto 25.0.5. Tests previously asserting mixed rocks behave exactly like STONE were updated because that historical contract was explicitly replaced; mixed determinism, material conservation and state checks remain.

Controlled `stoneSupportParity` compared the actual M5.5b class with this implementation: per-tick MSIM state fingerprints match for 200 ticks at both sizes, including the full cell grid, IDs, positions, states and timers, support removal/restoration and water. The combined fingerprint of all 38 freshly generated sessions also matched (`0aeb66ecdea9b33445d989fc7311bcbd957d3e464c1417a6d21a2edbf547cda5`). These are representative fixtures, not an exhaustive proof. GUI acceptance was performed by the user, not the agent.

## World calibration

`gradlew.bat strengthCalibration -Pm55bBaseline` and `gradlew.bat strengthCalibration`: actual baseline class versus new code, 800×401, 600 ticks, seeds 0–15, 431039, 357355, 12345, both sizes. Per-rock cell counts, IDs and complete ownership were checked; no loss was found. The probe also reports retained initial AIR across the entire world, not specifically cave AIR. Initial block counts match in all 38 comparisons. At 8×8, moved blocks range 0–30 in the new model versus 0–30 baseline; at 4×4, 1–91 versus 1–96. At tick 600 new 8×8 seed 4 has 11 unsupported and no falling blocks; 4×4 seeds 8, 9 and 12345 have respectively 34/15/1 unsupported and 0/3/1 falling. These include physically obstructed blocks; unsupported does not imply free movement. No mass collapse was observed in this sample. Seed 3 (8×8), 10 and 357355 (4×4) exercise larger local motion; seeds 9 and 12345 (4×4) retain activity. These are headless suggestions, not visual acceptance.

## Exploratory timing

Corretto 25.0.5+10, 800×401, one run per version, 20 warmup and 100 measured ticks; mean/p95/max milliseconds. JVM/OS noise and concurrent headless work affect comparability; dynamic fixtures may evolve differently.

| Scenario | Size | M5.5b | M5.5c |
|---|---:|---:|---:|
| STONE static 5000 | 8 | 2.290/2.463/2.523 | 2.173/2.410/3.636 |
| STONE static 5000 | 4 | 1.762/1.913/3.160 | 1.860/2.018/3.411 |
| mixed static 1000 | 8 | 1.081/1.388/1.481 | 1.159/1.561/1.644 |
| mixed static 1000 | 4 | 0.853/0.870/0.875 | 0.873/0.889/1.326 |
| mixed water | 8 | 1.171/1.521/1.576 | 1.194/1.550/1.804 |
| mixed water | 4 | 0.832/0.920/0.931 | 0.847/0.933/1.033 |

An additional controlled run added mixed-beam and mixed-stepped fixtures: baseline versus new mean/p95/max were 0.675/0.692/0.728 versus 0.676/0.695/0.783 (beam 8), 0.681/0.692/0.729 versus 0.681/0.693/0.713 (beam 4), 0.677/0.692/0.750 versus 0.684/0.696/0.732 (stepped 8), and 0.689/0.708/0.715 versus 0.690/0.705/0.754 (stepped 4). The mixed-static 8 support-analysis mean in the first run rose from 0.276 to 0.373 ms; the additional run gave 0.252 versus 0.271 ms, indicating substantial measurement variation rather than establishing a fixed regression.

Generated seed 12345 new first tick 24.208/8.349 ms for 8/4; late mean/p95/max 1.955/2.034/2.558 and 4.059/4.111/4.588 ms. The 8×8 first tick exceeds the 16.67 ms 60 TPS budget. Historical M5.5b generation timings are in its document, not a controlled same-run baseline. Observed maxima are not worst-case bounds.

## User manual acceptance

The user accepted M5.5c after manual testing:

- Homogeneous 8×8 cantilevers supported 6 GRANITE, 5 MARBLE, 4 STONE and 3 LIMESTONE blocks, excluding the support column; 4×4 limits matched 12/10/8/6.
- A mixed 8×8 cantilever supported two GRANITE and two LIMESTONE blocks; the next LIMESTONE fell. A reverse mixed 4×4 cantilever supported two LIMESTONE and seven GRANITE blocks; the next GRANITE fell. The latter matches the accumulated budget and was accepted.
- Stepped structures worked correctly. Generated-world visual quality, behavior after resuming, excavation near water, and save/load were accepted.
