package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyResourcePackPatcher {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> TEXTURE_REMAP = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> CASE_INSENSITIVE_CACHE = new ConcurrentHashMap<>();

    static {
        registerWoolRemaps();
        registerTerracottaRemaps();
        TEXTURE_REMAP.put("block/planks_oak", "block/oak_planks");
        TEXTURE_REMAP.put("block/planks_spruce", "block/spruce_planks");
        TEXTURE_REMAP.put("block/planks_birch", "block/birch_planks");
        TEXTURE_REMAP.put("block/planks_jungle", "block/jungle_planks");
        TEXTURE_REMAP.put("block/planks_acacia", "block/acacia_planks");
        TEXTURE_REMAP.put("block/planks_big_oak", "block/dark_oak_planks");
        TEXTURE_REMAP.put("block/log_oak", "block/oak_log");
        TEXTURE_REMAP.put("block/log_spruce", "block/spruce_log");
        TEXTURE_REMAP.put("block/log_birch", "block/birch_log");
        TEXTURE_REMAP.put("block/log_jungle", "block/jungle_log");
        TEXTURE_REMAP.put("block/log_acacia", "block/acacia_log");
        TEXTURE_REMAP.put("block/log_big_oak", "block/dark_oak_log");
    }

    private LegacyResourcePackPatcher() {
    }

    public static InputStream tryOpen(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return null;
        }
        InputStream lang = tryOpenLegacyLang(delegate, type, id, packName);
        if (lang != null) {
            return lang;
        }
        InputStream blockstate = tryPatchLegacyBlockstate(delegate, type, id, packName);
        if (blockstate != null) {
            return blockstate;
        }
        InputStream model = tryPatchLegacyModel(delegate, type, id, packName);
        if (model != null) {
            return model;
        }
        InputStream texture = tryOpenLegacyTexture(delegate, type, id, packName);
        if (texture != null) {
            return texture;
        }
        InputStream caseInsensitive = tryOpenCaseInsensitive(delegate, type, id, packName);
        if (caseInsensitive != null) {
            return caseInsensitive;
        }
        InputStream sounds = tryPatchLegacySounds(delegate, type, id, packName);
        if (sounds != null) {
            return sounds;
        }
        return null;
    }

    public static Collection<Identifier> findResources(ResourcePack delegate, ResourceType type, String namespace, String prefix, int maxDepth, java.util.function.Predicate<String> pathFilter) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return Collections.emptyList();
        }
        Collection<Identifier> base = delegate.findResources(type, namespace, prefix, maxDepth, pathFilter);
        if (!prefix.startsWith("lang")) {
            return base;
        }
        List<Identifier> result = new ArrayList<>();
        for (Identifier id : base) {
            String path = id.getPath();
            if (!path.startsWith("lang/")) {
                continue;
            }
            if (path.endsWith(".lang")) {
                String baseName = path.substring("lang/".length(), path.length() - ".lang".length());
                String jsonPath = "lang/" + baseName.toLowerCase(Locale.ROOT) + ".json";
                if (!pathFilter.test(jsonPath)) {
                    continue;
                }
                result.add(new Identifier(id.getNamespace(), jsonPath));
                continue;
            }
            if (path.endsWith(".json")) {
                String baseName = path.substring("lang/".length(), path.length() - ".json".length());
                String jsonPath = "lang/" + baseName.toLowerCase(Locale.ROOT) + ".json";
                if (!pathFilter.test(jsonPath)) {
                    continue;
                }
                result.add(new Identifier(id.getNamespace(), jsonPath));
            }
        }
        return result;
    }

    public static boolean contains(ResourcePack delegate, ResourceType type, Identifier id) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return false;
        }
        if (delegate.contains(type, id)) {
            return true;
        }
        if (hasLegacyTexture(delegate, type, id)) {
            return true;
        }
        if (hasLegacyLang(delegate, type, id)) {
            return true;
        }
        return findCaseInsensitive(delegate, type, id) != null;
    }

    public static boolean shouldWrapPackName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("relootplusplus:")) {
            return false;
        }
        if (lower.startsWith("lucky/")) {
            return true;
        }
        return lower.contains("lucky") || lower.contains("lootplusplus") || lower.contains("loot++");
    }

    private static InputStream tryOpenLegacyLang(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        String path = id.getPath();
        if (!path.startsWith("lang/") || !path.endsWith(".json")) {
            return null;
        }
        if (delegate.contains(type, id)) {
            return null;
        }
        String base = path.substring("lang/".length(), path.length() - ".json".length());
        String normalized = base.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        candidates.add("lang/" + normalized + ".lang");
        for (String candidate : candidates) {
            Identifier legacyId = new Identifier(id.getNamespace(), candidate);
            if (delegate.contains(type, legacyId)) {
                try (InputStream legacyStream = delegate.open(type, legacyId)) {
                    String json = convertLegacyLang(legacyStream);
                    warnOnce("LegacyLang", "converted " + legacyId + " -> " + id + " in " + packName);
                    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return null;
    }

    private static InputStream tryPatchLegacyBlockstate(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        String path = id.getPath();
        if (!path.startsWith("blockstates/") || !path.endsWith(".json")) {
            return null;
        }
        if (!delegate.contains(type, id)) {
            return null;
        }
        try (InputStream original = delegate.open(type, id)) {
            byte[] data = original.readAllBytes();
            JsonElement parsed = parseJsonOrNull(data);
            if (parsed == null || !parsed.isJsonObject()) {
                return new ByteArrayInputStream(data);
            }
            JsonObject obj = parsed.getAsJsonObject();
            if (!obj.has("variants") || !obj.get("variants").isJsonObject()) {
                return new ByteArrayInputStream(data);
            }
            JsonObject variants = obj.getAsJsonObject("variants");
            boolean changed = false;
            if (variants.has("normal")) {
                if (!variants.has("")) {
                    variants.add("", variants.get("normal"));
                }
                variants.remove("normal");
                changed = true;
                warnOnce("LegacyBlockstate", "converted variant 'normal' to '' in " + packName + ":" + id);
            }
            changed |= patchModelsInVariants(variants, packName);
            if (obj.has("multipart")) {
                changed |= patchModelsInMultipart(obj.get("multipart"), packName);
            }
            if (!changed) {
                return new ByteArrayInputStream(data);
            }
            String patched = GSON.toJson(obj);
            return new ByteArrayInputStream(patched.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static InputStream tryPatchLegacyModel(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        String path = id.getPath();
        if (!path.startsWith("models/") || !path.endsWith(".json")) {
            return null;
        }
        if (!delegate.contains(type, id)) {
            return null;
        }
        try (InputStream original = delegate.open(type, id)) {
            byte[] data = original.readAllBytes();
            JsonElement parsed = parseJsonOrNull(data);
            if (parsed == null || !parsed.isJsonObject()) {
                return new ByteArrayInputStream(data);
            }
            JsonObject obj = parsed.getAsJsonObject();
            boolean changed = false;
            if (obj.has("parent") && obj.get("parent").isJsonPrimitive()) {
                String parent = obj.get("parent").getAsString();
                String patched = patchModelRef(parent);
                if (!patched.equals(parent)) {
                    obj.addProperty("parent", patched);
                    warnOnce("LegacyModel", "prefixed parent " + parent + " -> " + patched + " in " + packName + ":" + id);
                    changed = true;
                }
            }
            if (obj.has("textures") && obj.get("textures").isJsonObject()) {
                JsonObject textures = obj.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    String value = entry.getValue().getAsString();
                    if (value.startsWith("#")) {
                        continue;
                    }
                    String patched = patchTextureRef(value);
                    if (!patched.equals(value)) {
                        textures.addProperty(entry.getKey(), patched);
                        warnOnce("LegacyTexture", "mapped texture " + value + " -> " + patched + " in " + packName + ":" + id);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return new ByteArrayInputStream(data);
            }
            String patchedJson = GSON.toJson(obj);
            return new ByteArrayInputStream(patchedJson.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static InputStream tryOpenLegacyTexture(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        String path = id.getPath();
        if (!path.startsWith("textures/")) {
            return null;
        }
        if (delegate.contains(type, id)) {
            return null;
        }
        Identifier legacy = mapLegacyTextureId(id);
        if (legacy == null || !delegate.contains(type, legacy)) {
            return null;
        }
        warnOnce("LegacyTexture", "mapped texture path " + id + " -> " + legacy + " in " + packName);
        return delegate.open(type, legacy);
    }

    private static boolean hasLegacyTexture(ResourcePack delegate, ResourceType type, Identifier id) {
        String path = id.getPath();
        if (!path.startsWith("textures/")) {
            return false;
        }
        if (delegate.contains(type, id)) {
            return true;
        }
        Identifier legacy = mapLegacyTextureId(id);
        if (legacy == null) {
            return false;
        }
        return delegate.contains(type, legacy);
    }

    private static InputStream tryOpenCaseInsensitive(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        Identifier mapped = findCaseInsensitive(delegate, type, id);
        if (mapped == null) {
            return null;
        }
        if (!delegate.contains(type, mapped)) {
            return null;
        }
        warnOnce("LegacyResourceCase", "mapped " + id + " -> " + mapped + " in " + packName);
        return delegate.open(type, mapped);
    }

    private static Identifier findCaseInsensitive(ResourcePack delegate, ResourceType type, Identifier id) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return null;
        }
        String path = id.getPath();
        int slash = path.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String prefix = path.substring(0, slash);
        if (!"models".equals(prefix) && !"textures".equals(prefix) && !"blockstates".equals(prefix)) {
            return null;
        }
        String cacheKey = System.identityHashCode(delegate) + ":" + type.getDirectory() + ":" + id.getNamespace() + ":" + prefix;
        Map<String, String> map = CASE_INSENSITIVE_CACHE.computeIfAbsent(cacheKey, key -> buildCaseMap(delegate, type, id.getNamespace(), prefix));
        String actual = map.get(path.toLowerCase(Locale.ROOT));
        if (actual == null) {
            return null;
        }
        return new Identifier(id.getNamespace(), actual);
    }

    private static Map<String, String> buildCaseMap(ResourcePack delegate, ResourceType type, String namespace, String prefix) {
        Map<String, String> built = new ConcurrentHashMap<>();
        Collection<Identifier> resources = delegate.findResources(type, namespace, prefix, 32, p -> true);
        for (Identifier res : resources) {
            built.put(res.getPath().toLowerCase(Locale.ROOT), res.getPath());
        }
        return built;
    }

    private static Identifier mapLegacyTextureId(Identifier id) {
        String path = id.getPath();
        String alt = null;
        if (path.startsWith("textures/item/")) {
            alt = "textures/items/" + path.substring("textures/item/".length());
        } else if (path.startsWith("textures/block/")) {
            alt = "textures/blocks/" + path.substring("textures/block/".length());
        }
        if (alt == null) {
            return null;
        }
        return new Identifier(id.getNamespace(), alt);
    }

    private static InputStream tryPatchLegacySounds(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        String path = id.getPath();
        if (!"sounds.json".equals(path)) {
            return null;
        }
        if (!delegate.contains(type, id)) {
            return null;
        }
        try (InputStream original = delegate.open(type, id)) {
            byte[] data = original.readAllBytes();
            JsonElement parsed = parseJsonOrNull(data);
            if (parsed == null || !parsed.isJsonObject()) {
                return new ByteArrayInputStream(data);
            }
            JsonObject obj = parsed.getAsJsonObject();
            boolean changed = false;
            JsonObject rebuilt = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
                String normalizedKey = normalizeSoundPath(key);
                if (!normalizedKey.equals(key)) {
                    warnOnce("LegacySound", "normalized event " + key + " -> " + normalizedKey + " in " + packName + ":" + id);
                    changed = true;
                }
                if (rebuilt.has(normalizedKey)) {
                    warnOnce("LegacySound", "duplicate event " + normalizedKey + " in " + packName + ":" + id);
                    continue;
                }
                if (!entry.getValue().isJsonObject()) {
                    rebuilt.add(normalizedKey, entry.getValue());
                    continue;
                }
                JsonObject soundDef = entry.getValue().getAsJsonObject();
                if (!soundDef.has("sounds") || !soundDef.get("sounds").isJsonArray()) {
                    rebuilt.add(normalizedKey, soundDef);
                    continue;
                }
                var sounds = soundDef.getAsJsonArray("sounds");
                for (int i = 0; i < sounds.size(); i++) {
                    JsonElement soundEntry = sounds.get(i);
                    if (soundEntry.isJsonPrimitive() && soundEntry.getAsJsonPrimitive().isString()) {
                        String value = soundEntry.getAsString();
                        String patched = patchSoundRef(value, packName, id);
                        if (!patched.equals(value)) {
                            sounds.set(i, new JsonPrimitive(patched));
                            changed = true;
                        }
                    } else if (soundEntry.isJsonObject()) {
                        JsonObject soundObj = soundEntry.getAsJsonObject();
                        if (soundObj.has("name") && soundObj.get("name").isJsonPrimitive()) {
                            String value = soundObj.get("name").getAsString();
                            String patched = patchSoundRef(value, packName, id);
                            if (!patched.equals(value)) {
                                soundObj.addProperty("name", patched);
                                changed = true;
                            }
                        }
                    }
                }
                rebuilt.add(normalizedKey, soundDef);
            }
            if (!changed) {
                return new ByteArrayInputStream(data);
            }
            String patchedJson = GSON.toJson(rebuilt);
            return new ByteArrayInputStream(patchedJson.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String patchSoundRef(String value, String packName, Identifier id) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String namespace = null;
        String path = value;
        int idx = value.indexOf(':');
        if (idx > 0) {
            namespace = value.substring(0, idx);
            path = value.substring(idx + 1);
        }
        String patchedPath = normalizeSoundPath(path);
        String patchedNamespace = namespace == null ? null : normalizeSoundNamespace(namespace);
        String patched = patchedNamespace == null ? patchedPath : patchedNamespace + ":" + patchedPath;
        if (!patched.equals(value)) {
            warnOnce("LegacySound", "normalized " + value + " -> " + patched + " in " + packName + ":" + id);
        }
        return patched;
    }

    private static String normalizeSoundNamespace(String namespace) {
        String lower = namespace.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static String normalizeSoundPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static JsonElement parseJsonOrNull(byte[] data) {
        try {
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean patchModelsInVariants(JsonObject variants, String packName) {
        boolean changed = false;
        for (String key : variants.keySet()) {
            JsonElement element = variants.get(key);
            if (element.isJsonObject()) {
                changed |= patchModelInObject(element.getAsJsonObject(), packName);
            } else if (element.isJsonArray()) {
                for (JsonElement entry : element.getAsJsonArray()) {
                    if (entry.isJsonObject()) {
                        changed |= patchModelInObject(entry.getAsJsonObject(), packName);
                    }
                }
            }
        }
        return changed;
    }

    private static boolean patchModelsInMultipart(JsonElement multipart, String packName) {
        if (multipart == null) {
            return false;
        }
        boolean changed = false;
        if (multipart.isJsonArray()) {
            for (JsonElement entry : multipart.getAsJsonArray()) {
                changed |= patchModelsInMultipart(entry, packName);
            }
            return changed;
        }
        if (!multipart.isJsonObject()) {
            return false;
        }
        JsonObject obj = multipart.getAsJsonObject();
        if (!obj.has("apply")) {
            return false;
        }
        JsonElement apply = obj.get("apply");
        if (apply.isJsonObject()) {
            changed |= patchModelInObject(apply.getAsJsonObject(), packName);
        } else if (apply.isJsonArray()) {
            for (JsonElement entry : apply.getAsJsonArray()) {
                if (entry.isJsonObject()) {
                    changed |= patchModelInObject(entry.getAsJsonObject(), packName);
                }
            }
        }
        return changed;
    }

    private static boolean patchModelInObject(JsonObject obj, String packName) {
        if (!obj.has("model") || !obj.get("model").isJsonPrimitive()) {
            return false;
        }
        String model = obj.get("model").getAsString();
        String patched = patchModelRef(model);
        if (!patched.equals(model)) {
            obj.addProperty("model", patched);
            warnOnce("LegacyBlockstate", "prefixed model path " + model + " -> " + patched + " in " + packName);
            return true;
        }
        return false;
    }

    private static String patchModelRef(String model) {
        if (model == null || model.isEmpty()) {
            return model;
        }
        if (model.startsWith("block/") || model.startsWith("item/") || model.startsWith("builtin/")) {
            return model;
        }
        int colon = model.indexOf(':');
        if (colon >= 0) {
            String ns = model.substring(0, colon);
            String path = model.substring(colon + 1);
            if (path.startsWith("block/") || path.startsWith("item/") || path.startsWith("builtin/") || path.contains("/")) {
                return model;
            }
            if (path.startsWith("blocks/")) {
                return ns + ":block/" + path.substring("blocks/".length());
            }
            if (path.startsWith("items/")) {
                return ns + ":item/" + path.substring("items/".length());
            }
            return ns + ":block/" + path;
        }
        if (model.startsWith("blocks/")) {
            return "block/" + model.substring("blocks/".length());
        }
        if (model.startsWith("items/")) {
            return "item/" + model.substring("items/".length());
        }
        if (model.contains("/")) {
            return model;
        }
        return "block/" + model;
    }

    private static String patchTextureRef(String texture) {
        if (texture == null || texture.isEmpty()) {
            return texture;
        }
        if (texture.startsWith("#")) {
            return texture;
        }
        String namespace = null;
        String path = texture;
        int colon = texture.indexOf(':');
        if (colon >= 0) {
            namespace = texture.substring(0, colon);
            path = texture.substring(colon + 1);
        }
        String normalized = normalizeTexturePath(path);
        if (namespace != null) {
            return namespace + ":" + normalized;
        }
        return normalized;
    }

    private static String normalizeTexturePath(String path) {
        String normalized = path;
        if (normalized.startsWith("blocks/")) {
            normalized = "block/" + normalized.substring("blocks/".length());
        } else if (normalized.startsWith("items/")) {
            normalized = "item/" + normalized.substring("items/".length());
        }
        String remapped = TEXTURE_REMAP.get(normalized);
        if (remapped != null) {
            return remapped;
        }
        return normalized;
    }

    private static void registerWoolRemaps() {
        String[] colors = new String[] {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };
        for (String color : colors) {
            TEXTURE_REMAP.put("block/wool_colored_" + color, "block/" + color + "_wool");
        }
    }

    private static void registerTerracottaRemaps() {
        String[] colors = new String[] {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };
        for (String color : colors) {
            String targetColor = color.equals("silver") ? "light_gray" : color;
            TEXTURE_REMAP.put("block/hardened_clay_stained_" + color, "block/" + targetColor + "_terracotta");
        }
    }

    private static boolean hasLegacyLang(ResourcePack delegate, ResourceType type, Identifier id) {
        String path = id.getPath();
        if (!path.startsWith("lang/") || !path.endsWith(".json")) {
            return false;
        }
        String base = path.substring("lang/".length(), path.length() - ".json".length());
        List<String> candidates = new ArrayList<>();
        candidates.add("lang/" + base.toLowerCase(Locale.ROOT) + ".lang");
        String legacyLocale = toLegacyLocale(base);
        if (!legacyLocale.equals(base)) {
            candidates.add("lang/" + legacyLocale + ".lang");
        }
        for (String candidate : candidates) {
            Identifier legacyId = Identifier.tryParse(id.getNamespace() + ":" + candidate);
            if (legacyId == null) {
                continue;
            }
            if (delegate.contains(type, legacyId)) {
                return true;
            }
        }
        return false;
    }

    static String convertLegacyLang(InputStream stream) throws IOException {
        byte[] data = stream.readAllBytes();
        String content;
        try {
            content = decodeStrict(data, StandardCharsets.UTF_8);
        } catch (java.nio.charset.CharacterCodingException e) {
            content = new String(data, StandardCharsets.ISO_8859_1);
        }
        JsonObject obj = new JsonObject();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(content))) {
            boolean first = true;
            String line;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    line = stripBom(line);
                    first = false;
                }
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = line.substring(0, idx);
                String value = line.substring(idx + 1);
                obj.addProperty(key, value);
            }
        }
        return GSON.toJson(obj);
    }

    private static String stripBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private static String toLegacyLocale(String base) {
        int idx = base.indexOf('_');
        if (idx <= 0 || idx >= base.length() - 1) {
            return base;
        }
        String lang = base.substring(0, idx).toLowerCase(Locale.ROOT);
        String region = base.substring(idx + 1).toUpperCase(Locale.ROOT);
        return lang + "_" + region;
    }

    private static String decodeStrict(byte[] data, java.nio.charset.Charset charset) throws java.nio.charset.CharacterCodingException {
        java.nio.charset.CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(data)).toString();
    }

    private static void warnOnce(String type, String detail) {
        String key = type + ":" + detail;
        if (!WARNED.add(key)) {
            return;
        }
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter != null) {
            reporter.warnOnce(type, detail, null);
            return;
        }
        Log.warn("[LootPP-Legacy] {} {}", type, detail);
    }
}
