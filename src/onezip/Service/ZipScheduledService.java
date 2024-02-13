package onezip.Service;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import onezip.CompressUtils.zip.zipUtils;
import onezip.testOne;
import onezip.themes.fxJava.fluent.Notification;
import onezip.tool.alertAssistant;
import onezip.tool.taskProgress;

import java.awt.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;

import static onezip.component.oneAlert.alert;
import static onezip.component.oneAlert.alertSuccess;

public class ZipScheduledService extends ScheduledService {
    int type;
    int level;
    int method;
    File input;
    File output;
    File inputFile;
    ArrayList<File> inputFiles;
    ArrayList<File> inputFolders;
    boolean isEncrypt;
    String password;
    String extractPassword;
    Charset charset;
    boolean fluentNotice = false;
    private int mode=2;//模式，确定应该执行何种操作
    public ZipScheduledService(int type,int level,int method,ArrayList<File> inputFiles,ArrayList<File> inputFolders,File output,boolean isEncrypt,String password) throws Exception {//压缩文件调用
        System.out.println("zip service");
        this.type = type;
        this.level = level;
        this.method = method;
        this.inputFiles = inputFiles;
        this.inputFolders=inputFolders;
        this.output = output;
        this.isEncrypt = isEncrypt;
        this.password = password;
    }
    public ZipScheduledService(int type,File input, File output, Charset charset){//解压文件调用
        this.type = type;
        this.input=input;
        this.output=output;
        this.charset=charset;
    }
    public ZipScheduledService(int type,File input, File output, Charset charset,String extractPassword){//解压文件调用
        this.type = type;
        this.input=input;
        this.output=output;
        this.charset=charset;
        this.extractPassword=extractPassword;
    }
    @Override
    protected Task<Number> createTask() {
        return new Task<Number>() {
            @Override
            protected Number call() throws Exception {//非UI线程处理,避免压缩包过大卡死
                System.out.println(Thread.currentThread().getName());
                System.out.println(inputFiles);
                System.out.println(inputFolders);
                taskProgress progress = new taskProgress();
                if (type==1){//压缩
                    if (inputFiles!=null&&inputFiles.size()!=0){
                        System.out.println(inputFiles);
                        zipUtils.zipFiles(inputFiles,output,method,level,isEncrypt,password);
                        System.out.println(inputFiles);
                    }

                    if (inputFolders!=null&&inputFolders.size()!=0){
                        if (inputFolders.size()==1){
                            System.out.println("zipUtils.zipFolder();");
                            zipUtils.zipFolder(inputFolders.get(0),output,method,level,isEncrypt,password);
                        }else{
                            mode=0;
                        }

                        System.out.println(inputFolders);
                    }

                }else  if(type ==2){//解压
                    if (extractPassword==null||extractPassword.length()==0){
                        zipUtils.unzip(input,output,charset,progress);
                    }else{
                        zipUtils.unzip(input,output,charset,extractPassword,progress);
                    }
                }
                mode = -1;
                return progress.getProgress();
            }

            @Override
            protected void updateValue(Number value) {
                if (mode==0){
                    alert("错误！不能添加多个文件夹，稍后可以在【设置】->【通用】->【将多个文件夹复制到同一文件夹后添加到压缩文件中】");
                }else{
                    alertAssistant.alertSuccess();
                    if (fluentNotice&&type==2){
                        try {
                            Notification notification = new Notification();
                            notification.displayTray("完成", input.getName() + "已成功解压到" + output.getPath());
                        } catch (AWTException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                    ZipScheduledService.this.cancel();

            }
        };
    }

    public void setFluentNotice(boolean fluentNotice) {
        this.fluentNotice = fluentNotice;
    }
}