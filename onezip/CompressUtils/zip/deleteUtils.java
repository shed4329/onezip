package onezip.CompressUtils.zip;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.File;

public class deleteUtils {
    public static void main(String[] args) throws ZipException {//好像在main方法里出现异常cannot delete the old zip file的概率最小
        String inputPath = args[0];
        String zipPath = args[1];
        System.out.println(inputPath);
        System.out.println(zipPath);
        File file = new File(zipPath);
        ZipFile zipFile = new ZipFile(file);
        zipFile.removeFile(inputPath);
    }
}
