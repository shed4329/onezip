package onezip.CompressUtils.zip;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class viewUtils {
    public static void fileView(File zip,ArrayList<String> fileList) throws IOException {
        System.out.println("\033[46m"+"CompressUtils.zip.viewUtils fileView\n\n\n\n"+"\033[m");
        InputStream inputStream = new BufferedInputStream(new FileInputStream(zip));
        Charset charset = Charset.forName("gbk");
        ZipInputStream zipInputStream = new ZipInputStream(inputStream,charset);
        ZipEntry zipEntry;
        while ((zipEntry=zipInputStream.getNextEntry())!=null){
            System.out.println(zipEntry);
            fileList.add(zipEntry.toString());
        }
    }
    public static ArrayList<String> getFileNameInPath(ArrayList<String> fileList,String path){
        System.out.println("\033[46m"+"CompressUtils.zip.viewUtils getFileNameInPath\n\n\n\n"+"\033[m");//IDEA输出颜色，多空几行，明显一点
        System.out.println(fileList.size());
        ArrayList<String> filesInPathList = new ArrayList<>();
        if (!path.equals("")){//根目录不应显示..
            filesInPathList.add("..");
        }
        fileList.forEach((e) ->{//第一个是根目录+"/"，然后依次展开
            if (e.length()>=path.length()+1) {
                //System.out.println("e-->" + e);
                if (!(e.length()==path.length()+1)){

                    int index = e.indexOf(path);
                    if (index == 0) {//必须从根目录开算
                        //System.out.println("path:" + e);
                        String restPath;
                        if (path.equals("")) {
                            restPath = e;//如果从根目录开始，从第0位开始
                        } else {
                            restPath = e.substring(path.length() + 1);//如果不是，算上”/“再开始
                        }
                        //System.out.println("restPath:" + restPath);
                        if (restPath.contains("/")) {//目录
                            int nextSeparatorIndex = restPath.indexOf("/");
                            String directoryName = restPath.substring(0, nextSeparatorIndex + 1);
                            //System.out.println("directory Name:" + directoryName);
                            if (!filesInPathList.contains(directoryName)) {//没有才添加
                                filesInPathList.add(directoryName);
                            }
                        } else {//文件
                            //System.out.println("file Name:" + fileName);
                            if (!filesInPathList.contains(restPath)) {
                                filesInPathList.add(restPath);
                            }
                        }
                    }
                }
            }
        });
        return filesInPathList;
    }
}
