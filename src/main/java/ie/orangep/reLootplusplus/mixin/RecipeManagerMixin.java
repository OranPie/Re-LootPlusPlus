package ie.orangep.reLootplusplus.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ie.orangep.reLootplusplus.config.model.recipe.RecipeDefinitions;
import ie.orangep.reLootplusplus.config.model.recipe.RecipeInput;
import ie.orangep.reLootplusplus.config.model.recipe.RecipeKey;
import ie.orangep.reLootplusplus.config.model.recipe.ShapedRecipeDef;
import ie.orangep.reLootplusplus.config.model.recipe.ShapelessRecipeDef;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyEntityIdFixer;
import ie.orangep.reLootplusplus.recipe.ModRecipes;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void relootplusplus$injectLegacyRecipes(Map<Identifier, JsonElement> map, ResourceManager manager, Profiler profiler, CallbackInfo ci) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        sanitizeLegacyAddonRecipes(map, reporter);

        RecipeDefinitions defs = RuntimeState.recipeDefinitions();
        if (defs == null) {
            return;
        }
        int added = 0;
        int index = 0;
        Map<String, String> knownItemCache = new HashMap<>();
        for (ShapedRecipeDef def : defs.shaped()) {
            Identifier id = buildId(def.outputId(), "shaped", def.sourceLoc(), index++);
            if (map.containsKey(id)) {
                continue;
            }
            JsonObject json = buildShaped(def, reporter, knownItemCache);
            if (json != null) {
                map.put(id, json);
                added++;
            }
        }
        for (ShapelessRecipeDef def : defs.shapeless()) {
            Identifier id = buildId(def.outputId(), "shapeless", def.sourceLoc(), index++);
            if (map.containsKey(id)) {
                continue;
            }
            JsonObject json = buildShapeless(def, reporter, knownItemCache);
            if (json != null) {
                map.put(id, json);
                added++;
            }
        }
        if (reporter != null && added > 0) {
            reporter.warnOnce("LegacyRecipe", "injected " + added + " recipes", null);
        }
    }

    private static void sanitizeLegacyAddonRecipes(Map<Identifier, JsonElement> map, LegacyWarnReporter reporter) {
        if (map == null || map.isEmpty()) {
            return;
        }
        Map<String, String> fuzzyItemCache = new HashMap<>();
        Iterator<Map.Entry<Identifier, JsonElement>> it = map.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<Identifier, JsonElement> entry = it.next();
            Identifier id = entry.getKey();
            if (id == null || !"relootplusplus".equals(id.getNamespace())) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (!(value instanceof JsonObject json)) {
                continue;
            }
            if (!sanitizeRecipeJson(json, reporter, fuzzyItemCache)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0 && reporter != null) {
            reporter.warnOnce("LegacyRecipe", "removed " + removed + " invalid addon recipes", null);
        }
    }

    private static boolean sanitizeRecipeJson(JsonObject json, LegacyWarnReporter reporter, Map<String, String> fuzzyItemCache) {
        JsonObject result = json.has("result") && json.get("result").isJsonObject() ? json.getAsJsonObject("result") : null;
        if (result == null) {
            return false;
        }
        if (!sanitizeItemField(result, "item", reporter, fuzzyItemCache)) {
            return false;
        }

        JsonObject key = json.has("key") && json.get("key").isJsonObject() ? json.getAsJsonObject("key") : null;
        if (key != null) {
            for (Map.Entry<String, JsonElement> symbolEntry : key.entrySet()) {
                if (!sanitizeIngredient(symbolEntry.getValue(), reporter, fuzzyItemCache)) {
                    return false;
                }
            }
        }

        if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            JsonArray ingredients = json.getAsJsonArray("ingredients");
            JsonArray fixed = new JsonArray();
            for (JsonElement ingredient : ingredients) {
                if (sanitizeIngredient(ingredient, reporter, fuzzyItemCache)) {
                    fixed.add(ingredient);
                }
            }
            if (fixed.size() == 0) {
                return false;
            }
            if (fixed.size() != ingredients.size()) {
                json.add("ingredients", fixed);
            }
        }
        return true;
    }

    private static boolean sanitizeIngredient(JsonElement ingredient, LegacyWarnReporter reporter, Map<String, String> fuzzyItemCache) {
        if (ingredient == null || ingredient.isJsonNull()) {
            return false;
        }
        if (ingredient.isJsonArray()) {
            JsonArray src = ingredient.getAsJsonArray();
            JsonArray fixed = new JsonArray();
            for (JsonElement nested : src) {
                if (sanitizeIngredient(nested, reporter, fuzzyItemCache)) {
                    fixed.add(nested);
                }
            }
            if (fixed.size() == 0) {
                return false;
            }
            if (fixed.size() != src.size()) {
                while (src.size() > 0) {
                    src.remove(0);
                }
                for (JsonElement nested : fixed) {
                    src.add(nested);
                }
            }
            return true;
        }
        if (!ingredient.isJsonObject()) {
            return false;
        }
        JsonObject obj = ingredient.getAsJsonObject();
        if (obj.has("tag")) {
            return true;
        }
        return sanitizeItemField(obj, "item", reporter, fuzzyItemCache);
    }

    private static boolean sanitizeItemField(JsonObject obj, String key, LegacyWarnReporter reporter, Map<String, String> fuzzyItemCache) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return false;
        }
        String rawId = obj.get(key).getAsString();
        String fixed = resolveRecipeItemId(rawId, reporter, fuzzyItemCache);
        if (fixed == null) {
            return false;
        }
        if (!fixed.equals(rawId)) {
            obj.addProperty(key, fixed);
            if (reporter != null) {
                reporter.warnOnce("LegacyRecipeItem", "mapped recipe item " + rawId + " -> " + fixed, null);
            }
        }
        return true;
    }

    private static String resolveRecipeItemId(String rawId, LegacyWarnReporter reporter, Map<String, String> fuzzyItemCache) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String normalized = LegacyEntityIdFixer.normalizeItemId(rawId, reporter, null);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        Identifier parsed = Identifier.tryParse(normalized);
        if (parsed == null) {
            return null;
        }
        if (Registry.ITEM.containsId(parsed)) {
            return parsed.toString();
        }

        String fuzzyKey = parsed.getNamespace() + ":" + fuzzyKey(parsed.getPath());
        if (fuzzyItemCache.containsKey(fuzzyKey)) {
            String cached = fuzzyItemCache.get(fuzzyKey);
            return cached == null || cached.isBlank() ? null : cached;
        }

        String resolved = null;
        for (Identifier candidate : Registry.ITEM.getIds()) {
            if (!candidate.getNamespace().equals(parsed.getNamespace())) {
                continue;
            }
            if (fuzzyKey(candidate.getPath()).equals(fuzzyKey(parsed.getPath()))) {
                resolved = candidate.toString();
                break;
            }
        }
        fuzzyItemCache.put(fuzzyKey, resolved == null ? "" : resolved);
        return resolved;
    }

    private static String fuzzyKey(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else if (Character.isUpperCase(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private static JsonObject buildShaped(ShapedRecipeDef def, LegacyWarnReporter reporter, Map<String, String> knownItemCache) {
        String outputId = normalizeRecipeItem(def.outputId(), def.outputMeta(), reporter, def.sourceLoc(), knownItemCache);
        if (outputId == null) {
            return null;
        }
        List<String> pattern = splitPattern(def.pattern());
        pattern = trimTrailingEmptyRows(pattern, reporter, def.sourceLoc());
        pattern = trimPatternToGrid(pattern, reporter, def.sourceLoc());
        if (pattern.isEmpty()) {
            return null;
        }
        JsonObject root = new JsonObject();
        if (def.outputNbt() != null && !def.outputNbt().isBlank() && !"{}".equals(def.outputNbt().trim())) {
            root.addProperty("type", ModRecipes.NBT_SHAPED_ID.toString());
        } else {
            root.addProperty("type", "minecraft:crafting_shaped");
        }
        JsonArray patternJson = new JsonArray();
        for (String row : pattern) {
            patternJson.add(row);
        }
        root.add("pattern", patternJson);
        JsonObject key = new JsonObject();
        java.util.Set<Character> used = usedSymbols(pattern);
        java.util.Set<Character> present = new java.util.HashSet<>();
        for (RecipeKey recipeKey : def.keys()) {
            char symbol = recipeKey.symbol();
            if (!used.contains(symbol)) {
                continue;
            }
            RecipeInput input = recipeKey.input();
            JsonObject ingredient = ingredientJson(input, reporter, def.sourceLoc(), knownItemCache);
            if (ingredient != null) {
                key.add(String.valueOf(symbol), ingredient);
                present.add(symbol);
            }
        }
        if (!present.containsAll(used)) {
            if (reporter != null) {
                String missing = used.stream()
                    .filter(symbol -> !present.contains(symbol))
                    .map(String::valueOf)
                    .sorted()
                    .reduce((a, b) -> a + "," + b)
                    .orElse("?");
                reporter.warnOnce("LegacyRecipePattern", "pattern references undefined key(s): " + missing, def.sourceLoc());
            }
            return null;
        }
        root.add("key", key);
        root.add("result", resultJson(outputId, def.outputCount(), def.outputNbt(), reporter, def.sourceLoc()));
        return root;
    }

    private static JsonObject buildShapeless(ShapelessRecipeDef def, LegacyWarnReporter reporter, Map<String, String> knownItemCache) {
        String outputId = normalizeRecipeItem(def.outputId(), def.outputMeta(), reporter, def.sourceLoc(), knownItemCache);
        if (outputId == null) {
            return null;
        }
        JsonObject root = new JsonObject();
        if (def.outputNbt() != null && !def.outputNbt().isBlank() && !"{}".equals(def.outputNbt().trim())) {
            root.addProperty("type", ModRecipes.NBT_SHAPELESS_ID.toString());
        } else {
            root.addProperty("type", "minecraft:crafting_shapeless");
        }
        JsonArray ingredients = new JsonArray();
        for (RecipeInput input : def.inputs()) {
            JsonObject ingredient = ingredientJson(input, reporter, def.sourceLoc(), knownItemCache);
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }
        if (ingredients.size() == 0) {
            if (reporter != null) {
                reporter.warnOnce("LegacyRecipe", "dropped shapeless recipe with no valid ingredients", def.sourceLoc());
            }
            return null;
        }
        root.add("ingredients", ingredients);
        root.add("result", resultJson(outputId, def.outputCount(), def.outputNbt(), reporter, def.sourceLoc()));
        return root;
    }

    private static JsonObject ingredientJson(RecipeInput input, LegacyWarnReporter reporter, SourceLoc loc, Map<String, String> knownItemCache) {
        if (input == null) {
            return null;
        }
        JsonObject obj = new JsonObject();
        if (input.oreDict()) {
            String tag = mapOreDict(input.itemId());
            if (tag == null) {
                if (reporter != null) {
                    reporter.warnOnce("LegacyOreDict", "unmapped ore dict " + input.itemId(), loc);
                }
                return null;
            }
            if (reporter != null && tag.startsWith("c:")) {
                reporter.warnOnce("LegacyOreDict", "fallback ore dict " + input.itemId() + " -> " + tag, loc);
            }
            obj.addProperty("tag", tag);
            return obj;
        }
        String id = normalizeRecipeItem(input.itemId(), input.meta(), reporter, loc, knownItemCache);
        if (id == null) {
            return null;
        }
        obj.addProperty("item", id);
        return obj;
    }

    private static JsonObject resultJson(String outputId, int count, String nbtRaw, LegacyWarnReporter reporter, SourceLoc loc) {
        JsonObject result = new JsonObject();
        result.addProperty("item", outputId);
        if (count > 1) {
            result.addProperty("count", count);
        }
        if (nbtRaw != null && !nbtRaw.isBlank() && !"{}".equals(nbtRaw.trim())) {
            result.addProperty("nbt", nbtRaw.trim());
        }
        return result;
    }

    private static String normalizeRecipeItem(String rawId, int meta, LegacyWarnReporter reporter, SourceLoc loc, Map<String, String> knownItemCache) {
        if (rawId == null || rawId.isEmpty()) {
            return null;
        }
        String normalized = LegacyEntityIdFixer.normalizeItemId(rawId, reporter, loc == null ? null : loc.formatShort());
        if (normalized == null) {
            return null;
        }
        if (meta >= 0 && meta <= 15) {
            String wool = mapWoolMeta(normalized, meta);
            if (wool != null) {
                return wool;
            }
            String dye = mapDyeMeta(normalized, meta, reporter, loc);
            if (dye != null) {
                return dye;
            }
        }
        String resolved = resolveKnownRecipeItem(normalized, knownItemCache);
        if (resolved != null) {
            if (!resolved.equals(normalized) && reporter != null) {
                reporter.warnOnce("LegacyRecipeItem", "fuzzy-mapped recipe item " + normalized + " -> " + resolved, loc);
            }
            return resolved;
        }
        if (reporter != null) {
            reporter.warnOnce("LegacyRecipeItem", "missing recipe item " + normalized + " (recipe dropped)", loc);
        }
        return null;
    }

    private static String mapWoolMeta(String id, int meta) {
        if (id == null) {
            return null;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(":wool")) {
            return null;
        }
        String[] dye = new String[] {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };
        int idx = Math.max(0, Math.min(15, meta));
        return "minecraft:" + dye[idx] + "_wool";
    }

    private static String mapDyeMeta(String id, int meta, LegacyWarnReporter reporter, SourceLoc loc) {
        if (id == null) {
            return null;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(":dye")) {
            return null;
        }
        String[] dye = new String[] {
            "black", "red", "green", "brown", "blue", "purple", "cyan", "light_gray",
            "gray", "pink", "lime", "yellow", "light_blue", "magenta", "orange", "white"
        };
        int idx = Math.max(0, Math.min(15, meta));
        if (reporter != null) {
            reporter.warnOnce("LegacyMeta", "mapped dye meta " + meta + " -> " + dye[idx] + "_dye", loc);
        }
        return "minecraft:" + dye[idx] + "_dye";
    }

    private static java.util.Set<Character> usedSymbols(List<String> pattern) {
        java.util.Set<Character> used = new java.util.HashSet<>();
        for (String row : pattern) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ') {
                    used.add(c);
                }
            }
        }
        return used;
    }

    private static String mapOreDict(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String key = token.trim();
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains(":")) {
            return lower;
        }
        return switch (lower) {
            case "plankwood" -> "minecraft:planks";
            case "logwood" -> "minecraft:logs";
            case "treesapling" -> "minecraft:saplings";
            case "ingotiron" -> "minecraft:iron_ingots";
            case "ingotgold" -> "minecraft:gold_ingots";
            case "gemdiamond" -> "minecraft:diamonds";
            case "gememerald" -> "minecraft:emeralds";
            default -> "c:" + camelToSnake(lower);
        };
    }

    private static String camelToSnake(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString().replace("__", "_");
    }

    private static List<String> splitPattern(String pattern) {
        if (pattern == null) {
            return List.of();
        }
        String raw = pattern.trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        String[] parts;
        if (raw.contains("/")) {
            parts = raw.split("/");
        } else if (raw.contains("|")) {
            parts = raw.split("\\|");
        } else if (raw.contains(",")) {
            parts = raw.split(",");
        } else if (raw.length() > 3) {
            List<String> rows = new ArrayList<>();
            int step = raw.length() / 3;
            if (step <= 0) {
                return List.of(raw);
            }
            for (int i = 0; i < raw.length(); i += step) {
                rows.add(raw.substring(i, Math.min(raw.length(), i + step)));
            }
            return rows;
        } else {
            parts = new String[] { raw };
        }
        List<String> rows = new ArrayList<>();
        int max = 0;
        for (String part : parts) {
            String row = part == null ? "" : part;
            rows.add(row);
            max = Math.max(max, row.length());
        }
        if (max == 0) {
            return rows;
        }
        if (max > 3) {
            max = 3;
        }
        for (int i = 0; i < rows.size(); i++) {
            String row = rows.get(i);
            if (row.length() > max) {
                rows.set(i, row.substring(0, max));
                continue;
            }
            if (row.length() < max) {
                rows.set(i, row + " ".repeat(max - row.length()));
            }
        }
        return rows;
    }

    private static List<String> trimTrailingEmptyRows(List<String> rows, LegacyWarnReporter reporter, SourceLoc loc) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        int end = rows.size();
        while (end > 1) {
            String row = rows.get(end - 1);
            if (row != null && row.trim().isEmpty()) {
                end--;
                continue;
            }
            break;
        }
        if (end == rows.size()) {
            return rows;
        }
        if (reporter != null) {
            reporter.warnOnce("LegacyRecipePattern", "trimmed trailing empty pattern rows", loc);
        }
        return new ArrayList<>(rows.subList(0, end));
    }

    private static List<String> trimPatternToGrid(List<String> rows, LegacyWarnReporter reporter, SourceLoc loc) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        if (rows.size() <= 3) {
            return rows;
        }
        if (reporter != null) {
            reporter.warnOnce("LegacyRecipePattern", "trimmed pattern rows to 3x3 grid", loc);
        }
        return new ArrayList<>(rows.subList(0, 3));
    }

    private static String resolveKnownRecipeItem(String id, Map<String, String> knownItemCache) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (knownItemCache != null && knownItemCache.containsKey(id)) {
            String cached = knownItemCache.get(id);
            return cached == null || cached.isBlank() ? null : cached;
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return null;
        }
        if (Registry.ITEM.containsId(parsed)) {
            String direct = parsed.toString();
            if (knownItemCache != null) {
                knownItemCache.put(id, direct);
            }
            return direct;
        }
        String target = fuzzyKey(parsed.getPath());
        for (Identifier candidate : Registry.ITEM.getIds()) {
            if (!candidate.getNamespace().equals(parsed.getNamespace())) {
                continue;
            }
            if (fuzzyKey(candidate.getPath()).equals(target)) {
                String resolved = candidate.toString();
                if (knownItemCache != null) {
                    knownItemCache.put(id, resolved);
                }
                return resolved;
            }
        }
        if (knownItemCache != null) {
            knownItemCache.put(id, "");
        }
        return null;
    }

    private static Identifier buildId(String outputId, String kind, SourceLoc loc, int index) {
        String base = outputId == null ? "recipe" : outputId.replace(':', '_');
        String suffix = kind + "_" + index;
        if (loc != null) {
            suffix = kind + "_" + loc.packId() + "_" + loc.lineNumber();
        }
        String path = sanitizePath(base + "_" + suffix);
        if (path.isEmpty()) {
            path = "recipe_" + index;
        }
        return new Identifier("relootplusplus", path);
    }

    private static String sanitizePath(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
                out.append(c);
            } else if (Character.isUpperCase(c)) {
                out.append(Character.toLowerCase(c));
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }
}
