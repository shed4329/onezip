package cn.shed4329.onezip;

import java.io.IOException;
import java.util.Properties;

/** Provides the Maven build version consistently to desktop and command-line entry points. */
public final class AppVersion {
    /** Version embedded by the build from the project POM. */
    public static final String VERSION = load();

    private AppVersion() {}

    private static String load() {
        Properties properties = new Properties();
        try (var input = AppVersion.class.getResourceAsStream("/onezip-version.properties")) {
            if (input == null) throw new IOException("Missing build version");
            properties.load(input);
            String version = properties.getProperty("version", "").strip();
            if (version.isEmpty() || version.contains("${")) {
                throw new IOException("Unresolved build version");
            }
            return version;
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
