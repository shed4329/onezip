# RAR／RAR5 解壓

2026-10-28：純 Java 的 RAR 讀取已接入 GUI 與 Java args CLI 的 `l`、`t`、`x`。建立壓縮檔仍只支援 ZIP／7z，沒有 RAR 壓縮器。生產程式沒有 JNI、DLL 呼叫、外部程序或壓縮庫。

## 已實作與邊界

- RAR3／4 容器、解碼版本 29／36 的 LZ／Huffman：字典、最近距離快取、低距離重複碼、多個 Huffman 區塊、solid 跨檔狀態與 CRC32。
- RAR4 PPMd 變體 H：範圍解碼、上下文模型、SEE／二元機率、符號遮罩、頻率縮放、12-byte 邏輯配置器、模型重啟與 PPMd／LZ 切換。配置器用 Java byte[] 與受檢查的整數偏移，沒有原生指標。
- RAR4 標準 VM 篩選器：E8、E8E9、Itanium、Delta、RGB、Audio。以程式長度、XOR 與 CRC 識別標準程式，不執行任意 VM 位元碼。實際檔案已覆蓋 x86 篩選器；其餘標準篩選器的實檔覆蓋仍需擴充。
- RAR5 區塊與 Huffman 表、LZ 匹配、solid、多檔案、Delta／E8／E8E9／ARM，CRC32 與 BLAKE2sp-256。
- RAR4 AES-128-CBC 資料／標頭解密，自行實作 RAR SHA-1 KDF，包括長密碼的歷史排程副作用。RAR5 AES-256-CBC、UTF-8 密碼 PBKDF2-HMAC-SHA256、密碼校驗、CRC-MAC 與 BLAKE2sp-MAC。AES／HMAC／SHA-256／SHA-1 使用 JDK 標準密碼原語；RAR29 長輸入先以自製 Java 模擬排程回寫，再雜湊完全相同的位元組。provider 不支援摘要快照時，保留完整自製相容核心作為後備。容器、KDF 次數控制、MAC 轉換與所有壓縮解碼均為自行撰寫的 Java，沒有壓縮庫／JNI。
- 壓縮資料及標頭範圍、CRC、精確輸出大小、字典引用與記憶體預算均驗證；錯誤與取消沿用既有解壓回滾。單個字典預算不超過 256 MiB 與 JVM 最大 heap 的四分之一；實際只分配該 solid group 所需的歷史。
- 檔名支援 UTF-8、RAR4 編碼 Unicode。舊式非 UTF-8 名稱預設 IBM437，可用 JVM 選項 `-Donezip.rar.charset=GBK` 或 `MS932` 指定原始編碼。

**尚未支援**：RAR 1.x／2.x 壓縮及其舊式加密、分卷、SFX、自訂 VM 程式與全域 VM 資料、符號／硬連結／檔案重導向、未知解壓大小，以及超出記憶體預算的字典。這些情形明確失敗，不回退原生引擎。RAR7 的擴充距離表已有解碼分支，但大型字典案例受預算限制，不能宣稱完整 RAR7 相容。

PPMd 模型需求依包內 MaxMB+1 配置，上限同樣為 256 MiB 與 heap 四分之一的較小者；沒有為了省記憶體偷偷降低模型大小。RAR4 worker 預算額外保留 PPMd 上限。RAR5 KDF 指數最多 24，推導過程定期檢查取消。密碼使用 reader 私有副本，關閉 reader 會清除密碼與快取金鑰，fork 不共用可清除的金鑰。資料加密包可不帶密碼列出公開目錄；加密標頭則必須提供密碼。密碼沿用 CLI `-p`／`--password-env`／`--ask-password` 與 GUI 密碼框，解密錯誤不輸出密碼。

## 執行緒與效能

`--threads 0` 自動、`1` 順序、`2..64` 限制 worker。不同獨立檔案／solid groups 可以並行；同一 solid group 的歷史相依仍順序解碼，增加 worker 不會使單一 solid stream 自動加速。每個 fork 使用獨立檔案游標與字典。

解碼採 MSB 位元快取、15-bit Huffman 查表與匹配批次複製，保留未經篩選的歷史字典。篩選器使用有界暫存，變換後的資料才寫出及驗證校驗碼。

前一版預熱實測 (historical local evidence; not distributed)保留該版 JAR 身份與固定 RAR LZ 測試包；其結果不代表本輪 PPMd／加密路徑的速度。60% 指吞吐量為 ReferenceArchive 的 0.6 倍，也就是允許耗時約 1.67 倍。本輪優先完成相容性與錯誤處理，沒有把舊數字當成新功能的效能測量。

## 驗證與來源

固定測試包來自 libarchive 與 JunRAR 的公開相容性測試集，沒有使用它們的解壓實作或執行期相依。25 個成功案例逐檔核對 ReferenceArchive 解出的 SHA-256；包括 40 MiB RAR5、約 19.2 MiB RAR4、多檔 solid、Unicode 與篩選器。另測隨機順序讀取、fork、CLI、多 worker、錯誤 CRC、BLAKE2sp、截斷、惡意路徑、取消與回滾。BLAKE2sp 邊界向量由 Python hashlib 獨立產生。

本輪另有 20 個案例逐檔核對獨立 ReferenceArchive SHA-256：PPMd、多區塊 PPMd／LZ（解出 241,647,978 bytes）、1／64 MiB 模型、音訊篩選器、RAR4／5 資料與標頭加密、非 ASCII 密碼、長密碼，以及 CRC／BLAKE2sp MAC。包含 5 個只替換加密封裝、完全保留原壓縮碼流的加密 solid／PPMd 組合；它們也經 ReferenceArchive 解開並核對原始未加密包的內容，並非只用自己的加密／解密互相驗證。完整清單在 `new-expected.json`／`new-expected.tsv`。

PPMd／加密首版驗證：131 項測試（含 GUI RAR5 加密解壓）通過，39 個生產 Java 原始碼與發行 JAR 的壓縮依賴檢查通過。另以當時 JAR 執行 PPMd、加密標頭測試；`-Xmx128m` 遇 64 MiB PPMd 模型時依 heap 四分之一預算明確拒絕，沒有 OOM。JAR 含解碼器與授權說明，不含測試加密器或第三方壓縮程式。

PPMd／加密首版 JAR SHA-256：`8d8ec67c3672ea4787f07716eed8b08fa755d10cb016ff566a572a9f20084f88`。

後續 RAR4 加密 solid 優化 (historical local evidence; not distributed)將短輸入 KDF 改為每批 4,096 輪，保留全部 262,144 輪、16 個 IV 快照與取消檢查。只有 `UTF-16LE(password) + salt` 長度不超過 64 bytes、確定不會觸發 RAR29 排程回寫時才使用標準 SHA-1；超過分界或 provider 不支援快照，使用歷史相容核心。長密碼核心重用排程陣列並展開輪次。沒有新增跨操作的密碼／金鑰快取。

首輪優化版 134 項測試（含 GUI）通過，新增 24 組金鑰與完整 IV 向量，覆蓋分界兩側、無鹽、非 ASCII 與長密碼；其中 14 組短輸入另以 Python hashlib 獨立核對。該版 JAR SHA-256：`f11bc583f7331bd042b023a9d429c32bf210e3956c087f4707ee05fd0952f1bf`。

第二輪 RAR4 長密碼單 worker 優化 (historical local evidence; not distributed)利用排程回寫不依賴摘要狀態的特性，先建立原本每輪真正送入 SHA-1 的訊息，再批次更新摘要。第一個拷貝區塊不回寫、後續直接區塊回寫 `W[64..79]` 的規則保留；每輪先複製原始輸入，再模擬下一輪需要的改寫。所有輪數、counter、16 個 IV 快照和取消檢查均保留，應用層只用 1 個 archive worker，也沒有跨次金鑰快取。

長密碼版 136 項測試（含 GUI）通過，新增 32 組長輸入向量，連同原有共 56 組，覆蓋 128／192／256-byte 分界、不同 salt、混合 Unicode、無鹽與 provider 後備路徑。該版 JAR SHA-256：`5a4dec8790b51695f175bd7f560bf8b29835ac91f1a1216e105476db3988d554`。

第三輪 大型 PPMd／LZ 混合包單 worker 調整 (historical local evidence; not distributed)定位到共用 CRC32 的 CPU 熱點，改為扁平 slicing-by-16 與平衡 XOR 樹。CRC 仍覆蓋全部解出資料；PPMd、LZ、字典與篩選器未改動。新增起始偏移、邊界與增量 CRC 對照，最新 138 項測試（含 GUI）通過。大檔量測有明顯 I/O 波動，報告保留初始試驗資料、最終交錯組及純 CRC 診斷，分開評估 CPU 改善和完整操作時間。

最新 JAR SHA-256：`1f8da612ccd87e8cdf3d5285b88723cfb61e76dea8be2e5788b655b387ba7edb`。

有一個 Unix RAR4 測試名稱是原始 UTF-8，ReferenceArchive 在這臺 Windows 上產生替代字元／改名；此名稱以 libarchive 的明確預期 `abcdefghijklmnopqrsテスト.txt` 核對，內容雜湊仍來自獨立解壓結果。詳見測試包 [來源與雜湊](../tests/fixtures/rar/README.md)。

容器參考 [RAR5 官方格式](https://www.rarlab.com/technote.htm)，解碼語義參考 Alexander Roshal 的 [UnRAR 7.3.2 官方來源](https://www.rarlab.com/rar/unrarsrc-7.3.2.tar.gz)；BLAKE2s 核心依 [RFC 7693](https://www.rfc-editor.org/rfc/rfc7693)。本輪沒有取得 ReferenceArchive 的 C++ 原始碼，不能稱為逐行移植其程式碼。使用的是自行撰寫的 Java 解壓器，依參考解碼規則實作。

UnRAR 的版權與完整授權說明隨 JAR 的 `cn/shed4329/onezip/engine/RAR-NOTICE.txt` 發行；參考碼只用於解壓，不實作 RAR 壓縮。測試來源授權保留於 fixtures 目錄。
