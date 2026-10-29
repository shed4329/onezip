# 壓縮與解壓的執行緒設定

GUI的壓縮選項、解壓密碼／選項對話框都有「執行緒數」。CLI直接由Java args解析`--threads N`、`--threads=N`、`-t:N`。ZIP／7z的壓縮及解壓皆可用，列表與測試仍依序處理。

- 0：自動，最多8與可用處理器數的較小者。
- 1：單worker順序路徑。
- 2–64：請求的上限，會依Java heap及可獨立執行的工作數降低；不是承諾每次都會啟動指定數量。

既有`ArchiveEngine.compress(...)`與`extract(...)`保留相容並預設0；有新增末尾`int threads`的overload。ArchiveTask亦有同樣overload，GUI將選擇值傳到引擎。

## ZIP壓縮

沿用自己的 DEFLATE、CRC 與 AES 實作，現在支援跨檔案及大檔內部分段並行。每包共用一個有界工作池，worker 重用匹配器與 Huffman 工作區。小於 4 MiB 的檔案在有界分塊記憶體流產生 local record／payload／descriptor，主流程依原目錄順序合併並重寫中央目錄 offset，不再作中型檔案磁碟 spool。

待合併項目至多 worker 數，合併一個後就可補進下一個工作，不再等待整批。大於等於 4 MiB 的單檔改成約 1 MiB 的有界片段工作，保留 32 KiB 跨片段歷史；主流程輸出單一標準 DEFLATE 串流，再套用原有 AES 與 ZIP metadata。未加密 ZIP 的小檔仍與單 worker 相同；大檔的單 worker／多 worker 區塊邊界不同，不保證壓縮位元組相同，已用獨立 decoder 交叉驗證。多 worker 固定片段政策不隨 worker 數改變。原 ZIP32 限制仍適用。分塊結果直接寫出，不展平成另一個大陣列；詳見[自製流與 NIO 實驗](STREAM-IO.md)。

## ZIP／7z解壓

全包先做既有的路徑與衝突預檢。ZIP每個項目為獨立工作；7z每個folder為工作，內含solid substreams保持原順序。沒有資料串流的空項目另成一組。只建立最多worker數的消費任務，以共享索引取下一組，沒有按檔案數累積無界executor queue。

worker 共用已完成解析、唯讀的 metadata；ZIP 每個 worker 重用自己的 RandomAccessFile，7z 每個工作另外開 reader，不共用 seek 位置、字典、機率模型或加密狀態。每 worker 的記憶體預算會計入 7z coder 宣告的字典大小，預留 96 MiB 後再限制並行數。這是估計值，不是整個程序 RSS 硬上限。

7z若只有單個含資料的folder、至少8個不超過64KiB的小檔，並且預算大於1，也可啟用[小檔寫出流水線](SEVENZ-OUTPUT-PIPELINE.md)：呼叫者依序解碼及驗證，最多預算減1個worker並行建檔／寫出。payload與queue有界，大檔仍串流處理。這不會使同一字典內的解碼變成並行。

ZIP 在 worker 開始前先建立唯一目錄，每次寫出仍重新檢查父路徑，並用 CREATE_NEW 建立檔案；只有新輸出追蹤與進度 callback 使用短鎖。其他格式保留既有序列化建目錄流程。完成數保持單調，但檔名顯示順序可能與目錄順序不同。所有 worker 保留大小、CRC 或 AES 驗證。

一個工作失敗或主執行緒中斷時，取消其他工作、等待worker真正停止並關閉檔案，再執行既有輸出回滾；保留呼叫者中斷旗標與原先存在的檔案。解壓不會因失敗而退回原生或第三方引擎。

## 何時會加快

XZ／BZIP2 的編碼與解碼目前都循序，threads 不會增加這兩種 codec 的 worker。TAR.XZ／TAR.BZ2 解碼／校驗至私有 spool 並建立 TAR 索引後，可按獨立檔案平行寫出；指定 1 時保持一個 archive worker。見 [XZ-BZIP2.md](XZ-BZIP2.md)。

LZ4 frame／TAR.LZ4 的 codec 目前循序；`threads` 不會替它開 codec worker。TAR.LZ4 解碼、校驗並完成磁碟索引後，才可依獨立 TAR 檔案並行寫出；`threads=1` 保持單 worker。見 [LZ4.md](LZ4.md)。

TAR／GZIP／TAR.GZ 也接受相同參數。GZIP 壓縮按既有有界 DEFLATE 片段編碼；TAR 打包循序。GZIP 解碼循序並先校驗到私有磁碟暫存；TAR 及索引後的 TAR.GZ 可按檔案並行寫出，單個 gzip 資料串流無法因此並行解碼。詳見 [格式、暫存成本與實測](TAR-GZIP.md)。

RAR／RAR5 解壓同樣接受執行緒參數，以獨立檔案／solid group 為單位派工；同一 solid group 仍依序解碼。每個 worker 保留自己的字典與檔案游標。其支援範圍與預熱實測見 [RAR.md](RAR.md)。

多檔 ZIP、大檔 ZIP 壓縮或有多個獨立 folder 的 7z 通常有並行機會。單一 solid folder 的 7z 解碼、單檔 ZIP 解碼、非常小的包或磁碟受限工作可能沒有收益，甚至因排程變慢。ZIP／7z 跨片段匹配仍依賴先前字典，不能把壓縮片段當成獨立的解壓工作；不會為了解壓並行強制移除歷史。

`benchmarks/ArchiveThreadBenchmark.java`對同一份8 MiB／8檔資料比較1與4workers，兩輪反轉worker順序，每種操作／設定至少5秒且5次預熱，再記錄5次完整API；校驗與清理不計時。這是本機特定資料的擴展性測試，不是ReferenceArchive核心或任意資料的速度保證。此前90%報告 (historical local evidence; not distributed)保留當時JAR身份，不能當作後續所有變更的重新測量。

本輪完整結果與原始樣本 (historical local evidence; not distributed)：此測資4workers相對1worker的中位數加速，ZIP壓縮1.47倍、ZIP解壓3.01倍、7z解壓3.35倍。88項測試（含GUI）及發行codec依賴檢查通過。

與ReferenceArchive的多等級對照及圖片 (historical local evidence; not distributed)另外測了ZIP／7z、0／1／3／5／7／9級及五類資料。包含相同OneZip包／相同ReferenceArchive包的交叉解壓；Java只計預熱API，native仍計完整CLI。兩種包的folder與字典並不相同，不能把自身worker擴展倍數當成相對ReferenceArchive的倍數。
