package onezip.CompressUtils;

import onezip.CompressUtils.zip.zipUtils;
import onezip.CompressUtils.zip.viewUtils;

import java.io.File;
import java.util.ArrayList;

import onezip.ProcessFrame;

import net.lingala.zip4j.*;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

public class test {
    public static void main(String[] args) throws Exception{
        /*ZipFile zipFile = new ZipFile("REDACTED_LOCAL_PATH");
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        zipFile.setPassword("six".toCharArray());
        zipFile.addFile(new File("REDACTED_LOCAL_PATH"),zipParameters);
         */
       //String[] array = zipUtils.list(new File("REDACTED_LOCAL_PATH"),"wallpaper__engine/wallpaper_engine/ui/dist",false);
       //for(int i=0;i<array.length;i++){
           //System.out.println("i "+array[i]);
       //}
        //ArrayList<String> arrayList=new ArrayList<String>();
        //viewUtils.fileView(new File("REDACTED_LOCAL_PATH"), arrayList );
        //System.out.println(viewUtils.getFileNameInPath(arrayList,"wallpaper__engine"));
        System.out.println(zipUtils.getComment(new File("REDACTED_LOCAL_PATH")));//实测UTF-8不乱码
        //zipUtils.zip(new File("REDACTED_LOCAL_PATH"),new File("REDACTED_LOCAL_PATH"),0,0,true,1,256,"javafx");
        //zipUtils.zip((new File("REDACTED_LOCAL_PATH")),new File("REDACTED_LOCAL_PATH"),0,0,true,"six");
    }
}
