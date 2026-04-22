package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.recipe.RecipeDefinitions;
import ie.orangep.reLootplusplus.config.model.recipe.RecipeInput;
import ie.orangep.reLootplusplus.config.model.recipe.RecipeKey;
import ie.orangep.reLootplusplus.config.model.recipe.ShapedRecipeDef;
import ie.orangep.reLootplusplus.config.model.recipe.ShapelessRecipeDef;
import ie.orangep.reLootplusplus.config.parse.LineReader;
import ie.orangep.reLootplusplus.config.parse.NumberParser;
import ie.orangep.reLootplusplus.config.parse.Splitter;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecipesLoader {
    private static final String SHAPELESS = "config/recipes/add_shapeless.txt";
    private static final String SHAPED = "config/recipes/add_shaped.txt";
    private static final String LEGACY = "recipes.txt";
    private static final String LEGACY_ALT = "config/recipes.txt";

    private final LegacyWarnReporter warnReporter;

    public RecipesLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public RecipeDefinitions loadAll(List<AddonPack> packs, PackIndex index) {
        RecipeDefinitions defs = new RecipeDefinitions();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> shapelessLines = files.get(SHAPELESS);
            if (shapelessLines != null) {
                for (PackIndex.LineRecord line : shapelessLines) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    ShapelessRecipeDef def = parseShapeless(raw, line.sourceLoc());
                    if (def != null) {
                        defs.addShapeless(def);
                    }
                }
            }
            List<PackIndex.LineRecord> shapedLines = files.get(SHAPED);
            if (shapedLines != null) {
                for (PackIndex.LineRecord line : shapedLines) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    ShapedRecipeDef def = parseShaped(raw, line.sourceLoc());
                    if (def != null) {
                        defs.addShaped(def);
                    }
                }
            }
            List<PackIndex.LineRecord> legacyLines = files.get(LEGACY);
            if (legacyLines == null) {
                legacyLines = files.get(LEGACY_ALT);
            }
            if (legacyLines != null) {
                for (PackIndex.LineRecord line : legacyLines) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    if (!parseLegacy(raw, line.sourceLoc(), defs)) {
                        warnReporter.warn("Parse", "legacy recipe not recognized", line.sourceLoc());
                    }
                }
            }
        }
        Log.info("Loader", "Loaded {} shaped + {} shapeless recipes", defs.shaped().size(), defs.shapeless().size());
        return defs;
    }

    private boolean parseLegacy(String raw, SourceLoc loc, RecipeDefinitions defs) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length >= 9) {
            ShapedRecipeDef shaped = parseShaped(raw, loc);
            if (shaped != null) {
                defs.addShaped(shaped);
                return true;
            }
            ShapelessRecipeDef shapeless = parseShapeless(raw, loc);
            if (shapeless != null) {
                defs.addShapeless(shapeless);
                return true;
            }
            return false;
        }
        if (parts.length >= 7) {
            ShapelessRecipeDef shapeless = parseShapeless(raw, loc);
            if (shapeless != null) {
                defs.addShapeless(shapeless);
                return true;
            }
        }
        return false;
    }

    private ShapelessRecipeDef parseShapeless(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 7) {
            warnReporter.warn("Parse", "shapeless recipe wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String outputId = normalizeItemId(parts[0], loc);
        if (outputId == null) {
            return null;
        }
        int outputCount = NumberParser.parseInt(parts[1], 1, warnReporter, loc, "output_count");
        int outputMeta = NumberParser.parseInt(parts[2], 0, warnReporter, loc, "output_meta");
        if (outputMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + outputMeta, loc);
            outputMeta = 32767;
        }
        String outputNbt = parts[3];
        List<RecipeInput> inputs = new ArrayList<>();
        int remaining = parts.length - 4;
        if (remaining % 3 != 0) {
            warnReporter.warn("Parse", "shapeless recipe inputs not multiple of 3 (" + remaining + ")", loc);
            return null;
        }
        for (int i = 4; i < parts.length; i += 3) {
            RecipeInput input = parseInput(parts[i], parts[i + 1], parts[i + 2], loc);
            if (input != null) {
                inputs.add(input);
            }
        }
        warnReporter.warnOnce("LegacyRecipe", "shapeless recipe", loc);
        return new ShapelessRecipeDef(outputId, outputCount, outputMeta, outputNbt, inputs, loc);
    }

    private ShapedRecipeDef parseShaped(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 9) {
            warnReporter.warn("Parse", "shaped recipe wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String outputId = normalizeItemId(parts[0], loc);
        if (outputId == null) {
            return null;
        }
        int outputCount = NumberParser.parseInt(parts[1], 1, warnReporter, loc, "output_count");
        int outputMeta = NumberParser.parseInt(parts[2], 0, warnReporter, loc, "output_meta");
        if (outputMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + outputMeta, loc);
            outputMeta = 32767;
        }
        String outputNbt = parts[3];
        String pattern = parts[4];
        List<RecipeKey> keys = new ArrayList<>();
        int remaining = parts.length - 5;
        if (remaining % 4 != 0) {
            warnReporter.warn("Parse", "shaped recipe keys not multiple of 4 (" + remaining + ")", loc);
            return null;
        }
        for (int i = 5; i < parts.length; i += 4) {
            String symbolRaw = parts[i];
            if (symbolRaw == null || symbolRaw.isEmpty()) {
                warnReporter.warn("Parse", "shaped recipe key symbol empty", loc);
                continue;
            }
            char symbol = symbolRaw.charAt(0);
            RecipeInput input = parseInput(parts[i + 1], parts[i + 2], parts[i + 3], loc);
            if (input != null) {
                keys.add(new RecipeKey(symbol, input));
            }
        }
        warnReporter.warnOnce("LegacyRecipe", "shaped recipe", loc);
        return new ShapedRecipeDef(outputId, outputCount, outputMeta, outputNbt, pattern, keys, loc);
    }

    private RecipeInput parseInput(String tokenRaw, String metaRaw, String nbtRaw, SourceLoc loc) {
        boolean oreDict = false;
        String token = tokenRaw;
        if (token != null && token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            oreDict = true;
            token = token.substring(1, token.length() - 1);
            warnReporter.warnOnce("LegacyOreDict", "ore dict key " + token, loc);
        }
        String itemId = oreDict ? token : normalizeItemId(token, loc);
        if (itemId == null) {
            return null;
        }
        int meta = NumberParser.parseInt(metaRaw, 0, warnReporter, loc, "meta");
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }
        return new RecipeInput(itemId, meta, nbtRaw, oreDict);
    }

    private String normalizeItemId(String raw, SourceLoc loc) {
        if (raw == null) {
            return null;
        }
        if (raw.contains(":")) {
            return validateId(raw, loc);
        }
        warnReporter.warnOnce("LegacyItemId", "missing namespace for " + raw, loc);
        return validateId("minecraft:" + raw, loc);
    }

    private String validateId(String raw, SourceLoc loc) {
        if (net.minecraft.util.Identifier.tryParse(raw) == null) {
            warnReporter.warn("LegacyItemId", "bad item id " + raw, loc);
            return null;
        }
        return raw;
    }
}
