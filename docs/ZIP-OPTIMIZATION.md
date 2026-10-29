# ZIP 優化與證據範圍

2026-10-28。繼續使用 HotSpot 21，壓縮、解壓與容器均為自行實作 Java；沒有 JNI、外部壓縮程序、壓縮庫或 JDK Deflater/Inflater 委派。測試與測速對照可使用獨立工具。

本文件記錄第一輪動態碼表／跨區塊歷史優化（JAR `ee2a0ba3...`）。後續的連續派工、自製公開流、移除中型檔磁碟暫存及 NIO 對照見 [STREAM-IO.md](STREAM-IO.md)；下列第一輪架構與數據保留作對照。

## 按差距處理

本輪把「差距」定義為剩餘吞吐落後 `max(0, 1 - OneZip吞吐/ReferenceArchive吞吐)`。因兩者處理相同原始 bytes，比值等於 `ReferenceArchive秒數/OneZip秒數`；它和「多花百分之多少時間」不同。大於 30% 先對照 ReferenceArchive 實際產物與熱點；10–30% 依具體工作量調整；小於 10% 查研究方法，保留實測有益且符合標準的改動。這是優化優先級，並非每個檔案都達到同一速度的保證。

## 先確認舊實作缺了什麼

REA 對凍結的 ReferenceArchive ZIP 做靜態檔案盤點；另外用本專案的 RFC 1951 位元流檢視器解析 payload。等級 5 的 8 MiB 文字包，ReferenceArchive 有 128 個 dynamic Huffman 區塊、63 個零長 stored 區塊及 61,689 個跨區塊 match；舊 OneZip 有 130 個 fixed 區塊，跨區塊 match 為零。這直接指出動態碼表與延續字典是舊版缺項。

空 stored 區塊可用來對齊非最終 DEFLATE 片段，屬標準格式內的組合方式。從輸出觀察到分組大小，不能證明 ReferenceArchive 的內部排程器、worker 數、match finder 或 parser 完全相同；本輪沒有聲稱還原其原生 ZIP 呼叫圖。

## 第一輪的壓縮路徑

- 每區塊最多 65,535 bytes，保留前區塊最後 32 KiB 字典。
- hash-chain 搜尋深度有界，等級 5–9 加一步 lazy parsing。正常等級以絕對位置和 32 KiB 環狀鏈保留索引，避免每區塊重新插入整個歷史。超長串流會重新定位，避免 Java int 位置溢位；快速等級保留原有稀疏索引的 prefix 重建政策。
- 統計 token 頻率，自建 Huffman 樹、碼長與反轉 canonical code。逐區塊比較 fixed、dynamic（含 code-length RLE/header）及 stored 的 bit 成本。極端頻率的碼長縮限採縮放頻率重建，尚非最佳 package-merge。
- 每包一個有界 worker pool，每 worker 重用工作區。小於等於 1 MiB 的檔案用有界記憶體片段，省去逐小檔磁碟 spool；中型檔案仍可 spool。
- 至少 4 MiB 的單檔，按約 1 MiB 分段並行。每段帶入前 32 KiB 歷史，輸出非最終區塊並以空 stored 對齊；主流程按序合併，再寫最終結束碼。不同 worker 數使用相同片段政策，結果可重現；單 worker 與多 worker 不要求壓縮位元組相同。
- FIFO 待完成片段不超過 worker 數；取消與失敗時先停止並等待 worker，再清理暫存。原 ZIP32 寫入限制不變。

滾動索引改動的五類等級 5 包，與第一階段動態碼表／跨區塊版逐位元組一致，沒有透過減少搜尋深度換速度。

## 第一輪的解壓路徑

JFR 指出文字包的壓縮輸入逐位元組讀取與 bit 讀取是熱點。新增 16 KiB 自有輸入緩衝，避免每個壓縮 byte 經過同步的 `BufferedInputStream.read()`；stored 區塊同樣消費已預讀資料，結束時檢查尚未使用的預讀 bytes，避免隱藏尾端垃圾。

canonical Huffman 使用 9-bit 首層，長碼只為實際出現的 prefix 配置子表，不再讓所有長碼走逐位元迴圈。短尾端仍檢查實際可用 bit 數，無效碼及截斷保持拒絕。獨立檔案並行解壓時，worker 重用自己的 reader，避免每個小檔重開一次 archive；預建唯一目錄，但每次輸出仍重新驗證父路徑並使用 CREATE_NEW。

## 論文取捨

[Nekritch，Decoding of Canonical Huffman Codes with Look-Up Tables](https://theory.cs.uni-bonn.de/ftp/reports/cs-reports/1999/85209-CS.pdf) 討論短 prefix 表、長碼附加表，以及表大小和建表時間的取捨。本輪的兩層表遵循這類原理，沒有照搬整個論文演算法。

[Klein 與 Klein，Fast decoding of prefix encoded texts，DCC 2005](https://u.cs.biu.ac.il/~tomi/Postscripts/dcc05.pdf) 討論部分表及一次還原多個碼字的速度／空間取捨。沒有直接把它套成多 literal 解碼：DEFLATE 同一棵樹包含長度與結束符號，後面還可能接距離與 extra bits；每區塊建表成本也必須計入。這個方向保留為後續候選，不能拿論文的其他格式速度當作本程式收益。

也檢索了近期研究。[Zavadskyi/Kovalchuk 2025](https://bphm.knu.ua/bphm/article/view/532) 研究詞彙級大字母表的 canonical 解碼與 SIMD／查表混合；其工作負載與 DEFLATE 最多 286 個 literal/length 符號不同，論文速度不能直接套用。[Żukowski，PivCo-Huffman，2026-06](https://marcinzukowski.github.io/pivco-huffman/paper-1.0/ph.html) 把位元資料改成按樹節點分組的 bitmap 以利 SIMD。由這個資料佈局可推知，直接替換目前 entropy payload 會失去標準 DEFLATE 相容性，因此本輪沒有採用。

壓縮 parser 另參考 [Ferragina/Nitto/Venturini，On the bit-complexity of Lempel-Ziv compression，SODA 2009](https://pages.di.unipi.it/rossano/assets/pdf/papers/SODA09.pdf)：以加權圖最短路徑處理輸出 bit 成本，說明最長 match 不必然使位元數最少。但目前動態 Huffman 的價格又由區塊 token 頻率決定，不能直接把固定價格模型的最適性結論當成本程式證明。這輪先保留有界 lazy parser；後續若做成本 parser，需另外比較頻率估計／DP 的時間、記憶體和包大小，不能只根據理論最適性宣稱更快。

沒有改用 ANS、改 Huffman 規則或另創壓縮格式，因此 ReferenceArchive/JDK 等標準 decoder 可讀。

## 驗證與測量

回歸測試包含獨立 JDK/Zip4j 解碼、動態樹、stored 後延續歷史、環狀／重疊複製、多 worker 固定輸出、AES、取消及預讀尾端拒絕。完整實測以凍結舊 JAR、新 JAR 和 ReferenceArchive 7.47 Beta 對照六個等級、五類來源；所有解壓結果與原始檔逐檔 SHA-256 比對。

Java 僅計預熱後完整 API；ReferenceArchive 計新程序完整 CLI，含程序啟動。這個邊界對小檔／短工作影響較大，不能推論 DLL 純核心同速。相同等級數字也不保證內部參數相同。詳見 完整結果 (historical local evidence; not distributed)。
