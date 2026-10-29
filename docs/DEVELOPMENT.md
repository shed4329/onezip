# Build and maintenance

Use JDK 21 and Maven (`mvnw.cmd` bootstraps pinned Maven 3.9.11). GUI dependencies
are JavaFX 21.0.8, JMetro 11.6.16 and ControlsFX 11.0.0. Archive/codec libraries
appear only in test scope as interoperability oracles; none are shipped.

Commands from the repository root:

```powershell
./mvnw.cmd test package javadoc:javadoc
python scripts/verify-java-encoders.py target/onezip-1.01-all.jar
./scripts/Format-Code.ps1 -Check
./scripts/Build-Distribution.ps1
```

The normal Maven suite excludes `GuiSmokeTest`; desktop acceptance runs
separately. Do not count a classpath test as a named-module or desktop test.
`Run-ModularAcceptance.ps1` compiles an external test harness and exercises the
actual portable runtime with isolated settings. Its `--add-exports/--add-opens`
flags belong to testing and are absent from the shipped launchers.

Production compilation enables `-Xlint:all`. The module-name digit warning is
locally suppressed to preserve the requested namespace. Javadoc treats warnings
as failures and enables structural/reference doclint; missing-comment lint is
excluded. Key API documentation describes ownership, cancellation, no-overwrite
and temporary-space contracts; this is not a claim of complete member coverage.
Generated API documentation is under `target/reports/apidocs/index.html`.

Maven retains the named-module artifact as `target/onezip-1.01.jar`.
The separate `target/onezip-1.01-all.jar` includes GUI dependencies for
`java -jar` compatibility; its merged dependency descriptors are stripped.
Do not put that standalone JAR on a JPMS module path. Keeping the primary artifact
modular also allows `package javadoc:javadoc` in the same Maven invocation.

Formatting uses google-java-format 1.29.0, AOSP style. Place its Maven Central
`google-java-format-1.29.0-all-deps.jar` in `.mvn/tools/`. The wrapper checks SHA256
`aeb4e1831d56011e7e06f3393c4e17340c7cca0fd7ba9076ab8dc0624759f7f0` before execution.
The tool is not part of the distribution. Run without `-Check` to format sources.

## Layers and ownership

- `Launcher`: dispatches Java args without initializing JavaFX for CLI commands.
- `cli`: argument parsing, console progress/exit codes and password prompting.
- `application`: JavaFX archive-task adapter, owned password copies, progress.
- `engine`: archive facade, readers/writers, checked binary I/O, codecs and bounded
  worker pools. It must not import GUI, settings or localization code.
- `setting`: single-file persistence and typed preferences, independent of JavaFX.
- `i18n`: session ResourceBundle and saved language selection.
- `themes.fxJava.fluent`: MainApplication lifecycle, MainWindowModel state,
  MainWindowController events, SettingsWindow, FXML/CSS and shared controls.

ArchiveReader is the internal reader strategy. ArchiveEngine is the operation
facade. ArchiveReplacement shares checked publication for ZIP edits/descriptions.
Streams use the JDK stream contracts; worker pools own their threads, bound queues
and await shutdown before rollback. No dependency-injection container or pattern
framework is required. Mutable codec workspaces remain confined to one owner.

Java21 records, switch expressions, Math.clamp/ceilDiv and sequenced collection
operations are used where they simplify code. CPU-bound matcher/entropy workers
remain bounded platform threads; replacing them with virtual threads would not
remove CPU or dictionary-memory costs. No preview/incubator API is required.

## Packaging

The desktop entry defaults to GUI file browsing. Use `OneZip.exe --cli <command>`
or `java -jar onezip-1.01-all.jar --cli <command>` for headless work.
`OneZipCLI.exe` invokes a small shim which prepends `--cli` to that same entry.
It does not maintain a second command implementation.

`Build-Distribution.ps1 -InstallerTypes msi,exe -WixHome <portable-wix3-dir>`
also builds per-user Windows installers from the prepared app-image. The
standalone `Build-Installers.ps1` accepts an existing app-image. Neither script
installs the generated packages or changes system file associations.

The verified Liberica Java21 x86 runtime can run the portable image. Its Windows
jpackage backend nevertheless emits x64 MSI metadata. The installer script
rejects x86 publication; build x86 app-images without `-InstallerTypes`.
The x86 portable image was tested under WOW64, not on a 32-bit Windows machine.
Installer creation and static MSI inspection do not replace install, upgrade
and uninstall acceptance on a separate test machine.

Build-Distribution requires a Java21 JDK with matching JavaFX21 jmods (tested with
Liberica Full). jlink keeps the required platform modules, three locale families,
legacy filename charsets and crypto providers. jpackage creates GUI and console
launchers. Application/dependency JARs remain in `app/mods`, preserving application
line numbers independently of the stripped runtime. Platform sources, compiler,
JavaFX Web/Media, test libraries and historical code are omitted.

Use a fresh output directory; the script refuses to replace an existing one.
Do not run `mvn clean` when target contains benchmark/screenshot evidence. If
renaming production classes, remove only verified `target/classes` and
`target/test-classes` before rebuilding to avoid stale packaged classes.

The portable directory is a unit: do not move only OneZip.exe or delete legal
files. User docs and licenses are included; file hashes are generated outside the
portable root. The Windows image is not a cross-platform runtime. Other platforms
need their own matching JavaFX natives and packaging run.

Import `pom.xml` in IntelliJ rather than the former manually maintained IML files.
Stale IDE module/library/artifact definitions are preserved under `legacy/ide`;
they referenced obsolete native bridges and no longer define a supported build.

The selected JVM change is `-Xms32m`, leaving maximum heap and collector ergonomic
defaults intact. It reduces idle commit on this machine; archive dictionaries
and worker counts still increase memory under load. Profiling artifacts under
`target/delivery-audit` distinguish process memory from Java heap, and GUI startup
from warmed codec throughput. Never advertise these small fixed-corpus samples as
general speed guarantees or a new ReferenceArchive comparison.
