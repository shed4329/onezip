# 純 Java LZ4 frame／TAR.LZ4

CLI 與 GUI 支援 `.lz4` 和 `.tar.lz4` 的建立、列出、完整測試與解壓。使用本專案自行實作的 byte-oriented LZ77 matcher／literal-match decoder 及 incremental XXH32；沒有壓縮庫、JNI、DLL 或原生子程序。格式依據官方 [LZ4 frame](https://github.com/lz4/lz4/blob/dev/doc/lz4_Frame_format.md)、[block format](https://github.com/lz4/lz4/blob/dev/doc/lz4_Block_format.md) 及 [XXH32](https://github.com/Cyan4973/xxHash/blob/dev/doc/xxhash_spec.md) 規格，生產程式碼未委派測試對照引擎。

```powershell
java -jar target/onezip-1.01-all.jar --cli c report.txt.lz4 report.txt
java -jar target/onezip-1.01-all.jar --cli c backup.tar.lz4 folder --format tar.lz4
java -jar target/onezip-1.01-all.jar --cli x backup.tar.lz4 -o restored --threads 1
java -jar target/onezip-1.01-all.jar --cli l backup.tar.lz4
java -jar target/onezip-1.01-all.jar --cli t report.txt.lz4
```

單檔 LZ4 frame 不攜帶原始檔名，解出名稱取自 archive 去掉 `.lz4`；建議使用 `<原始名稱>.lz4`。多檔案、資料夾、Unicode／長檔名及空目錄使用 TAR.LZ4，保留既有 TAR metadata 與路徑檢查。兩種格式都沒有密碼加密。

寫入標準 frame v1，使用獨立 256 KiB blocks、可用時的 content size、header checksum、每 block checksum 與完整 content checksum。匹配器以 4-byte hash 找最近候選，距離不超過 65535，輸出標準 token／長度延伸／offset；保留最後 5 個 literals 與最後 match 起點距結尾至少 12 bytes。不能有效縮小的 block 依標準 uncompressed bit 原樣儲存。等級 0 僅儲存；1–9 目前共用快速 greedy matcher，尚未提供 LZ4 HC，不應把等級 9 當作 HC。

讀取支援 64 KiB／256 KiB／1 MiB／4 MiB block maxima、獨立與相依 block、stored block、可選 content size、header／block／content XXH32、串接 frame 與 skippable frame。相依 blocks 使用前面解出內容的最後 64 KiB，並支援重疊 match copy。拒絕無效 offset／長度、越界、保留 flags、錯誤 checksum／content size、截斷及尾端雜訊。沒有可選 checksum 的外部 frame，當然無法保證偵測所有 payload 改動；本專案 writer 一律寫入 block 和 content checksum。

目前不讀寫 legacy LZ4、無容器的 raw block、Hadoop／其他私有 LZ4 容器或需要外部字典的 frame；這些輸入會明確失敗。解碼記憶體受宣告的最大 block 限制，最大約為 packed 4 MiB 加 decoded 4 MiB／64 KiB history，沒有按整個 content size 配置 RAM。

LZ4 壓縮／frame 解碼目前循序執行，接受既有 threads 參數但不會開啟 codec worker；TAR.LZ4 在串流解碼並完整校驗到私有磁碟 spool、取得 TAR 索引後，可按檔案平行寫出。`--threads 1` 全程只有一個 archive worker。需要等於解出串流大小的暫存磁碟空間，成功／失敗／取消皆關閉並移除 spool。`l` 也需解碼，`t` 不建立目的檔案但會使用這份私有暫存。這與 GZIP 的索引策略相同。

公開 `Lz4InputStream`／`Lz4OutputStream` 可用於 Java 串流。OutputStream 的 expected size 可為 -1（未知），finish 保持目的 stream 開啟，close 會結束 frame 並關閉目的 stream；輸入大小已知時會檢查一致性。輸入 close 關閉來源，完整校驗需讀至 EOF。ArchiveEngine 操作會完整讀取並核對，沿用 staging、不覆蓋、取消回滾及輸出路徑檢查。

`Lz4Test` 用僅限 test scope 的 Commons Compress frame decoder 驗證本專案輸出，用其 encoder 產生獨立輸入；所有標準 block maxima 與 checksum 設定均有覆蓋。另以手工定義的 linked frame、獨立 Commons Codec XXH32 和另一個解碼器驗證「大 block 尾端字典」，避免讓同一實作的錯誤互相掩蓋。測試中發現 Commons Compress 1.28 的 frame writer 在 linked block 大於 64 KiB 時保留起始而非尾端字典；因此該 writer 的 linked oracle 使用不超過 64 KiB 的區塊，較大的跨 block 正確性以明確 frame fixture 驗證。production 不包含這些對照類別。

測試也涵蓋 0／1／4／5／12／13 bytes、長度延伸、block 邊界、不可壓縮與重複內容、串接／skippable／空 frames、header／block／content checksum 損壞、content size 不符、外部字典拒絕、非法 offset／長度、截斷、CLI 別名與 GUI TAR.LZ4 建立／解壓。這輪未承諾或量測 LZ4 相對 ReferenceArchive 的固定速度；60% 專項是既有 TAR／TAR.GZ 單 worker 的重測。
