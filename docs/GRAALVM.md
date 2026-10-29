# Native Image 本機試驗

此頁記錄較早的原生試驗；目前已選擇繼續使用HotSpot／C2。此頁EXE及性能數據沒有包含後續solid寫入改動，不能當作目前JAR的性能結果。

本輪依照要求只繼續試 **Native Image**。壓縮核心仍是自行實作的 Java；AOT 編譯成 Windows x64 EXE，沒有加入 JNI 壓縮橋接、Ark DLL 或壓縮庫。

產物：`target/native-image-20261027/onezip-cli.exe`。這是 **CLI／壓縮核心試驗**，尚未包含 JavaFX GUI，也沒有更換原本 GUI／JAR 的啟動方式。試驗入口包含內部 benchmark 分支，尚不作為正式發行版。

```powershell
.\target\native-image-20261027\onezip-cli.exe --help
.\target\native-image-20261027\onezip-cli.exe c example.7z "D:\我的資料" -l:5
.\target\native-image-20261027\onezip-cli.exe x example.7z -o restored
.\target\native-image-20261027\onezip-cli.exe t example.7z
```

命令仍由 Java 的 `CommandLine.run(args)` 解析。已在移除 Java PATH／JAVA_HOME 的子程序中解碼外部 7z fixture；PE 匯入表沒有 `jvm.dll`。仍依賴 Windows 的 VC++ 執行階段，並非完全靜態執行檔。

## 建置與限制

- Oracle GraalVM **21.0.8+12.1** 可攜 SDK 放在 `.tools/graalvm-21.0.8`，與原本 JVM 的 JDK 修補版本對齊；並非宣稱這是最新版本。
- 建置採 `--no-fallback -O2 -march=native -H:+AddAllCharsets`。沒有訓練資料產生的 PGO；build log 的 ML-inferred PGO 不等於實測訓練。
- 本機原有 VS 2019。使用 Microsoft 簽署的 VS 2022 Build Tools bootstrapper 僅下載 offline layout，再依套件 SHA-256 校驗解壓 MSVC **19.44.35229** 到 `.tools/native-toolchain/portable`，沿用 Windows SDK **10.0.19041.0**。沒有安裝／升級 Visual Studio；PATH／INCLUDE／LIB 僅在建置子程序生效。
- 已嵌入 `benchmarks/native-cli.manifest`，選用 UTF-8 process code page，修正中文參數轉碼。依 [Microsoft 文件](https://learn.microsoft.com/en-us/windows/apps/design/globalizing/use-utf8-code-page)，適用 Windows 10 1903 以上；不修改系統語系。
- `-march=native` 針對本機 CPU 最佳化，未驗證較舊 CPU／其他 Windows 環境。GUI、安裝器、簽章及跨機發行均尚未處理。

## 重現

本機工具已備妥，執行 `python benchmarks/prepare_native_toolchain.py`，再執行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File benchmarks/try_native_image.ps1` 即可重建。前提是 `target/onezip-1.01-all.jar` 已由 Maven package 產生。

其他電腦需先使用 [Microsoft 官方 bootstrapper](https://aka.ms/vs/17/release/vs_buildtools.exe) 建立 layout：確認 `Get-AuthenticodeSignature` 為 Valid／Microsoft Corporation 後，使用 `--layout "<專案>\.tools\native-toolchain\layout" --add Microsoft.VisualStudio.Component.VC.Tools.x86.x64 --lang en-US --quiet --wait --norestart`。解壓腳本明確鎖定 14.44.17.14 套件；未來 layout 變版需更新與重新驗證。Windows SDK 需另外已安裝。

GraalVM [官方 SDK ZIP](https://download.oracle.com/graalvm/21/archive/graalvm-jdk-21.0.8_windows-x64_bin.zip)／[SHA-256](https://download.oracle.com/graalvm/21/archive/graalvm-jdk-21.0.8_windows-x64_bin.zip.sha256)。本次 ZIP SHA-256 是 `0401a5c9b4a5478640b0d5563a5e0f2c97513ab689c5ee647d41293b92eed0e4`。解壓後 `.tools/graalvm-21.0.8/installation.json` 的 `java`、`native_image` 欄位分別指向 `bin/java.exe`、`bin/native-image.cmd` 絕對路徑。

功能驗證：`python benchmarks/verify_native_image.py`；性能測量：`python benchmarks/benchmark_native_image.py`。這兩支腳本使用固定輸入快照及新建的結果資料夾，會拒絕覆寫既有結果；重跑需先另存舊結果並調整結果目錄。

性能與驗證報告 (historical local evidence; not distributed) 附建置紀錄、原始資料、產物雜湊與測量限制。
