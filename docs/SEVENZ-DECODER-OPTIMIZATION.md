# LZMA／LZMA2解碼熱路徑優化

本文記錄第一階段，只修改`LzmaInputStream`。7z容器、folder排程、字典大小、CRC、密碼驗證、壓縮器及既有`--threads`設定均沿用原實作。完整測量使用前一輪已凍結的60個7z包，不重建包、不犧牲壓縮率。後續已加入[小檔寫出流水線](SEVENZ-OUTPUT-PIPELINE.md)，其測量與JAR身份另外保留。

## 從CPU取樣定位

HotSpot JFR對舊版解壓ReferenceArchive L5混合資料取樣，最頂層約六成落在`Range.normalize`，其次是token解碼；CRC及字典拷貝所佔比例較少。取樣包含暖身及未計時驗證，不能將方法取樣比例當作某段原始碼精確耗時百分比，但足以決定先處理range／literal路徑。

## 採用的修改

- `range`與`code`改用32位元`int`保存無號狀態，以邏輯右移及符號位映射完成無號比較，保留原本模2^32溢位語義。normalization直接留在bit解碼路徑，避免每次都用64位元遮罩處理狀態。
- 一個literal的八個bit在區域變數中推進range、code及輸入cursor，完成後寫回一次。模型機率仍在原陣列中逐bit更新，沒有改格式或機率更新規則。
- 一般literal用無號減法的borrow mask選擇bit及更新狀態，減少資料相關分支。這個mask計算暫用64位元差值；並非宣稱整個解碼器完全沒有64位元運算。
- matched literal保留分支路徑。全面改成無分支曾使部分文字變慢，因此將兩種語義路徑分開，而非按檔名或測資切換。
- 輸入buffer至少還有8bytes時，literal使用區域cursor讀陣列；每個bit至多normalize一次，一個literal最多消耗8bytes。少於8bytes時回到原本的嚴格`nextByte()`路徑，LZMA1 refill與LZMA2 packed邊界檢查仍存在。沒有補零後容忍截斷資料。

這些修改沒有增加解碼器工作執行緒，也沒有把仍保留字典依賴的片段假設成獨立串流。

## 未採用的試驗

全面無分支literal對混合資料有收益，但文字與Canterbury初測退化；未納入。另試4KiB token批次及整段match輸出，收益不穩定，也未納入。試驗來源與原始timing保留在本輪報告中，避免只保留最好的一次。

## 驗證與速度邊界

新增獨立XZ產生的LZMA1／LZMA2資料，覆蓋lc/lp模型組合、pb0/pb4、4KiB字典繞回、單byte／7byte短讀、end marker及48種尾端截斷；沿用raw/model/property切換、solid分組、取消與損毀CRC測試。全套加GUI共91項通過。

正式測量在每個新JVM先用兩種包至少各15秒暖身；逐包再至少5次且1秒暖身，之後3次正式。每種版本各兩個獨立JVM，第二輪反轉版本與包順序；每組6次中位數。所有解壓內容逐次對照原始檔案SHA-256。ReferenceArchive另對完全相同的固定包重新計時，仍是含程序啟動的完整CLI；不能把相對值當作同邊界DLL核心速度。

完整結果、JAR身份及原始樣本 (historical local evidence; not distributed)另列每類／等級結果與記憶體。單一solid folder仍依序解碼，小檔建立等I/O成本也仍包含在API時間內。
