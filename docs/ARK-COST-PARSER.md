# Ark 成本決策與 Java parser（2026-10-27）

等級5–9已接入自行實作的有界成本搜尋；等級1–4保留greedy、0只儲存。HotSpot／C2維持預設，不使用JNI、DLL、外部壓縮器或壓縮庫。這次也修正大量小檔案的未壓縮目錄標頭。實測與原始資料 (historical local evidence; not distributed)分別列出包大小、主要資料流、metadata、暖API及完整CLI耗時。

後續[平衡版研究](LZMA-RESEARCH.md)已對預設等級5的ASCII前綴限制深度16，含高位元組的前綴保留原深度64，並省去無效複合尾端的價格計算；等級6–9仍保留原深度。這些是本專案依品質／速度實驗採用的政策，未宣稱來自Ark的原生level設定。最新實測 (historical local evidence; not distributed)與此頁初次成本parser結果分開保留。

## 原生指令觀察

目標 `ark.x64.dll` 7.47.0.1，SHA-256 `868a4b870f43024688d4e9c74d9c4e42bc7f4e439972a93059ee95f4a2dbb135`。REA檔頭證據只支持目標身分，parser語意來自GNU objdump指令的人工分析；新擷取的每段機器碼均與目前DLL的PE區段比對。沒有配置Ghidra／IDA，未宣稱自動反編譯或動態命中這些分支。保留的SDK 23.01原始碼僅協助語意命名，不能據此確定Ark使用該SDK版本。

- `0x1800fd672–0x1800fd6b6`：128格整數價格，格中點 `(i<<4)+8`，四輪平方／正規化，最後 `0xa1-count`；16單位約等於1 bit。bit0查 `table[prob>>4]`，bit1查 `table[(prob^0x7f0)>>4]`。
- `0x1800fb190–0x1800fb204`：matched literal累加八次價格，不對假想路徑修改模型。
- `0x1800fc109–0x1800fc26f`：literal + rep0；尾端儲存rep長度、code0、extra1。
- `0x1800fc3fd–0x1800fc5e5`：rep + literal + rep0；extra為首rep長度+1，保留首rep編號。
- `0x1800fc7ac–0x1800fc9aa`：match + literal + rep0；首code為distance-minus-one+4。
- `0x1800fcaac–0x1800fcb6d`：32-byte節點的extra/length/code回溯，展開複合邊。普通relax使用嚴格較小成本；終點掃描 `0x1800fca2f–0x1800fca6f` 同價偏向較後終點。
- `0x1800fd0e0` 是alignment價格；`0x1800fd1ec` 是distance／slot價格。舊手寫偽代碼曾將兩者標籤對調，本次已更正並保留更正註記。主迴圈 `0x1800fe1e3–0x1800fe254` 對match計數64及rep計數有不同刷新處理。

以上是人工映射的靜態推論；原生每個level及參數覆寫的實際執行路徑仍未逐一動態驗證。完整指令、身分與位元組比對位於報告的evidence子目錄。

## Java 落實的決策

每次規劃凍結目前機率模型，計算整數價格與長度／距離快取。每個位置保存一組最低成本、state、四個rep距離、前驅及至多三段token；競爭路徑以較低成本更新，回溯後依序寫入range coder並更新真正模型。

除literal、short-rep、四個rep與普通match外，明確加入上述三種複合邊。若只逐位置保留一條路徑，某條前段稍貴的literal可能被淘汰，連帶失去隨後低價rep；複合邊讓這個整體選項可以直接競爭。兩位元組head、三位元組候選與四位元組hash鏈提供短匹配及長度／距離前沿。

為限制工作量，每次最多展開256個位置，但允許整個match／複合尾端自然越過256；選定終點後完整提交，才檢查packed預算，未展開尾端補入索引。這避免在第256 byte硬切token。nice門檻為level5/6的32及level7–9的64，可提早回溯並提交長匹配。這些是本專案的有界設計，未等同原生2048節點工作區。

JFR確認每位置的hash鏈候選搜尋是主要熱點。重複價格旗標已外提，候選陣列只清除可能缺失的長度2／3，其餘前沿逐格覆寫；這些純工作量調整以21組新parser輸出的SHA-256一致驗證。它們沒有解決成本搜尋帶來的全部吞吐損失。

## 小檔案的另一個瓶頸

先看容器再判斷parser：舊small-files包16,600 bytes中，資料流4,171 bytes、raw目錄標頭12,397 bytes，另有32-byte signature。parser初版將資料流降至2,884 bytes，但raw標頭仍占主要空間；ReferenceArchive同批使用type0x17 encoded header。

`SevenZWriter` 現在將至少1 KiB的目錄標頭交給同一套自製LZMA2、使用獨立重設串流；只有壓縮payload加descriptor確實較小才採用。level0與微小標頭維持原形式。PackInfo指向主資料後方的metadata payload，next-header改指向descriptor並重新計算CRC。1 KiB門檻、LZMA2方法及收益判斷是本專案政策，未宣稱等同Ark標頭編碼器。單元測試涵蓋多檔Unicode名稱、空目錄／空檔、metadata-only包及損壞標頭，並用Commons獨立解碼。

## 尚存差異

matcher仍為hash-chain；未完整移植binary-tree候選、原生2048節點、全部價格刷新時機、solid排序／分組及多執行緒排程。單個節點只保留一組state／reps也不代表全域最優。新增測資及歷史向量用來守住品質與互通，不保證所有小檔案都變小：兩個4,097-byte歷史向量分別多3／2 bytes，測試明確允許固定小額誤差且限制總量不增。速度與壓縮率必須一起看最新報告。
