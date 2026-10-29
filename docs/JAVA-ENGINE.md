# 自行實作 Java 壓縮引擎

本版本把自行實作的 Java 編碼器與解碼器接回原有 JavaFX Fluent GUI。壓縮與解壓均不使用 JNI、Ark DLL、7-Zip-JBinding、外部壓縮程序、Zip4j、Commons Compress、XZ，也不把 DEFLATE 委派給 JDK Deflater/Inflater。第三方壓縮庫只有 Maven test scope，作為測試對照，不會進入發行 JAR。

## 實作範圍

- `ArkLzma2OutputStream`：自行實作 literal、matched literal、最近四個距離的 rep、新 match、長度/距離機率樹、12-state 轉移、自適應 range coder、延遲進位、LZMA2 header、raw fallback 與 EOF。壓縮 chunk 在輸出上限前結束；同一字典區塊內可保留模型，並重用匹配器工作區。
- `SevenZWriter`：自行實作 7z signature/start header、pack/unpack/folder/files headers、UTF-16LE 名稱、empty-stream bitsets 與 CRC。相鄰非空檔案共享累計最多2 MiB的solid folder，保留逐檔substream大小／CRC及folder CRC；大於2 MiB的單檔使用獨立串流folder。
- `Lzma2EncoderPool`、`ParallelLzma2OutputStream`：每archive有界worker queue，依序收集結果；大檔的工作片段保留前方最多2 MiB歷史，只重設模型，不逐段清除解碼字典。獨立solid folders亦可同時壓縮，worker工作區各自隔離。
- `DeflateEncoder`、`DeflateHuffman`：自行實作 LZ77 hash-chain、32 KiB 跨區塊歷史、lazy parsing、canonical 動態／固定 Huffman 與 stored 成本選擇；輸出批次寫入，工作區重用，正常等級以環狀鏈保留匹配索引。
- `DeflateOutputStream`、`ChunkedOutputStream`、`NioInputStream`／`NioOutputStream`：公開的 raw DEFLATE 串流 API、有界分塊結果及 blocking FileChannel adapter；ZIP 連續派工取代整批等待，中型檔不再作磁碟 spool。用法、資源所有權及 I/O 實驗限制見 [STREAM-IO.md](STREAM-IO.md)。
- `ZipWriter`、`Crc32`：自行實作 ZIP32 本地標頭、data descriptor、central directory、UTF-8 名稱及扁平 slicing-by-16 CRC32；以平衡 XOR 樹合併查表結果，保留增量與逐 byte 更新。
- `ZipAesOutputStream`：自行實作 WinZip AE-2 salt/verifier/CTR/authentication 封裝；AES、PBKDF2、HMAC 使用 JDK JCE 密碼學原語。
- `DeflateInputStream`：自行實作儲存／固定 Huffman／動態 Huffman 三種區塊、canonical code 建樹、9-bit 首層與長碼子表、code-length 重複碼、長度／距離解碼及 32 KiB 循環字典。以 16 KiB 批次讀入壓縮資料；stored 與匹配資料採批次複製，檢查環狀、重疊、截斷及預讀尾端資料。
- `LzmaInputStream`：自行實作 LZMA1/LZMA2 的 adaptive range decoder、literal/matched-literal、rep/new match、12-state 狀態、機率樹、循環字典、LZMA2 raw/compressed 區塊及重設規則。range input 緩衝重用，raw／匹配輸出批次處理；預讀剩餘資料仍參與尾端驗證。
- `ZipArchiveReader`：自行解析 EOCD、ZIP64、中央與本地標頭、UTF-8/CP437/Unicode-path 名稱、STORE/DEFLATE、大小與 CRC；自行實作 ZipCrypto 與 WinZip AES-128/192/256 解密封裝和 HMAC 驗證。AES-128/256 已用 Zip4j 輸出交叉驗證；Zip4j 不提供 AES-192 寫入，因此 192 路徑未有同等獨立測資覆蓋。
- `SevenZArchiveReader`：自行解析 start/next header、encoded header、PackInfo、coder graph、UnpackInfo、solid SubStreamsInfo、CRC、空項目與 UTF-16LE 名稱。支援單一輸入／輸出的串接 coder：Copy、LZMA、LZMA2、DEFLATE、Delta、x86 BCJ、7z AES。
- `X86BcjInputStream`：自行還原 E8/E9 分支位址，處理五位元組 lookahead、符號位與重疊候選遮罩。
- `SevenZAesInputStream`：自行實作 7z 的 SHA-256 迭代 KDF、UTF-16LE 密碼、AES-256 CBC 封裝與零填充；AES、SHA-256 使用 JDK JCE 原語。支援加密內容及加密 encoded header。
- `ReadSupport`：位元組／整數邊界、stream 範圍、輸出大小與 CRC 檢查；`ZipComments` 取代舊 GUI 註解功能中的 Zip4j 相依。
- `ArchiveEngine`：檔案樹、空資料夾、多來源、進度、取消、暫存輸出完成後發布、拒絕覆蓋既有壓縮包。
- `ArchiveTask`、`ArchiveDialogs`：JavaFX 背景工作、等級、格式、ZIP 密碼、進度、取消及失敗狀態。

**這是可互通格式的獨立實作，不是 ReferenceArchive 原始碼，也不是逐位元相同的克隆。** 根據逆向定位的編碼主迴圈與公開格式重建 token/state/range 邏輯；目前已加入有界成本動態規劃，具體範圍見[成本 parser 筆記](ARK-COST-PARSER.md)。

## 實作取捨

目前目標已更新為：維持正常壓縮率，在HotSpot預熱後以完整Java壓縮API吞吐達到ReferenceArchive參考速度90%。目前可量到的原生參考是ReferenceArchive CLI，仍含程序啟動；最新實測 (historical local evidence; not distributed)明列此邊界及逐類達標結果，不將它稱為同邊界DLL純核心測試。先前80%及僅要求複雜度同階的目標保留作歷史。

以輸入bytes數n分析，現有核心的匹配長度上限273、每位置候選數至多256（預設等級5為16／64）、DP展開位置256及有界尾端皆固定，不隨整檔大小增長；索引／range輸出按串流推進，因此固定參數下為O(n)的有界線性處理。這不代表常數小或已達相同實測速度。Ark已確認的parser也限制273-byte匹配及約2048節點工作區；其完整match-finder尚未逐分支還原，因此只能在固定搜尋預算的條件下作同階比較，不能宣稱已證明整個ReferenceArchive程式的最壞時間上界。檔案樹掃描、項目排序與metadata成本須另按項目數及名稱總長度分析。

目前以HotSpot／C2執行。新一輪已在Ark指令中追出fast／normal選擇、成本工作區與回溯結構；[編碼決策學習筆記](ARK-DECISIONS.md)明確列出已觀察位址、人工映射與尚未移植的部分。Solid寫入採2 MiB有界分組，不在檔案邊界重設字典；這是配合現有encoder的政策，不代表Ark使用同樣分組或排序。本輪報告 (historical local evidence; not distributed)另驗證ReferenceArchive與OneZip的實際folder/substream結構及固定測資改善。

LZMA 等級5–9使用有界成本 parser，等級1–4保留貪婪選擇，-1對應5；0只儲存。成本工作區每次展開最多256個位置，容許完整match／複合尾端越過該處，保存state、四個rep距離及前驅並回溯；包含literal、short-rep、兩位元組match及三種複合路徑。價格使用16單位／bit的整數量化、長度／距離快取；不模擬每條假想路徑的機率更新。此處仍為hash-chain matcher，未完整移植Ark的2048節點政策及全部刷新時機。

單worker使用2 MiB滑動字典，跨輸入暫存區與flush保留歷史、模型與最近距離；只在新串流初始化字典，所有匹配距離仍限制在宣告的2 MiB內。暫存新輸入最多2 MiB，歷史加新輸入的實體陣列最多4 MiB，匹配鏈隨視窗平移，不把整檔載入記憶體。並行模式仍限制2 MiB匹配距離，但各工作片段重設機率／rep狀態並以既有原始資料前綴建立自己的索引；解碼字典保留，因此不是把每段當成完全無歷史的新資料。

LZMA2的64 KiB上限是壓縮後payload，上限2 MiB則是還原大小，兩者分開處理。每個chunk先處理64 KiB；只有已編碼大小低於輸入的1/16、下一段也能找到字典匹配時，才繼續延長至最多2 MiB，避免把後續高熵資料盲目拖進range編碼。range payload接近60 KiB即結束，保留進位／flush餘量。這個成長條件是本專案的性能策略，不是Ark已確認的閾值。raw fallback後重設機率狀態，字典保留；非對齊flush保留位置context。本輪驗證 (historical local evidence; not distributed)另列壓縮率、速度與額外記憶體成本。

匹配器採四位元組索引及兩／三位元組短匹配候選，按檔案順序重用工作區。每個大於等於8 KiB的小區塊，可透過抽樣目前／既有字典中的匹配判斷是否直接用raw，以避免對隨機資料做大量無效range編碼。這是有界的速度／壓縮率取捨，不是最優判定；可能放過少量可壓縮機會，但raw區塊始終無損。回歸測試另確認看似隨機但在字典中重複的區塊仍會被壓縮。

快速模式以rep長度建立候選下界；正常模式改比較價格與回溯，不能套用同一貪婪淘汰規則。JDK陣列VarHandle的32／64位讀取合併hash計算與匹配後索引更新；短前綴採機器字比較，range區間採32位unsigned運算。歷史21組向量保留原檔：4組快速短向量仍要求位元組一致，17組獨立解碼；正常模式低於8 KiB者允許固定8-byte品質誤差，其餘不能變大，且總量不能增長。實際兩個4,097-byte向量多3／2 bytes；沒有用新版重產歷史預期雜湊。最新報告 (historical local evidence; not distributed)另保留改進前後大小及速度。標準JDK的陣列操作不包含任何壓縮實作、JNI橋接或off-heap存取。

目錄標頭至少1 KiB、非level0且壓縮後確實較小時，SevenZWriter以自製LZMA2寫入獨立encoded-header串流，減少大量UTF-16名稱與substream資訊的開銷。此門檻為本專案政策，未宣稱與Ark相同。

後續[研究及原型實驗](LZMA-RESEARCH.md)比較搜尋預算、DP展開長度與nice提早停止。預設等級5對四位元組皆為ASCII的前綴採深度16，含高位元組或不足四位元組時保留原深度64；這是實測導出的保守預算政策，不是文字編碼識別器。保留nice32、256位置展開與自然尾端；等級6–7／8–9分別保留128／256深度。先檢查複合尾端是否匹配，再計算價格，省去無效選項的樹遍歷。目前實測 (historical local evidence; not distributed)列出完整包相對上一輪成本parser的增幅與速度，包含公開語料及額外中文／雙語輸入；不保證任意資料的包大小增幅上限，亦未宣稱已達先前80%速度目標。

本輪進一步在DP內直接重用同position-state的長度價格列、相同distance-state的距離價格及複合路徑首token價格；21組raw向量維持與本輪前完全相同，並以單worker完整包驗證更多資料。多worker的模型重設則刻意改變部分輸出，另做大小與跨實作解碼驗證；[並行設計與限制](PARALLEL-SEVENZ.md)記錄取捨。

預熱速度目標及量測邊界見7z專項報告 (historical local evidence; not distributed)。其中80%定義為`ReferenceArchive預設CLI耗時 / Java預熱完整壓縮API耗時 >= 0.8`，Java耗時包含I/O、不含JVM啟動；原生CLI包含啟動／日誌。資料集達標不代表所有檔案或同等ReferenceArchive壓縮率下達標。

DEFLATE 每區塊最多 65535 bytes，保留前區塊最後 32 KiB 字典。先產生 token 與頻率，再比較動態 Huffman（含碼表 header）、固定 Huffman 與 stored 的實際 bit 成本。等級 5–9 使用有界一步 lazy parsing。自行建立 Huffman 樹與 canonical codes；極端直方圖超過格式碼長限制時，以頻率縮放重建合法樹，尚非最佳 length-limited 演算法。大於等於 4 MiB 的單檔可分約 1 MiB 工作，附帶 32 KiB 已有歷史，透過非最終區塊與空 stored 對齊後依序合併為標準 DEFLATE 串流；詳見 [ZIP 優化設計](ZIP-OPTIMIZATION.md)。

解壓的大小、CRC、MAC及尾端驗證保留。沒有CRC欄位的中介串流不再計算未使用的CRC；提供CRC時仍完整驗證。目錄預檢只重用唯讀掃描結果，實際建立／寫入前仍重新檢查完整父目錄鏈，並以NOFOLLOW取得屬性；測試涵蓋預檢後父目錄變成普通檔案的情況。

## 支援界線

- 壓縮：ZIP / 7z、檔案和多個資料夾、中文名稱、空資料夾與空檔案、0–9等級。
- 密碼壓縮：ZIP AES-256；7z **加密寫入**尚未實作，GUI會停用該選項，API會明確拒絕。密碼解壓：ZIP ZipCrypto／WinZip AES、7z AES；錯誤密碼或完整性驗證失敗會移除本次新建輸出。
- ZIP 寫入為 ZIP32：單一大小及整體偏移小於 `0xffffffff`、最多65534個項目；未提供 ZIP64。
- 寫入器目前不保留來源時間戳、Unix權限或符號連結；壓縮輸入中的連結會明確拒絕。
- ZIP64 **讀取**已實作；不支援分卷或自解壓包。ZIP 解壓僅支援 STORE 與 DEFLATE，尚無 Deflate64、ZIP-LZMA、BZip2、Zstandard 等其他方法。
- 7z 支援 solid 及壓縮／加密 header。尚不支援 BCJ2 多串流、ARM/ARM64 等其他 BCJ、PPMd、BZip2 或任意多輸入／輸出 coder graph；不會回退第三方引擎。不支援外部屬性串流及 anti-item。
- 解析 header 上限 64 MiB、項目上限 1,000,000、LZMA 字典上限 256 MiB；7z AES 一般 KDF 上限為 2^24 次，另支援特殊 power=63 的直接 key 模式。超限會明確拒絕。解碼串流不會把整個檔案或 solid folder 載入記憶體。
- RAR／RAR5 解壓已以純 Java 接入，支援 LZ、RAR4 PPMd、solid、標準篩選器與資料／標頭加密；分卷等限制見 [RAR.md](RAR.md)。不提供 RAR 建立與舊版「透過原生引擎編輯7z」功能。舊 JNI／壓縮庫原型原始碼保留在倉庫供對照，但 Maven 明確排除，發行 JAR 不包含它們或第三方壓縮庫。
- JavaFX 使用自己的平台視窗元件；「無原生壓縮橋接」是對壓縮引擎的限制，不代表整個 JavaFX/JDK 執行環境不使用作業系統原生程式碼。

## 驗證

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Dtest=GuiSmokeTest test
.\mvnw.cmd package
python scripts/verify-java-encoders.py target/onezip-1.01-all.jar
```

單元測試雙向交叉驗證：新編碼器輸出交由 XZ、JDK ZIP、Zip4j、Commons Compress 讀取；新解碼器輸入由上述獨立編碼器或 ReferenceArchive 產生。涵蓋各等級、三種 DEFLATE 區塊、LZMA 屬性組合、LZMA1 有／無結束碼、LZMA2 raw/continuation、字典繞回、BCJ 遮罩與邊界、ZIP64、solid／encoded header、中文、空項目、ZIP/7z 加密、錯誤密碼、HMAC/CRC 損壞、截斷、取消、路徑越界與拒絕覆蓋。

`tests/fixtures` 包含本機 ReferenceArchive 7.47 產生的實際 ZIP、solid 7z 與 AES 7z 小型測資。測試不會執行 ReferenceArchive，也不要求測試機安裝 ReferenceArchive；測資內容、來源命令與雜湊見該目錄的 README。

GUI smoke test會開啟原有主視窗，按「全部压缩」，在新對話框選7z，等待背景作業完成；再經 GUI 進度視窗與 `ArchiveTask` 執行自行實作的解壓，核對還原內容。截圖保存於 `target/gui-smoke`。桌面測試須在有顯示環境時明確執行。

`verify-java-encoders.py` 是靜態約束檢查，不是一般用途安全審計。大型檔案、效能、所有異常格式或所有OS的完整驗收仍需更多測試。

## 參考規格

- RFC 1951 DEFLATE：https://www.rfc-editor.org/rfc/rfc1951.html
- 7-Zip LZMA/LZMA2 公開參照（Public Domain）：https://github.com/ip7z/7zip/tree/23.01/C
- 7z format：https://github.com/ip7z/7zip/blob/23.01/DOC/7zFormat.txt
- WinZip AES AE-2：https://www.winzip.com/en/support/aes-encryption/

第三方依賴由Maven取得，沒有將ReferenceVendor DLL、反組譯檔或私有二進位拷入此專案。

## ReferenceArchive 解壓的已知範圍

先前針對本機 7.47.0.1 的 REA／指令分析，確認 `CreateArk` 建立 `CArkMan`，以及主程式動態載入 `ark.x64.dll` 的邊界。RTTI 另包含 `CArkUnZip`、`CArk7Zip`、`CArkUnRar`、`CArkUnRar5`、AES/ZipCrypto 與 parallel extraction 類別；選定位址出現 ZIP local/EOCD signature 檢查。這支持格式處理器分工的判斷，尚不等於完整的解壓呼叫圖。

依公開格式，解壓需要先解析目錄與 coder 資料，若有加密則先還原壓縮位元流，接著依方法解碼並檢查長度／CRC。DEFLATE 以 Huffman 碼還原 literal 或 `(length,distance)`，LZMA 以 range coder 還原同類 token；字典按距離複製支援重疊，才能還原重複資料。7z solid 需要在多個檔案之間保留字典，再依 substream 大小切分並驗證。

上述格式步驟是我們獨立實作的依據，不能當作已逐函式還原 ReferenceArchive 的證據。原版的平行排程、全部格式處理器與錯誤恢復流程尚未完整逆向。本專案也未宣稱具有原版全部格式與效能。
