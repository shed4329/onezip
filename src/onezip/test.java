package onezip;

import onezip.FX.setting.FX_GUISetting;

public class test {
    public static void main(String[] args) throws Exception{
        FX_GUISetting fxGuiSetting = new FX_GUISetting();
        fxGuiSetting.setCursorPath("D:\\cursor\\me.png");
        fxGuiSetting.setBootMode("JavaFX");
        fxGuiSetting.setBootTheme("Fluent");
        System.out.println(fxGuiSetting.getCursorPath());
        System.out.println(fxGuiSetting.getBootMode());
        System.out.println(fxGuiSetting.getBootTheme());
    }
}
