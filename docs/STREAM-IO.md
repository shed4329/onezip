# 自製串流、連續派工與 NIO 實驗

2026-10-28。壓縮演算法仍為自行實作 Java，使用 HotSpot；沒有引入壓縮庫或 JNI。GUI／CLI 使用既有 `--threads`／`-t:N` 設定。

## 多執行緒改動

ZIP 先前按一批 worker 工作等待整批合併。現在保留最多 worker 數的待合併項目，按 ZIP 順序寫出一個結果後，就可接續提交下一個檔案。前置目錄直接由主流程寫入，避免根目錄占掉一個檔案 worker。遇到至少 4 MiB 的大檔前先排空檔案工作，再讓大檔分段流使用同一工作池，避免巢狀等待。

小於 4 MiB 的檔案在有界的分塊記憶體流中產生 local record，不再把中型檔案暫存磁碟，也不呼叫 `toByteArray()` 展平成新大陣列。每個結果的上限為原始檔大小加 128 KiB（標頭與壓縮格式開銷）；待合併結果至多 worker 數。原有每 worker 48 MiB 的保守排程預算仍保留。取消或失敗會先停止並等待所有工作，再回滾本次輸出。

大檔分段輸入改為按已知片段大小配置，避免每片段從 64 KiB 反覆擴容到約 1 MiB；壓縮輸出也以分塊流傳遞，不複製成連續大陣列。片段邊界、32 KiB 歷史、壓縮決策和 ZIP 格式保持一致。這不是作業系統意義的零拷貝：編碼字典、CRC／AES 和最終檔案寫入仍要存取資料。

ZIP／7z 原有獨立項目／folder 的解壓多執行緒繼續使用。單一 DEFLATE 字典或 solid folder 的解碼仍依序進行，沒有把相依字典錯當獨立工作。

## 可直接使用的公開流

`cn.shed4329.onezip.engine.DeflateOutputStream` 繼承 `OutputStream`，`DeflateInputStream` 繼承 `InputStream`。它們處理 **raw DEFLATE**，不是完整 ZIP／7z 容器；GUI／CLI 的容器、CRC／AES 驗證仍由原有引擎負責。

公開流由單一呼叫執行緒依序讀寫／flush／finish；內部使用多個 worker，不表示可以讓多個呼叫者同時寫入同一個流。

```java
import cn.shed4329.onezip.engine.DeflateOutputStream;
import cn.shed4329.onezip.engine.DeflateInputStream;
import java.io.*;
import java.nio.file.*;

long size = Files.size(input);
try (InputStream source = Files.newInputStream(input);
     DeflateOutputStream compressed = new DeflateOutputStream(
             Files.newOutputStream(rawDeflateFile), 5, 8, size)) {
    source.transferTo(compressed);
}
try (DeflateInputStream restored = new DeflateInputStream(
             Files.newInputStream(rawDeflateFile));
     OutputStream destination = Files.newOutputStream(restoredFile)) {
    restored.transferTo(destination);
}
```

- `new DeflateOutputStream(out, level)`：大小未知時的順序壓縮。
- `new DeflateOutputStream(out, level, threads, size)`：大小已知，可用有界多 worker；0 自動、1 順序、2–64 為請求上限。小於 4 MiB 時走順序編碼。傳入大小不符會報錯。
- `flush()`：輸出非最終區塊，保留字典，後續可繼續寫；單 byte、短片段及跨區塊 flush 都有獨立解碼驗證。
- `finish()`：結束壓縮並停止自有工作池，但不關閉目的流；可重複呼叫。完成後禁止再寫。
- `close()`：結束並關閉目的流；解碼流的 `close()` 關閉來源流。已有寫出錯誤時不重播緩衝資料，也不重拋同一物件造成 try-with-resources 自我 suppression。

`ChunkedOutputStream(maximum[, chunkSize])` 提供有界分塊儲存。`close()` 封存後可 `writeTo(out)`，或 `openInputStream()` 開啟獨立讀取游標；這些操作直接引用原來的塊，不展平複製。它不是可同時寫入的同步容器：封存後透過 Future 等正常方式安全發布，再跨執行緒讀取。

## NIO 版本與測量範圍

`NioInputStream`／`NioOutputStream` 是自行實作的 **blocking channel** adapter，支援 heap／direct `ByteBuffer`、短讀寫、暫時零進度、EOF、中斷及資源關閉。heap 大批次可直接讀寫呼叫者的陣列；direct 模式因編解碼器使用 `byte[]`，仍需在 direct buffer 和 Java 陣列之間複製。

原有 `Files.newInputStream`／`Files.newOutputStream` 已是 NIO.2 介面。實驗比較的是直接控制 FileChannel 和緩衝策略，不是簡單的「舊 I/O 對新 I/O」。可用 JVM property 選擇實驗後端，放在 `-jar` 前面：

```powershell
java -Donezip.io=nio-heap -jar target/onezip-1.01-all.jar c data.zip folder -t:8
```

- `stream`：既有 Files 串流；ZIP archive 使用原有 BufferedOutputStream。
- `buffered`：普通 64 KiB 緩衝流。
- `nio-heap`：FileChannel + 自製 64 KiB heap buffer 流。
- `nio-direct`：FileChannel + 自製 64 KiB direct buffer 流。

實驗後端替換壓縮來源讀取、ZIP archive 寫入及解壓目的檔寫入；壓縮包的 ZIP 解析／壓縮輸入 reader 維持原來實作。各模式都保留 NOFOLLOW、CREATE_NEW、來源大小、CRC／AES、取消和回滾檢查。`flush()` 沒有增加 `force(true)`，與原本測試一樣不保證斷電持久化。

預設維持 `stream`，不根據「NIO」名稱推定更快。本輪暖 API 實測 (historical local evidence; not distributed)比較相同輸入、相同壓縮位元組，分開列出派工／分塊流收益、普通緩衝收益、heap／direct 的差異，以及 1／4／8 workers 的擴展情形。
