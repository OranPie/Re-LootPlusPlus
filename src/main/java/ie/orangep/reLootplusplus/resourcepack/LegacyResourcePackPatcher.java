package ie.orangep.reLootplusplus.resourcepack;

import ie.orangep.reLootplusplus.config.CustomRemapStore;
import ie.orangep.reLootplusplus.config.TextureAdditionStore;
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
import java.util.Comparator;
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
        TEXTURE_REMAP.put("block/door_iron_lower", "block/iron_door_bottom");
        TEXTURE_REMAP.put("block/door_iron_upper", "block/iron_door_top");
        TEXTURE_REMAP.put("block/quartz_block_lines", "block/quartz_block_side");
        TEXTURE_REMAP.put("block/anvil_base", "block/anvil");
        TEXTURE_REMAP.put("block/anvil_top_damaged_2", "block/anvil_top_damaged_2");
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
        if (!containsNamespace(delegate, type, id.getNamespace())) {
            return false;
        }
        if (canSynthesizeBlockstate(delegate, type, id)) {
            return true;
        }
        if (canSynthesizeModel(delegate, type, id)) {
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
                    String json = convertLegacyLang(legacyStream, id.getNamespace());
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
            String stem = path.substring("blockstates/".length(), path.length() - ".json".length());
            Identifier modelId = Identifier.tryParse(id.getNamespace() + ":models/block/" + stem + ".json");
            if (modelId == null || !delegate.contains(type, modelId)) {
                modelId = findClosestResource(delegate, type, id.getNamespace(), "models/block", stem + ".json");
            }
            if (modelId == null) {
                Identifier syntheticModel = Identifier.tryParse(id.getNamespace() + ":models/block/" + stem + ".json");
                if (syntheticModel != null && canSynthesizeModel(delegate, type, syntheticModel)) {
                    modelId = syntheticModel;
                }
            }
            if (modelId == null) {
                return null;
            }
            String modelRef = modelId.getNamespace() + ":" + modelId.getPath()
                .substring("models/".length(), modelId.getPath().length() - ".json".length());
            String json = generateSimpleBlockstate(modelRef);
            warnOnce("LegacyBlockstate", "generated blockstate for " + id + " from " + modelId + " in " + packName);
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
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
        boolean isItemModel = path.startsWith("models/item/");
        String modelRef = id.getNamespace() + ":" + path.substring("models/".length(), path.length() - ".json".length());
        TextureAdditionStore additions = TextureAdditionStore.load();
        String generated = additions.generatedModelTexture(modelRef);
        if (generated != null) {
            String json = generateItemModel(additions.mapTextureRef(generated));
            warnOnce("TextureAdditions", "generated model for " + modelRef + " in " + packName);
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }
        String remappedModel = additions.mapModelRef(modelRef);
        if (remappedModel != null && !remappedModel.equals(modelRef)) {
            Identifier target = toModelResourceId(remappedModel);
            if (target != null && delegate.contains(type, target)) {
                warnOnce("TextureAdditions", "mapped model " + modelRef + " -> " + remappedModel + " in " + packName);
                return openAndPatchModel(delegate, type, target, packName);
            }
        }
        if (!delegate.contains(type, id)) {
            Identifier closest = findClosestResource(delegate, type, id.getNamespace(), isItemModel ? "models/item" : "models/block",
                path.substring(path.lastIndexOf('/') + 1));
            if (closest != null) {
                warnOnce("LegacyModel", "mapped missing model " + id + " -> " + closest + " in " + packName);
                return openAndPatchModel(delegate, type, closest, packName);
            }
            if (isItemModel) {
                String itemStem = path.substring("models/item/".length(), path.length() - ".json".length());
                Identifier textureId = findFallbackItemTexture(delegate, type, id.getNamespace(), itemStem);
                String textureRef = textureId == null
                    ? "minecraft:item/barrier"
                    : textureId.getNamespace() + ":" + textureId.getPath()
                        .substring("textures/".length(), textureId.getPath().length() - ".png".length());
                warnOnce("LegacyModel", "generated fallback item model for " + id + " -> " + textureRef + " in " + packName);
                return new ByteArrayInputStream(generateItemModel(textureRef).getBytes(StandardCharsets.UTF_8));
            }
            String blockTexture = "minecraft:block/stone";
            warnOnce("LegacyModel", "generated fallback block model for " + id + " -> " + blockTexture + " in " + packName);
            return new ByteArrayInputStream(generateCubeAllBlockModel(blockTexture).getBytes(StandardCharsets.UTF_8));
        }
        return openAndPatchModel(delegate, type, id, packName);
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
            Identifier closest = findClosestTextureResource(delegate, type, id);
            if (closest == null) {
                return null;
            }
            warnOnce("LegacyTexture", "mapped texture path " + id + " -> " + closest + " in " + packName);
            return delegate.open(type, closest);
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
        if (legacy != null && delegate.contains(type, legacy)) {
            return true;
        }
        return findClosestTextureResource(delegate, type, id) != null;
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

    private static Identifier findClosestTextureResource(ResourcePack delegate, ResourceType type, Identifier id) {
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (path == null || !path.startsWith("textures/")) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash >= path.length() - 1) {
            return null;
        }
        String file = path.substring(slash + 1);
        List<String> prefixes = new ArrayList<>();
        if (path.startsWith("textures/item/") || path.startsWith("textures/items/")) {
            prefixes.add("textures/item");
            prefixes.add("textures/items");
        } else if (path.startsWith("textures/block/") || path.startsWith("textures/blocks/")) {
            prefixes.add("textures/block");
            prefixes.add("textures/blocks");
        } else {
            prefixes.add("textures/item");
            prefixes.add("textures/items");
            prefixes.add("textures/block");
            prefixes.add("textures/blocks");
        }
        for (String prefix : prefixes) {
            Identifier closest = findClosestResource(delegate, type, id.getNamespace(), prefix, file);
            if (closest != null) {
                return closest;
            }
        }
        return null;
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
        String custom = CustomRemapStore.map(model);
        if (custom != null && !custom.equals(model)) {
            return custom;
        }
        String namespace = null;
        String path = model;
        int colon = model.indexOf(':');
        if (colon >= 0) {
            namespace = normalizeNamespace(model.substring(0, colon));
            path = model.substring(colon + 1);
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("blocks/")) {
            normalized = "block/" + normalized.substring("blocks/".length());
        } else if (normalized.startsWith("items/")) {
            normalized = "item/" + normalized.substring("items/".length());
        } else if (!normalized.startsWith("block/") && !normalized.startsWith("item/") && !normalized.startsWith("builtin/")) {
            if (!normalized.contains("/")) {
                normalized = "block/" + normalized;
            }
        }
        if (namespace != null) {
            return namespace + ":" + normalized;
        }
        return normalized;
    }

    private static String patchTextureRef(String texture) {
        if (texture == null || texture.isEmpty()) {
            return texture;
        }
        if (texture.startsWith("#")) {
            return texture;
        }
        String custom = CustomRemapStore.map(texture);
        if (custom != null && !custom.equals(texture)) {
            return custom;
        }
        String additions = TextureAdditionStore.load().mapTextureRef(texture);
        if (additions != null && !additions.equals(texture)) {
            return additions;
        }
        String namespace = null;
        String path = texture;
        int colon = texture.indexOf(':');
        if (colon >= 0) {
            namespace = texture.substring(0, colon);
            path = texture.substring(colon + 1);
        }
        String normalizedNamespace = namespace == null ? null : normalizeNamespace(namespace);
        String normalizedPath = normalizeTexturePath(path);
        if (normalizedNamespace != null) {
            return normalizedNamespace + ":" + normalizedPath;
        }
        return normalizedPath;
    }

    private static String normalizeTexturePath(String path) {
        String normalized = path == null ? "" : path;
        normalized = normalized.replace('\\', '/');
        normalized = normalizeTextureSegment(normalized);
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("blocks/")) {
            normalized = "block/" + normalized.substring("blocks/".length());
        } else if (normalized.startsWith("items/")) {
            normalized = "item/" + normalized.substring("items/".length());
        }
        normalized = sanitizePath(normalized);
        String remapped = TEXTURE_REMAP.get(normalized);
        if (remapped != null) {
            return remapped;
        }
        return normalized;
    }

    private static String normalizeNamespace(String namespace) {
        String lower = namespace.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static String resolveTextureNamespace(ResourcePack delegate, Identifier modelId, String textureRef) {
        if (textureRef == null || textureRef.isBlank()) {
            return textureRef;
        }
        int colon = textureRef.indexOf(':');
        if (colon <= 0) {
            return textureRef;
        }
        String namespace = textureRef.substring(0, colon);
        String path = textureRef.substring(colon + 1);
        if (!"minecraft".equals(namespace)) {
            return textureRef;
        }
        Identifier textureId = toTextureId(namespace, path);
        if (textureId != null && delegate.contains(ResourceType.CLIENT_RESOURCES, textureId)) {
            return textureRef;
        }
        String modelNamespace = modelId == null ? null : modelId.getNamespace();
        if (modelNamespace == null || modelNamespace.isBlank() || "minecraft".equals(modelNamespace)) {
            return textureRef;
        }
        Identifier altId = toTextureId(modelNamespace, path);
        if (altId != null && delegate.contains(ResourceType.CLIENT_RESOURCES, altId)) {
            return modelNamespace + ":" + path;
        }
        return textureRef;
    }

    private static String resolveClosestTextureRef(ResourcePack delegate, Identifier modelId, String textureRef) {
        if (textureRef == null || textureRef.isBlank()) {
            return textureRef;
        }
        int colon = textureRef.indexOf(':');
        if (colon <= 0 || colon >= textureRef.length() - 1) {
            return textureRef;
        }
        String namespace = textureRef.substring(0, colon);
        String path = textureRef.substring(colon + 1);
        Identifier direct = toTextureId(namespace, path);
        if (direct != null && delegate.contains(ResourceType.CLIENT_RESOURCES, direct)) {
            return textureRef;
        }
        String file = path.substring(path.lastIndexOf('/') + 1) + ".png";
        List<String> prefixes = texturePrefixesForPath(path);
        for (String prefix : prefixes) {
            Identifier closest = findClosestResource(delegate, ResourceType.CLIENT_RESOURCES, namespace, prefix, file);
            if (closest != null) {
                return closest.getNamespace() + ":" + closest.getPath()
                    .substring("textures/".length(), closest.getPath().length() - ".png".length());
            }
        }
        String modelNs = modelId == null ? null : modelId.getNamespace();
        if (modelNs != null && !modelNs.equals(namespace)) {
            for (String prefix : prefixes) {
                Identifier closest = findClosestResource(delegate, ResourceType.CLIENT_RESOURCES, modelNs, prefix, file);
                if (closest != null) {
                    return closest.getNamespace() + ":" + closest.getPath()
                        .substring("textures/".length(), closest.getPath().length() - ".png".length());
                }
            }
        }
        return textureRef;
    }

    private static List<String> texturePrefixesForPath(String path) {
        if (path.startsWith("item/") || path.startsWith("items/")) {
            return List.of("textures/item", "textures/items");
        }
        if (path.startsWith("block/") || path.startsWith("blocks/")) {
            return List.of("textures/block", "textures/blocks");
        }
        return List.of("textures/item", "textures/items", "textures/block", "textures/blocks");
    }

    private static String fallbackMissingTextureRef(ResourcePack delegate, String textureRef) {
        if (textureRef == null || textureRef.isBlank()) {
            return textureRef;
        }
        int colon = textureRef.indexOf(':');
        if (colon <= 0 || colon >= textureRef.length() - 1) {
            return textureRef;
        }
        String namespace = textureRef.substring(0, colon);
        String path = textureRef.substring(colon + 1);
        Identifier direct = toTextureId(namespace, path);
        if (direct != null && delegate.contains(ResourceType.CLIENT_RESOURCES, direct)) {
            return textureRef;
        }
        if (path.startsWith("block/") || path.startsWith("blocks/")) {
            return "minecraft:block/stone";
        }
        return "minecraft:item/barrier";
    }

    private static Identifier toTextureId(String namespace, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String clean = path.startsWith("/") ? path.substring(1) : path;
        String texPath = "textures/" + clean + ".png";
        return Identifier.tryParse(namespace + ":" + texPath);
    }

    private static String sanitizePath(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static String normalizeTextureSegment(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash >= path.length() - 1) {
            return toSnakeCase(path);
        }
        String prefix = path.substring(0, slash + 1);
        String segment = path.substring(slash + 1);
        return prefix + toSnakeCase(segment);
    }

    private static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 4);
        char prev = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
            prev = c;
        }
        return out.toString();
    }

    private static String generateItemModel(String textureRef) {
        String value = textureRef == null ? "" : textureRef;
        JsonObject obj = new JsonObject();
        obj.addProperty("parent", "item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", value);
        obj.add("textures", textures);
        return GSON.toJson(obj);
    }

    private static String generateCubeAllBlockModel(String textureRef) {
        JsonObject obj = new JsonObject();
        obj.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", textureRef == null ? "minecraft:block/stone" : textureRef);
        obj.add("textures", textures);
        return GSON.toJson(obj);
    }

    private static InputStream openAndPatchModel(ResourcePack delegate, ResourceType type, Identifier id, String packName) throws IOException {
        try (InputStream original = delegate.open(type, id)) {
            byte[] data = original.readAllBytes();
            return patchModelJson(delegate, id, packName, data);
        }
    }

    private static InputStream patchModelJson(ResourcePack delegate, Identifier id, String packName, byte[] data) {
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
                String resolved = resolveTextureNamespace(delegate, id, patched);
                resolved = resolveClosestTextureRef(delegate, id, resolved);
                resolved = fallbackMissingTextureRef(delegate, resolved);
                if (!resolved.equals(value)) {
                    textures.addProperty(entry.getKey(), resolved);
                    warnOnce("LegacyTexture", "mapped texture " + value + " -> " + resolved + " in " + packName + ":" + id);
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

    private static Identifier findFallbackItemTexture(ResourcePack delegate, ResourceType type, String namespace, String modelStem) {
        String file = modelStem + ".png";
        Identifier directItem = Identifier.tryParse(namespace + ":textures/item/" + file);
        if (directItem != null && delegate.contains(type, directItem)) {
            return directItem;
        }
        Identifier directItems = Identifier.tryParse(namespace + ":textures/items/" + file);
        if (directItems != null && delegate.contains(type, directItems)) {
            return directItems;
        }
        Identifier closest = findClosestResource(delegate, type, namespace, "textures/item", file);
        if (closest != null) {
            return closest;
        }
        return findClosestResource(delegate, type, namespace, "textures/items", file);
    }

    private static Identifier findClosestResource(ResourcePack delegate, ResourceType type, String namespace, String prefix, String requestedFile) {
        Collection<Identifier> resources = delegate.findResources(type, namespace, prefix, 48, p -> p.endsWith(".json") || p.endsWith(".png"));
        if (resources.isEmpty()) {
            return null;
        }
        String req = requestedFile.toLowerCase(Locale.ROOT);
        String reqStem = stripExtension(req);
        String reqNorm = normalizeComparableStem(reqStem);
        Identifier best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Identifier candidate : resources) {
            String candidatePath = candidate.getPath();
            int slash = candidatePath.lastIndexOf('/');
            if (slash < 0 || slash >= candidatePath.length() - 1) {
                continue;
            }
            String file = candidatePath.substring(slash + 1).toLowerCase(Locale.ROOT);
            int score = scoreFileMatch(file, req, reqStem, reqNorm);
            if (score <= Integer.MIN_VALUE / 2) {
                continue;
            }
            if (best == null || score > bestScore || (score == bestScore
                && Comparator.<String>naturalOrder().compare(candidatePath, best.getPath()) < 0)) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static int scoreFileMatch(String candidateFile, String reqFile, String reqStem, String reqNorm) {
        String candidateStem = stripExtension(candidateFile);
        String candidateNorm = normalizeComparableStem(candidateStem);
        if (candidateFile.equals(reqFile)) {
            return 120;
        }
        if (candidateStem.equals(reqStem)) {
            return 110;
        }
        if (candidateStem.endsWith("." + reqStem) || candidateStem.endsWith("_" + reqStem) || candidateStem.endsWith("-" + reqStem)) {
            return 100;
        }
        if (candidateNorm.equals(reqNorm)) {
            return 90;
        }
        if (!reqNorm.isEmpty() && candidateNorm.endsWith(reqNorm)) {
            return 80;
        }
        return Integer.MIN_VALUE;
    }

    private static String stripExtension(String file) {
        int dot = file.lastIndexOf('.');
        if (dot <= 0) {
            return file;
        }
        return file.substring(0, dot);
    }

    private static String normalizeComparableStem(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String generateSimpleBlockstate(String modelRef) {
        JsonObject obj = new JsonObject();
        JsonObject variants = new JsonObject();
        JsonObject normal = new JsonObject();
        normal.addProperty("model", modelRef);
        variants.add("", normal);
        obj.add("variants", variants);
        return GSON.toJson(obj);
    }

    private static boolean canSynthesizeBlockstate(ResourcePack delegate, ResourceType type, Identifier id) {
        if ("minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        if (!path.startsWith("blockstates/") || !path.endsWith(".json")) {
            return false;
        }
        String stem = path.substring("blockstates/".length(), path.length() - ".json".length());
        Identifier directModel = Identifier.tryParse(id.getNamespace() + ":models/block/" + stem + ".json");
        if (directModel != null && delegate.contains(type, directModel)) {
            return true;
        }
        if (findClosestResource(delegate, type, id.getNamespace(), "models/block", stem + ".json") != null) {
            return true;
        }
        return directModel != null && canSynthesizeModel(delegate, type, directModel);
    }

    private static boolean canSynthesizeModel(ResourcePack delegate, ResourceType type, Identifier id) {
        if ("minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        if (!path.startsWith("models/") || !path.endsWith(".json")) {
            return false;
        }
        String modelRef = id.getNamespace() + ":" + path.substring("models/".length(), path.length() - ".json".length());
        TextureAdditionStore additions = TextureAdditionStore.load();
        if (additions.generatedModelTexture(modelRef) != null) {
            return true;
        }
        String remappedModel = additions.mapModelRef(modelRef);
        if (remappedModel != null && !remappedModel.equals(modelRef)) {
            Identifier target = toModelResourceId(remappedModel);
            if (target != null && delegate.contains(type, target)) {
                return true;
            }
        }
        boolean isItem = path.startsWith("models/item/");
        String prefix = isItem ? "models/item" : "models/block";
        String requested = path.substring(path.lastIndexOf('/') + 1);
        Identifier closest = findClosestResource(delegate, type, id.getNamespace(), prefix, requested);
        if (closest != null) {
            return true;
        }
        if (!isItem) {
            return false;
        }
        String stem = path.substring("models/item/".length(), path.length() - ".json".length());
        return findFallbackItemTexture(delegate, type, id.getNamespace(), stem) != null;
    }

    private static boolean containsNamespace(ResourcePack delegate, ResourceType type, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return false;
        }
        try {
            return delegate.getNamespaces(type).contains(namespace.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Identifier toModelResourceId(String modelRef) {
        if (modelRef == null || modelRef.isBlank()) {
            return null;
        }
        String value = modelRef.trim();
        int idx = value.indexOf(':');
        if (idx <= 0 || idx >= value.length() - 1) {
            return null;
        }
        String namespace = value.substring(0, idx);
        String path = value.substring(idx + 1);
        if (path.startsWith("models/")) {
            path = path.substring("models/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return Identifier.tryParse(namespace + ":models/" + path + ".json");
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

    static String convertLegacyLang(InputStream stream, String namespace) throws IOException {
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
                String mapped = mapLegacyKey(key, namespace);
                if (mapped != null && !mapped.equals(key)) {
                    obj.addProperty(mapped, value);
                }
                String alt = mapLegacyKeySnake(key, namespace);
                if (alt != null && !alt.equals(key) && !alt.equals(mapped)) {
                    obj.addProperty(alt, value);
                }
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
        Log.warn("Legacy", "{} {}", type, detail);
    }

    private static String mapLegacyKey(String key, String namespace) {
        if (key == null || !key.endsWith(".name")) {
            return null;
        }
        String base = key.substring(0, key.length() - ".name".length());
        if (base.startsWith("tile.")) {
            return mapWithPrefix("block", base.substring("tile.".length()), namespace);
        }
        if (base.startsWith("item.")) {
            return mapWithPrefix("item", base.substring("item.".length()), namespace);
        }
        if (base.startsWith("entity.")) {
            return mapWithPrefix("entity", base.substring("entity.".length()), namespace);
        }
        return null;
    }

    private static String mapLegacyKeySnake(String key, String namespace) {
        if (key == null || !key.endsWith(".name")) {
            return null;
        }
        String base = key.substring(0, key.length() - ".name".length());
        if (base.startsWith("tile.")) {
            return mapWithPrefix("block", snakeSuffix(base.substring("tile.".length()), namespace), namespace);
        }
        if (base.startsWith("item.")) {
            return mapWithPrefix("item", snakeSuffix(base.substring("item.".length()), namespace), namespace);
        }
        if (base.startsWith("entity.")) {
            return mapWithPrefix("entity", snakeSuffix(base.substring("entity.".length()), namespace), namespace);
        }
        return null;
    }

    private static String mapWithPrefix(String prefix, String legacy, String namespace) {
        if (legacy == null || legacy.isBlank()) {
            return null;
        }
        String ns = namespace == null || namespace.isBlank() ? "minecraft" : namespace;
        String path = legacy;
        int colon = legacy.indexOf(':');
        if (colon > 0 && colon < legacy.length() - 1) {
            ns = legacy.substring(0, colon);
            path = legacy.substring(colon + 1);
        } else {
            int dot = legacy.indexOf('.');
            if (dot > 0 && dot < legacy.length() - 1) {
                ns = legacy.substring(0, dot);
                path = legacy.substring(dot + 1);
            }
        }
        return prefix + "." + ns + "." + path;
    }

    private static String snakeSuffix(String legacy, String namespace) {
        if (legacy == null || legacy.isBlank()) {
            return legacy;
        }
        String ns = namespace == null ? "" : namespace;
        String path = legacy;
        int colon = legacy.indexOf(':');
        if (colon > 0 && colon < legacy.length() - 1) {
            ns = legacy.substring(0, colon);
            path = legacy.substring(colon + 1);
        } else {
            int dot = legacy.indexOf('.');
            if (dot > 0 && dot < legacy.length() - 1) {
                ns = legacy.substring(0, dot);
                path = legacy.substring(dot + 1);
            }
        }
        StringBuilder out = new StringBuilder(path.length() + 8);
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && out.charAt(out.length() - 1) != '_') {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        String snake = out.toString();
        if (!ns.isBlank()) {
            return ns + "." + snake;
        }
        return snake;
    }
}
