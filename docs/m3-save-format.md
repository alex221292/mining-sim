# MSIM binary format, version 1

All multibyte integers are big-endian. The file extension is `.msim`. Offsets are byte offsets from the start:

| Offset | Size | Type | Meaning |
|---:|---:|---|---|
| 0 | 4 | bytes | ASCII `MSIM` |
| 4 | 4 | signed int32 | version, exactly 1 |
| 8 | 4 | signed int32 | width, 1–4096 |
| 12 | 4 | signed int32 | height, 1–4096 |
| 16 | 4 | signed int32 | chunk size, exactly 64 |
| 20 | width × height | unsigned bytes | material IDs, see below |
| 20 + width × height | 4 | unsigned uint32 | CRC32, encoded big-endian |

The width × height product must not exceed 4,194,304 cells. Chunk grid dimensions are derived by ceiling division, not stored. Traverse chunk y, then chunk x in increasing order; inside each chunk traverse local y, then local x. Only valid world cells are written: partial edge chunks have no padding. Material IDs are the stable IDs documented in `materials.md`; unknown IDs are errors. CRC32 (standard IEEE CRC-32) covers every byte from offset 0 through the last material byte inclusive; it excludes the checksum and there must be no trailing bytes.

The reader validates the header and checked payload size before allocating a new world; truncated streams, unsupported versions, invalid dimensions/chunk sizes/IDs, checksum mismatches and trailing bytes fail with `IOException`. CRC32 detects accidental corruption, not malicious tampering. The format is independent of Java serialization and the in-memory chunk layout. Identical dimensions and cell states produce identical bytes.

Only dimensions and materials are saved. Random state, tick count, moved/activity masks, timing metrics, camera, brush and UI are not saved; loading creates a new world and simulation, conservatively activating regions. It is **not** an exact resumable simulation snapshot. Version 1 readers reject future versions rather than guessing. Future versions may add compression, metadata, dynamic chunks or exact snapshots; none is implemented here.

In the desktop application use Ctrl+S and Ctrl+O. A cancelled dialog or failed read leaves the current world unchanged. On successful load the simulation is recreated with seed 12345 and the current pause/brush state is preserved; camera is reset. I/O runs synchronously on the Swing event thread and can temporarily freeze the UI. Saving over an existing file requires confirmation. Run `./gradlew test`, `./gradlew benchmark` and `./gradlew persistenceBenchmark` (Windows: `gradlew.bat`). Performance measurements and methodology are in `m3-save-performance.md`.
