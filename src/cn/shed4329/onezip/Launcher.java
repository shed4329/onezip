package cn.shed4329.onezip;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Routes shell file arguments to the desktop while keeping explicit CLI commands headless. */
public final class Launcher {
    public static void main(String[] args) {
        String[] desktop = desktopArguments(args);
        if (desktop != null) {
            cn.shed4329.onezip.i18n.I18n.initialize();
            cn.shed4329.onezip.themes.fxJava.fluent.MainApplication.main(desktop);
        } else {
            PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
            PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
            int code =
                    cn.shed4329.onezip.cli.CommandLine.run(
                            java.util.Arrays.copyOfRange(args, 1, args.length), out, err);
            if (code != 0) System.exit(code);
        }
    }

    /** Returns shell paths, or null when the caller requested a console command. */
    static String[] desktopArguments(String[] args) {
        if (args.length == 0) return args.clone();
        String first = args[0].toLowerCase(java.util.Locale.ROOT);
        if (first.equals("--cli")) return null;
        if (first.equals("gui") || first.equals("--gui") || first.equals("--open"))
            return java.util.Arrays.copyOfRange(args, 1, args.length);
        return args.clone();
    }
}
