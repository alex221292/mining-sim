# M4.7 — MSIM v2 fixed-block persistence

Ctrl+S writes fixed-block sessions as MSIM v2; Ctrl+O validates a new session completely before replacing the current one and restores its 4×4 or 8×8 configuration. The current pause and brush remain; the camera resets. Save runs synchronously on the Swing owner thread between ticks. If commands are queued, save is refused without applying them: resume or single-step and retry. Temporary stroke pins are recorded and validated, then released on load because mouse input does not carry over; a formerly pinned block starts its ordinary fall delay. Legacy MSIM v1 is loaded as a separate LEGACY session, with no conversion of arbitrary STONE to blocks. V1's `WorldIO` behavior and format remain unchanged.

## Binary layout

All multibyte integers are big-endian; `.msim` streams are caller-owned. CRC32 is IEEE CRC-32 over all preceding bytes; the checksum is unsigned uint32 and trailing bytes are forbidden.

| Offset | Bytes | Field |
|---:|---:|---|
| 0 | 4 | ASCII `MSIM` |
| 4 | 4 | int32 version = 2 |
| 8 | 4 | int32 width |
| 12 | 4 | int32 height |
| 16 | 4 | int32 world mode = 1 (BEDROCK_ENABLED) |
| 20 | 4 | int32 block size, 4 or 8 |
| 24 | 4 | int32 block count |
| 28 | 8 | int64 next block ID |
| 36 | width × height | unsigned material IDs, row-major |
| 36 + cells | count × 20 | block records in ascending ID order when written |
| 36 + cells + 20 × count | 4 | CRC32 |

Each block record contains int64 positive ID, int32 x, int32 y, uint8 state (0 STATIC, 1 FALLING), uint8 unsupported-tick counter (0–8), uint8 pinned (0/1), uint8 substance (0 STONE). World dimensions are 1–4096, product at most 4,194,304; count is at most cells/(size²). IDs must be unique, next ID positive and greater than every live ID, positions aligned to the bottom-anchored grid, within bounds and non-overlapping. All cells in each block must be STONE and no STONE may be unowned; BEDROCK occurs exactly on the final row. FALLING cannot be pinned or have a nonzero waiting counter. Invalid records, dimensions, material IDs, checksum, version, truncation or trailing data fail with `IOException`.

Only authoritative block state and World cells are serialized; diagnostic counters, camera, UI and derived caches are not. The cellular RNG is **not** serialized: loading creates a new simulation with seed 12345. Consequently, a loaded scene continues with conserved materials and block state, but is **not guaranteed to match an uninterrupted deterministic replay**. Identical files followed by identical commands replay deterministically from the same load point. No Java object serialization is used for the file format.

Path saves write a sibling temporary file then require atomic replacement; if the filesystem does not support it, the save fails rather than falling back to a potentially unsafe overwrite. Input validation precedes publication of the replacement session. CRC32 detects accidental corruption, not malicious tampering. Synchronous large saves can stall the UI; no background I/O or compression is implemented.

## Verification and measurements

Headless tests cover both sizes, block waiting/falling/pinned states, legacy v1, corruption and invalid ownership, existing-file preservation and two seeded 10,000-tick replays with periodic reload. `gradlew.bat test build` and `git diff --check` are the final checks. Swing was not launched by the agent.

Windows, OpenJDK 25.0.5+8-LTS OpenJDK 64-Bit Server VM; `java -cp build/classes/java/main miningsim.benchmark.FixedSessionIOBenchmark 10`. In-memory streams, two warmups and ten measured runs; mean/p95 milliseconds, excluding disk and GUI:

| Block size | World | Blocks | Bytes | Write | Read |
|---|---|---:|---:|---:|---:|
| 8 | 64×65 | 10 | 4,400 | 0.568/0.758 | 0.540/0.662 |
| 8 | 800×401 | 1,000 | 340,840 | 5.804/7.361 | 5.582/6.357 |
| 4 | 64×65 | 10 | 4,400 | 0.140/0.153 | 0.111/0.137 |
| 4 | 800×401 | 4,000 | 400,840 | 5.159/6.392 | 4.046/5.993 |

Disk measurements on the same JVM, with the same warmup and run counts, using the system's approved temporary directory and atomic replacement (OS cache included; no fsync), mean/p95 ms:

| Block size | World | Blocks | Bytes | Write | Read |
|---|---|---:|---:|---:|---:|
| 8 | 64×65 | 10 | 4,400 | 1.805/2.164 | 1.197/1.467 |
| 8 | 800×401 | 1,000 | 340,840 | 7.258/11.936 | 15.289/20.165 |
| 4 | 64×65 | 10 | 4,400 | 1.474/2.252 | 0.759/0.847 |
| 4 | 800×401 | 4,000 | 400,840 | 6.890/7.601 | 13.051/15.988 |

## Manual Swing acceptance

For each block size, draw a stack and water, pause during the fall-delay and save via Ctrl+S. Modify the scene, then load with Ctrl+O; check block size, shapes, water, pause, single-step and subsequent falling. Repeat while a block is falling. Load a v1 file and confirm LEGACY mode and editable STONE, then try an invalid file and confirm the active scene remains. Check save-over confirmation and F7's scene-replacement confirmation. Perform GUI checks only as the user.
