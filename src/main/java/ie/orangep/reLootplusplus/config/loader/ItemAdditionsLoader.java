package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.item.ArmorDef;
import ie.orangep.reLootplusplus.config.model.item.ArmorSlotType;
import ie.orangep.reLootplusplus.config.model.item.BowDef;
import ie.orangep.reLootplusplus.config.model.item.FoodDef;
import ie.orangep.reLootplusplus.config.model.item.FoodEffectSpec;
import ie.orangep.reLootplusplus.config.model.item.GenericItemDef;
import ie.orangep.reLootplusplus.config.model.item.GunDef;
import ie.orangep.reLootplusplus.config.model.item.ItemAdditions;
import ie.orangep.reLootplusplus.config.model.item.MaterialDef;
import ie.orangep.reLootplusplus.config.model.item.MultitoolDef;
import ie.orangep.reLootplusplus.config.model.item.SwordDef;
import ie.orangep.reLootplusplus.config.model.item.ToolDef;
import ie.orangep.reLootplusplus.config.model.item.ToolType;
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

public final class ItemAdditionsLoader {
    private static final String DIR = "config/item_additions/";

    private final LegacyWarnReporter warnReporter;

    public ItemAdditionsLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public ItemAdditions loadAll(List<AddonPack> packs, PackIndex index) {
        ItemAdditions additions = new ItemAdditions();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            for (Map.Entry<String, List<PackIndex.LineRecord>> entry : files.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith(DIR) || !path.endsWith(".txt")) {
                    continue;
                }
                String fileName = path.substring(DIR.length());
                if ("thrown.txt".equals(fileName)) {
                    continue;
                }
                for (PackIndex.LineRecord line : entry.getValue()) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    parseLine(fileName, raw, line.sourceLoc(), additions);
                }
            }
        }
        Log.info(
            "Loader",
            "Loaded item additions: generic={}, materials={}, swords={}, tools={}, armor={}, foods={}, bows={}, guns={}, multitools={}",
            additions.genericItems().size(),
            additions.materials().size(),
            additions.swords().size(),
            additions.tools().size(),
            additions.armors().size(),
            additions.foods().size(),
            additions.bows().size(),
            additions.guns().size(),
            additions.multitools().size()
        );
        return additions;
    }

    private void parseLine(String fileName, String raw, SourceLoc loc, ItemAdditions additions) {
        switch (fileName) {
            case "generic_items.txt" -> parseGeneric(raw, loc, additions);
            case "materials.txt" -> parseMaterials(raw, loc, additions);
            case "swords.txt" -> parseSwords(raw, loc, additions);
            case "pickaxes.txt" -> parseTool(raw, loc, additions, ToolType.PICKAXE);
            case "axes.txt" -> parseTool(raw, loc, additions, ToolType.AXE);
            case "shovels.txt" -> parseTool(raw, loc, additions, ToolType.SHOVEL);
            case "hoes.txt" -> parseTool(raw, loc, additions, ToolType.HOE);
            case "helmets.txt" -> parseArmor(raw, loc, additions, ArmorSlotType.HELMET);
            case "chestplates.txt" -> parseArmor(raw, loc, additions, ArmorSlotType.CHESTPLATE);
            case "leggings.txt" -> parseArmor(raw, loc, additions, ArmorSlotType.LEGGINGS);
            case "boots.txt" -> parseArmor(raw, loc, additions, ArmorSlotType.BOOTS);
            case "foods.txt" -> parseFoods(raw, loc, additions);
            case "bows.txt" -> parseBows(raw, loc, additions);
            case "guns.txt" -> parseGuns(raw, loc, additions);
            case "multitools.txt" -> parseMultitools(raw, loc, additions);
            default -> warnReporter.warnOnce("LegacyItemAdditions", "item_additions type " + fileName + " not implemented", loc);
        }
    }

    private void parseGeneric(String raw, SourceLoc loc, ItemAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 2) {
            warnReporter.warn("Parse", "generic_items wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        boolean shiny = parts.length >= 3 && Boolean.parseBoolean(parts[2]);
        additions.addGeneric(new GenericItemDef(itemId, displayName, shiny, loc));
    }

    private void parseMaterials(String raw, SourceLoc loc, ItemAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 9) {
            warnReporter.warn("Parse", "materials wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeItemId(parts[0], loc);
        if (itemId == null) {
            return;
        }
        int meta = NumberParser.parseInt(parts[1], -1, warnReporter, loc, "material_meta");
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }
        int harvestLevel = NumberParser.parseInt(parts[2], 0, warnReporter, loc, "harvest_level");
        int durability = NumberParser.parseInt(parts[3], 0, warnReporter, loc, "durability");
        float efficiency = NumberParser.parseFloat(parts[4], 0.0f, warnReporter, loc, "efficiency");
        float damage = NumberParser.parseFloat(parts[5], 0.0f, warnReporter, loc, "damage");
        int enchantability = NumberParser.parseInt(parts[6], 0, warnReporter, loc, "enchantability");
        int armorDurability = NumberParser.parseInt(parts[7], 0, warnReporter, loc, "armor_durability");
        int[] protection = parseProtection(parts[8], loc);
        additions.addMaterial(new MaterialDef(
            itemId,
            meta,
            harvestLevel,
            durability,
            efficiency,
            damage,
            enchantability,
            armorDurability,
            protection,
            loc
        ));
    }

    private void parseSwords(String raw, SourceLoc loc, ItemAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 4) {
            warnReporter.warn("Parse", "swords wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        String materialId = normalizeItemId(parts[2], loc);
        if (materialId == null) {
            return;
        }
        float damage = NumberParser.parseFloat(parts[3], 0.0f, warnReporter, loc, "damage");
        int meta = -1;
        if (parts.length >= 5) {
            meta = NumberParser.parseInt(parts[4], -1, warnReporter, loc, "material_meta");
        }
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }
        additions.addSword(new SwordDef(itemId, displayName, materialId, meta, damage, loc));
    }

    private void parseTool(String raw, SourceLoc loc, ItemAdditions additions, ToolType type) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 3) {
            warnReporter.warn("Parse", type.name().toLowerCase() + " wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        String materialId = normalizeItemId(parts[2], loc);
        if (materialId == null) {
            return;
        }
        int meta = -1;
        if (parts.length >= 4) {
            meta = NumberParser.parseInt(parts[3], -1, warnReporter, loc, "material_meta");
        }
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }
        additions.addTool(new ToolDef(itemId, displayName, materialId, meta, type, loc));
    }

    private void parseArmor(String raw, SourceLoc loc, ItemAdditions additions, ArmorSlotType slot) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 4) {
            warnReporter.warn("Parse", "armor wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        String materialId = normalizeItemId(parts[2], loc);
        if (materialId == null) {
            return;
        }
        String textureBase = parts[3];
        int meta = -1;
        if (parts.length >= 5) {
            meta = NumberParser.parseInt(parts[4], -1, warnReporter, loc, "material_meta");
        }
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }
        additions.addArmor(new ArmorDef(itemId, displayName, materialId, meta, textureBase, slot, loc));
    }

    private void parseFoods(String raw, SourceLoc loc, ItemAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 8) {
            warnReporter.warn("Parse", "foods wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        boolean shiny = Boolean.parseBoolean(parts[2]);
        int hunger = NumberParser.parseInt(parts[3], 0, warnReporter, loc, "food");
        float saturation = NumberParser.parseFloat(parts[4], 0.0f, warnReporter, loc, "saturation");
        boolean wolvesEat = Boolean.parseBoolean(parts[5]);
        boolean alwaysEdible = Boolean.parseBoolean(parts[6]);
        int timeToEat = NumberParser.parseInt(parts[7], 32, warnReporter, loc, "time_to_eat");
        List<FoodEffectSpec> effects = new ArrayList<>();
        for (int i = 8; i < parts.length; i++) {
            String effectRaw = parts[i];
            if (effectRaw == null || effectRaw.isEmpty()) {
                continue;
            }
            String[] effectParts = Splitter.splitRegex(effectRaw, "-");
            if (effectParts.length != 5) {
                warnReporter.warn("Parse", "food effect wrong parts (" + effectParts.length + ")", loc);
                continue;
            }
            String effectId = effectParts[0];
            int duration = NumberParser.parseInt(effectParts[1], 0, warnReporter, loc, "duration");
            int amplifier = NumberParser.parseInt(effectParts[2], 0, warnReporter, loc, "amplifier");
            float probability = NumberParser.parseFloat(effectParts[3], 1.0f, warnReporter, loc, "probability");
            String particles = effectParts[4];
            effects.add(new FoodEffectSpec(effectId, duration, amplifier, probability, particles));
        }
        additions.addFood(new FoodDef(
            itemId,
            displayName,
            shiny,
            hunger,
            saturation,
            wolvesEat,
            alwaysEdible,
            timeToEat,
            effects,
            loc
        ));
    }

    private void parseBows(String raw, SourceLoc loc, ItemAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 2) {
            warnReporter.warn("Parse", "bows wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        int durability = 0;
        if (parts.length >= 3) {
            durability = NumberParser.parseInt(parts[2], 0, warnReporter, loc, "durability");
        }
        additions.addBow(new BowDef(itemId, displayName, durability, List.of(parts), loc));
    }

    private void parseGuns(String raw, SourceLoc loc, ItemAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 2) {
            warnReporter.warn("Parse", "guns wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        int durability = 0;
        if (parts.length >= 3) {
            durability = NumberParser.parseInt(parts[2], 0, warnReporter, loc, "durability");
        }
        additions.addGun(new GunDef(itemId, displayName, durability, List.of(parts), loc));
    }

    private void parseMultitools(String raw, SourceLoc loc, ItemAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 3) {
            warnReporter.warn("Parse", "multitools wrong parts (" + parts.length + ")", loc);
            return;
        }
        String itemId = normalizeNewItemId(parts[0], loc);
        String displayName = parts[1];
        String toolType = parts[2];
        additions.addMultitool(new MultitoolDef(itemId, displayName, toolType, List.of(parts), loc));
    }

    private int[] parseProtection(String raw, SourceLoc loc) {
        if (raw == null || raw.isEmpty()) {
            return new int[] {0, 0, 0, 0};
        }
        String[] parts = Splitter.splitRegex(raw, "-");
        if (parts.length != 4) {
            warnReporter.warn("Parse", "armor protection wrong parts (" + parts.length + ")", loc);
            return new int[] {0, 0, 0, 0};
        }
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = NumberParser.parseInt(parts[i], 0, warnReporter, loc, "armor_protection");
        }
        return out;
    }

    private String normalizeNewItemId(String raw, SourceLoc loc) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String normalized = sanitizeItemId(value, "lootplusplus", loc);
        if (!normalized.equals(value)) {
            warnReporter.warnOnce("LegacyItemId", "sanitized '" + value + "' -> '" + normalized + "'", loc);
        }
        return normalized;
    }

    private String normalizeItemId(String raw, SourceLoc loc) {
        if (raw == null) {
            return null;
        }
        if (raw.contains(":")) {
            return validateItemId(raw, loc);
        }
        warnReporter.warnOnce("LegacyItemId", "missing namespace for " + raw, loc);
        return validateItemId("minecraft:" + raw, loc);
    }

    private String validateItemId(String raw, SourceLoc loc) {
        String normalized = sanitizeItemId(raw, "minecraft", loc);
        if (!normalized.equals(raw)) {
            warnReporter.warnOnce("LegacyItemId", "sanitized '" + raw + "' -> '" + normalized + "'", loc);
        }
        if (net.minecraft.util.Identifier.tryParse(normalized) == null) {
            warnReporter.warn("LegacyItemId", "bad item id " + raw, loc);
            return null;
        }
        return normalized;
    }

    private String sanitizeItemId(String raw, String defaultNamespace, SourceLoc loc) {
        String trimmed = raw.trim();
        int idx = trimmed.indexOf(':');
        String namespace;
        String path;
        if (idx > 0) {
            namespace = trimmed.substring(0, idx).toLowerCase(java.util.Locale.ROOT);
            path = trimmed.substring(idx + 1);
        } else {
            warnReporter.warnOnce("LegacyItemId", "missing namespace for " + trimmed, loc);
            namespace = defaultNamespace;
            path = trimmed;
        }
        String sanitizedPath = sanitizePath(path);
        return namespace + ":" + sanitizedPath;
    }

    private String sanitizePath(String path) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (net.minecraft.util.Identifier.isPathCharacterValid(c)) {
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
