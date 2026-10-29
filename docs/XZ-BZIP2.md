# 純 Java XZ／BZIP2

GUI 與 Java CLI 均可建立、解壓、列出及完整測試 XZ、BZIP2 與其 TAR 組合格式。容器、LZMA2、BWT、MTF、RLE、Huffman 與 CRC 均由本專案實作；沒有壓縮庫、JNI、DLL 或外部壓縮程序。外部程式與第三方 codec 只在測試中作獨立對照，不在發行 JAR。

支援副檔名：`.xz`、`.bz2`／`.bzip2`、`.tar.xz`／`.txz`、`.tar.bz2`／`.tar.bzip2`／`.tbz2`／`.tbz`。副檔名不區分大小寫。單獨 XZ／BZ2 只接受一個一般檔案，沒有原始檔名欄位，解出名稱從 archive 去掉最後副檔名推斷；建議命名為 `<原始名稱>.xz` 或 `<原始名稱>.bz2`。資料夾、多個來源與需保留完整檔名時選相應 TAR 格式。TAR 內的 Unicode、長檔名、空檔案／資料夾沿用既有 USTAR／PAX 支援。

```powershell
java -jar target/onezip-1.01-all.jar --cli c report.txt.xz report.txt --level 5
java -jar target/onezip-1.01-all.jar --cli c report.txt.bz2 report.txt --format bzip2 --level 5
java -jar target/onezip-1.01-all.jar --cli c backup.tar.xz folder --threads 1
java -jar target/onezip-1.01-all.jar --cli c backup.tbz2 folder1 folder2 --level 9
java -jar target/onezip-1.01-all.jar --cli x backup.tar.bz2 -o restored --threads 1
java -jar target/onezip-1.01-all.jar --cli l backup.txz
java -jar target/onezip-1.01-all.jar --cli t report.txt.bzip2
```

CLI 的 `--format` 接受 `xz`、`tar.xz`／`txz`、`bz2`／`bzip2`、`tar.bz2`／`tar.bzip2`／`tbz2`／`tbz`，須符合輸出副檔名。這些格式沒有密碼寫入；傳入非空密碼會明確失敗。GUI 選單同樣提供 XZ、TAR.XZ、BZIP2、TAR.BZ2，檔案選擇與拖放也認得新副檔名。

## XZ

依官方 [XZ file format](https://tukaani.org/xz/xz-file-format.txt) 實作 header、block header、Index、footer、canonical VLI、對齊 padding 及各 CRC32。寫入使用原有自製 LZMA2、2 MiB 字典、單一串流 block 與 CRC64；空輸入使用零 blocks 的合法 XZ stream。LZMA2 內部以既有有界 staging 與滑動字典處理資料，並不把整個檔案載入記憶體。等級 0 使用 raw LZMA2 chunks，1–9 沿用既有編碼搜尋策略。

讀取支援多 blocks、串接 streams、4-byte 對齊 stream padding、可選 compressed／uncompressed sizes，以及 None、CRC32、CRC64、SHA-256 checks。SHA-256 使用標準 JDK MessageDigest；其他 CRC 自製。檢查實際解碼量、packed 範圍、Index 各記錄及 footer backward size，不只相信標頭大小。支援最後一個 filter 為 LZMA2，前置 filter 可為 Delta 或 x86 BCJ；尚未提供 ARM／ARM64／IA64／PowerPC／SPARC／RISC-V BCJ 或其他 filter，遇到會明確失敗。LZMA2 字典沿用 256 MiB 上限；索引最多一百萬 blocks。外部選擇 None check 時，格式本身沒有 payload checksum；本專案 writer 固定寫 CRC64。

## BZIP2

依標準 BZh 格式實作：RLE1 → cyclic BWT → MTF／RUNA／RUNB → adaptive canonical Huffman，解壓反向處理；不委派其他 BZIP2 codec。循環後綴排序使用 prefix doubling 與 counting sort，時間約為每 block O(n log n)，記憶體隨 block 大小有界。寫入目前使用兩棵相同的逐 block 自適應 Huffman tree，為合法格式，未宣稱複製原生編碼器的多表選擇策略。原始資料的 block CRC 與 combined CRC 皆使用 BZIP2 的非反射 CRC32 規則。

等級 1–9 選擇 100–900 KB 的 BWT block；預設 5。BZIP2 沒有 STORE 模式，CLI 等級 0 對映到 1，GUI 不顯示「僅儲存」。解碼支援現代 BZh1–BZh9、多 blocks、2–6 個 Huffman groups、selector MTF、串接 streams 與完整 CRC，逆 RLE 串流產生資料，避免因長重複內容整塊展開到 RAM。拒絕非法碼長、selector、RUNA/B 範圍、原始 BWT pointer、截斷、錯誤 CRC 及尾端垃圾。不支援早期 Bzip1 或 randomized BZIP2 blocks，會明確拒絕。背景與格式來源見 [bzip2 官方手冊](https://sourceware.org/bzip2/manual/manual.html)。

## 串流、執行緒與暫存

公開 `XzInputStream`／`XzOutputStream`、`Bzip2InputStream`／`Bzip2OutputStream` 可直接接 Java IO streams。output expected size 可為 -1（未知），已知大小會檢查來源一致性；finish 保持目的 stream 開啟，close 完成格式並關閉目的 stream。完整讀至 EOF 才完成所有尾端／校驗驗證。

兩種 codec 目前都循序處理。既有 `threads` 參數仍接受：TAR 組合格式先解碼、校驗到私有磁碟 spool 並取得索引，再按獨立檔案平行寫出；指定 1 時全程只有一個 archive worker。列出與 `t` 也需要這份暫存，所需額外磁碟空間約為整個解出串流大小。成功、錯誤與取消皆關閉並刪除 spool，正式目的檔案沿用完整路徑預檢、逐次父鏈檢查、CREATE_NEW、不覆蓋與回滾。TAR 連結／sparse／裝置檔的限制也沿用。

## 驗證

最終 **161 項回歸與 GUI 測試通過**，54 個生產 Java／整合來源及發行 JAR 的純 Java 檢查通過。獨立測試使用僅限 test scope 的 Tukaani XZ 與 Commons Compress BZIP2：空檔、極小資料、RLE 邊界、各等級、block 邊界、大於 2 MiB 的滑動字典、XZ 多 block／Delta／x86／四種 checks、可選 block sizes、串接／padding、錯誤 flags／checksums／index／footer、截斷、取消與不覆蓋。CLI 覆蓋所有副檔名別名的 c／x／l／t；GUI 實際建立並解出四種新格式，BZIP2 等級選單也驗證不含 STORE。

封裝後再做 13 項檔案 SHA-256 互通檢查：Python 與 ReferenceArchive 解開 OneZip 的四種包、OneZip 解開 Python 的四種包，以及 OneZip 解開 ReferenceArchive 建立的 XZ。詳見 發行驗證報告與原始日誌 (historical local evidence; not distributed)。本輪驗證格式功能與相容性，沒有以先前其他格式的數字宣稱 XZ／BZIP2 相對 ReferenceArchive 的速度。
