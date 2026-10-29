# 從編碼決策理解 Ark，並補上 solid 寫入

此頁記錄solid改進那一輪的證據與狀態，下文「目前」指該輪。後續已加入連續字典與自適應分塊 (historical local evidence; not distributed)，以及[有界成本parser與encoded header](ARK-COST-PARSER.md)。最新頁明確區分已實作的複合路徑與尚未完整移植的Ark政策。

這輪保留 HotSpot／C2，針對兩個可分開驗證的問題前進：Ark 如何挑 literal／rep／match，以及為何我們壓縮相似小檔案的品質特別差。第一項是靜態指令研究，第二項已落實為 Java 修改。**完整 optimal parser 尚未移植。**

## 證據範圍

目標仍是本機 ReferenceArchive 7.47.0.1 的 `ark.x64.dll`，SHA-256：`868a4b870f43024688d4e9c74d9c4e42bc7f4e439972a93059ee95f4a2dbb135`。

REA 本輪確認身分與 GNU objdump 檔頭，Evidence ID 為 `ev_cee813d33f5da9f8ddc00d088891db4ead3885640ab22543cb2b82da4ff50993`。此 ID **只支持檔案身分與檔頭，不支持下述每項語意判斷**。本機沒有配置 Ghidra／IDA，所以反編譯呼叫回傳 provider_unavailable；後續使用既有 GNU objdump 與人工閱讀指令，不把人工偽代碼稱為反編譯器輸出。

完整指令與校驗保存於工作區 `referenceArchive-analysis/ark-deep/main-loop/decision-study`。`check_instructions.py` 將五份指令記錄內的每段機器碼與現有 DLL 比對；這能核對分析沒有引用別版位元組，不能自動證明語意正確。下文語意名稱參照既有的 7-Zip 23.01 `LzmaEnc.c`，不是 DLL 自帶符號，也未證明原版等同該版本原始碼。

## 一、原版確實有兩條 token 選擇路徑

`0x1800fd943` 檢查 encoder `+0xcc`：非零呼叫 `0x1800fcb98`；否則在既有決策耗盡時呼叫 `0x1800fb754`。其餘時候直接消費已保存的 token 序列。這說明「搜尋候選／選擇 token／輸出 range bits」是不同層次。

設定也能接起來：

- `0x1800fa774` 正規化 props。`0x1800fa81d–0x1800fa830` 在 algo 尚未指定時，設定 `algo = level >= 5 ? 1 : 0`。
- `0x1800fa834–0x1800fa848` 在 fast-bytes 尚未指定時，使用 `level < 7 ? 32 : 64`。
- `0x1800faeb0` 複製 props 並呼叫上述正規化，再於 `0x1800faf5a–0x1800faf76` 寫入 `encoder.fastMode = (algo == 0)`；`+0x48` 是被限制在 5–273 的 fast-bytes 門檻。

所以，**沿這條設定路徑、沒有顯式覆寫 algo 時，level 5 會選正常成本路徑**。這是條件式靜態推導；未在本輪用斷點觀察 CLI 實際走入哪條分支，不能宣稱所有等級 5 呼叫都如此。

## 二、正常路徑是有界成本搜尋，不是單純選最長

`0x1800fb754–0x1800fcb97` 的主要觀察：

- `0x1800fb7b6` 將單次匹配上限限制為 273；四個 rep 距離從 `+0x50..+0x5c` 讀取。
- `0x1800fb86d–0x1800fb8be`：rep 或一般 match 已達 fast-bytes 門檻時可提早返回，不必完成整張成本表。
- `0x1800fb94f–0x1800fba48`：使用 `+0x820` 的價格表、機率右移四位後查表，累加 literal／isMatch／isRep／short-rep 等成本。
- `0x1800fbb77–0x1800fbba2` 與 `0x1800fbcd1–0x1800fbd01`：候選總價低於既有值才寫入選擇，包括長度及距離碼。
- 決策項目以 `encoder + 0xf578 + 32*i` 存取。項目內可對應 price（+0）、state（+4）、複合路徑標記（+6）、length（+8）、distance code（+12）、四個 rep（+16..+28）。欄位名稱為人工映射。
- `0x1800fc01e` 使用 `0x7ff - cur` 限制剩餘候選範圍；`0x1800fbd32` 在 cur 達到 `0x7c0` 時進入收尾。這與 2048 項決策工作區、保留尾端餘量一致，不是整個檔案的全域最優搜尋。
- `0x1800fca8f` 用 `0x40000000` 作不可達／無限成本；`0x1800fcaac–0x1800fcb6d` 回溯長度、距離與複合路徑，保存可供主迴圈消費的決策。

可讀骨架如下。這是有標明省略項目的教學偽代碼，**不是已完成、可替換原版的實作**：

```text
choose_normal(encoder, position):
    matches = read_or_reuse_match_candidates()
    rep_lengths = compare_the_four_recent_distances(max_length=273)
    if best_rep.length >= fast_bytes: return best_rep
    if best_match.length >= fast_bytes: return best_match

    opt[0] = current_state_and_recent_distances
    opt[1] = literal_price_at_current_context
    relax_short_rep_if_cheaper(opt[1])
    relax_initial_rep_and_match_lengths()

    for cur within the bounded lookahead workspace:
        read_next_match_candidates()
        derive_state_and_rep_distances_from_selected_predecessor()
        relax literal / short_rep / each available rep length
        relax ordinary match lengths with length + distance prices
        consider compound paths containing literal and a later rep
        # Composite-transition details still need individual reconstruction.
        # No real output bits are emitted by this abstract relaxation step.

    backtrack_selected_path()
    save_remaining_tokens_for_the_outer_encoder()
    return first_selected_token
```

其中 `relax` 的意思是：如果「到目前位置的成本＋這個 token 的成本」比較小，就更新目的位置的選擇。價格是根據當前模型與快取價格表估算，不等於完整模擬每條候選路徑的所有機率更新，因此不能把它稱為數學上全域最佳壓縮。

## 三、快速路徑也不等於我們目前的 greedy parser

`0x1800fcb98–0x1800fce6e` 可讀出這些具體規則：

- 四個 rep 任一達 fast-bytes 門檻就提早採用；長 ordinary match 也可以直接返回。
- `0x1800fccda–0x1800fcd03`：若較短 match 只少一個 byte，而較長 match 的距離大幅增加，可能退回較短且較近者。比較形式對應 `bigDistance >> 7 > smallDistance`。
- `0x1800fcd0f`：長度 2、distance-minus-one 至少 `0x80` 的普通 match 會被捨棄。
- `0x1800fcd18–0x1800fcd60`：rep 可比普通 match 短 1 byte；普通距離較大時還可短 2／3 bytes，距離門檻為 `0x200`／`0x8000`（這裡是 distance-minus-one）。
- `0x1800fcd7e` 會先讀下一位置的候選。當下一位置能給出更合適的長度／距離組合時，先返回 literal；後面還測下一位置能否開始較長的 rep。

我們現有 Java encoder 沒有這套完整的距離取捨與 lookahead，更沒有上面的成本工作區。兩邊能寫出合法 LZMA2，不代表選出了相同 token；這是目前仍需完善的核心差異。

## 四、本輪先落實的改進：跨檔案共享歷史

用 test-only Commons Compress 1.28.0 實際解析並完整解碼同一批 256 個小檔案的壓縮包，可觀察到：

- 原版 ReferenceArchive 輸出：1 個 folder、256 個 substreams；這份包的 LZMA2 字典宣告為 1 MiB。
- 舊 OneZip：256 個 folders，每個只有 1 個 substream；宣告字典為 2 MiB。

這證明本例的大字典宣告不是決定性優勢：**舊版每換一個檔案就丟掉可用的跨檔歷史**。此結論來自包內結構與實際還原，不是單憑壓縮包大小猜測。

`SevenZWriter` 現在讓相鄰非空檔案共用一個 LZMA2 encoder，累計最多 2 MiB 後換組；空檔／空目錄不占 substream。大於 2 MiB 的單檔仍獨立串流編碼，不把單檔拆到不同 folders。容器補上 substream 大小與逐檔 CRC，保留 folder CRC。

**2 MiB 分組是我們配合現有字典限制所做的明確策略，不是宣稱還原了 Ark 的分組／排序預設。** 沒有整包讀進記憶體，也沒有改動 LZMA token encoder。單檔輸出與 21 組既有 raw LZMA2 向量應保持一致；多檔 7z 位元組則是刻意改變。

本輪測速與獨立驗證 (historical local evidence; not distributed) 保留輸入與產物 SHA-256、HotSpot 設定及測量邊界。參考解碼庫只存在測試／benchmark，發行引擎仍自行實作。

## 還沒完成的部分

- 正常 parser 的所有複合轉移、價格表刷新與 tie-break 尚未逐分支完整移植。
- matcher 的 binary-tree／hash-chain 選擇與每個候選生成過程仍需追查。
- 尚未動態驗證每個壓縮等級的實際路徑及全部參數覆寫。
- 尚未還原原版 solid 分組、檔案排序、多執行緒區塊與記憶體政策；本輪不宣稱 bit-exact 克隆。

下一個直接針對「相同資料為何選出不同 token」的工作，是完成價格／候選 trace 對照，逐段替換目前 greedy 決策，而不是再用執行環境差異解釋壓縮率。
