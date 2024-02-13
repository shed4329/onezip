package onezip.Service;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import onezip.CompressUtils.SevenZip.ExtractUtils;
import onezip.testOne;
import onezip.themes.fxJava.fluent.Notification;

import java.awt.*;
import java.io.File;

import static onezip.component.oneAlert.alert;
import static onezip.component.oneAlert.alertSuccess;

public class SevenZipExtractService extends ScheduledService {
    String toExtract;
    String extractTo;
    String password;
    int type=0;
    boolean fluentNotice = false;
    public SevenZipExtractService(String toExtract,String extractTo){
        this.toExtract=toExtract;
        this.extractTo=extractTo;
        type=1;
    }
    public SevenZipExtractService(String toExtract,String extractTo,String password){
        this.toExtract=toExtract;
        this.extractTo=extractTo;
        this.password=password;
        type=2;
    }
    @Override
    protected Task createTask() {
        return new Task<Integer>() {

            @Override
            protected Integer call() throws Exception {
                System.out.println(Thread.currentThread().getName());
                if (type==1){
                    ExtractUtils.extract(toExtract,extractTo);
                    return 1;
                }else if (type==2){
                    ExtractUtils.extract(toExtract,extractTo,password);
                    return 2;
                }
                return 0;
            }

            @Override
            protected void updateValue(Integer value) {
                if (value==0){
                    alert("error:no model selected");
                }else if (value==1||value==2){
                    if (fluentNotice) {
                        try {
                            Notification notification = new Notification();
                            notification.displayTray("完成", new File(toExtract).getName() + "已成功解压到" + extractTo);
                        } catch (AWTException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    alertSuccess();
                }
                SevenZipExtractService.this.cancel();
            }
        };
    }

    public void setFluentNotice(boolean fluentNotice) {
        this.fluentNotice = fluentNotice;
    }
}