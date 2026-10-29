package cn.shed4329.onezip;

/** Console launcher shim that prepends --cli and delegates to the single application entry. */
public final class CliLauncher {
    private CliLauncher() {}

    public static void main(String[] args) {
        String[] forwarded = new String[args.length + 1];
        forwarded[0] = "--cli";
        System.arraycopy(args, 0, forwarded, 1, args.length);
        Launcher.main(forwarded);
    }
}
