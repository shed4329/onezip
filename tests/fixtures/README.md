# ReferenceArchive reference fixtures

Created on 2026-10-27 with an external reference tool (version 7.47, x64). These archives contain only synthetic text and empty entries created for these tests; no executable or vendor DLL is included.

Source tree: `target/interop-source/alpha.txt`, `beta.txt`, `empty.txt`, `empty/`.

- `alpha.txt`: UTF-8 bytes of `ReferenceArchive external reference\r\n`, repeated 17,000 times.
- `beta.txt`: UTF-8 bytes of `ReferenceArchive 中文 fixture\r\n`, repeated 13,000 times.
- `empty.txt` and `empty/`: empty file and directory.

The archive retains the relative `target/interop-source` path. Creation commands, executed from the repository root:

```powershell
& $env:ONEZIP_REFERENCE_TOOL a -y -fmt:zip -l:5 target/referenceArchive-reference.zip target/interop-source
& $env:ONEZIP_REFERENCE_TOOL a -y -fmt:7z -l:5 target/referenceArchive-reference.7z target/interop-source
& $env:ONEZIP_REFERENCE_TOOL a -y -fmt:7z -l:5 -p:onezip-fixture target/referenceArchive-reference-aes.7z target/interop-source
```

Result archives were copied here and renamed. The 7z fixture contains one solid stream for both nonempty files. File timestamps and AES random IVs mean regenerating archives need not produce identical bytes.

SHA-256 of the checked-in fixtures:

```text
10948e3fcb84ecf75ec3b198b4702393f364bb37d3c7bb67a05bf8348463ffac  referenceArchive-7.47.zip
ba8599c3b594cc946f96d3c4b15ea31e945016ee921bc3075d258ae1412fc864  referenceArchive-7.47.7z
1a80b41663ff739f8581e3c8499899e10ce4fa4ac25537126c2c92c51b04d4ac  referenceArchive-7.47-aes.7z
```

`DecoderTest` reads these bytes with our own Java implementation. It never invokes `bz.exe`, loads a DLL, or needs ReferenceArchive installed. Other generated unit-test inputs use test-scope dependencies only.

## Bit-preserving 7z speed optimization vectors

`lzma-speed-golden.tsv` holds 21 raw LZMA2 output commitments produced by the archived pre-optimization OneZip JAR with `benchmarks/SevenZGolden.java`. It includes levels 1/5/9, short tails, 64 KiB and 2 MiB boundaries. Each row records `size seed noise_period distance level sha256 packed_bytes`.

The input is generated with Java `Random(seed).nextBytes(data)`, then, for each `i` from `distance` through `size-1`, replaces `data[i]` with `data[i-distance]` whenever `i % noise_period != 0`. `LzmaSpeedGoldenTest` checks the optimized encoder against the saved lengths and SHA-256, rather than computing expected results from itself.

These vectors intentionally constrain this round's word loads, rep-bound pruning and unsigned range arithmetic to the old bitstream. A future deliberate parser/format change must review the vectors explicitly. The archived JAR identity and seven larger corpus commitments are in `benchmarks/results/2026-10-27-sevenz-speed`.


### Later dictionary-continuity and chunk changes

The TSV remains the historical pre-change commitment; it has not been regenerated from the new implementation. The continuity round retained 12 short exact vectors and allowed changed framing on nine larger vectors. `LzmaHistoryTest` covers sliding-history bounds, nonaligned flush, raw/compressed transitions, workspace isolation and both LZMA2 size limits.

### Later cost-parser change

Normal levels now deliberately choose different tokens. Current `LzmaSpeedGoldenTest` preserves four fast short vectors exactly and independently decodes the remaining 17 through XZ. Normal vectors below 8 KiB allow a fixed eight-byte quality tolerance; larger vectors cannot exceed historical sizes, and the aggregate cannot grow. This tolerance is a regression policy, not a proof about range-coder flush overhead. Actual 4,097-byte exceptions are +3 bytes at level 5 and +2 at level 9; other historical vectors are equal or smaller. The original TSV remains untouched. Five new `LzmaCostParserTest` cases check text quality, short matches, raw/flush boundaries, price quantization and workspace isolation. Diagnostic performance-only changes preserve all 21 outputs from the new parser, separately recorded in the cost-parser report.
