package ie.orangep.reLootplusplus.pack;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.io.PackFileReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackIndex {
    public static final class LineRecord {
        private final SourceLoc sourceLoc;
        private final String rawLine;

        public LineRecord(SourceLoc sourceLoc, String rawLine) {
            this.sourceLoc = sourceLoc;
            this.rawLine = rawLine;
        }

        public SourceLoc sourceLoc() {
            return sourceLoc;
        }

        public String rawLine() {
            return rawLine;
        }
    }

    private final Map<AddonPack, Map<String, List<LineRecord>>> linesByPack = new HashMap<>();

    public void indexAll(List<AddonPack> packs) {
        for (AddonPack pack : packs) {
            index(pack);
        }
    }

    public Map<String, List<LineRecord>> filesFor(AddonPack pack) {
        return linesByPack.getOrDefault(pack, Collections.emptyMap());
    }

    public int totalFileCount() {
        int total = 0;
        for (Map<String, List<LineRecord>> byFile : linesByPack.values()) {
            total += byFile.size();
        }
        return total;
    }

    public int totalLineCount() {
        int total = 0;
        for (Map<String, List<LineRecord>> byFile : linesByPack.values()) {
            for (List<LineRecord> lines : byFile.values()) {
                total += lines.size();
            }
        }
        return total;
    }

    private void index(AddonPack pack) {
        Map<String, List<LineRecord>> fileMap = new HashMap<>();
        try (ZipFile zip = new ZipFile(pack.zipPath().toFile())) {
            String prefix = findZipContentPrefix(zip);
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                String rawName = entry.getName();
                String name = prefix.isEmpty() ? rawName : stripPrefix(rawName, prefix);
                if (name == null || !isConfigText(name)) {
                    continue;
                }
                List<String> rawLines = PackFileReader.readLines(zip, entry);
                List<LineRecord> records = new ArrayList<>(rawLines.size());
                for (int i = 0; i < rawLines.size(); i++) {
                    int lineNumber = i + 1;
                    String raw = rawLines.get(i);
                    SourceLoc loc = new SourceLoc(
                        pack.id(),
                        pack.zipPath().toString(),
                        name,
                        lineNumber,
                        raw
                    );
                    records.add(new LineRecord(loc, raw));
                }
                fileMap.put(name, records);
            }
        } catch (Exception e) {
            Log.error("Pack", "Failed to index pack {}", pack.zipPath(), e);
        }
        linesByPack.put(pack, fileMap);
    }

    /**
     * Returns the single top-level directory prefix shared by ALL entries in a zip, or "".
     * Example: if every entry starts with "LuckyBlockOlymp/", returns "LuckyBlockOlymp/".
     * If any entry sits at the root level, returns "" (no stripping needed).
     */
    public static String findZipContentPrefix(ZipFile zip) {
        String prefix = null;
        for (ZipEntry entry : Collections.list(zip.entries())) {
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            int slash = name.indexOf('/');
            if (slash < 0) {
                return "";
            }
            String dir = name.substring(0, slash + 1);
            if (prefix == null) {
                prefix = dir;
            } else if (!prefix.equals(dir)) {
                return "";
            }
        }
        return prefix != null ? prefix : "";
    }

    private static String stripPrefix(String name, String prefix) {
        if (name.startsWith(prefix)) {
            return name.substring(prefix.length());
        }
        return null;
    }

    private boolean isConfigText(String name) {
        if (name == null || !name.endsWith(".txt")) {
            return false;
        }
        if (name.startsWith("config/")) {
            return true;
        }
        return "recipes.txt".equals(name) || "config/recipes.txt".equals(name);
    }
}
