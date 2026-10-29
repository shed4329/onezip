# OneZip 1.01

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

vibe coding に感謝。古いアプリが、もう一度活躍できるようになりました。

OneZip は Java 21 と JavaFX Fluent で再構築したデスクトップ用アーカイバーです。製品の圧縮エンジンは Java で実装し、圧縮ライブラリ、JNI コーデック、外部圧縮プログラムに依存しません。

## ダウンロードと起動

[Releases](https://github.com/shed4329/onezip/releases/latest) から取得できます。64 ビット版は ZIP／EXE／MSI、32 ビット版は ZIP のみです。フォルダー全体を展開して `OneZip.exe` を実行してください。Java の追加インストールは不要です。CLI は `OneZipCLI.exe` または `OneZip.exe --cli` で起動します。

## 機能と制限

ZIP、7z、TAR、GZIP、LZ4、XZ、BZIP2 と TAR 複合形式の作成・展開・一覧・検証に対応します。RAR4／RAR5 は読み取り専用で、RAR 作成と分割書庫は非対応です。ZIP32 は追加・置換・削除・AES-256 作成・書庫コメントに対応し、ZIP64 と 7z 暗号化は読み取り専用です。

4 言語、多スレッド、圧縮レベル、進捗・速度・経過時間、キャンセルに対応します。テーマ色、明暗、背景画像、画像ぼかし、コントロールの透明度を設定できます。画像ぼかしは Windows Acrylic ではありません。設定は `%LOCALAPPDATA%\OneZip\settings.properties` に保存し、パスワードは保存しません。

インストーラーは未署名です。インストール・更新・削除と Windows 10 実機の完全な検証は未実施です。32 ビット版は 64 ビット Windows 上で検証しました。詳細は英語 README と docs を参照してください。

## 実際の画面

サンプルファイルと独立した設定で撮影しています。個人の壁紙、通知、ユーザーパスは含まれません。

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
