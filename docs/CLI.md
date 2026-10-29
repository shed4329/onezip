# OneZip 命令列

免安装 Windows 包直接使用 `OneZipCLI.exe`，已包含所需 Java21 运行时；下文
`onezip.cmd --cli` 的示例在便携版中替换为 `OneZipCLI.exe` 即可。便携版没有 Maven
建置脚本。以下 Java 安装／自动建置说明适用于源码目录和独立 JAR。

需要 Java 21。`onezip.cmd` 在 JAR 不存在時自動建置；`run-onezip.cmd` 是相同入口的別名。也可在任何作業系統執行 `java -jar <onezip.jar> ...`。Windows 腳本會優先使用 `JAVA_HOME`，未設定則使用 PATH 裡的 Java。

預設開啟 GUI；傳入壓縮檔路徑會直接瀏覽檔案。以 `--cli` 作為第一個參數才執行命令列，在不載入 JavaFX 的情況下執行，沿用自行實作的編解碼器，不依賴原生／第三方壓縮引擎。

## 命令與例子

XZ／BZIP2：`c report.txt.xz report.txt`、`c report.txt.bz2 report.txt --format bzip2`；資料夾使用 `c backup.tar.xz folder` 或 `c backup.tar.bz2 folder`。`c`／`x`／`l`／`t` 均支援 `.xz`、`.bz2`／`.bzip2`、`.tar.xz`／`.txz`、`.tar.bz2`／`.tar.bzip2`／`.tbz2`／`.tbz`。兩種 codec 目前循序，TAR 索引後可並行寫出；BZIP2 等級 0 對映為 1，沒有 STORE。完整範圍見 [XZ-BZIP2.md](XZ-BZIP2.md)。

LZ4 frame：`c report.txt.lz4 report.txt --format lz4`；多檔案／資料夾使用 `c backup.tar.lz4 folder --format tar.lz4`。同樣提供 `x`／`l`／`t`。單檔解出名稱取自 archive basename，沒有原始檔名欄位；codec 循序，等級 0 儲存、1–9 使用相同快速 matcher，無 HC／legacy／外部字典。詳見 [LZ4.md](LZ4.md)。

TAR／GZIP／TAR.GZ 也使用相同 Java 命令：`c backup.tar folder`、`c report.txt.gz report.txt --level 5 --threads 4`、`c backup.tgz folder`、`x backup.tar.gz -o restored`。`.gzip`、`.tar.gzip` 亦可；`--format` 可填 `tar`、`gz`／`gzip`、`tgz`／`tar.gz`，必須符合輸出副檔名。GZIP 僅接受一個一般檔案；多來源用 TAR 或 TAR.GZ。TAR 不壓縮，忽略等級，三者不提供密碼寫入。GZIP 的 `l`／`t` 也會解碼並使用私有暫存磁碟，詳見 [TAR-GZIP.md](TAR-GZIP.md)。

以下例子在儲存庫根目錄執行。從別處呼叫腳本時，所有相對路徑仍以呼叫者的工作目錄為準。

```powershell
# 建立新壓縮檔：第一個路徑是輸出，後面是一或多個來源檔案／資料夾
.\onezip.cmd --cli c backup.7z "D:\工作文件" "D:\照片" --level 5
.\onezip.cmd --cli compress documents.zip one.txt two.txt

# 解壓；未指定 -o 時，在目前工作目錄建立 backup 資料夾
.\onezip.cmd --cli x backup.7z -o "D:\還原"
.\onezip.cmd --cli extract documents.zip
.\onezip.cmd --cli x backup.rar -o restored-rar --threads 4
.\onezip.cmd --cli t backup.rar

# 列出標頭中的目錄，不驗證壓縮 payload
.\onezip.cmd --cli l backup.7z

# 解碼所有項目並驗證大小、CRC／驗證碼，不寫出任何解壓檔案
.\onezip.cmd --cli t backup.7z

# 說明、版本、明確開啟 GUI
.\onezip.cmd --cli --help
.\onezip.cmd --cli --version
.\onezip.cmd gui

# 原始 JAR 使用同一套命令；不需要 Windows 腳本
java -jar target/onezip-1.01-all.jar --cli t backup.7z
```

壓縮別名為 `c`、`a`、`compress`；解壓為 `x`、`extract`；列表為 `l`、`list`；測試為 `t`、`test`。`a` 目前只建立新檔，沒有追加／更新語意。既有壓縮檔和解壓目的檔案都不覆蓋；遇到衝突回傳失敗。空資料夾、中文名稱、多個來源及所有格式限制均與 GUI 相同。

RAR／RAR5 的資料／標頭加密沿用相同密碼選項，RAR4 PPMd 也可直接 `x`／`t`，不需額外開關。加密標頭的列表也需要密碼，例如 `onezip.cmd --cli l secret.rar --ask-password`；只有資料加密的包可以不帶密碼列出公開目錄。舊 RAR1／2 加密、分卷與其他限制見 [RAR.md](RAR.md)。

## 選項

- `-l 0..9` / `--level 0..9`：壓縮等級，預設 5，0 表示僅儲存。
- `-t 0..64` / `--threads 0..64`：壓縮及解壓的 worker 上限；0 自動、1 順序，仍受 heap 與獨立工作數限制。TAR 打包與單 GZIP 解碼維持循序，詳見格式文件。GUI 預設 0。由 Java `args` 解析，也接受 `-t:4` 與 `--threads=4`；列表及測試命令不接受此選項。
- `-f <格式>` / `--format <格式>`：`zip`、`7z`、`tar`、`gz`／`gzip`、`tgz`／`tar.gz`／`tar.gzip`、`lz4`、`tar.lz4`、`xz`、`tar.xz`／`txz`、`bz2`／`bzip2`、`tar.bz2`／`tar.bzip2`／`tbz2`／`tbz`；也可由輸出副檔名推斷。指定格式須與副檔名相符。
- `-o <資料夾>` / `--output <資料夾>`：僅適用於解壓。
- `-p <密碼>` / `--password <密碼>`：提供密碼值。
- `--password-env <環境變數名稱>`：從環境變數取得密碼；缺少變數會回傳用法錯誤。
- `--ask-password`：使用終端的隱藏密碼輸入。重新導向／沒有互動終端時回傳用法錯誤，可改用環境變數。密碼 buffer 在作業完成後清除。
- `-q` / `--quiet`：不顯示進度與成功訊息；列表資料及錯誤仍輸出。
- `-h` / `--help`：顯示說明；可在子命令後使用。
- `--version` / `-v`：顯示版本，只能作為頂層選項。
- `--`：後面的參數全部作為路徑，適用於以 `-` 開頭的檔名。

接受 `--level=5`、`--output=folder` 等長選項形式，及常見的 `-l:5`、`-fmt:7z`、`-o:folder`、`-p:value`。選項可放在子命令後的路徑前或後；重複、未知或不適用的選項會被拒絕。

例如`java -jar target/onezip-1.01-all.jar --cli c parallel.7z folder -l:5 -t:4`；不需要Windows腳本解析命令或執行緒參數。

```powershell
java -jar target/onezip-1.01-all.jar --cli c backup.zip folder --threads 4
java -jar target/onezip-1.01-all.jar --cli x backup.zip -o restored --threads 4
java -jar target/onezip-1.01-all.jar --cli x backup.7z -o restored7z -t:4
```

ZIP 按獨立項目並行；至少 4 MiB 的單檔也可將 DEFLATE 分段並行壓縮，片段保留既有 32 KiB 歷史。單檔解碼仍依序執行。7z 解壓按獨立 folder 並行，同一 solid folder 內仍依序解碼；即使壓縮時使用多 worker，保留字典的片段也不等於能獨立解碼。小包、單 folder 或 I/O 受限時，多執行緒可能沒有加速；可以用 1 停用。詳見[排程與資源限制](THREADING.md)。

三種密碼輸入方式只能選一種。命令參數可能出現在 shell 歷史及程序參數中，互動時可用 `--ask-password`，自動化可由環境提供密碼。ZIP 加密壓縮不接受空密碼。7z AES 解壓已支援，加密寫入仍未提供。

```powershell
$env:ONEZIP_SECRET = '你的密碼'
.\onezip.cmd --cli c secret.zip file.txt --password-env ONEZIP_SECRET
.\onezip.cmd --cli t secret.zip --password-env ONEZIP_SECRET --quiet
.\onezip.cmd --cli x secret.zip -o restored --ask-password

# 處理以 - 開頭的來源檔名；選項須放在 -- 前
.\onezip.cmd --cli c files.zip --level 3 -- -notes.txt
```

## 輸出與退出碼

CLI 的標準輸出與錯誤輸出固定為 UTF-8。進度與錯誤寫入 stderr；成功訊息與列表寫入 stdout。列表為 TSV，欄位為 `Type`、`Size`、`Name`，`D` 表示資料夾、`F` 表示檔案，大小單位 bytes。名稱中的 `\`、Tab、CR/LF 及控制字元會跳脫，避免破壞列結構。一般格式的列表只讀取標頭，需要檢查內容完整性時使用 `t`；GZIP／TAR.GZ 的列表則必須先解碼及驗證到私有暫存。

- `0`：成功。
- `1`：作業失敗，包括檔案不存在、衝突、錯誤密碼、資料損壞、格式／方法尚未支援。
- `2`：命令或選項用法錯誤。
- `130`：作業在 Java 層觀察到中斷／取消。直接終止程序時，最終退出碼由作業系統決定。

Ctrl+C 進入 JVM 的正常 shutdown 流程時，CLI 會通知核心取消並等待最多 10 秒清理暫存壓縮檔／本次新建的解壓檔案。強制終止程序無法保證執行清理。

## 驗證

`CommandLineTest` 涵蓋 ZIP／7z／TAR／GZIP／TAR.GZ 的壓縮／列表／測試／解壓、格式別名、環境變數與隱藏密碼、密碼清除、實際 ReferenceArchive AES solid 7z、CRC 損壞、用法與退出碼、取消，以及完全沒有 JavaFX 類別的獨立 JVM。封裝後另從不同工作目錄呼叫 Windows 腳本，確認相對路徑、空白與中文檔名及退出碼。

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
python scripts/verify-java-encoders.py target/onezip-1.01-all.jar
```

## 統一啟動入口

OneZip.exe 與 JAR 都以 --cli 選擇命令列。OneZipCLI.exe 只在相同入口前補上 --cli，不是另一套實作。

    OneZip.exe --cli --help
    OneZip.exe --cli c backup.zip source.txt
    OneZipCLI.exe c backup.zip source.txt
    OneZip.exe "C:\檔案 空間\archive.zip"

GUI 啟動器不建立主控台；需要互動密碼輸入或可靠的終端顯示，使用 OneZipCLI.exe。
