# OneZip

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

一個使用 Java 21 與 JavaFX Fluent 的輕巧壓縮工具。

感謝 vibe coding，也能讓舊軟件煥發第二春了。

[**下載最新版本 →**](https://github.com/shed4329/onezip/releases/latest)

![OneZip 瀏覽範例壓縮包](docs/screenshots/extract.png)

<details>
<summary>首頁與壓縮介面</summary>

![首頁](docs/screenshots/home.png)
![建立壓縮包](docs/screenshots/compress.png)

</details>

## 功能

- 建立與解壓 ZIP、7z、TAR、GZIP、LZ4、XZ、BZIP2 及 TAR 組合格式。
- 開啟與解壓 RAR4／RAR5。
- ZIP32 檔案新增、取代、刪除，以及 AES-256 加密建立。
- 多執行緒、壓縮等級、即時速度與進度顯示、取消操作。
- 四種介面語言、淺色／深色主題、主題色與自訂圖片背景。
- GUI 與 CLI 共用一套程式，壓縮編解碼器由 Java 自行實作。

## 開始使用

Windows 使用者下載 **x64 安裝包**或 **x64／x86 可攜 ZIP**。可攜版自帶 Java，完整解壓後執行 `OneZip.exe` 即可。

命令列使用 `OneZipCLI.exe`：

```powershell
OneZipCLI.exe c backup.7z documents --threads 4
OneZipCLI.exe x backup.7z -o restored
```

## 從原始碼執行

安裝 JDK 21 後執行：

```powershell
.\mvnw.cmd test package
.\run-onezip.cmd
```

[命令列](docs/CLI.md) · [設定](docs/SETTINGS.md) · [格式與限制](docs/JAVA-ENGINE.md) · [開發](docs/DEVELOPMENT.md)

## 授權

[Apache-2.0](LICENSE)。依賴、圖示及執行環境授權見[第三方說明](docs/THIRD-PARTY.md)。
