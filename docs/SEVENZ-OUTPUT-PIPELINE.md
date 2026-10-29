# 單solid folder的小檔寫出流水線

7z共用字典仍依目錄順序解碼。這輪讓解碼與小檔案建檔／寫出重疊，不把LZMA2模型reset誤認成獨立字典，也沒有改動壓縮包或省略CRC。

## 啟用與資源

只有一個含資料的folder、至少8個非目錄項目不超過64KiB，且執行緒預算大於1時啟用。沿用GUI／CLI的執行緒設定與heap限制；`--threads 1`保留原順序路徑。若預算為8，呼叫者解碼，最多7個worker寫出。其他多folder或大檔案工作仍沿用既有路徑。

producer逐個讀完小檔的宣告大小，再讀到substream EOF、完成既有大小／CRC／folder檢查，才將獨立byte陣列交給writer。碰到大檔時先排空待寫工作，再用原串流直接寫出，避免把大檔放進heap。

待完成工作上限等於writer數，每個payload最多64KiB；producer另有一個當前payload。Queue與future清單均有界，滿時先等待最早工作。這不是把整包讀進記憶體，也不會累積與檔案數成比例的payload。

## 路徑、進度與失敗

先完成全包路徑／衝突預檢，再由呼叫者依序建立唯一父目錄。writer寫檔前仍檢查完整父路徑，使用CREATE_NEW拒絕覆蓋；沒有快取跳過每檔的父路徑安全檢查。只有新建項目追蹤清單與進度累計會短暫鎖定，檔案I/O不放在全域鎖內。

進度只累加已完成寫出的bytes，callback序列化；檔名顯示順序可與目錄不同。所有writer完成、整個串流驗證完成後才通知「完成」。

producer或writer任何一方失敗、取消或中斷時，先取消待做工作，停止並等待writer關閉檔案，再由ArchiveEngine回滾新建輸出。保留原先存在的檔案及呼叫者中斷旗標。

## 驗證與實測

新增測試涵蓋單solid串流的多writer、大小檔交錯、零byte及65536byte邊界、單執行緒回退、單調進度、writer callback例外、取消、後段substream CRC損毀，以及join後回滾／原檔保留。完整回歸加GUI共94項通過。

最終JAR與ReferenceArchive的暖解壓對照 (historical local evidence; not distributed)使用固定60個7z包、六個壓縮等級與兩種包來源。基準是已發布的第一階段解碼優化JAR；兩版解碼熱路徑相同，分辨本輪寫出流水線的收益。Java計預熱API，native仍計完整CLI（含啟動），因此不是同邊界的DLL核心比較。

初測不能當作正式結果或任意資料保證。小檔受目錄層級、磁碟、即時掃描與背景負載影響，大檔與多folder也不會自動得到這條小檔路徑的收益。
