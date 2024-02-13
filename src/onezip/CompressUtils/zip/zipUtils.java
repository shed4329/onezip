package onezip.CompressUtils.zip;

import javafx.application.Platform;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import net.lingala.zip4j.progress.ProgressMonitor;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;

import onezip.testOne;
import onezip.ProcessFrame;
import onezip.themes.fxJava.fluent.component.FluentProcessFrame;
import onezip.tool.taskProgress;

import static onezip.component.oneAlert.alertException;

public class zipUtils {
    /*
           method: 1-> CompressionMethod.STORE(只打包，不压缩）
                   2->CompressionMethod.AES_INTERNAL_ONLY（加密）
                   其他数->CompressionMethod.DEFLATE（默认）
           level: 0->CompressionLevel.NORMAL(一般）
                  1->CompressionLevel.FASTEST（最快）
                  2->CompressionLevel.FASTER（较快）
                  3->CompressionLevel.FAST（快）
                  4->CompressionLevel.MEDIUM_FAST（中快）
                  5->CompressionLevel.HIGHER（较高压缩比）
                  6->CompressionLevel.MAXIMUM（高压缩比）
                  7->CompressionLevel.PRE_ULTRA（极高压缩比）
                  8->CompressionLevel.ULTRA（极限压缩）
                  其他数->CompressionLevel.NORMAL(一般）
        */
    public static void zipFiles(ArrayList<File> inputFiles, File output, int method, int level, boolean isEncrypt,String password) throws Exception {

        long d1 = System.currentTimeMillis();//开始时间
        System.out.println(d1);

        ZipFile zipFile = new ZipFile(output.getPath());//生成的压缩包

        ZipParameters zipParameters = new ZipParameters();//压缩参数



        setZipParameters(zipFile,zipParameters,method,level,isEncrypt,password);

        ProgressMonitor progressMonitor = zipFile.getProgressMonitor();

        System.out.println("s");
        zipFile.setRunInThread(true);

        /*if(input.isFile()){//文件
            zipFile.addFile(input,zipParameters);
        }else{//文件夹
            zipFile.addFolder(input, zipParameters);
        }
         */
        System.out.println("u");
        //zipFile.addFile(input,zipParameters);
        try {
            zipFile.addFiles(inputFiles);
        }catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("n");
        monitor(progressMonitor);
        System.out.println("java");
        //System.out.println("摘要"+"\n"+"要压缩的文件："+input.getPath+"(路径:"+inputFiles.getPath()+")"+"\n"+"生成的压缩包："+output.getName()+"(路径:"+output.getPath()+")"+"\n"+"用时："+(System.currentTimeMillis()-d1)+"ms");
        System.out.println("摘要"+"\n"+"要压缩的文件："+inputFiles+"生成的压缩包："+output.getName()+"(路径:"+output.getPath()+")"+"\n"+"用时："+(System.currentTimeMillis()-d1)+"ms");
    }
    public static void zipFolder(File folder, File output, int method, int level, boolean isEncrypt,String password) throws Exception{


        ZipFile zipFile = new ZipFile(output.getPath());//生成的压缩包
        ZipParameters zipParameters = new ZipParameters();//压缩参数

        setZipParameters(zipFile,zipParameters,method,level,isEncrypt,password);
        ProgressMonitor progressMonitor = zipFile.getProgressMonitor();


        zipFile.setRunInThread(true);

        /*if(input.isFile()){//文件
            zipFile.addFile(input,zipParameters);
        }else{//文件夹
            zipFile.addFolder(input, zipParameters);
        }
         */
        InputStream inputStream = null;

        System.out.println(folder);
        ProgressMonitor progressMonitor1 =zipFile.getProgressMonitor();
        zipFile.addFolder(folder,zipParameters);
        monitor(progressMonitor1);


        //monitor(progressMonitor,progressBar);
        //System.out.println("摘要"+"\n"+"要压缩的文件："+input.getPath+"(路径:"+inputFiles.getPath()+")"+"\n"+"生成的压缩包："+output.getName()+"(路径:"+output.getPath()+")"+"\n"+"用时："+(System.currentTimeMillis()-d1)+"ms");
        //System.out.println("摘要"+"\n"+"要压缩的文件："+inputFolders+"生成的压缩包："+output.getName()+"(路径:"+output.getPath()+")"+"\n"+"用时："+(System.currentTimeMillis()-d1)+"ms");
    }

    public static void unzip(File input, File output, Charset charset, taskProgress progress) throws Exception{
        long d1 = System.currentTimeMillis();//开始时间

        ZipFile zipFile = new ZipFile(input);

        ProgressMonitor progressMonitor = zipFile.getProgressMonitor();
        zipFile.setRunInThread(true);

        zipFile.setCharset(charset);
        zipFile.extractAll(output.getPath());
        monitor(progressMonitor);//监视进度

        System.out.println("摘要"+"\n"+"要解压的文件："+input.getName()+"(路径:"+input.getPath()+")"+"\n"+"解压到："+output.getName()+"(路径:"+output.getPath()+")"+"\n"+"用时："+(System.currentTimeMillis()-d1)+"ms");
    }

    public static void unzip(File input, File output,Charset charset,String password,taskProgress progress) throws Exception{
        long d1 = System.currentTimeMillis();//开始时间

        ZipFile zipFile = new ZipFile(input,password.toCharArray());

        ProgressMonitor progressMonitor = zipFile.getProgressMonitor();
        zipFile.setRunInThread(true);
        zipFile.setCharset(charset);
        zipFile.extractAll(output.getPath());
        monitor(progressMonitor);//监视进度

        System.out.println("摘要" + "\n" + "要解压的文件：" + input.getName() + "(路径:" + input.getPath() + ")" + "\n" + "解压到：" + output.getName() + "(路径:" + output.getPath() + ")" + "\n" + "用时：" + (System.currentTimeMillis() - d1) + "ms");

    }

    public static void add(File input,File output,int method) throws Exception{//不能添加文件夹
        long d1 = System.currentTimeMillis();//开始时间

        InputStream inputStream;

        ZipFile zipFile = new ZipFile(output);

        ZipParameters zipParameters = new ZipParameters();//压缩参数
        setCompressionMethod(zipParameters, method);
        zipParameters.setFileNameInZip(input.getName());
        zipParameters.setWriteExtendedLocalFileHeader(true);

        inputStream = new BufferedInputStream(new FileInputStream(input));
        zipFile.addStream(inputStream, zipParameters);

        System.out.println("摘要" + "\n" + "要添加到压缩包的文件：" + input.getName() + "(路径:" + input.getPath() + ")" + "\n" + "压缩包" + output.getName() + "(路径:" + output.getPath() + ")" + "\n" + "用时：" + (System.currentTimeMillis() - d1) + "ms");


    }
    public static void add(File input,File output) throws Exception{
        add(input,output,0);
    }

    public static int deleteFile(String inputPath,File zip){
        long d1 = System.currentTimeMillis();//开始时间
        System.out.println(d1);
        ZipFile zipFile = new ZipFile(zip.getPath());
        System.out.println(zipFile);

        //inputPath=inputPath.replace("\\","/");
        System.out.println(inputPath);
        String[] argument = new String[]{inputPath, zip.getPath()};
        try {
            deleteUtils.main(argument);
        } catch (ZipException e) {
            e.printStackTrace();
            alertException(e);
            return 12;
        }
        File folder = zip.getParentFile();
        String[] fileList = folder.list();
        for (String fileName:fileList){
            System.out.println(fileName);
            if (fileName.contains(zip.getName())&&fileName.length()>zip.getName().length()){
                File tempFile=new File(folder.getPath()+File.separator+fileName);
                tempFile.delete();
                System.out.println("删除临时文件（*.zipxxxx："+folder.getPath()+File.separator+fileName);
            }
        }

        System.out.println("摘要"+"\n"+"要删除的文件："+inputPath+"\n"+"压缩包："+zip.getPath()+"\n"+"用时："+(System.currentTimeMillis()-d1)+"ms");
        return 2;
    }

    private static void monitor(ProgressMonitor progressMonitor) throws InterruptedException {

        //ProcessFrame processFrame = new ProcessFrame();
        FluentProcessFrame processFrame = new FluentProcessFrame();
        Platform.runLater(processFrame::newProgressFrame);

        System.out.println("m");
        System.out.println(progressMonitor.getState().toString());
        while (!progressMonitor.getState().equals(ProgressMonitor.State.READY)) {
            System.out.println("progress monitor");
            System.out.println("Percentage done: " + progressMonitor.getPercentDone());
            Platform.runLater(()->processFrame.setProgress(progressMonitor.getPercentDone()));
            System.out.println("Current file: " + progressMonitor.getFileName());
            System.out.println("Current task: " + progressMonitor.getCurrentTask());
            System.out.println("x");
            //noinspection BusyWait
            Thread.sleep(50);
            System.out.println("/");
        }
        //Platform.runLater(()->processFrame.close());
    }

    public static void setComment(String comment,File zip)  {
        ZipFile zipFile = new ZipFile(zip);
        try {
            zipFile.setComment(comment);
        } catch (ZipException e) {
            alertException(e);
            throw new RuntimeException(e);
        }
    }
    public static String getComment(File zip) throws ZipException{
        ZipFile zipFile = new ZipFile(zip);
        return zipFile.getComment();
    }

    public static boolean isEncrypted(File zip) throws ZipException {
        ZipFile zipFile = new ZipFile(zip);
        return zipFile.isEncrypted();
    }
    public static boolean isValid(File file) throws ZipException {
        ZipFile zipFile = new ZipFile(file);
        return zipFile.isValidZipFile();
    }

    public static void list(File zip,ArrayList<String> fileList) throws IOException {
        //用java自带的包预览
        viewUtils.fileView(zip,fileList);
    }

    public static ArrayList<String> viewInPath(ArrayList<String> fileList,String path){
        return viewUtils.getFileNameInPath(fileList,path);
    }

    private static void setCompressionMethod(ZipParameters zipParameters,int method){
        //压缩方法
        if(method==1) {
            zipParameters.setCompressionMethod(CompressionMethod.STORE);
        }else if(method==2){
            zipParameters.setCompressionMethod(CompressionMethod.AES_INTERNAL_ONLY);
        }else{
            zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        }
    }
    private static void setZipParameters(ZipFile zipFile,ZipParameters zipParameters,int method,int level,boolean isEncrypt,String password){
        setCompressionMethod(zipParameters, method);

        //压缩等级
        if (level == 1) {
            zipParameters.setCompressionLevel(CompressionLevel.FASTEST);
        } else if (level == 3) {
            zipParameters.setCompressionLevel(CompressionLevel.FAST);
        } else if (level == 6) {
            zipParameters.setCompressionLevel(CompressionLevel.MAXIMUM);
        } else if (level == 8) {
            zipParameters.setCompressionLevel(CompressionLevel.ULTRA);
        } else {
            zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
        }

        if (isEncrypt) {//加密
            zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
            zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
            zipParameters.setEncryptFiles(true);
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
            zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            zipFile.setPassword(password.toCharArray());
        }
    }
}
