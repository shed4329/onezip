package onezip.setting;

import java.io.*;

public class NormalSetting {
    static String viewSwitch;
    static boolean isGet = false;


    public static Boolean getViewSwitch() {
        if (!isGet){
            try {
                getSetting();
                isGet=true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (viewSwitch.equals("false")){
            return false;
        }else{
            return true;
        }
    }

    public static void setViewSwitch(boolean Switch) {
        if (Switch){
            viewSwitch="true";
        }else{
            viewSwitch="false";
        }
        try {
            setSetting();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void getSetting() throws Exception{

        //配置文件在临时目录下

        FileInputStream inputStream=null;
        String tempDir = System.getProperty("java.io.tmpdir");//临时目录
        System.out.println(tempDir);
        String OneZipTempDir = tempDir+ File.separator+"OneZip";//OneZip临时目录
        File OneZipTempFolder = new File(OneZipTempDir);
        String OneZipTempFX = OneZipTempDir+File.separator+"NormalSetting.txt";//配置文件
        File OneZipTempFXFile= new File(OneZipTempFX);
        if (OneZipTempFolder.exists()){
            if (!OneZipTempFXFile.exists()) {
                OneZipTempFXFile.createNewFile();
            }
            inputStream=new FileInputStream(OneZipTempFX);
        }else{
            OneZipTempFolder.mkdirs();
            OneZipTempFXFile.createNewFile();
            inputStream=new FileInputStream(OneZipTempFX);
        }

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String lineText = "";
        int i=1;
        while ((lineText=bufferedReader.readLine())!=null){
            if (i==1){
               viewSwitch=lineText;
            }
        }
        inputStream.close();
        bufferedReader.close();
    }
    private static void setSetting() throws IOException {
        String string = viewSwitch;
        byte[] data = string.getBytes();

        String filePath = System.getProperty("java.io.tmpdir")+File.separator+"OneZip"+File.separator+"NormalSetting.txt";
        FileOutputStream outputStream = new FileOutputStream(filePath);
        outputStream.write(data);
        outputStream.close();
    }
}
