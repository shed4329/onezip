package onezip.themes.fxJava.fluent;

import java.awt.*;

public class test {
    public static void main(String[] args) {
        Notification notification = new Notification();
        try {
            notification.displayTray("1","2");
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }
    }
}
