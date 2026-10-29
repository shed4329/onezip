package cn.shed4329.onezip.cli;

import cn.shed4329.onezip.engine.ArchiveEngine;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Headless command dispatch; intentionally independent of JavaFX and archive libraries. */
public final class CommandLine {
    public static final String VERSION = cn.shed4329.onezip.AppVersion.VERSION;

    @FunctionalInterface
    interface PasswordPrompt {
        char[] read() throws IOException;
    }

    private CommandLine() {}

    /**
     * Executes one parsed command without loading the JavaFX toolkit.
     *
     * @param args command-line arguments without the executable name
     * @param out destination for command results and progress
     * @param err destination for usage and failure diagnostics
     * @return a process-style status code; zero indicates success
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(
                args,
                out,
                err,
                System.getenv(),
                () -> {
                    Console console = System.console();
                    if (console == null) throw new Usage("沒有互動終端；請改用 --password-env 或 -p。");
                    char[] value = console.readPassword("Password: ");
                    if (value == null) throw new Usage("密碼輸入已結束。");
                    return value;
                });
    }

    static int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            PasswordPrompt prompt) {
        char[] password = null;
        try {
            Options options = parse(args);
            if (options.help) {
                help(out);
                return 0;
            }
            if (options.version) {
                out.println("OneZip " + VERSION);
                return 0;
            }
            password = options.password(environment, prompt);
            if (options.command.equals("compress") && password != null && password.length == 0)
                throw new Usage("加密密碼不可為空。");
            ArchiveEngine engine = new ArchiveEngine();
            AtomicBoolean cancelled = new AtomicBoolean();
            CountDownLatch finished = new CountDownLatch(1);
            Thread caller = Thread.currentThread();
            Thread shutdown =
                    new Thread(
                            () -> {
                                cancelled.set(true);
                                caller.interrupt();
                                try {
                                    finished.await(10, TimeUnit.SECONDS);
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            "onezip-cli-cleanup");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                ArchiveEngine.Progress progress = options.quiet ? null : new ConsoleProgress(err);
                Path archive = Path.of(options.paths.getFirst());
                switch (options.command) {
                    case "compress" -> {
                        List<Path> sources =
                                options.paths.subList(1, options.paths.size()).stream()
                                        .map(Path::of)
                                        .toList();
                        Path result =
                                engine.compress(
                                        sources,
                                        archive,
                                        options.format,
                                        options.level,
                                        password,
                                        cancelled::get,
                                        progress,
                                        options.threads);
                        if (!options.quiet) out.println("Created: " + escaped(result.toString()));
                    }
                    case "extract" -> {
                        Path destination =
                                options.output == null
                                        ? Path.of(stem(archive))
                                        : Path.of(options.output);
                        Path result =
                                engine.extract(
                                        archive,
                                        destination,
                                        password,
                                        cancelled::get,
                                        progress,
                                        options.threads);
                        if (!options.quiet) out.println("Extracted: " + escaped(result.toString()));
                    }
                    case "list" -> {
                        List<ArchiveEngine.ArchiveItem> entries =
                                engine.list(archive, password, cancelled::get);
                        out.println("Type\tSize\tName");
                        for (ArchiveEngine.ArchiveItem entry : entries) {
                            if (cancelled.get() || Thread.currentThread().isInterrupted())
                                throw new InterruptedIOException("操作已取消");
                            out.printf(
                                    Locale.ROOT,
                                    "%s\t%d\t%s%n",
                                    entry.directory() ? "D" : "F",
                                    entry.size(),
                                    escaped(entry.name()));
                        }
                    }
                    case "test" -> {
                        engine.test(archive, password, cancelled::get, progress);
                        if (!options.quiet) out.println("OK: " + escaped(archive.toString()));
                    }
                    default -> throw new AssertionError(options.command);
                }
                if (out.checkError() || err.checkError()) throw new IOException("命令列輸出失敗。");
                return 0;
            } finally {
                finished.countDown();
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                } catch (IllegalStateException ignored) {
                    /* Shutdown is already waiting for cleanup. */
                }
            }
        } catch (Usage | InvalidPathException ex) {
            err.println("Usage error: " + escaped(ex.getMessage()));
            err.println("Run onezip --help for usage.");
            return 2;
        } catch (InterruptedIOException ex) {
            err.println("Cancelled: " + escaped(ex.getMessage()));
            return 130;
        } catch (IOException | IllegalArgumentException | ArithmeticException ex) {
            err.println("Error: " + escaped(ex.getMessage()));
            return 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private static Options parse(String[] args) throws Usage {
        if (args.length == 0) throw new Usage("請指定命令。");
        Options o = new Options();
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("--help") || first.equals("-h") || first.equals("help")) {
            if (args.length != 1) throw new Usage("help 不接受其他參數。");
            o.help = true;
            return o;
        }
        if (first.equals("--version") || first.equals("-v")) {
            if (args.length != 1) throw new Usage("version 不接受其他參數。");
            o.version = true;
            return o;
        }
        o.command =
                switch (first) {
                    case "a", "c", "compress" -> "compress";
                    case "x", "extract" -> "extract";
                    case "l", "list" -> "list";
                    case "t", "test" -> "test";
                    default -> throw new Usage("未知命令: " + args[0]);
                };
        Set<String> seen = new HashSet<>();
        boolean literal = false;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (literal) {
                o.paths.add(arg);
                continue;
            }
            if (arg.equals("--")) {
                literal = true;
                continue;
            }
            if (arg.equals("-h") || arg.equals("--help")) {
                o.help = true;
                continue;
            }
            if (!arg.startsWith("-")) {
                o.paths.add(arg);
                continue;
            }
            String option = arg, value = null;
            int colon = arg.indexOf(':'), equals = arg.indexOf('=');
            if (arg.startsWith("--") && equals > 0) {
                option = arg.substring(0, equals);
                value = arg.substring(equals + 1);
            } else if (colon > 0) {
                option = arg.substring(0, colon);
                value = arg.substring(colon + 1);
            }
            String key =
                    switch (option) {
                        case "-o", "--output" -> "output";
                        case "-f", "-fmt", "--format" -> "format";
                        case "-l", "--level" -> "level";
                        case "-t", "--threads" -> "threads";
                        case "-p", "--password" -> "password";
                        case "--password-env" -> "password-env";
                        case "--ask-password" -> "ask-password";
                        case "-q", "--quiet" -> "quiet";
                        default -> throw new Usage("未知選項: " + option);
                    };
            if (!seen.add(key)) throw new Usage("重複選項: " + option);
            if (key.equals("quiet") || key.equals("ask-password")) {
                if (value != null) throw new Usage(option + " 不接受值。");
                if (key.equals("quiet")) o.quiet = true;
                else o.askPassword = true;
                continue;
            }
            if (value == null) {
                if (++i == args.length || args[i].startsWith("--"))
                    throw new Usage(option + " 缺少值。");
                value = args[i];
            }
            switch (key) {
                case "output" -> {
                    if (value.isBlank()) throw new Usage("輸出資料夾不可為空。");
                    o.output = value;
                }
                case "format" -> o.requestedFormat = value.toLowerCase(Locale.ROOT);
                case "level" -> {
                    try {
                        o.level = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new Usage("壓縮等級應為 0–9。");
                    }
                    if (o.level < 0 || o.level > 9) throw new Usage("壓縮等級應為 0–9。");
                }
                case "threads" -> {
                    try {
                        o.threads = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new Usage("執行緒數應為0–64。");
                    }
                    if (o.threads < 0 || o.threads > 64) throw new Usage("執行緒數應為0–64。");
                }
                case "password" -> o.passwordValue = value;
                case "password-env" -> {
                    if (value.isBlank()) throw new Usage("環境變數名稱不可為空。");
                    o.passwordEnvironment = value;
                }
                default -> throw new AssertionError(key);
            }
        }
        if (o.help) return o;
        int methods =
                (o.passwordValue != null ? 1 : 0)
                        + (o.passwordEnvironment != null ? 1 : 0)
                        + (o.askPassword ? 1 : 0);
        if (methods > 1) throw new Usage("請只使用一種密碼輸入方式。");
        if (o.paths.isEmpty()
                || (o.command.equals("compress") ? o.paths.size() < 2 : o.paths.size() != 1))
            throw new Usage(o.command.equals("compress") ? "壓縮需要輸出壓縮檔與至少一個來源。" : "此命令需要一個壓縮檔。");
        if (o.command.equals("compress")) {
            if (o.output != null) throw new Usage("壓縮的輸出是第一個路徑，不使用 -o。");
            String name = o.paths.getFirst().toLowerCase(Locale.ROOT);
            o.format = ArchiveEngine.outputFormat(name);
            if (o.format == null)
                throw new Usage(
                        "支援 .zip、.7z、.tar、.gz、.gzip、.lz4、.xz、.bz2、.bzip2 及相應 TAR 副檔名（含"
                                + " .tgz／.txz／.tbz2）。");
            if (o.requestedFormat != null) {
                ArchiveEngine.Format requested =
                        switch (o.requestedFormat) {
                            case "zip" -> ArchiveEngine.Format.ZIP;
                            case "7z" -> ArchiveEngine.Format.SEVEN_Z;
                            case "tar" -> ArchiveEngine.Format.TAR;
                            case "gz", "gzip" -> ArchiveEngine.Format.GZIP;
                            case "tar.gz", "tar.gzip", "tgz" -> ArchiveEngine.Format.TAR_GZIP;
                            case "lz4" -> ArchiveEngine.Format.LZ4;
                            case "tar.lz4" -> ArchiveEngine.Format.TAR_LZ4;
                            case "xz" -> ArchiveEngine.Format.XZ;
                            case "tar.xz", "txz" -> ArchiveEngine.Format.TAR_XZ;
                            case "bz2", "bzip2" -> ArchiveEngine.Format.BZIP2;
                            case "tar.bz2", "tar.bzip2", "tbz2", "tbz" ->
                                    ArchiveEngine.Format.TAR_BZIP2;
                            default ->
                                    throw new Usage(
                                            "格式應為 zip、7z、tar、gzip、lz4、xz、bzip2 或相應 tar 格式。");
                        };
                if (requested != o.format) throw new Usage("指定格式與輸出檔名副檔名不符。");
            }
        } else {
            if (seen.contains("level") || seen.contains("format"))
                throw new Usage("--level 和 --format 僅適用於壓縮。");
            if (seen.contains("threads") && !o.command.equals("extract"))
                throw new Usage("--threads 僅適用於壓縮與解壓。");
            if (!o.command.equals("extract") && o.output != null)
                throw new Usage("--output 僅適用於解壓。");
        }
        return o;
    }

    private static String stem(Path archive) throws Usage {
        Path name = archive.getFileName();
        if (name == null) throw new Usage("無效的壓縮檔路徑。");
        String s = name.toString(), lower = s.toLowerCase(Locale.ROOT);
        boolean compound = false;
        for (String suffix :
                List.of(".tar.gzip", ".tar.bzip2", ".tar.lz4", ".tar.bz2", ".tar.xz", ".tar.gz"))
            if (lower.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
                compound = true;
                break;
            }
        if (!compound) {
            int dot = s.lastIndexOf('.');
            if (dot > 0) s = s.substring(0, dot);
        }
        if (s.isBlank() || s.equals(".") || s.equals("..")) throw new Usage("請以 -o 指定解壓資料夾。");
        return s;
    }

    static String escaped(String text) {
        if (text == null) return "Unknown error";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '\t' -> result.append("\\t");
                case '\r' -> result.append("\\r");
                case '\n' -> result.append("\\n");
                default -> {
                    if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029')
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.toString();
    }

    private static void help(PrintStream out) {
        out.println(
                """
                OneZip — independent Java ZIP / 7z / RAR / TAR / GZIP readers
                Usage:
                  OneZip.exe                             Open the GUI
                  OneZip.exe <archive>                   Browse an archive in the GUI
                  OneZip.exe --cli c <archive> <sources...> [options]
                  OneZip.exe --cli x <archive> [-o <directory>] [options]
                  OneZip.exe --cli l <archive> [options]
                  OneZip.exe --cli t <archive> [options]
                  OneZipCLI.exe <command> [options]       Adds --cli to the same entry

                JAR: java -jar onezip-all.jar --cli <command> [options]

                Commands: c/a/compress = create; x/extract = extract;
                          l/list = list metadata; t/test = decode and verify, without files.
                Options:
                  -f, --format <format>        zip|7z|tar|gzip|lz4|xz|bzip2 or tar.<codec>
                  -l, --level 0..9             Compression level (default 5)
                  -t, --threads 0..64          Compress/extract workers; 0=auto, 1=sequential
                  -o, --output <directory>     Extract here (default ./<archive stem>)
                  -p, --password <value>       Password supplied as an argument
                      --password-env <name>    Read password from an environment variable
                      --ask-password           Read a hidden password from an interactive terminal
                  -q, --quiet                  Suppress progress and success messages
                  -h, --help                   Show help
                      --version                Show version (top-level option)
                  --                           Treat remaining arguments as paths

                Also accepts -fmt:7z, -l:5, -o:path and -p:value.
                Existing archives/files are never overwritten; c and a create new archives.
                List writes escaped TSV to stdout; progress/errors go to stderr.
                Read/extract: ZIP, 7z, RAR4 LZ/PPMd, RAR5, TAR, GZIP, LZ4, XZ, BZIP2 and TAR variants.
                Create: ZIP, 7z, TAR, GZIP, LZ4, XZ, BZIP2 and TAR variants.
                GZIP takes one regular file; use TAR.GZ for directories/multiple files.
                TAR stores files without compression. TAR/GZIP have no password encryption.
                GZIP concatenated members are joined; TAR links/sparse/devices are refused.
                LZ4 frame and TAR.LZ4: create/extract/list/test; no legacy/raw blocks or external dictionaries.
                LZ4 takes one file, naming output from the .lz4 archive basename; use TAR.LZ4 for multiple files.
                LZ4 encoding/decoding is sequential; level 0 stores blocks, levels 1..9 use the fast matcher.
                XZ/BZIP2 and TAR.XZ/TAR.BZ2: create/extract/list/test; .txz/.tbz/.tbz2 aliases accepted.
                XZ reads LZMA2, Delta/x86 filters and None/CRC32/CRC64/SHA256 checks; writes LZMA2/CRC64.
                BZIP2 levels 1..9 select 100..900KB blocks; level 0 maps to 1 (not STORE).
                XZ/BZIP2 codecs are sequential; TAR variants can parallelize indexed file writes.
                Framed stream listing/testing needs private temporary disk space for decoded contents.
                RAR4/5 encrypted data and headers are supported with the password options.
                RAR split volumes, custom VM code and links are not supported.
                ZIP AES writing and ZIP/7z/RAR password reading are supported; no 7z AES writing.
                Exit codes: 0 success, 1 operation failed, 2 usage error, 130 interrupted.
                """);
    }

    private static final class Options {
        String command, requestedFormat, output, passwordValue, passwordEnvironment;
        ArchiveEngine.Format format;
        int level = 5, threads;
        boolean quiet, help, version, askPassword;
        final List<String> paths = new ArrayList<>();

        char[] password(Map<String, String> env, PasswordPrompt prompt) throws IOException {
            if (askPassword) return prompt.read();
            if (passwordValue != null) return passwordValue.toCharArray();
            if (passwordEnvironment != null) {
                String value = env.get(passwordEnvironment);
                if (value == null) throw new Usage("找不到密碼環境變數。");
                return value.toCharArray();
            }
            return null;
        }
    }

    private static final class Usage extends IOException {
        @Serial private static final long serialVersionUID = 1L;

        Usage(String message) {
            super(message);
        }
    }

    private static final class ConsoleProgress implements ArchiveEngine.Progress {
        final PrintStream err;
        long last;

        ConsoleProgress(PrintStream err) {
            this.err = err;
        }

        public void update(long completed, long total, String file) {
            long now = System.nanoTime();
            boolean done = "完成".equals(file);
            if (last != 0 && !done && now - last < TimeUnit.MILLISECONDS.toNanos(250)) return;
            last = now;
            double percent = done ? 100 : total == 0 ? 0 : Math.min(99, completed * 100.0 / total);
            err.printf(Locale.ROOT, "%.1f%% %s%n", percent, escaped(file));
        }
    }
}
