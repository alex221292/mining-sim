# M6.1 — Mining interaction

M toggles mining mode. Hold left mouse over a fixed rock block; move to select another, release to stop. Focus loss and leaving the panel stop the action. Space pauses; N advances exactly one physical tick while paused. Outside mining mode the ordinary brushes remain available. F3 displays the hovered block ID, rock, existing ore composition, required and accumulated work, percentage and readiness; the mining-mode bar shows hovered progress. F9 remains the ore diagnostic overlay. These overlays are sandbox diagnostics, not prospecting.

Only supported, unpinned STATIC blocks are eligible; WAITING, FALLING and PINNED blocks are not. Each physical tick applies two work units (120/s at 60 TPS) to the active target. Limestone, stone, marble and granite require respectively 60, 120, 180 and 240 units at 4×4, scaled by block area / 16 (four times as much at 8×8). Values are game parameters independent of structural strength and ore content. Bedrock and non-rock cells cannot be mined.

Work is sparse, session-local and keyed by stable block ID; stopping or switching retains it, removal clears it, and movement retains it. Work saturates at the requirement. A ready result is logical only: cells, ID, rock, ore, ownership and physics remain unchanged. No GRIT, extraction or destruction is implemented. M6.2 is intended to define atomic conversion to rock GRIT and material conservation.

Neither unfinished nor completed work is serialized in MSIM v4. Loading, clearing, generating, changing block size or replacing the session resets it. MSIM v1–v4 readers and permanent block properties are unchanged. Persistent mining progress is deferred until the M6.2 mechanism is defined.

## Verification and limitations

On Windows Corretto 25.0.5+10, `gradlew.bat test --tests 'miningsim.block.MiningTest'` and `gradlew.bat test build` passed. GUI was not launched. `gradlew.bat worldGenerationBenchmark`: 800×401, seed 12345, generation warmup 2 / runs 10; 120 early ticks and steady warmup 20 / runs 100, milliseconds mean/p95/max:

| size | generation | first tick | early | steady |
|---|---|---:|---|---|
| 8 | 16.467/41.646/41.646 | 22.695 | 2.649/6.057/11.939 | 1.941/2.024/2.261 |
| 4 | 21.741/24.885/24.885 | 6.838 | 4.492/5.922/6.621 | 4.126/4.198/4.370 |

One uncontrolled run, without active mining, is not a regression proof or worst-case bound. No FPS or rendering cost is measured.

## User manual acceptance

The user accepted M6.1 and confirmed that all eight proposed manual scenarios passed:

1. Mining all four rocks and F3 diagnostics.
2. Comparing mining for 4×4 and 8×8 blocks.
3. Stopping and resuming, and switching between blocks.
4. Pause and a single physical step with N.
5. Multicomponent ore composition and F9.
6. Retention of the block and matter after full extraction readiness.
7. Save/load preserves permanent properties and resets temporary mining progress.
8. Existing brushes and world generation.

The user performed the GUI acceptance; the agent did not launch the GUI. The next agreed stage is M6.1a — Block Damage Visualization: ten cumulative crack stages using shared rock-independent templates, supporting both block sizes and camera scaling, while retaining the diagnostic progress bar. It must not affect physical state, mass, shape, strength or ore composition. Damage data and template definitions are graphics-API-independent; Java2D may pre-cache masks. This is a plan, not an implementation. See the architectural directions in `PROJECT.md`; M6.2 Rock Grit & Material Conservation remains planned.
