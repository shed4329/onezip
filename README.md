# OneZip

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

A lightweight file archiver with a JavaFX Fluent interface, powered by Java 21.

Thanks to vibe coding, an old application gets a second life.

[**Download the latest release →**](https://github.com/shed4329/onezip/releases/latest)

![OneZip browsing a sample archive](docs/screenshots/extract.png)

<details>
<summary>Home and compression screens</summary>

![Home](docs/screenshots/home.png)
![Create an archive](docs/screenshots/compress.png)

</details>

## Features

- Create and extract ZIP, 7z, TAR, GZIP, LZ4, XZ, BZIP2, and TAR combinations.
- Open and extract RAR4/RAR5 archives.
- Add, replace, and delete files in ZIP32 archives; create AES-256 encrypted ZIPs.
- Multithreading, compression levels, live speed and progress, and cancellation.
- Four interface languages, light/dark themes, accent colors, and customizable image backgrounds.
- One application for GUI and CLI, with independently implemented Java compression codecs.

## Get started

On Windows, download the **x64 installer** or **x64/x86 portable ZIP**. Portable packages include Java: extract the whole folder and run `OneZip.exe`.

For the command line, use `OneZipCLI.exe`:

```powershell
OneZipCLI.exe c backup.7z documents --threads 4
OneZipCLI.exe x backup.7z -o restored
```

## Build from source

With JDK 21 installed:

```powershell
.\mvnw.cmd test package
.\run-onezip.cmd
```

[CLI guide](docs/CLI.md) · [Settings](docs/SETTINGS.md) · [Supported formats and limits](docs/JAVA-ENGINE.md) · [Development](docs/DEVELOPMENT.md)

## License

[Apache-2.0](LICENSE). See [third-party notices](docs/THIRD-PARTY.md) for dependency, icon, and runtime licenses.
