# RAR compatibility fixtures

These are fixed independent archives, not archives produced by OneZip.
`manifest.json` records every archive's original source URL, SHA-256 and byte count.
Sources: libarchive/libarchive `libarchive/test/*.rar.uu` (decoded with Python binascii.a2b_uu)
and junrar/junrar `src/test/resources/com/github/junrar` (downloaded as binary).
No decoder source from either project is compiled into OneZip.

Libarchive notices are retained in LIBARCHIVE-RAR-LICENSE.txt and LIBARCHIVE-RAR5-LICENSE.txt;
the JunRAR repository's LICENSE is retained in JUNRAR-LICENSE.txt. Decoder attribution and
the current UnRAR license ship in src/cn/shed4329/onezip/engine/RAR-NOTICE.txt.

Files named `.safe.rar` are derived from the corresponding libarchive fixture by
removing symbolic-link file records, including their payload, and retaining every other
header and payload byte unchanged. This lets strict no-link extraction exercise real
compressed streams. Original fixtures are retained for rejection tests; their file data
was not recompressed or synthesized.

`expected.json` and `expected.tsv` contain SHA-256 hashes of all regular files from
25 archives independently extracted by ReferenceArchive 7.47 Beta x64 on 2026-10-28.
One filename in test_read_format_rar_unicode.safe.rar was corrected to the explicit
libarchive expected name `abcdefghijklmnopqrsテスト.txt`: its original header contains
valid raw UTF-8, while ReferenceArchive on this Windows system produced a replacement character
and sanitized part of the name. The content hash was retained unchanged. This exception
is recorded in expected.json and is not a change to archive bytes.

`blake2sp.tsv` uses input byte i % 251 for the indicated size. Expected hashes were
generated independently using Python hashlib.blake2s: eight striped 64-byte leaf streams,
fanout=8, depth=2, inner_size=32, leaf node_depth=0 and offsets=0..7, last_node on leaf7;
root node_depth=1, node_offset=0, last_node=True, hashing the eight 32-byte leaf digests.

Known invalid and unsupported archives are rejection tests, not successful compatibility
samples. In particular the two `different_*window_size` libarchive samples are malformed
regression inputs, the RAR7 sample declares a 4.25 GiB output and 6 GiB dictionary, and
the oversized dictionary sample covers an explicit error. Those malformed samples must
not be extracted with native tools as part of fixture preparation.

The second-stage `new-expected.json` / `.tsv` adds 20 successful PPMd and encryption
cases. The passwords are public fixture passwords, never user credentials. Outputs were
independently extracted with ReferenceArchive, including the 241,647,978-byte PPMd/LZ mixed file.
SHA comparison is streamed to avoid retaining that file in the Java heap.

Five `crypto-test-*.rar` fixtures only replace the encryption wrapper of existing
compressed streams; no RAR compression algorithm is implemented. The test-only assembler
`scripts/make-rar-crypto-fixtures.py` uses Python hashlib and PyCryptodome AES; these are
not runtime dependencies. For faster long-password fixture assembly it optionally calls
the test-only `benchmarks/RarFixtureKeys.java` via RAR_FIXTURE_JAVA_CP. The long-password
fixture was assembled with that option, then independently accepted and decoded by
ReferenceArchive. All five generated archives' decoded hashes also match their original,
unencrypted parent archives. Parentage and SHA-256 are recorded in manifest.json.

`kdf4-vectors.tsv` contains 24 AES-key / full-IV vectors captured from frozen
baseline JAR `8d8ec67c3672ea4787f07716eed8b08fa755d10cb016ff566a572a9f20084f88`
with `benchmarks/RarKdfVectors.java`. Fields: UTF-16LE password hex, salt hex (`-`
means absent), AES key hex, IV hex. Passwords include 0/1/27/28/29/31/32/33/63/127
UTF-16 code units, non-ASCII and surrogate pairs, each with and without an 8-byte salt.
The 14 cases with raw password+salt <=64 bytes were independently checked with Python
hashlib: hash all 262,144 `(raw || little-endian-24-bit-counter)` records, capture the
last digest byte after rounds 0, 16384, ..., 245760, and reverse bytes within each of
the first four final digest words for the AES key. These vectors check the batch
boundaries as well as the historical mutation path; no oracle is recomputed inside tests.

`kdf4-long-vectors.tsv` adds 32 key/full-IV vectors captured from frozen release
`f11bc583f7331bd042b023a9d429c32bf210e3956c087f4707ee05fd0952f1bf`, before
long-input message reconstruction was introduced. Generate with the test-only
`benchmarks/RarLongKdfVectors.java` against that JAR. Deterministic varied passwords
cover lengths 59/60/61/64/65/91/92/93/95/96/97/123/124/125/126 plus mixed Unicode
and surrogate pairs, with and without salt ff00112233445566. Combined with the original
24 vectors, they cover direct-block boundaries at 128, 192 and 256 bytes and all input
alignments occurring during the full 262,144-round derivation.
