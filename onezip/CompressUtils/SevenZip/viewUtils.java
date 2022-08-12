package onezip.CompressUtils.SevenZip;

import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class viewUtils {
    static int type=0;
    public static void fileView(String sourceSevenZipFile,ArrayList<String> fileList) {
        fileView(sourceSevenZipFile, fileList,"");
    }
    public static ArrayList<String> getFileNameInPath(ArrayList<String> fileList, String path){
        System.out.println("\033[46m"+"CompressUtils.sevenZip.viewUtils getFileNameInPath\n\n\n\n"+"\033[m");//IDEA输出颜色，多空几行，明显一点
        System.out.println(fileList);
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
        if (type==0) {
            while (iterator.hasNext()) {
                String s = iterator.next();
                //System.out.println(s);
                //System.out.println(s+" contains "+path+" is "+s.contains(path));
                if (s.contains(path)) {
                    //System.out.println("contain");
                    if (path.equals("")) {
                        if (!s.contains("\\")) {//这个库的文件预览不带\\，相当于没有扩展名的文件
                            System.out.println("top add:" + s);
                            filesInPathList.add(s);
                        }
                    } else if (appearNumber(s, "\\") == (appearNumber(path, "\\") + 1)) {
                        System.out.println("add" + s);
                        filesInPathList.add(s);
                    }

                }
            }
        }else{
            String str="";
            while (iterator.hasNext()) {
                String s = iterator.next();
                System.out.println("p"+path);
                //System.out.println(s);
                //System.out.println(s+" contains "+path+" is "+s.contains(path));
                if (s.contains(path)) {
                    //System.out.println("contain");
                    if (path.equals("")) {
                        if (!s.contains("\\")) {//这个库的文件预览不带\\，相当于没有扩展名的文件
                            System.out.println("top add:" + s);
                            filesInPathList.add(s);
                        }else{
                            if (s.contains("\\")) {
                                if (str.isEmpty()) {
                                    str = s.substring(0, s.indexOf("\\"));
                                    filesInPathList.add(s.substring(0, s.indexOf("\\")));
                                } else {
                                    System.out.println(s.substring(0,s.indexOf("\\")));
                                    System.out.println(str);
                                    if (!str.equals(s.substring(0,s.indexOf("\\")))) {
                                        str = s.substring(0, s.indexOf("\\"));;
                                        filesInPathList.add(s.substring(0, s.indexOf("\\")));
                                    }
                                }
                            }

                        }
                    } else if (appearNumber(s, "\\") == (appearNumber(path, "\\") + 1)) {
                        System.out.println("add" + s);
                        filesInPathList.add(s);
                    }

                }
            }
        }
        System.out.println("7z"+filesInPathList);

        return filesInPathList;
    }
    public static void fileView(String sourceSevenZipFile,ArrayList<String> fileList,String password) {
        if (password==null||password.isEmpty()){

        }else{
            type=1;
        }
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;

        try {
            randomAccessFile = new RandomAccessFile(sourceSevenZipFile, "r");

            inArchive = SevenZip.openInArchive(null,
                    new RandomAccessFileInStream(randomAccessFile),password);


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
}

