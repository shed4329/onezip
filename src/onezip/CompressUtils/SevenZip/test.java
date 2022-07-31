package onezip.CompressUtils.SevenZip;

import com.sun.xml.internal.messaging.saaj.util.FinalArrayList;

import java.io.File;
import java.util.ArrayList;


public class test {
    public static void main(String[] args) {
        ArrayList<String> arrayList = new ArrayList<>();
        viewUtils.fileView("REDACTED_LOCAL_PATH",arrayList);
        ArrayList<String> arrayList1=viewUtils.getFileNameInPath(arrayList,"jre\\lib");//写法jre或jre\\lib
        for (int i=0;i<arrayList1.size();i++){
            System.out.println(arrayList1.get(i));
        }

        long d1 = System.currentTimeMillis();
        //ExtractUtils.extract("REDACTED_LOCAL_PATH","REDACTED_LOCAL_PATH","1111");
        /*String[] argument={"REDACTED_LOCAL_PATH","REDACTED_LOCAL_PATH","android.txt","android os is also an operation system based on open-source system,linux,created by google llc."};//实现7z的自身更新
          String[] argument={"REDACTED_LOCAL_PATH","REDACTED_LOCAL_PATH","ios.txt"};//实测不可以，可以尝试的数组3
          String[] argument={"REDACTED_LOCAL_PATH","REDACTED_LOCAL_PATH","sir.txt","this txt file appear due to some technology limits,we're sorry for that","sorry.txt"};//实现7z的自身更新
          AddOrDeleteUtils.main(argument);
         */
        ///ArrayList<File> arrayList = new ArrayList<>();
        //arrayList.add(new File("REDACTED_LOCAL_PATH"));
        //arrayList.add(new File("REDACTED_LOCAL_PATH"));
        /*压缩方式一
        SevenZipJBindingJunitCompressArchiveStructure.Receiver(arrayList);//注意，指定位置请用绝对路径，否则以Java文件的路径为根目录来算
        long d2 = System.currentTimeMillis();
        System.out.println("adding files to structure using time"+(d2-d1)+"ms");
        String[] argument={"REDACTED_LOCAL_PATH","password"};
        CompressWithPassword.main(argument);
        //ArrayList<File> arrayList = new ArrayList();
        //arrayList.add(new File("REDACTED_LOCAL_PATH"));
        //MyItem.Receiver(arrayList);
        //MyItem.create();
         */
        //压缩方式二
        //CompressUtils.compress(arrayList,"REDACTED_LOCAL_PATH");
        long d3 = System.currentTimeMillis();
        System.out.println("time used:"+(d3-d1)+"ms");
    }
}
