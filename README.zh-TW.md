# OneZip 1.01

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

感謝 vibe coding，也能讓舊軟件煥發第二春了。

OneZip 是使用 Java 21 與 JavaFX Fluent 重新建構的桌面壓縮工具。正式壓縮引擎自行實作容器與編解碼器，不依賴壓縮庫、JNI 或外部壓縮程式。

## 下載與啟動

從 [Releases](https://github.com/shed4329/onezip/releases/latest) 下載。64 位提供可攜 ZIP、EXE／MSI 安裝包；32 位提供可攜 ZIP。保留整個目錄，執行 `OneZip.exe`，無須另裝 Java。命令列使用 `OneZipCLI.exe`，或 `OneZip.exe --cli`。

## 功能與限制

支援 ZIP、7z、TAR、GZIP、LZ4、XZ、BZIP2 及 TAR 組合的建立、解壓、列表和校驗。RAR4／RAR5 唯讀，不建立 RAR 或支援分卷。ZIP32 支援新增、取代、刪除、AES-256 加密建立及檔案說明；ZIP64 與 7z 加密唯讀。

支援四種介面語言、多執行緒、壓縮等級、進度／速度／用時顯示及取消。提供主題色、淺色／深色、圖片背景、圖片模糊強度及控制項透明度；圖片模糊不是系統 Acrylic。設定存於 `%LOCALAPPDATA%\OneZip\settings.properties`，不保存密碼。

安裝包未簽章，安裝／升級／移除及 Windows 10 真機未完整驗收；32 位包僅在 64 位 Windows 下驗證。詳細建構方式與格式限制見英文 README 和 docs。

## 實際介面截圖

只使用範例檔案和隔離設定，沒有私人背景、桌面通知或使用者路徑。

![Home](docs/screenshots/home.png)
![Compress](docs/screenshots/compress.png)
![Extract](docs/screenshots/extract.png)

```powershell
.\mvnw.cmd test package javadoc:javadoc
.\run-onezip.cmd
OneZipCLI.exe c backup.7z documents --level 5 --threads 4
OneZipCLI.exe x backup.7z -o restored --threads 4
```

Apache-2.0 · [CLI](docs/CLI.md) · [Settings](docs/SETTINGS.md) · [Licenses](docs/THIRD-PARTY.md)
