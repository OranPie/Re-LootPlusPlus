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
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void relootplusplus$injectLegacyRecipes(Map<Identifier, JsonElement> map, ResourceManager manager, Profiler profiler, CallbackInfo ci) {
        RecipeDefinitions defs = RuntimeState.recipeDefinitions();
        if (defs == null) {
            return;
        }
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        int added = 0;
        int index = 0;
        for (ShapedRecipeDef def : defs.shaped()) {
            Identifier id = buildId(def.outputId(), "shaped", def.sourceLoc(), index++);
            if (map.containsKey(id)) {
                continue;
            }
            JsonObject json = buildShaped(def, reporter);
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
            JsonObject json = buildShapeless(def, reporter);
            if (json != null) {
                map.put(id, json);
                added++;
            }
        }
        if (reporter != null && added > 0) {
            reporter.warnOnce("LegacyRecipe", "injected " + added + " recipes", null);
        }
    }

    private static JsonObject buildShaped(ShapedRecipeDef def, LegacyWarnReporter reporter) {
        String outputId = normalizeRecipeItem(def.outputId(), def.outputMeta(), reporter, def.sourceLoc());
        if (outputId == null) {
            return null;
        }
        List<String> pattern = splitPattern(def.pattern());
        pattern = trimTrailingEmptyRows(pattern, reporter, def.sourceLoc());
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
        for (RecipeKey recipeKey : def.keys()) {
            char symbol = recipeKey.symbol();
            if (!used.contains(symbol)) {
                continue;
            }
            RecipeInput input = recipeKey.input();
            JsonObject ingredient = ingredientJson(input, reporter, def.sourceLoc());
            if (ingredient != null) {
                key.add(String.valueOf(symbol), ingredient);
            }
        }
        root.add("key", key);
        root.add("result", resultJson(outputId, def.outputCount(), def.outputNbt(), reporter, def.sourceLoc()));
        return root;
    }

    private static JsonObject buildShapeless(ShapelessRecipeDef def, LegacyWarnReporter reporter) {
        String outputId = normalizeRecipeItem(def.outputId(), def.outputMeta(), reporter, def.sourceLoc());
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
            JsonObject ingredient = ingredientJson(input, reporter, def.sourceLoc());
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }
        root.add("ingredients", ingredients);
        root.add("result", resultJson(outputId, def.outputCount(), def.outputNbt(), reporter, def.sourceLoc()));
        return root;
    }

    private static JsonObject ingredientJson(RecipeInput input, LegacyWarnReporter reporter, SourceLoc loc) {
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
        String id = normalizeRecipeItem(input.itemId(), input.meta(), reporter, loc);
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

    private static String normalizeRecipeItem(String rawId, int meta, LegacyWarnReporter reporter, SourceLoc loc) {
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
        return normalized;
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
