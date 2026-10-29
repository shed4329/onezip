# Delivery gates — completed on 2026-10-29

The validated Windows portable revision is
`target/onezip-delivery-20261029-r3/OneZip`. Its ZIP, artifact checksums and delivery
receipt are beside that directory. Other images under target are retained probes.

Completed gates:

1. Current production tree reviewed through compilation/lint, formatting, dependency
   and layer scans plus manual examination of the critical paths in REVIEW.md.
   The final inventory contains 81 Java source files and hashes. Forty-two legacy
   Java prototypes/facades and stale IDE definitions remain outside production.
2. Single-file UTF-8 settings, typed preferences, atomic replacement, corruption
   protection and migration/regression coverage. Key API contracts documented;
   Javadoc generation and structural/reference doclint pass.
3. Real JPMS artifact, separate standalone JAR, module-safe bundle/icon loading,
   matching JavaFX21 dependencies, trimmed runtime, retained application line
   tables, GUI/CLI native launchers, original logo and third-party/legal notices.
4. Actual modular settings/appearance, cancellation-cleanup barrier, smart
   extraction, AES ZIP, collision/wrong-password rollback and fresh-JVM restore
   pass. Four GUI languages pass ZIP/7z flows and saved-language restart. The ZIP
   modification-date display is checked with a fixed source time. The last r3
   run repeats the changed extraction paths and Chinese GUI/date acceptance.
5. Native console create/list/test/extract passes all eleven writable formats and
   AES ZIP. Bundled charsets and crypto providers pass; external AES 7z and RAR4/
   RAR5 PPMd/long-password/encrypted-solid fixtures pass in the actual runtime.
6. Final non-GUI suite: 207 tests, zero failures/errors/skips. Existing historical
   GuiSmokeTest XML is excluded from this count. Formatting, Javadoc and pure-Java
   archive dependency audits pass. Self-package/test/unpack verifies every one of
   the portable image's 196 files; relocated GUI and CLI both start successfully.

JVM measurements keep process private commit separate from working set/heap and
fresh-JVM GUI startup separate from warmed codec throughput. Only `-Xms32m` was
selected; maximum heap and GC remain ergonomic defaults. Runtime-only module
trimming compares equal jlink compression/locales. No generic throughput speedup
or refreshed ReferenceArchive comparison is claimed. Raw samples and the final receipt
record actual figures, including timestamp-restoration costs.

Known limits are documented in README, SETTINGS.md, REVIEW.md and the format docs.
Windows10 has no dedicated test-machine result. Native file-picker automation was
not re-run in this pass. Windows Acrylic is not shipped. Missing-member-comment
Javadoc lint is excluded; generated docs are not a claim of complete comment
coverage or proof of all possible inputs. No commit, release publication, system
security change or shutdown scheduling is part of this local delivery.
