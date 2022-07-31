package onezip.CompressUtils.SevenZip;

import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class viewUtils {
    public static void fileView(String sourceSevenZipFile,ArrayList<String> fileList) {
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;

        try {
            randomAccessFile = new RandomAccessFile(sourceSevenZipFile, "r");

            inArchive = SevenZip.openInArchive(null,
                    new RandomAccessFileInStream(randomAccessFile));


            for (final ISimpleInArchiveItem item : inArchive.getSimpleInterface().getArchiveItems()) {

                fileList.add(item.getPath());
                //System.out.println(item.getPath()+"      comment:"+item.getComment());
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
    public static ArrayList<String> getFileNameInPath(ArrayList<String> fileList, String path){
        System.out.println("\033[46m"+"CompressUtils.sevenZip.viewUtils getFileNameInPath\n\n\n\n"+"\033[m");//IDEA输出颜色，多空几行，明显一点
        //System.out.println(fileList.size());
        ArrayList<String> filesInPathList = new ArrayList<>();
        if (!path.equals("")){//根目录不应显示..
            filesInPathList.add("..");
        }
            /*for (String item:fileList){
                System.out.println(item);
                if (item.contains(path)){
                    if (appearNumber(item,"\\")==(appearNumber(path,"\\")+1)){
                        System.out.println(item);
                        fileList.add(item);
                    }
                }
            }
             */
        Object[] temp= fileList.toArray();
        CopyOnWriteArrayList fileList1 = new CopyOnWriteArrayList(temp);//用arraylist不知道为什么会抛出异常ConcurrentModificationException（String s=iterator.next();这行），但好像用了CopyOnWriteArrayList就没有，
        Iterator<String> iterator=fileList1.iterator();
        while(iterator.hasNext()){
            String s=iterator.next();
            //System.out.println(s);
            //System.out.println(s+" contains "+path+" is "+s.contains(path));
            if (s.contains(path)) {
                //System.out.println("contain");
                if (path.equals("")){
                    if (!s.contains("\\")){//这个库的文件预览不带\\，相当于没有扩展名的文件
                        System.out.println("top add:"+s);
                        filesInPathList.add(s);
                    }
                }else if (appearNumber(s,"\\")==(appearNumber(path,"\\")+1)){
                    System.out.println("add"+s);
                    filesInPathList.add(s);
                }

            }
        }
        System.out.println("7z"+filesInPathList);

        return filesInPathList;
    }
    private static int appearNumber(String srcText, String findText) {
        int count = 0;
        int index = 0;
        while ((index = srcText.indexOf(findText, index)) != -1) {
            index = index + findText.length();
            count++;
        }
        System.out.println(count);
        return count;
    }
    public static void getTopFolder(String sourceSevenZipFile,ArrayList<String> fileList){
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;

        try {
            randomAccessFile = new RandomAccessFile(sourceSevenZipFile, "r");

            inArchive = SevenZip.openInArchive(null,
                    new RandomAccessFileInStream(randomAccessFile));


            for (final ISimpleInArchiveItem item : inArchive.getSimpleInterface().getArchiveItems()) {
                fileList.add(item.getPath());

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
}

