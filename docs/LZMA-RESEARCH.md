# LZMA2 速度／大小取捨研究（2026-10-27）

目標是在HotSpot上維持自行實作的Java LZMA2與標準7z互通，保留上一輪大部分文字／小檔案改善，降低編碼時間。研究結果分為可直接實驗的成熟方法與需要新架構／格式的近期研究，不能把其他codec、原生程式或多執行緒的倍數套到本專案。

## 原始來源與適用性

- [XZ/liblzma官方選項與註解](https://github.com/tukaani-project/xz/blob/master/src/liblzma/api/lzma/lzma12.h)：`nice_len`決定找到足夠長匹配後停止搜尋，仍編碼該匹配的實際長度；`depth`限制每位置候選數。兩者直接適用本encoder，屬成熟工程方法。本輪前Java的parser有nice提早返回，但匹配器本身仍搜尋至273或depth上限，工作量並未一起停止。
- [Fast LZMA2作者專案](https://github.com/conor42/fast-lzma2)：以parallel buffered radix matcher處理匹配搜尋，作者報告高等級較7-Zip預設快20–100%，預設字典下包大小常多1–5%；這些是該原生實作的自報結果。演算法仍可產生LZMA2，但其block／字典／執行緒架構不同，不能以載入該庫替代自己的Java實作，也不能推定我們可獲同樣倍數。適合作為後續大幅替換matcher的方向。
- [ESA matchfinder作者專案](https://github.com/IlyaGrebnov/esa-matchfinder)，2025-09版本更新：利用suffix array、LCP與區間樹，取代逐位置深鏈搜尋。作者實作是block-based、額外空間約12倍輸入、預設匹配2–64 bytes；這對目前2 MiB滑動字典、273-byte匹配與JVM記憶體並非直接替換。後續若自行實作，必須連同建索引成本、跨block歷史與峰值記憶體一起測。
- [Dinklage，SEA 2026：Efficient Large-Scale Text Precompression via Approximate LZ77 Parsings](https://drops.dagstuhl.de/entities/document/10.4230/LIPIcs.SEA.2026.16)，2026-06-15：以approximate LZ77、prefix-free parsing及共享記憶體平行化先處理長距離重複，再交標準壓縮器。本研究主要針對數十GB高度重複資料；新precompression pipeline與目前一般7z資料流不同，不能將其輸出直接冒充標準LZMA2。保留作大型備份／資料去重架構方向，本輪不加入新解碼依賴。
- [Wu，Improved LZ77 Compression with Match-Length-Dependent Sliding Windows](https://arxiv.org/abs/2610.06530)，2026-10-05預印本：短匹配用較小視窗、長匹配保留遠距離歷史，並分析搜尋與編碼成本。文中若干收益依賴新的token碼及特定來源／精確搜尋假設；不是對既有adaptive LZMA parser的無損品質保證。可啟發分層候選或不同長度的搜尋預算，但不能只砍遠距離匹配就宣稱已移植論文或保證更小。
- [Ferragina、Nitto、Venturini：Bit-Optimal Lempel-Ziv compression](https://arxiv.org/abs/0802.0835)：提醒「最少phrase」不等於最少bit，支持保留價格決策而非全面退回最長匹配。其變動碼長模型不能直接證明我們含自適應模型與四個rep距離的有界DP最優。
- [Farruggia等：Bicriteria data compression](https://epubs.siam.org/doi/10.1137/1.9781611973402.115)：把空間與**解碼**時間作雙目標；本輪主要瓶頸是**編碼**時間，不能把該論文當作減少encoder搜尋即有最優保證的依據。

以上均查閱原作者、官方程式碼或出版社／arXiv原始來源；檢索日期2026-10-27。近期預印本與成熟工程方法的證據強度不同，未把發表日期新當成較適用的保證。

## 本輪先驗證的假設

1. 保留state／四rep／複合邊／回溯，只降低匹配候選預算，是否能保住大部分壓縮率？
2. 縮短DP展開長度是否值得，還是使成本估計／切點更差？
3. 讓matcher本身在足夠長匹配後停止，再由parser提早提交，是否比只調depth更有效？

隔離原型由`benchmarks/research_parser_tradeoff.py`產生，編譯到target獨立目錄，classpath overlay載入自己的Java類別；沒有將第三方壓縮器連結進產品。先用五類固定資料篩選，再用全部資料及額外未參與篩選的輸入驗證。初步以相對上一輪完整包增幅約3%為篩選目標，這是實驗政策而非對任意資料的保證。最終採用方案、失敗試驗及實測見本輪報告 (historical local evidence; not distributed)。

八組原型後，固定深度16在初步五類資料的最大增幅約2.3%，但新增中文組增大7.23%，因此進一步採保守byte前綴預算：四個byte皆為ASCII時限制深度16，含高位元組或短尾時保留原深度64。這只是實測導出的啟發式，並非UTF-8識別器或論文的品質保證。原中文組已參與策略選擇；最後另以seed130813產生不同內容／邊界的中文與雙語兩組，避免將已看過的中文資料冒充未見驗證。等級6–9保留原深度，沒有加入以副檔名或測資名稱切換的邏輯。

另外先檢查複合尾端是否至少匹配兩個byte，再計算前段與中間literal價格，省去大量必定失敗候選的機率樹遍歷。這項純工作量調整與同預算、原價格順序的21組raw向量逐位元一致；調整深度本身則刻意改變部分輸出，兩種效果分開驗證。
