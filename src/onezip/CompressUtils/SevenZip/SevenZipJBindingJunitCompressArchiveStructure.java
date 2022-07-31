import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Random;

public class SevenZipJBindingJunitCompressArchiveStructure {
    static ArrayList<String> filePath = new ArrayList<>();
    static ArrayList<String> filePathIn7Zip = new ArrayList<>();
    public static SevenZipJBindingJunitCompressArchiveStructure.Item[] create() throws Exception{

        //     <root>
        //     |
        //     +- info.txt
        //     +- random-100-bytes.dump
        //     +- dir1
        //     |  +- file-in-a-directory1.txt
        //     +- dir2
        //        +- file-in-a-directory2.txt

        SevenZipJBindingJunitCompressArchiveStructure.Item[] items = new SevenZipJBindingJunitCompressArchiveStructure.Item[filePath.size()];

       /*items[0] = new SevenZipJBindingJunitCompressArchiveStructure.Item("info.txt", "This is the info");

       byte[] content = new byte[100];
       new Random().nextBytes(content);
       items[1] = new SevenZipJBindingJunitCompressArchiveStructure.Item("random-100-bytes.dump", content);

       // dir1 doesn't have separate archive item
       items[2] = new SevenZipJBindingJunitCompressArchiveStructure.Item("dir1" + File.separator + "file1.txt", //
               "This file located in a directory 'dir'");

       // dir2 does have separate archive item
       items[3] = new SevenZipJBindingJunitCompressArchiveStructure.Item("dir2" + File.separator, (byte[]) null);
       items[4] = new SevenZipJBindingJunitCompressArchiveStructure.Item("dir2" + File.separator + "file2.txt", //
               "This file located in a directory 'dir'");

        */
        int count=0;
        for (String path:filePath){
            //System.out.println(path);
            File file = new File(path);
            System.out.println(filePathIn7Zip.get(count));
            int length = (int) file.length();
            byte[] data = new byte[length];
            new FileInputStream(file).read(data);
            items[count] = new SevenZipJBindingJunitCompressArchiveStructure.Item(filePathIn7Zip.get(count),data);

            count++;
        }

        return items;
    }

    static class Item {
        private String /*f*/path/**/;
        private byte[] /*f*/content/**/;

        Item(String path, String content) {
            this(path, content.getBytes());
        }

        Item(String path, byte[] content) {
            this./*f*/path/**/ = path;
            this./*f*/content/**/ = content;
        }

        String getPath() {
            return /*f*/path/**/;
        }

        byte[] getContent() {
            return /*f*/content/**/;
        }
    }

    public static void Receiver(ArrayList<File> arrayList) {
        for (File item : arrayList) {//理想状态：传入一个含有文件列表的数组，自己记录每个文件的内容,如REDACTED_LOCAL_PATH
            if (item.isFile()) {
                filePath.add(item.getPath());
            } else {
                //这里用递归添加，下次再写
                listAll(item.getName(),item);
            }
        }
    }

    public static void listAll(String path,File file) {
        if (file.exists()) {//先判断文件是否存在
            File[] files = file.listFiles();//获取指定目录下当前的所有文件夹或者文件对象
            for (File file1 : files) {
                if (file1.exists()){
                    if (file1.isDirectory()){
                        listAll(path+File.separator+file1.getName(),file1);
                    }else {
                        //System.out.println(path+File.separator+file1.getName());
                        filePath.add(file1.getPath());
                        filePathIn7Zip.add(path+File.separator+file1.getName());
                    }
                }
            }
        }
    }
}
