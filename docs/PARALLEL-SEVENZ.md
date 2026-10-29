# 純Java多worker 7z壓縮（2026-10-27）

GUI及既有ArchiveEngine API預設使用自動worker設定；CLI由Java讀取`-t:N`／`--threads N`，`0`自動、`1`為原有順序串流路徑，接受0–64。後續版本也已將此設定套用於ZIP壓縮及ZIP／7z解壓，GUI兩個對話框均可設定，詳見[統一執行緒設定](THREADING.md)。自動上限為8與可用處理器數的較小者，再依最大Java heap保守限制；明確指定數量也受到heap預算限制。沒有JNI、外部壓縮程序或壓縮庫委派，使用JDK執行緒與自己的encoder。

## 排程與資料順序

- 每個archive建立自己的worker pool，每個worker各自重用自己的LZMA workspace；不在worker之間共用機率、rep距離、hash heads或range coder。
- 大於等於4 MiB的單檔使用1 MiB工作片段，避免最後少於64 KiB的尾巴成為獨立小工作，尾段可略長於1 MiB。前方最多2 MiB原始資料隨任務一同提供，讓worker重建可用匹配歷史。
- 每個片段重設機率／rep狀態，輸出LZMA2模型／properties reset，後續片段不輸出dictionary reset。只有第一段初始化解碼字典，合併後只有一個終止碼。這容許worker獨立運算，又保留跨段資料匹配。
- 多檔案仍保留原先2 MiB有界solid分組及逐檔CRC。各folder可同時壓縮；適合的folder亦可切成至多4個內部片段，並不把單一檔案拆到不同7z folders。
- 輸出按提交順序等待future、寫入payload並記錄packed大小，完成快的後段不會先寫到檔案。待收集工作數限制在worker數內；超過時先收取最早結果，形成背壓，不載入整個大檔或累積無界輸出。

## 壓縮率與工作量政策

模型重設仍有品質成本。首64 KiB若呈現最多1024-byte的完全週期，改用原本的連續模型路徑；這類資料原本很快、模型也已很準，切段常得不償失。此偵測不能保證輸入後半仍呈週期，誤判會影響速度，但順序路徑仍無損且保留原品質。

小型solid folder至少128 KiB才考慮內部分工，以8-byte seed探測樣本：幾乎不匹配的資料、8-byte重複率很高的資料、或大量256-byte長匹配，維持完整folder模型。其餘按約96 KiB工作量切分、上限4份；這是本專案的實測政策，不是Ark原生排程的還原或任意資料的品質保證。沒有按測資名稱或副檔名切換。

並行片段的首4 KiB若控制碼很少，採標準LZMA2的`lc=3, lp=0, pb=0`；其他片段保留`pb=2`。這是byte特徵啟發式，不是文字編碼識別器；props隨片段寫入，不需要解碼器猜測。減少文字不必要的位置context，也減少切段後模型重學的壓縮率成本。單worker及受保護的完整folder保留原設定。測試特別驗證同一字典內pb0→pb2→pb0切換，並由XZ獨立還原。

matcher／DP另省去純重複工作：同長度價格列準備一次後讓relax迴圈直接取用；長度至少5時相同距離的distance-state共用價格；複合邊重用已計算的首token價格。沒有降低本輪前的候選深度、nice門檻或DP展開位置。21組raw輸出不變，用來區分這項等價優化與多worker模型重設的品質取捨。

達nice門檻的rep先返回，省去必定不會被採用的普通匹配搜尋；第一個DP位置不重算已知四個rep長度。片段歷史的4-byte鏈仍完整建立，2／3-byte heads則回掃只寫最新slot，減少覆寫相同head的工作；中間階段14類並行完整包逐位元相同。曾試4槽8-byte候選快取，但速度收益不穩、部分品質下降，沒有納入最後版本。

## 失敗、取消及資源

worker只寫自己的記憶體結果，不直接碰archive檔案。worker例外傳回主流程；等待被中斷時保留呼叫者interrupt狀態。取消／失敗會停止並等待worker，ArchiveEngine仍移除未發布的暫存檔。寫入失敗為黏性狀態，不能在失敗後再次finish製造成功；正常完成也會結束pool後才發布。

單worker保留原來位元流。並行片段數／folder分工可隨明確worker數及heap上限不同，壓縮位元組不保證跨這些設定相同，但內容／CRC／順序必須相同。不同worker完成先後則不影響同一設定的輸出。

## 速度目標與驗證

使用者最新接受的目標是HotSpot預熱後達到ReferenceArchive參考吞吐的90%。定義為`ReferenceArchive CLI中位秒數 / Java預熱完整API中位秒數 >= 0.90`。Java時間包含掃描、I/O、CRC及容器完成，不含JVM啟動；原生CLI仍含啟動與日誌，因此只能稱為本次參考目標，不能證明DLL純壓縮核心同邊界的90%。

最終預熱政策對所有14類一致：每類至少5秒且至少5次壓縮與未計時驗證，再記錄5次；另開第二個JVM反轉資料順序，合計每類10個正式樣本取中位數。先前每類只預熱3次的結果另外保留，避免短文尚未穩定的測量與較長預熱混用。固定預熱時間仍不是每個方法均達C2峰值的證明。

本輪實測 (historical local evidence; not distributed)列出每類達標狀態、單worker與多worker、壓縮率及記憶體；保留失敗試驗。新增測試涵蓋跨段歷史、非對齊flush、raw切換、尾段、solid分組、排序／CRC、worker失敗、取消與CLI參數。獨立XZ／Commons／ReferenceArchive只用於驗證，不進入發行codec。
