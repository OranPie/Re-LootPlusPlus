package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.io.PackFileReader;

import java.io.BufferedReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AddonConfigIndex {
    public static final class ItemRef {
        private final String kind;
        private final SourceLoc sourceLoc;
        private final String rawLine;

        public ItemRef(String kind, SourceLoc sourceLoc, String rawLine) {
            this.kind = kind;
            this.sourceLoc = sourceLoc;
            this.rawLine = rawLine;
        }

        public String kind() {
            return kind;
        }

        public SourceLoc sourceLoc() {
            return sourceLoc;
        }

        public String rawLine() {
            return rawLine;
        }
    }

    private AddonConfigIndex() {
    }

    public static List<ItemRef> findItemRefs(AddonPack pack, String itemId, int limit) {
        if (itemId == null || itemId.isBlank() || limit <= 0) {
            return List.of();
        }
        Path path = pack.zipPath();
        if (Files.isRegularFile(path)) {
            return scanZip(pack, itemId, limit);
        }
        if (Files.isDirectory(path)) {
            return scanDir(pack, itemId, limit);
        }
        return List.of();
    }

    private static List<ItemRef> scanZip(AddonPack pack, String itemId, int limit) {
        List<ItemRef> out = new ArrayList<>();
        String query = itemId.toLowerCase(Locale.ROOT);
        String shortId = shortId(query);
        try (ZipFile zip = new ZipFile(pack.zipPath().toFile())) {
            zip.stream().forEach(entry -> {
                if (out.size() >= limit || entry.isDirectory()) {
                    return;
                }
                String name = entry.getName();
                if (!isConfigFile(name)) {
                    return;
                }
                List<String> lines;
                try {
                    lines = PackFileReader.readLines(zip, entry);
                } catch (Exception e) {
                    return;
                }
                for (int i = 0; i < lines.size() && out.size() < limit; i++) {
                    String raw = lines.get(i);
                    if (matches(raw, query, shortId)) {
                        SourceLoc loc = new SourceLoc(pack.id(), pack.zipPath().toString(), name, i + 1, raw);
                        out.add(new ItemRef(classify(name, raw), loc, raw));
                    }
                }
            });
        } catch (Exception e) {
            Log.error("Pack", "Failed to scan config for {}", pack.zipPath(), e);
        }
        return out;
    }

    private static List<ItemRef> scanDir(AddonPack pack, String itemId, int limit) {
        List<ItemRef> out = new ArrayList<>();
        String query = itemId.toLowerCase(Locale.ROOT);
        String shortId = shortId(query);
        try {
            Files.walk(pack.zipPath()).forEach(path -> {
                if (out.size() >= limit || !Files.isRegularFile(path)) {
                    return;
                }
                String rel = pack.zipPath().relativize(path).toString().replace('\\', '/');
                if (!isConfigFile(rel)) {
                    return;
                }
                List<String> lines = readLines(path);
                for (int i = 0; i < lines.size() && out.size() < limit; i++) {
                    String raw = lines.get(i);
                    if (matches(raw, query, shortId)) {
                        SourceLoc loc = new SourceLoc(pack.id(), pack.zipPath().toString(), rel, i + 1, raw);
                        out.add(new ItemRef(classify(rel, raw), loc, raw));
                    }
                }
            });
        } catch (Exception e) {
            Log.error("Pack", "Failed to scan config for {}", pack.zipPath(), e);
        }
        return out;
    }

    private static boolean isConfigFile(String path) {
        return path.startsWith("config/") && path.endsWith(".txt");
    }

    private static boolean matches(String raw, String query, String shortId) {
        if (raw == null) {
            return false;
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        if (lowered.contains(query)) {
            return true;
        }
        return shortId != null && lowered.contains(shortId);
    }

    private static String shortId(String query) {
        int colon = query.indexOf(':');
        if (colon >= 0 && colon + 1 < query.length()) {
            return query.substring(colon + 1);
        }
        return null;
    }

    private static String classify(String path, String raw) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("item_additions")) {
            return "ItemAdditions";
        }
        if (lower.contains("thrown")) {
            return "Thrown";
        }
        if (lower.contains("blockdrops") || lower.contains("block_drops")) {
            return "BlockDrops";
        }
        if (lower.contains("recipes")) {
            return "Recipes";
        }
        if (lower.contains("effects")) {
            return "Effects";
        }
        if (lower.contains("fishing")) {
            return "Fishing";
        }
        if (lower.contains("chest")) {
            return "Chest";
        }
        if (lower.contains("entity")) {
            return "EntityDrops";
        }
        if (lower.contains("records")) {
            return "Records";
        }
        if (lower.contains("worldgen")) {
            return "WorldGen";
        }
        if (lower.contains("command")) {
            return "Command";
        }
        if (lower.contains("creative")) {
            return "CreativeMenu";
        }
        if (raw != null) {
            String lowered = raw.toLowerCase(Locale.ROOT);
            if (lowered.contains("type=effect") || lowered.contains("effect=")) {
                return "Effects";
            }
        }
        return "Config";
    }

    private static List<String> readLines(Path path) {
        try {
            byte[] data = Files.readAllBytes(path);
            try {
                return splitLines(decodeStrict(data, StandardCharsets.UTF_8));
            } catch (CharacterCodingException e) {
                return splitLines(new String(data, StandardCharsets.ISO_8859_1));
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String decodeStrict(byte[] data, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(data)).toString();
    }

    private static List<String> splitLines(String content) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader buffered = new BufferedReader(new java.io.StringReader(content))) {
            boolean first = true;
            String line;
            while ((line = buffered.readLine()) != null) {
                if (first) {
                    line = stripBom(line);
                    first = false;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    private static String stripBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }
}
