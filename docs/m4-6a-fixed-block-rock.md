# M4.6a — fixed-block sandbox

Historical M4.6a behavior and baseline; water behavior is superseded by [M4.6b](m4-6b-water-displacement.md).

The ordinary Swing sandbox uses `FIXED_BLOCK`; F4 reference scenes retain the old diagnostic systems. Left-drag with STONE places whole 8×8 blocks; a grid-line stroke fills skipped cells. Newly placed blocks are held until mouse release (also on focus loss). Right-drag removes whole blocks and erases other editable cells. A block has a stable ID, STONE substance, and STATIC/FALLING state; it does not rotate or split. World STONE cells are the block's cellular representation, not a second quantity of matter. Each block move, placement or removal uses a validated World batch on the single simulation owner thread, in a world without a structural change listener.

One fixed tick consumes input, updates blocks bottom-first in stable order, then advances cellular simulation. Blocks move vertically by eight cells per tick if the complete destination is AIR. Any non-AIR material, including sand, gravel, soil and water, is a conservative obstacle: no displacement or material deletion occurs. Blocks do not enter SLEEPING or use rigid-body physics. The grid is anchored at the bottom BEDROCK row; `yOffset = (height - 1) mod 8`. Cells above that offset and incomplete right-edge columns cannot hold a block. Other material painting cannot overwrite a block. F3 reports block counts, update time, rejected placements and cellular ticks.

Saving is disabled in fixed-block mode because `.msim` does not preserve block identity or state. Loading is permitted only for BEDROCK-enabled worlds without legacy STONE; incompatible files are refused without replacing the current world. Clearing or switching reference scenes explicitly replaces the session. Legacy reference scenes and their controls remain separate. This stage does not support other block substances, arbitrary shapes, damage or rotation. Manual Swing acceptance has not been performed.

## Measurements

Windows, OpenJDK 25.0.5+8-LTS OpenJDK 64-Bit Server VM; `java -cp build/classes/java/main miningsim.block.FixedBlockBenchmark 100`. Dense static blocks in an 800×401 world, 20 warmup ticks and 100 measured ticks; wall time excludes cellular simulation, initialization and Swing repaint. Mean/p95 block update: 100 blocks **0.039/0.072 ms**, 1,000 **0.137/0.158 ms**, 5,000 **0.572/0.702 ms**. This is not a moving-block or whole-frame benchmark.

Acceptance baseline: `gradlew.bat test build` passed, including `FixedBlockStressTest`: two seeded 64×65 worlds, 10,000 ticks each, repeated block creation and support removal, and SAND/GRAVEL/SOIL/WATER conservation and replay. A separate baseline run of the same benchmark measured mean/p95 **0.037/0.069**, **0.139/0.166**, **0.586/0.672 ms** respectively. The user reported successful manual drawing, stack and support-removal tests.
