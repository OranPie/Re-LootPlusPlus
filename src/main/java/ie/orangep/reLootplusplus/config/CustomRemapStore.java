package ie.orangep.reLootplusplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CustomRemapStore {
    private static final String FILE_NAME = "relootplusplus_remap.txt";
    private static volatile Map<String, String> remap;

    private CustomRemapStore() {
    }

    public static String map(String raw) {
        return map(raw, null, null, null);
    }

    public static String map(String raw, LegacyWarnReporter warnReporter, SourceLoc loc, String context) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return raw;
        }
        Map<String, String> map = remap();
        if (map.isEmpty()) {
            return raw;
        }
        String normalized = normalizeKey(trimmed);
        String mapped = map.get(trimmed);
        if (mapped == null) {
            mapped = map.get(normalized);
        }
        if (mapped == null) {
            mapped = map.get(trimmed.toLowerCase(Locale.ROOT));
        }
        if (mapped == null) {
            mapped = map.get(normalized.toLowerCase(Locale.ROOT));
        }
        if (mapped == null || mapped.isBlank()) {
            return raw;
        }
        if (warnReporter != null && !mapped.equals(raw)) {
            String prefix = context == null || context.isBlank() ? "" : context + " ";
            warnReporter.warnOnce("CustomRemap", prefix + "'" + raw + "' -> '" + mapped + "'", loc);
        }
        return mapped;
    }

    private static Map<String, String> remap() {
        Map<String, String> cached = remap;
        if (cached != null) {
            return cached;
        }
        synchronized (CustomRemapStore.class) {
            if (remap != null) {
                return remap;
            }
            remap = load();
            return remap;
        }
    }

    private static Map<String, String> load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            writeDefault(file);
            return Collections.emptyMap();
        }
        try {
            Map<String, String> map = new LinkedHashMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = stripComment(line);
                if (trimmed.isEmpty()) {
                    continue;
                }
                int idx = trimmed.indexOf("->");
                int sepLen = 2;
                if (idx < 0) {
                    idx = trimmed.indexOf('=');
                    sepLen = 1;
                }
                if (idx <= 0 || idx >= trimmed.length() - sepLen) {
                    continue;
                }
                String from = trimmed.substring(0, idx).trim();
                String to = trimmed.substring(idx + sepLen).trim();
                if (from.isEmpty() || to.isEmpty()) {
                    continue;
                }
                String key = normalizeKey(from);
                map.putIfAbsent(from, to);
                map.putIfAbsent(key, to);
                map.putIfAbsent(from.toLowerCase(Locale.ROOT), to);
                map.putIfAbsent(key.toLowerCase(Locale.ROOT), to);
            }
            return map;
        } catch (Exception e) {
            Log.error("Config", "Failed to read remap config {}", file, e);
            return Collections.emptyMap();
        }
    }

    private static void writeDefault(Path file) {
        try {
            Files.createDirectories(file.getParent());
            String content = """
                # relootplusplus remap rules
                # format: from=to or from->to
                # examples:
                # lootplusplus:textures/item/galaxyleggings.png=lootplusplus:textures/items/galaxyLeggings.png
                # minecraft:textures/block/door_iron_lower.png=minecraft:textures/block/iron_door_bottom.png
                """;
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Config", "Failed to write remap config {}", file, e);
        }
    }

    private static String stripComment(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
            return "";
        }
        int hash = trimmed.indexOf('#');
        if (hash >= 0) {
            trimmed = trimmed.substring(0, hash).trim();
        }
        int slashes = trimmed.indexOf("//");
        if (slashes >= 0) {
            trimmed = trimmed.substring(0, slashes).trim();
        }
        return trimmed;
    }

    private static String normalizeKey(String raw) {
        return raw.replace('\\', '/').trim();
    }
}
