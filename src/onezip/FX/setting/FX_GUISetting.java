package onezip.FX.setting;

import java.io.*;

public class FX_GUISetting {
    boolean setting;
    String cursorPath="";

    public String getCursorPath() throws Exception {
        if (!setting){
            getSetting();
        }
        return cursorPath;
    }

    public void setCursorPath(String cursorPath) throws IOException {
        this.cursorPath = cursorPath;
        setSetting();
    }

    private void getSetting() throws Exception{
        setting=true;
       //配置文件在临时目录下

        FileInputStream inputStream=null;
        String tempDir = System.getProperty("java.io.tmpdir");//临时目录
        System.out.println(tempDir);
        String OneZipTempDir = tempDir+File.separator+"OneZip";//OneZip临时目录
        File OneZipTempFolder = new File(OneZipTempDir);
        String OneZipTempFX = OneZipTempDir+File.separator+"FX_GUISetting.txt";//配置文件
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
                lineText.replace("/u005C",File.separator);
                lineText.replace("/",File.separator);
                cursorPath=lineText;
            }
        }
        inputStream.close();
        bufferedReader.close();
    }
    private void setSetting() throws IOException {
        String string = cursorPath;
        byte[] data = string.getBytes();

        String filePath = System.getProperty("java.io.tmpdir")+File.separator+"OneZip"+File.separator+"FX_GUISetting.txt";
        FileOutputStream outputStream = new FileOutputStream(filePath);
        outputStream.write(data);
        outputStream.close();
    }
}
