package onezip.CompressUtils.SevenZip;

import java.io.File;
import java.util.ArrayList;


public class CompressUtils {
    //缺点：有一点点慢
    public static void compress(ArrayList<File> arrayList,String sevenZipFilePath){
        SevenZipJBindingJunitCompressArchiveStructure.Receiver(arrayList);
        String[] argument={sevenZipFilePath};
        SevenZipJBindingJunitCompressNonGeneric7z.main(argument);
    }
    public static void compress(ArrayList<File> arrayList,String sevenZipFilePath,String password){
        SevenZipJBindingJunitCompressArchiveStructure.Receiver(arrayList);
        String[] argument={sevenZipFilePath,password};
        CompressWithPassword.main(argument);
    }
}