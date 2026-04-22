package ie.orangep.reLootplusplus.config;

import ie.orangep.reLootplusplus.diagnostic.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TextureAdditionStore {
    private static final String FILE_NAME = "relootplusplus_texture_additions.txt";
    private static volatile TextureAdditionStore loaded;

    private final Map<String, String> textureMap;
    private final Map<String, String> modelMap;
    private final Map<String, String> generatedModels;

    private TextureAdditionStore(Map<String, String> textureMap, Map<String, String> modelMap, Map<String, String> generatedModels) {
        this.textureMap = textureMap;
        this.modelMap = modelMap;
        this.generatedModels = generatedModels;
    }

    public static TextureAdditionStore load() {
        TextureAdditionStore cached = loaded;
        if (cached != null) {
            return cached;
        }
        synchronized (TextureAdditionStore.class) {
            if (loaded != null) {
                return loaded;
            }
            loaded = read();
            return loaded;
        }
    }

    public String mapTextureRef(String raw) {
        if (raw == null) {
            return null;
        }
        String key = normalizeTextureRef(raw);
        String mapped = textureMap.get(key);
        if (mapped == null) {
            return raw;
        }
        return applyNamespaceFallback(raw, mapped);
    }

    public String mapModelRef(String raw) {
        if (raw == null) {
            return null;
        }
        String key = normalizeModelRef(raw);
        String mapped = modelMap.get(key);
        if (mapped == null) {
            return raw;
        }
        return applyNamespaceFallback(raw, mapped);
    }

    public String generatedModelTexture(String modelRef) {
        if (modelRef == null) {
            return null;
        }
        String key = normalizeModelRef(modelRef);
        return generatedModels.get(key);
    }

    private static TextureAdditionStore read() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            writeDefault(file);
            return new TextureAdditionStore(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
        Map<String, String> textures = new LinkedHashMap<>();
        Map<String, String> models = new LinkedHashMap<>();
        Map<String, String> generated = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = stripComment(line);
                if (trimmed.isEmpty()) {
                    continue;
                }
                String prefix = null;
                String rest = trimmed;
                int colon = trimmed.indexOf(':');
                int space = trimmed.indexOf(' ');
                if (colon > 0 && (space < 0 || colon < space)) {
                    prefix = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    rest = trimmed.substring(colon + 1).trim();
                } else if (space > 0) {
                    prefix = trimmed.substring(0, space).trim().toLowerCase(Locale.ROOT);
                    rest = trimmed.substring(space + 1).trim();
                }
                if (prefix == null) {
                    continue;
                }
                ParsedPair pair = parsePair(rest);
                if (pair == null) {
                    continue;
                }
                switch (prefix) {
                    case "texture" -> textures.put(normalizeTextureRef(pair.from), pair.to);
                    case "model" -> models.put(normalizeModelRef(pair.from), pair.to);
                    case "generate" -> generated.put(normalizeModelRef(pair.from), pair.to);
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            Log.error("Config", "Failed to read texture additions {}", file, e);
        }
        return new TextureAdditionStore(textures, models, generated);
    }

    private static ParsedPair parsePair(String raw) {
        int idx = raw.indexOf("->");
        int sepLen = 2;
        if (idx < 0) {
            idx = raw.indexOf('=');
            sepLen = 1;
        }
        if (idx <= 0 || idx >= raw.length() - sepLen) {
            return null;
        }
        String from = raw.substring(0, idx).trim();
        String to = raw.substring(idx + sepLen).trim();
        if (from.isEmpty() || to.isEmpty()) {
            return null;
        }
        return new ParsedPair(from, to);
    }

    private static String applyNamespaceFallback(String original, String mapped) {
        if (mapped.contains(":")) {
            return mapped;
        }
        int idx = original.indexOf(':');
        if (idx > 0) {
            return original.substring(0, idx) + ":" + mapped;
        }
        return mapped;
    }

    private static String normalizeTextureRef(String raw) {
        String value = raw.trim().replace('\\', '/');
        int idx = value.indexOf(':');
        String namespace = null;
        String path = value;
        if (idx > 0) {
            namespace = value.substring(0, idx).toLowerCase(Locale.ROOT);
            path = value.substring(idx + 1);
        }
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        String result = (namespace == null ? path : namespace + ":" + path);
        return result.toLowerCase(Locale.ROOT);
    }

    private static String normalizeModelRef(String raw) {
        String value = raw.trim().replace('\\', '/');
        int idx = value.indexOf(':');
        String namespace = null;
        String path = value;
        if (idx > 0) {
            namespace = value.substring(0, idx).toLowerCase(Locale.ROOT);
            path = value.substring(idx + 1);
        }
        if (path.startsWith("models/")) {
            path = path.substring("models/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        String result = (namespace == null ? path : namespace + ":" + path);
        return result.toLowerCase(Locale.ROOT);
    }

    private static void writeDefault(Path file) {
        try {
            Files.createDirectories(file.getParent());
            String content = """
                # relootplusplus texture additions
                # syntax:
                # texture <from> = <to>
                # model <from> = <to>
                # generate <modelId> = <textureRef>
                #
                # examples:
                # texture lootplusplus:item/galaxyleggings = lootplusplus:item/galaxyLeggings
                # model lootplusplus:item/galaxy.galaxy_leggings = lootplusplus:item/galaxy.galaxy_leggings
                # generate lootplusplus:item/custom_missing = lootplusplus:item/existing_texture
                """;
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.error("Config", "Failed to write texture additions {}", file, e);
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

    private record ParsedPair(String from, String to) {
    }
}
