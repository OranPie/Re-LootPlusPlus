package ie.orangep.reLootplusplus.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.pack.AddonPack;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class AddonResourceIndex {
    public static final class ItemModelInfo {
        private final String itemId;
        private final String modelPath;
        private final String texture;

        public ItemModelInfo(String itemId, String modelPath, String texture) {
            this.itemId = itemId;
            this.modelPath = modelPath;
            this.texture = texture;
        }

        public String itemId() {
            return itemId;
        }

        public String modelPath() {
            return modelPath;
        }

        public String texture() {
            return texture;
        }
    }

    private AddonResourceIndex() {
    }

    public static List<ItemModelInfo> scanItemModels(AddonPack pack) {
        Path path = pack.zipPath();
        if (Files.isRegularFile(path)) {
            return scanZip(path);
        }
        if (Files.isDirectory(path)) {
            return scanDir(path);
        }
        return List.of();
    }

    private static List<ItemModelInfo> scanZip(Path zipPath) {
        List<ItemModelInfo> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            zip.stream().forEach(entry -> {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    return;
                }
                ItemModelInfo info = parseItemModel(name, entry, zip);
                if (info != null) {
                    out.add(info);
                }
            });
        } catch (Exception e) {
            Log.error("ResourcePack", "Failed to scan pack items {}", zipPath, e);
        }
        out.sort(Comparator.comparing(ItemModelInfo::itemId));
        return out;
    }

    private static List<ItemModelInfo> scanDir(Path dir) {
        List<ItemModelInfo> out = new ArrayList<>();
        try {
            Files.walk(dir).forEach(path -> {
                if (!Files.isRegularFile(path)) {
                    return;
                }
                String rel = dir.relativize(path).toString().replace('\\', '/');
                ItemModelInfo info = parseItemModel(rel, path);
                if (info != null) {
                    out.add(info);
                }
            });
        } catch (Exception e) {
            Log.error("ResourcePack", "Failed to scan pack items {}", dir, e);
        }
        out.sort(Comparator.comparing(ItemModelInfo::itemId));
        return out;
    }

    private static ItemModelInfo parseItemModel(String name, ZipEntry entry, ZipFile zip) {
        if (!isItemModelPath(name)) {
            return null;
        }
        String itemId = toItemId(name);
        if (itemId == null) {
            return null;
        }
        String texture = null;
        try (InputStream in = zip.getInputStream(entry)) {
            texture = parseTexture(in);
        } catch (Exception ignored) {
        }
        return new ItemModelInfo(itemId, name, texture);
    }

    private static ItemModelInfo parseItemModel(String rel, Path file) {
        if (!isItemModelPath(rel)) {
            return null;
        }
        String itemId = toItemId(rel);
        if (itemId == null) {
            return null;
        }
        String texture = null;
        try (InputStream in = Files.newInputStream(file)) {
            texture = parseTexture(in);
        } catch (Exception ignored) {
        }
        return new ItemModelInfo(itemId, rel, texture);
    }

    private static boolean isItemModelPath(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("assets/") && lower.contains("/models/item/") && lower.endsWith(".json");
    }

    private static String toItemId(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        int nsStart = "assets/".length();
        int slash = lower.indexOf('/', nsStart);
        if (slash <= nsStart) {
            return null;
        }
        String namespace = lower.substring(nsStart, slash);
        int modelsIdx = lower.indexOf("/models/item/", slash);
        if (modelsIdx < 0) {
            return null;
        }
        int modelStart = modelsIdx + "/models/item/".length();
        String model = lower.substring(modelStart, lower.length() - ".json".length());
        return namespace + ":" + model;
    }

    private static String parseTexture(InputStream in) {
        String raw;
        try {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
        JsonElement element = JsonParser.parseString(raw);
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        JsonObject textures = obj.getAsJsonObject("textures");
        if (textures == null) {
            return null;
        }
        if (textures.has("layer0")) {
            return textures.get("layer0").getAsString();
        }
        if (textures.has("particle")) {
            return textures.get("particle").getAsString();
        }
        return null;
    }
}
