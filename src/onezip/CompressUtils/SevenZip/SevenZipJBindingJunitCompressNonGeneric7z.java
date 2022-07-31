package onezip.CompressUtils.SevenZip;

/* BEGIN_SNIPPET(CompressNonGeneric7z) */
import java.io.IOException;
import java.io.RandomAccessFile;

import net.sf.sevenzipjbinding.IOutCreateArchive7z;
import net.sf.sevenzipjbinding.IOutCreateCallback;
import net.sf.sevenzipjbinding.IOutItem7z;
import net.sf.sevenzipjbinding.ISequentialInStream;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.OutItemFactory;
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream;
import net.sf.sevenzipjbinding.util.ByteArrayStream;


public class SevenZipJBindingJunitCompressNonGeneric7z {
    /**
     * The callback provides information about archive items.
     */
    private final class MyCreateCallback //
            implements IOutCreateCallback<IOutItem7z> {

        public void setOperationResult(boolean operationResultOk)//
                throws SevenZipException {
            // Track each operation result here
        }

        public void setTotal(long total) throws SevenZipException {
            // Track operation progress here
        }

        public void setCompleted(long complete) throws SevenZipException {
            // Track operation progress here
        }

        public IOutItem7z getItemInformation(int index,//
                                             OutItemFactory<IOutItem7z> outItemFactory) {
            IOutItem7z item = outItemFactory.createOutItem();

            if (/*f*/items/**/[index].getContent() == null) {
                // Directory
                item.setPropertyIsDir(true);
            } else {
                // File
                item.setDataSize((long) /*f*/items/**/[index].getContent()./*f*/length/**/);
            }

            item.setPropertyPath(/*f*/items/**/[index].getPath());

            return item;
        }

        public ISequentialInStream getStream(int i) throws SevenZipException {
            System.out.println(i);//此处i是从某个数字开始，可以统计i的个数，与数组元素个数相比，得到进度
            if (/*f*/items/**/[i].getContent() == null) {
                return null;
            }
            return new ByteArrayStream(/*f*/items/**/[i].getContent(), true);
        }
    }

    private SevenZipJBindingJunitCompressArchiveStructure.Item[] items;

    public static void main(String[] args) {
        if (args./*f*/length/* */== 1) {
            try {
                new SevenZipJBindingJunitCompressNonGeneric7z().compress(args[0]);
            }catch (Exception e){
                e.printStackTrace();
            }
            return;
        }
        System.out.println("Usage: java CompressNonGeneric7z <archive> \n tip:before you use this main method,please use SevenZipJBindingJunitCompressArchiveStructure.receiver() to put your file into the 7z ,or you will get an empty 7z file");
    }


    public void compress(String filename) throws Exception{
        items = SevenZipJBindingJunitCompressArchiveStructure.create();

        boolean success = false;
        RandomAccessFile raf = null;
        IOutCreateArchive7z outArchive = null;
        try {
            raf = new RandomAccessFile(filename, "rw");

            // Open out-archive object
            outArchive = SevenZip.openOutArchive7z();

            // Configure archive
            outArchive.setLevel(5);
            outArchive.setSolid(true);

            // Create archive
            outArchive.createArchive(new RandomAccessFileOutStream(raf),//
                    /*f*/items/**/./*f*/length/**/, new MyCreateCallback());

            success = true;
        } catch (SevenZipException e) {
            System.err.println("7z-Error occurs:");
            // Get more information using extended method
            e.printStackTraceExtended();
        } catch (Exception e) {
            System.err.println("Error occurs: " + e);
        } finally {
            if (outArchive != null) {
                try {
                    outArchive.close();
                } catch (IOException e) {
                    System.err.println("Error closing archive: " + e);
                    success = false;
                }
            }
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    System.err.println("Error closing file: " + e);
                    success = false;
                }
            }
        }
        if (success) {
            System.out.println("Compression operation succeeded");
        }
    }
}
