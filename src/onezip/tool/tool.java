package onezip.tool;

public class tool {
    public static int FILE_IS_ARCHIVE = 1;
    public static int FILE_IS_NOT_ARCHIVE = 2;
    public static int isFileAnArchive(String filename){
        if (filename.contains(".zip")|filename.contains(".7z")|filename.contains(".rar")){
            return FILE_IS_ARCHIVE;
        }else{
            return FILE_IS_NOT_ARCHIVE;
        }
    }
}
