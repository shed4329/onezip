package onezip.FX.setting;

import java.io.*;

import onezip.tool.ThemeType;
import onezip.tool.themeAssistant;

public class FX_GUISetting {
    static boolean setting;
    static String cursorPath="";
    static String bootMode="";
    static String bootTheme="";
    static String isdarkMode = "";

    public static String getCursorPath() throws Exception {
        if (!setting){
            getSetting();
        }
        return cursorPath;
    }

    public static String isIsdarkMode() throws Exception{
        if (!setting){
            getSetting();
        }
        return isdarkMode;
    }

    public static void setDarkMode(String isdarkMode) throws IOException{
        FX_GUISetting.isdarkMode = isdarkMode;
        setSetting();
    }

    public void setCursorPath(String cursorPath) throws IOException {
        this.cursorPath = cursorPath;
        setSetting();
    }

    public String getBootMode() throws Exception{
        if (!setting){
            getSetting();
        }
        return bootMode;
    }

    public void setBootMode(String bootMode) throws IOException{
        this.bootMode = bootMode;
        setSetting();
    }

    public String getBootTheme() throws Exception{
        if (!setting){
            getSetting();
        }
        if (bootTheme.equals("fluent")){
            themeAssistant.setThemeType(ThemeType.Fluent);
        }
        return bootTheme;
    }

    public void setBootTheme(String bootTheme) throws IOException{
        this.bootTheme = bootTheme;
        setSetting();
    }

    private static void getSetting() throws Exception{
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
            }else if (i==2){
                bootMode=lineText;
            }else if (i==3){
                bootTheme=lineText;
            } else if (i==4) {
                isdarkMode= lineText;
            }
            i++;
        }
        inputStream.close();
        bufferedReader.close();
    }
    private static void setSetting() throws IOException {
        String string = cursorPath+"\n"+bootMode+"\n"+bootTheme+"\n"+isdarkMode;
        byte[] data = string.getBytes();

        String filePath = System.getProperty("java.io.tmpdir")+File.separator+"OneZip"+File.separator+"FX_GUISetting.txt";
        FileOutputStream outputStream = new FileOutputStream(filePath);
        outputStream.write(data);
        outputStream.close();
    }
}
