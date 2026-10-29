# Delivery review scope

This pass covers the current Maven production source tree, dependencies, resources,
launchers and distribution scripts. Historical prototypes remain outside production;
they were archived, not silently converted into supported features.

Every active Java source is covered by Java21 compilation with lint, formatting,
the source/dependency boundary audit and the complete regression build. Javadoc
validates module/API references and structure, with missing-comment lint excluded.
The source inventory in `target/delivery-audit` records filenames, hashes and line
counts so the evidence can be tied to a particular revision. Those automated gates
are distinct from the manual examination below; this is not a claim that a formatter
or a passing suite proves every line or input correct.

Manual review focused on:

- Launcher/CLI separation, argument/password ownership, interrupts and exit codes.
- ArchiveEngine planning/publication/rollback, path traversal checks, CREATE_NEW,
  directory ownership and independent extraction cursors.
- Container readers/writers, checked binary lengths and integer arithmetic, CRC/
  authentication boundaries and temporary-file disposal.
- DEFLATE/LZMA/LZ4/BZIP2/RAR loop bounds, bounded dictionaries/workspaces,
  cancellation polling and existing independent/golden/malformed-input tests.
- Worker queue bounds, future error propagation, shutdown-before-rollback and stream
  ownership/finish/close contracts. CPU workers remain bounded platform threads.
- UI model/binding ownership, metadata/listing cancellation, scene registration,
  resource loading on JPMS, theme/settings error reversion and task lifetimes.
- Settings single-file migration, deterministic escaping, failed-write behavior,
  corruption preservation and multi-process last-writer-wins limitations.
- Named-module descriptors, mixed JavaFX dependencies, trimmed runtime capabilities,
  application line tables, launchers, licenses and repeatable verification scripts.

Concrete fixes include queued-task password clearing, cancellation cleanup gating,
transactional ZIP description edits, checked shared archive replacement, module-safe
ResourceBundle/icon loading, removal of the shadow ControlsFX implementation, typed
appearance preferences, stale IDE dependency definitions and JavaFX11 transitives.
The user-reported ZIP modification-date bug was confirmed as fixed 1980 DOS fields;
new ZIP paths capture source times, write NTFS UTC plus DOS fallback, display extended
timestamps and restore modification times after extraction. Sequential, parallel,
AES, addition, timezone and range cases have dedicated independent-reader tests.
Windows junction/reparse directories are checked through `isOther()` as well as
symbolic-link status. Real junction fixtures verify that extraction cannot be
redirected and creation/ZIP editing refuse linked source directories.

Known limits remain explicit in the format/user documents: ZIP32 writing/editing,
RAR read-only and unsupported variants, no 7z encryption writing, stream-format disk
spooling, settings multi-process last-writer-wins, optimistic external-change checks,
and filesystem-dependent timestamp precision. System Acrylic is not shipped. Windows
10 has no dedicated test machine in this run. Measured memory/startup and warmed codec
samples are separate from correctness gates and are not universal performance claims.
Reparse directories, including some cloud-placeholder directories, are deliberately
refused as archive sources/extraction parents. This run does not certify OneDrive
placeholder compatibility. Full time/attribute restoration for formats other than
ZIP was not added in this pass.
