# M5.6a — multi-component ore content (accepted)

Rock host material and substance remain independent of ore identity. IRON, COPPER, SILVER and GOLD have stable IDs 1–4; 0 is invalid. Concentrations are mass fractions in integer ppm: 1 ppm = 0.0001%, 10,000 ppm = 1%, 1,000,000 ppm = 100%. These are conditional extractable components, not mineral chemistry or recovery rates. Each component is unique, positive and at most 1,000,000; the total is at most 1,000,000. Composition is immutable and sorted by stable ID. Future mining and crushing must conserve each component's mass; their mass balance is not implemented.

Each fixed-block entry stores one composition reference. Empty blocks share an immutable singleton with no per-block array, list or map; a nonempty composition uses an immutable list of component records, allocated once per supplied value. Actual memory usage has not been measured. Adding IDs requires extending the registry; physics does not require per-ore changes. Movement retains the entry and its composition; deletion removes it. Ore is not stored in world cells and does not affect support, density, movement, collision or water. Generation consumes no new RNG and produces empty compositions; ore deposits, mining and processing are future work.

## MSIM v4 binary format

Big-endian integers; v3 header and cells and 20-byte block records are followed by a sparse ore section before the CRC32. The section is a signed 32-bit record count, then for each nonempty block in ascending ID order: signed 64-bit block ID, unsigned byte component count, and that many pairs of unsigned byte stable ore ID and signed 32-bit ppm, in ascending ore ID order. Empty compositions have no ore record. CRC32 covers header, cells, block records and ore section; the final 32-bit CRC and atomic replacement retain the existing contract. A malformed or truncated file is rejected before publication. v1 remains LEGACY; v2 and v3 read as ore-free; loading never overwrites the old file or advances physics. RNG continuation remains subject to the pre-existing load contract.

The 36-byte header is: magic `MSIM` (4 bytes), version 4, width, height, mode 1, block size, block count (each signed 32-bit), next block ID (signed 64-bit). It is followed by width × height row-major unsigned material bytes, then block count records: ID (64-bit), x and y (32-bit each), state (byte, 0 static/1 falling), unsupported ticks (byte), pinned (byte, 0/1), host code (byte: 0 STONE, 1 GRANITE, 2 LIMESTONE, 3 MARBLE). The sparse section and CRC follow exactly as described above. Inputs with unknown IDs, duplicate components or block references, invalid counts or fractions, invalid ownership, bad CRC or trailing bytes are rejected.

F4's first reference scene creates a paused mixed-rock ore fixture, with ordinary, one-component and four-component blocks, supported and falling examples and water, at either block size. F3 shows each component in ppm and percent to four decimals, or `Ore: none`. Rock colors are unchanged. GUI verification is by the user only.

## Verification and manual acceptance

Benchmark: `gradlew.bat oreBenchmark`, Windows, Corretto 25.0.5+10, 800×401, seed 12345, 20 warmup and 100 measured ticks; means/p95/max in ms. Static fixture contains 1000 blocks and water fixture 10 blocks; sequential scenes evolve during warmup. Two runs used the same JVM configuration; fixed case ordering, JIT and OS noise limit attribution. Initial 8×8 empty static mean was higher than ore scenes, so a second comparable run was made; this is not proof of a performance improvement or worst-case bound.

| Size / scene | Components | Run 1 mean/p95/max | Run 2 mean/p95/max |
|---|---:|---:|---:|
| 8 static | 0 | .727/.905/.993 | .727/.922/.991 |
| 8 static | 1 | .573/.609/.664 | .571/.607/.679 |
| 8 static | 4 | .563/.590/.598 | .562/.586/.594 |
| 4 static | 0 | .822/.873/.927 | .866/.967/1.076 |
| 4 static | 1 | .796/.812/.833 | .814/.828/.848 |
| 4 static | 4 | .804/.823/.844 | .823/.843/.867 |
| 8 water | 0 | 1.135/1.494/1.569 | 1.067/1.268/1.788 |
| 8 water | 1 | 1.140/1.492/1.598 | 1.069/1.270/1.398 |
| 8 water | 4 | 1.132/1.487/1.541 | 1.059/1.260/1.289 |
| 4 water | 0 | .795/.880/.896 | .770/.815/.830 |
| 4 water | 1 | .797/.883/.902 | .770/.814/.829 |
| 4 water | 4 | .794/.877/.897 | .770/.812/.921 |

Focused `gradlew.bat test --tests 'miningsim.block.OreContentTest' --tests 'miningsim.block.RockIdentityTest' --tests 'miningsim.block.FixedSessionIOTest'` and `gradlew.bat test build` passed on Corretto 25. Tests cover composition validation, canonicalization, v4 round-trip and malformed data, a v3 fixture emitted before the v4 writer change, existing v1/v2 compatibility, physical parity and water, support restoration and ownership. For both sizes the 38 generated initial sessions match the recorded combined M5.5c v3 fingerprint after removing the empty v4 section; generated blocks have empty ore. The agent did not run GUI.

## User manual acceptance

The user accepted M5.6a and confirmed that F3 displays empty, single-component and multi-component compositions, including small silver and gold concentrations in ppm and percent. After resuming, blocks fell into water, stopped at the bottom and retained their compositions. The generated world contained no ore; save/load retained ore compositions.

The user did not separately report manual checks of both block sizes, support restoration or loading historical formats; the automated coverage described above is distinct from manual acceptance. No ore textures, extraction, inventory or procedural ore are implemented.
