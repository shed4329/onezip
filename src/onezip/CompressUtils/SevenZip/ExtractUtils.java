import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

import java.io.*;

public class ExtractUtils {
    public static void extract(final String sourceZipFile, String destinationDir, final String password) {
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;
        String lastFolderName="";

        try {
            randomAccessFile = new RandomAccessFile(sourceZipFile, "r");

            inArchive = SevenZip.openInArchive(null,
                    new RandomAccessFileInStream(randomAccessFile),password);
            ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();

            //如果解压到的目标文件夹不存在，则新建
            File destinationFolder = new File(destinationDir);
            if (!destinationFolder.exists())
                new File(destinationDir).mkdirs();

            for (final ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                if (!item.isFolder()) {
                    ExtractOperationResult result;
                    final long[] sizeArray = new long[1];
                    //如果压缩文件自带文件夹，在这里面创建文件夹
                    //另外，如果压缩文件中有空文件夹，空文件夹是无法解压出来的。
                    if (item.getPath().indexOf(File.separator) > 0) {
                        String path = destinationDir + File.separator
                                + item.getPath().substring(0, item.getPath().lastIndexOf(File.separator));
                        //File folderExisting = new File(path);
                        //if (!folderExisting.exists())
                        //new File(path).mkdirs();
                        if (!path.equals(lastFolderName)){
                            new File(path).mkdirs();
                        }
                    }
                    //被解压的文件
                    OutputStream out = new FileOutputStream(destinationDir + File.separator + item.getPath());
                    //如果文件很大用这个方法可以解压完整，当然文件小用这个也没毛病
                    result = item.extractSlow(new ISequentialOutStream() {
                        public int write(final byte[] data) throws SevenZipException {
                            try {
                                out.write(data);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            sizeArray[0] += data.length;
                            return data.length;
                        }
                    }, password);
                    out.close();

                    if (result == ExtractOperationResult.OK) {
                        System.out.println(String.format("%10s | %s", //
                                sizeArray[0], item.getPath()));
                    } else {
                        System.out.println("Error extracting item: " + result);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (SevenZipException e) {
                    System.err.println("Error closing archive: " + e);
                    e.printStackTrace();
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    System.err.println("Error closing file: " + e);
                    e.printStackTrace();
                }
            }
        }
    }
    public static void extract(String sourceZipFile, String destinationDir){
        extract(sourceZipFile, destinationDir,"");
    }
}
