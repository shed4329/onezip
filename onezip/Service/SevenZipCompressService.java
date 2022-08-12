package onezip.Service;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import onezip.CompressUtils.SevenZip.CompressUtils;
import onezip.testOne;

import java.io.File;
import java.util.ArrayList;

public class SevenZipCompressService extends ScheduledService {
    ArrayList<File> toCompress=new ArrayList<>();
    String compressTo;
    String password;
    int type=0;
    public SevenZipCompressService(ArrayList<File> compressedFile,String compressTo){
        toCompress.addAll(compressedFile);
        this.compressTo=compressTo;
        type=1;
    }
    public SevenZipCompressService(ArrayList<File> compressedFile,String compressTo,String password){
        toCompress.addAll(compressedFile);
        this.compressTo=compressTo;
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
                    CompressUtils.compress(toCompress,compressTo);
                    return 1;
                }else if (type==2){
                    CompressUtils.compress(toCompress,compressTo,password);
                    return 2;
                }
                return 0;
            }

            @Override
            protected void updateValue(Integer value) {
                if (value==0){
                    testOne.alert("error:no model selected");
                }else if (value==1||value==2){
                    testOne.alertSuccess();
                }
                SevenZipCompressService.this.cancel();
            }
        };
    }

}