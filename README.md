# OneZip 1.01

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

Thanks to vibe coding, an old application gets a second life.

OneZip is a desktop file archiver rebuilt with Java 21 and a JavaFX Fluent interface. The archive containers and compression codecs are implemented in Java, without compression libraries, JNI codec calls, or external archive programs in the production engine.

## Screenshots

Actual application captures using isolated demo settings and sample files. No personal wallpaper, desktop, notifications, or user paths are included.

![OneZip home](docs/screenshots/home.png)
![Create an archive](docs/screenshots/compress.png)
![Browse an archive](docs/screenshots/extract.png)

## Download and launch

Get the latest packages from [Releases](https://github.com/shed4329/onezip/releases/latest).

- **Windows x64:** portable ZIP, EXE installer, or MSI installer. The runtime is bundled.
- **Windows x86:** portable ZIP only. A 32-bit installer is not provided.
- Unpack the entire portable package and run `OneZip.exe`; keep its runtime and app folders together.
- `OneZipCLI.exe` is the console launcher. Both launchers use the same Java argument parser; the console launcher selects `--cli` automatically.

```powershell
OneZip.exe "C:\Archives\example.zip"
OneZipCLI.exe c backup.7z documents --level 5 --threads 4
OneZipCLI.exe x backup.7z -o restored --threads 4
OneZipCLI.exe t backup.7z
OneZipCLI.exe l backup.zip
```

## Features and limits

- Create, extract, list, and test ZIP, 7z, TAR, GZIP, LZ4 frame, XZ, BZIP2, and TAR combinations.
- Read RAR4/RAR5, including supported solid, PPMd, and encrypted archives; RAR creation and split archives are not supported.
- ZIP32 add, replace, delete, AES-256 creation, and archive descriptions. ZIP64 is read-only; 7z encryption is read-only.
- Configurable workers and compression levels, progress, throughput, elapsed time, cancellation, and output selection in Explorer.
- English, Simplified Chinese, Traditional Chinese, and Japanese. Theme color, light/dark mode, optional image backgrounds, image blur strength, and control transparency.
- Image blur is an application effect; native Windows Acrylic is not a released feature.
- Settings are stored in one UTF-8 file: `%LOCALAPPDATA%\OneZip\settings.properties`. Passwords are not saved.

Installers are unsigned. Installer generation and static inspection are verified; installation/upgrade/uninstallation and Windows 10 hardware have not been fully tested. x86 packages have been tested under 64-bit Windows. See [format documentation](docs/CLI.md) for detailed limits.

## Build

Use a Java 21 JDK. The Maven wrapper downloads Maven on its first run.

```powershell
.\mvnw.cmd test package javadoc:javadoc
.\run-onezip.cmd
java -jar target/onezip-1.01-all.jar --cli --version
python scripts/verify-java-encoders.py target/onezip-1.01-all.jar
```

Production sources are in `src`, tests in `tests`. The JPMS module is `cn.shed4329.onezip`. Desktop and CLI version strings are generated from `pom.xml`.

Windows runtime images require matching JavaFX 21 jmods; EXE/MSI generation additionally requires WiX 3. See [development](docs/DEVELOPMENT.md), [settings](docs/SETTINGS.md), and [third-party notices](docs/THIRD-PARTY.md).

The project is licensed under Apache-2.0. Dependency, icon, RAR-related, and runtime notices must be retained. Historical versions remain available through Git history and previous releases.
