package onezip.Service;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import onezip.CompressUtils.RAR.UnCompressUtil;
import onezip.testOne;
import onezip.themes.fxJava.fluent.Notification;

import java.awt.*;
import java.io.File;

@SuppressWarnings("rawtypes")
public class RARExtractService extends ScheduledService {
    String RARFile;
    String outputPath;
    String password;
    boolean fluentNotice = false;

    public RARExtractService(String RARFile, String outputPath) {
        this.RARFile = RARFile;
        this.outputPath = outputPath;
    }

    public RARExtractService(String RARFile, String outputPath, String password) {
        this.RARFile = RARFile;
        this.outputPath = outputPath;
        this.password = password;
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Task createTask() {
        return new Task<Integer>() {

            @Override
            protected Integer call() throws Exception {
                System.out.println(Thread.currentThread().getName());

                if (password == null || password.isEmpty()) {
                    System.out.println(RARFile+" "+outputPath);
                    UnCompressUtil.unRar5AndOver5(RARFile, outputPath);
                } else {
                    UnCompressUtil.unRar5AndOver5(RARFile, outputPath,password);
                }
                return 1;

            }

            @Override
            protected void updateValue(Integer value) {
                if (value == 0) {
                    testOne.alert("error:no model selected");
                }else{
                    if (fluentNotice) {
                        testOne.alertSuccess();
                        try {
                            Notification notification = new Notification();
                            notification.displayTray("完成", new File(RARFile).getName() + "已成功解压到" + outputPath);
                        } catch (AWTException e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
                RARExtractService.this.cancel();
            }
        };
    }

    public void setFluentNotice(boolean fluentNotice) {
        this.fluentNotice = fluentNotice;
    }
}
