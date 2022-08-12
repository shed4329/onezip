package onezip.CompressUtils.SevenZip;

import java.io.Closeable;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.IOutCreateCallback;
import net.sf.sevenzipjbinding.IOutItemAllFormats;
import net.sf.sevenzipjbinding.IOutUpdateArchive;
import net.sf.sevenzipjbinding.ISequentialInStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.OutItemFactory;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream;
import net.sf.sevenzipjbinding.util.ByteArrayStream;


public class AddOrDeleteUtils {
    int itemToAdd; // New index of the item to add
    String itemToAddPath;
    byte[] itemToAddContent;

    static String fileToAddName;
    static String fileToDeleteName;
    static byte[] fileToAddContent;
    static boolean addMode = true;
    static String password;

    int itemToRemove; // Old index of the item to be removed


    private final class MyCreateCallback
            implements IOutCreateCallback<IOutItemAllFormats> {

        public void setOperationResult(boolean operationResultOk)
                throws SevenZipException {
            // Track each operation result here
        }

        public void setTotal(long total) throws SevenZipException {
            // Track operation progress here
        }

        public void setCompleted(long complete) throws SevenZipException {
            // Track operation progress here
        }

        public IOutItemAllFormats getItemInformation(int index,
                                                     OutItemFactory<IOutItemAllFormats> outItemFactory)
                throws SevenZipException {
                if (addMode) {
                    if (index == itemToAdd) {
                        // Adding new item
                        IOutItemAllFormats outItem = outItemFactory.createOutItem();
                        outItem.setPropertyPath(itemToAddPath);
                        outItem.setDataSize((long) itemToAddContent.length);

                        return outItem;
                    }
                }


            // Remove item by changing the mapping "new index"->"old index"
            if (index < itemToRemove) {
                return outItemFactory.createOutItem(index);
            }
            return outItemFactory.createOutItem(index + 1);
        }

        public ISequentialInStream getStream(int i) throws SevenZipException {
            if (i != itemToAdd) {
                return null;
            }
            return new ByteArrayStream(itemToAddContent, true);
        }
    }


    private void initUpdate(IInArchive inArchive) throws SevenZipException {
        itemToAdd = inArchive.getNumberOfItems() - 1;
        itemToAddPath = fileToAddName;
        itemToAddContent = fileToAddContent;
        itemToRemove = -1;
        for (int i = 0; i < inArchive.getNumberOfItems(); i++) {
            if (inArchive.getProperty(i, PropID.PATH).equals(fileToDeleteName)) {
                System.out.println("delete");
                itemToRemove = i;
                break;
            }
        }
        if (itemToRemove == -1) {
            System.out.println("no found");
            //throw new RuntimeException("Item "+fileToDeleteName+" not found");
        }
    }

    public static void main(String[] args) {
        if (args.length == 4) {
            System.out.println("add");
            fileToAddName=args[2];
            fileToAddContent = args[3].getBytes();

            new AddOrDeleteUtils().compress(args[0], args[1]);
            return;
        }/*else if(args.length==3){//应该是native方法的问题，从之这样处理抛异常，干脆注释掉
            addMode=false;
            fileToDeleteName=args[2];
            new AddOrDeleteUtils().compress(args[0], args[1]);
            return;

        }*/else if(args.length==5){
            System.out.println("delete"+args[0]+args[1]+args[2]+args[3]+args[4]);
            fileToAddName=args[2];
            fileToAddContent = args[3].getBytes();
            fileToDeleteName=args[4];
            new AddOrDeleteUtils().compress(args[0], args[1]);
            return;
        }
        System.out.println("Usage1: java UpdateAddRemoveItems <input 7z> <output 7z> <to add file name> <addFile content>\nUsage2: java UpdateAddRemoveItems <input 7z> <output 7z> <delete file name>");
    }
    public static void encryptUtilsLoader(String[] args){
        if (args.length == 5) {

            fileToAddName=args[2];
            fileToAddContent = args[3].getBytes();
            password=args[4];

            new AddOrDeleteUtils().compress(args[0], args[1]);
            return;
        }/*else if(args.length==3){//应该是native方法的问题，从之这样处理抛异常，干脆注释掉
            addMode=false;
            fileToDeleteName=args[2];
            new AddOrDeleteUtils().compress(args[0], args[1]);
            return;

        }*/else if(args.length==6){
            fileToAddName=args[2];
            fileToAddContent = args[3].getBytes();
            fileToDeleteName=args[4];
            password=args[5];
            new AddOrDeleteUtils().compress(args[0], args[1]);
            return;
        }
        System.out.println("Usage1: java UpdateAddRemoveItems <input 7z> <output 7z> <to add file name> <addFile content> <7z password>\nUsage2: java UpdateAddRemoveItems <input 7z> <output 7z> <delete file name> <7z password>");
    }


    private void compress(String in, String out) {
        boolean success = false;
        RandomAccessFile inRaf = null;
        RandomAccessFile outRaf = null;
        IInArchive inArchive;
        IOutUpdateArchive<IOutItemAllFormats> outArchive = null;
        List<Closeable> closeables = new ArrayList<Closeable>();
        try {
            // Open input file
            inRaf = new RandomAccessFile(in, "r");
            closeables.add(inRaf);
            IInStream inStream = new RandomAccessFileInStream(inRaf);

            // Open in-archive
            inArchive = SevenZip.openInArchive(null, inStream);
            closeables.add(inArchive);

            initUpdate(inArchive);

            outRaf = new RandomAccessFile(out, "rw");
            closeables.add(outRaf);

            // Open out-archive object
            outArchive = inArchive.getConnectedOutArchive();
            System.out.println("here");
            RandomAccessFileOutStream randomAccessFileOutStream = new RandomAccessFileOutStream(outRaf);
            System.out.println("1");
            inArchive.getNumberOfItems();
            System.out.println("2");
            MyCreateCallback myCreateCallback = new MyCreateCallback();
            System.out.println("3");

            // Modify archive
            outArchive.updateItems(new RandomAccessFileOutStream(outRaf),
                    inArchive.getNumberOfItems(), new MyCreateCallback());

            success = true;
        } catch (SevenZipException e) {
            System.err.println("7z-Error occurs:");
            // Get more information using extended method
            e.printStackTraceExtended();
        } catch (Exception e) {
            System.err.println("Error occurs: " + e);
        } finally {
            for (int i = closeables.size() - 1; i >= 0; i--) {
                try {
                    closeables.get(i).close();
                } catch (Throwable e) {
                    System.err.println("Error closing resource: " + e);
                    success = false;
                }
            }
        }
        if (success) {
            System.out.println("Update successful");
        }
    }
}
