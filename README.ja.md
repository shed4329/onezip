# OneZip

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

Java 21 と JavaFX Fluent で作られた、軽快なファイルアーカイバー。

vibe coding に感謝。古いアプリが、もう一度活躍できるようになりました。

[**最新版をダウンロード →**](https://github.com/shed4329/onezip/releases/latest)

![OneZip でサンプル書庫を表示](docs/screenshots/extract.png)

<details>
<summary>ホーム画面と圧縮画面</summary>

![ホーム](docs/screenshots/home.png)
![書庫を作成](docs/screenshots/compress.png)

</details>

## 機能

- ZIP、7z、TAR、GZIP、LZ4、XZ、BZIP2 と TAR 複合形式の作成・展開。
- RAR4／RAR5 の閲覧・展開。
- ZIP32 内のファイル追加・置換・削除、AES-256 暗号化 ZIP の作成。
- マルチスレッド、圧縮レベル、速度・進捗表示、キャンセル。
- 4 言語、ライト／ダークテーマ、アクセントカラー、背景画像のカスタマイズ。
- GUI と CLI を一つのアプリに統合。圧縮コーデックは Java で独自実装。

## 使い方

Windows 用 **x64 インストーラー**または **x64／x86 ポータブル ZIP** をダウンロードしてください。ポータブル版は Java を同梱しています。フォルダー全体を展開して `OneZip.exe` を起動します。

コマンドラインでは `OneZipCLI.exe` を使用します：

```powershell
OneZipCLI.exe c backup.7z documents --threads 4
OneZipCLI.exe x backup.7z -o restored
```

## ソースから実行

JDK 21 をインストールして実行します：

```powershell
.\mvnw.cmd test package
.\run-onezip.cmd
```

[CLI](docs/CLI.md) · [設定](docs/SETTINGS.md) · [対応形式と制限](docs/JAVA-ENGINE.md) · [開発](docs/DEVELOPMENT.md)

## ライセンス

[Apache-2.0](LICENSE)。依存ライブラリ、アイコン、ランタイムについては[サードパーティ通知](docs/THIRD-PARTY.md)を参照してください。
