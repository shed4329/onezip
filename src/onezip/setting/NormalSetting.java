package onezip.setting;

import java.io.*;

public class NormalSetting {
    //预览是否关闭
    static String viewSwitch="false";
    //设置时如果有一个值没有设定，会覆盖之前设定的值，所以用来检验
    static boolean isViewSwitchEdited;
    //以何种方式编码注释
    static String commentDecodingType;
    static boolean isCommentDecodingTypeEdited;
    // 是否已经加载了配置文件
    static boolean isGet = false;


    public static String getCommentDecodingType() {
        if (!isGet){
            try {
                getSetting();
                isGet=true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return commentDecodingType;
    }

    public static void setCommentDecodingType(String commentDecodingType) {
        NormalSetting.commentDecodingType = commentDecodingType;
        isCommentDecodingTypeEdited = true;
        try {
            setSetting();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static Boolean getViewSwitch() {
        if (!isGet){
            try {
                getSetting();
                isGet=true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return !viewSwitch.equals("false");
    }

    public static void setViewSwitch(boolean Switch) {
        if (Switch){
            viewSwitch="true";
        }else{
            viewSwitch="false";
        }
        isViewSwitchEdited = true;
        try {
            setSetting();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void getSetting() throws Exception{

        //配置文件在临时目录下

        FileInputStream inputStream;
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
        String lineText;
        int i=1;
        while ((lineText=bufferedReader.readLine())!=null){
            System.out.println("NormalSetting:"+lineText);
            if (i==1){
               viewSwitch=lineText;
            } else if (i==2) {
                commentDecodingType=lineText;
            }
            i++;
        }
        inputStream.close();
        bufferedReader.close();
    }
    private static void setSetting() throws IOException {
        if (!isViewSwitchEdited){//字符串设置引发的设置
            String temp = commentDecodingType;
            try {
                getSetting();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            commentDecodingType=temp;
        } else if (!isCommentDecodingTypeEdited) {//由预览设置引发
            String temp = viewSwitch;
            try {
                getSetting();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            viewSwitch = temp;
        }
        System.out.println("value:"+viewSwitch);
        String string = viewSwitch+"\n"+commentDecodingType;
        System.out.println("NormalSetting.setSetting:"+"viewSwitch,"+viewSwitch+",comment"+commentDecodingType);
        byte[] data = string.getBytes();

        String filePath = System.getProperty("java.io.tmpdir")+File.separator+"OneZip"+File.separator+"NormalSetting.txt";
        FileOutputStream outputStream = new FileOutputStream(filePath);
        outputStream.write(data);
        outputStream.close();
    }
}
