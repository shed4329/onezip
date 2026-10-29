package cn.shed4329.onezip.themes.fxJava.fluent.component;

import static cn.shed4329.onezip.i18n.I18n.t;

import cn.shed4329.onezip.engine.ArchiveEngine.ArchiveItem;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * In-memory archive directory index. Names stay in the archive; no filesystem paths are resolved.
 */
public final class ArchiveDirectory {
    public static final class Folder {
        private final String label;
        private final Folder parent;
        private final ArchiveItem item;
        private final Map<String, Folder> folders = new LinkedHashMap<>();
        private final List<ArchiveItem> files = new ArrayList<>();
        private List<ArchiveItem> children;

        private Folder(String label, Folder parent, String path) {
            this.label = label;
            this.parent = parent;
            this.item = new ArchiveItem(path, true, 0);
        }

        public String label() {
            return label;
        }

        public Folder parent() {
            return parent;
        }

        public List<ArchiveItem> children() {
            return children;
        }

        public Collection<Folder> folders() {
            return Collections.unmodifiableCollection(folders.values());
        }
    }

    private final Folder root = new Folder(t("navigation.root"), null, "");
    private final Map<ArchiveItem, Folder> targets = new IdentityHashMap<>();
    private final int entryCount;
    private final Map<ArchiveItem, cn.shed4329.onezip.engine.ArchiveEngine.EntryDetails> details =
            new IdentityHashMap<>();

    public static ArchiveDirectory fromDetails(
            List<cn.shed4329.onezip.engine.ArchiveEngine.EntryDetails> entries,
            BooleanSupplier cancelled) {
        ArchiveDirectory result =
                new ArchiveDirectory(
                        entries.stream()
                                .map(cn.shed4329.onezip.engine.ArchiveEngine.EntryDetails::item)
                                .toList(),
                        cancelled);
        Map<String, cn.shed4329.onezip.engine.ArchiveEngine.EntryDetails> explicit =
                new HashMap<>();
        for (var entry : entries) {
            if (cancelled.getAsBoolean()) throw new CancellationException();
            result.details.put(entry.item(), entry);
            if (entry.item().directory()) explicit.put(entry.item().name(), entry);
        }
        for (var item : result.targets.keySet()) {
            var entry = explicit.get(item.name());
            if (entry != null) result.details.put(item, entry);
        }
        return result;
    }

    public cn.shed4329.onezip.engine.ArchiveEngine.EntryDetails details(ArchiveItem item) {
        return details.get(item);
    }

    public ArchiveDirectory(List<ArchiveItem> entries, BooleanSupplier cancelled) {
        entryCount = entries.size();
        List<Folder> all = new ArrayList<>();
        all.add(root);
        for (ArchiveItem entry : entries) {
            if (cancelled.getAsBoolean()) throw new CancellationException();
            String[] parts = entry.name().split("/", -1);
            Folder parent = root;
            int last = parts.length - 1;
            while (last >= 0 && parts[last].isEmpty()) last--;
            for (int i = 0; i <= last; i++) {
                if (parts[i].isEmpty()) continue;
                if (i == last && !entry.directory()) {
                    parent.files.add(entry);
                    break;
                }
                Folder child = parent.folders.get(parts[i]);
                if (child == null) {
                    child = new Folder(parts[i], parent, parent.item.name() + parts[i] + "/");
                    parent.folders.put(parts[i], child);
                    all.add(child);
                    targets.put(child.item, child);
                }
                parent = child;
            }
        }
        for (Folder folder : all) {
            if (cancelled.getAsBoolean()) throw new CancellationException();
            List<ArchiveItem> children =
                    new ArrayList<>(folder.folders.size() + folder.files.size());
            for (Folder child : folder.folders.values()) children.add(child.item);
            children.addAll(folder.files);
            folder.children = List.copyOf(children);
        }
    }

    public Folder root() {
        return root;
    }

    public Folder folder(ArchiveItem item) {
        return targets.get(item);
    }

    public int entryCount() {
        return entryCount;
    }

    public String path(Folder folder) {
        return folder.item.name();
    }

    public static String fileName(ArchiveItem item) {
        String name = item.name();
        int end = name.length();
        while (end > 0 && name.charAt(end - 1) == '/') end--;
        int slash = name.lastIndexOf('/', end - 1);
        return name.substring(slash + 1, end);
    }
}
