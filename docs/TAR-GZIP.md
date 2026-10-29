# 純 Java TAR／GZIP

後續的小檔案單 worker 改動見 正常桌面與 AppContainer 分列重測 (historical local evidence; not distributed)：減少重複屬性查詢與配置，仍逐次檢查完整父鏈並以 CREATE_NEW 寫出。正常桌面四個設定均超過 ReferenceArchive 60%，隔離環境尚有例外；不能把先前隔離環境與新版正常桌面直接當成同環境加速。另已新增 [LZ4 frame／TAR.LZ4](LZ4.md)。

GUI 的壓縮格式選單與 Java CLI `args` 已支援 TAR、GZIP、TAR.GZ；`c`／`x`／`l`／`t` 都可用。建立及讀取接受 `.tar`、`.gz`／`.gzip`、`.tar.gz`／`.tar.gzip`／`.tgz`。TAR 僅打包，不會壓縮；GZIP 使用現有自製 DEFLATE，單獨 GZIP 只接受一個一般檔案，多檔案與資料夾使用 TAR.GZ。這些格式不提供密碼加密。

```powershell
java -jar target/onezip-1.01-all.jar --cli c backup.tar folder1 folder2
java -jar target/onezip-1.01-all.jar --cli c report.txt.gz report.txt --level 5 --threads 4
java -jar target/onezip-1.01-all.jar --cli c backup.tar.gz folder --level 5 --threads 4
java -jar target/onezip-1.01-all.jar --cli x backup.tgz -o restored --threads 4
java -jar target/onezip-1.01-all.jar --cli l backup.tar.gz
java -jar target/onezip-1.01-all.jar --cli t report.txt.gzip
```

TAR 寫入 USTAR 格式，長檔名與 Unicode 路徑使用 POSIX PAX `path`；大數值可用正值 base-256 欄位。讀取支援常見 V7、USTAR prefix、POSIX PAX path／size／global header 與 GNU long-name，檢查每個標頭 checksum、大小、padding 範圍及完整雙零結尾。拒絕連結、裝置、FIFO、sparse 和其他特殊項目；不還原擁有者、Unix 權限或 mtime。名稱使用 UTF-8，沒有猜測舊系統編碼。TAR 只有標頭 checksum，沒有內容 CRC，不能聲稱能偵測任意 payload 位元翻轉。

GZIP 依 [RFC1952](https://www.rfc-editor.org/rfc/rfc1952) 自行解析 header／extra／name／comment／FHCRC、每成員 CRC32 與模 2^32 的 ISIZE，接受串接成員並串接其未壓縮內容，拒絕截斷、保留 flags、錯誤校驗與尾端雜訊。DEFLATE decoder 會把預讀的 trailer／下一成員還給 framing；既有 ZIP raw DEFLATE 仍採嚴格結尾檢查。一般 GZIP 使用第一成員的 FNAME basename；缺少 FNAME 時從 archive 去掉 `.gz`／`.gzip` 推斷，空名稱使用 `data`。寫入只對 ISO-8859-1 可表示名稱存 FNAME；Unicode 名稱不寫此選填欄位，因此要保留 Unicode basename 時建議把輸出命名為 `<原始名稱>.gz`，或用 TAR.GZ。

GZIP 沒有可直接使用的完整隨機索引，末尾 ISIZE 也不代表多成員或超過 4 GiB 的總長度。讀取時先串流解碼、校驗至私有暫存檔，再以正確總長度提供一般檔案或 TAR 索引。RAM 使用不隨解出大小增長，但需要等於未壓縮內容大小的額外暫存磁碟空間；成功、失敗與取消皆會關閉並刪除暫存檔。`l` 也會解碼及驗證 GZIP，`t` 不建立目的檔案但同樣使用私有暫存。不要把這條路徑描述成完全無暫存或單次磁碟寫出。

GZIP／TAR.GZ 壓縮重用自製 DEFLATE 的有界多 worker 片段編碼與 32 KiB 歷史，仍為標準單成員 GZIP，沒有改成不相容格式。`--threads 0` 自動、1 順序、2–64 請求上限。解碼同一 GZIP 串流仍循序；TAR 及完成索引的 TAR.GZ 可按獨立檔案平行寫出，單檔 GZIP 無解碼並行加速。TAR 打包本身循序串流寫入，忽略壓縮等級。多 worker 的實際收益取決於檔案數、heap 與磁碟。

建立沿用來源連結檢查、大小一致性檢查、同目錄 staging 與成功後發佈。解壓沿用完整路徑預檢、拒絕越界／重複／已有檔案、不跟隨連結、取消與失敗回滾。GZIP spool 用於避免解碼兩次與提供可靠索引，效能比較必須把它算進完整 API 時間。

獨立互通測試使用僅限 test scope 的 Commons Compress TAR 與 JDK GZIP，涵蓋多來源、Unicode／長檔名、空檔案／目錄、各等級、平行片段邊界、外部 PAX／GNU／V7、串接成員與選填標頭、錯誤 CRC／ISIZE／FHCRC、截斷、checksum、連結、路徑穿越、重複項目、不覆蓋與取消回滾。生產 JAR 不含這些對照 codec。

暖機效能與 60% 目標驗證 (historical local evidence; not distributed)保存固定 JAR、獨立 corpus、ReferenceArchive 相同包解壓、各等級／執行緒設定及全部樣本。這是本機固定資料的完整操作量測，不能保證所有資料都達到相同比例。
